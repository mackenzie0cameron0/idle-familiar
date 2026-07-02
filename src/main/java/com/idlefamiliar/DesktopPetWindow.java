package com.idlefamiliar;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;

public class DesktopPetWindow
{
	private static final int BASE_AVATAR_SIZE = 64;

	// Layout constants for the transparent widget. The window grows to fit the
	// scaled avatar plus the info panel so nothing clips when the scale changes.
	private static final int MARGIN = 10;
	private static final int TEXT_GAP = 6;
	private static final int TEXT_ROW_HEIGHT = 20;
	private static final int TEXT_PADDING_X = 9;
	private static final int MIN_WIDTH = 80;
	private static final int MIN_HEIGHT = 64;

	/** Side length of the collapsed widget / minimize-control hit area, in pixels. */
	public static final int CARROT_SIZE = 28;

	/** How often to re-assert the window's always-on-top flag, in millis. */
	private static final long TOPMOST_REASSERT_INTERVAL_MS = 4000;

	/** Side length of the inline minimize control button. */
	private static final int CARET_SIZE = 18;

	// --- Attention shimmer ---------------------------------------------------
	/** Full sweep period of the attention shimmer, in millis. */
	private static final int SHIMMER_PERIOD_MS = 2200;
	/** Peak alpha of the shimmer band; kept low for subtlety and further faded by the widget opacity composite. */
	private static final int SHIMMER_MAX_ALPHA = 85;
	/** Enchanted-item gold for the shimmer band. */
	private static final Color SHIMMER_GOLD = new Color(255, 224, 130);

	// Side info-panel (HP / prayer / inventory / XP-hr) metrics, OSRS styled.
	private static final int PANEL_PADDING = 7;
	private static final int PANEL_ROW_HEIGHT = 16;
	/** Minimum gap between a row's label column and its right-aligned value. */
	private static final int PANEL_LABEL_GAP = 14;
	/** Horizontal gap between the avatar and the info panel. */
	private static final int PANEL_GAP = 8;

	// --- OSRS palette ------------------------------------------------------
	// Panels: dark brown inventory wood with a bronze bevel.
	private static final Color OSRS_PANEL = new Color(48, 39, 28);
	private static final Color OSRS_PANEL_DARK = new Color(26, 21, 14);
	private static final Color OSRS_BRONZE = new Color(122, 100, 64);
	private static final Color OSRS_BRONZE_LIGHT = new Color(168, 142, 92);
	private static final Color OSRS_TEXT = new Color(255, 224, 79);
	private static final Color SHADOW = new Color(0, 0, 0, 210);

	private final IdleFamiliarPlugin plugin;
	private final IdleFamiliarConfig config;
	private final ConfigManager configManager;
	private JWindow window;
	private Timer repaintTimer;
	/** Wall-clock millis the always-on-top flag was last re-asserted. */
	private long lastTopmostAssertMs;

	/** Whether the widget is collapsed (avatar hidden; the info panel stays visible). */
	private boolean collapsed;

	public DesktopPetWindow(IdleFamiliarPlugin plugin, IdleFamiliarConfig config, ConfigManager configManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		this.collapsed = config != null && config.widgetCollapsed();
	}

	public void show()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (window != null)
			{
				return;
			}

			window = new JWindow();
			window.setAlwaysOnTop(true);
			window.setBackground(new Color(0, 0, 0, 0));
			window.setContentPane(new PetComponent());
			// The first paint grows the window to fit its content via fitWindow().
			window.setSize(MIN_WIDTH, MIN_HEIGHT);
			window.setLocationRelativeTo(null);
			window.setVisible(true);
			lastTopmostAssertMs = System.currentTimeMillis();

			repaintTimer = new Timer(100, event ->
			{
				window.repaint();
				reassertAlwaysOnTop();
			});
			repaintTimer.start();
		});
	}

	public void hide()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (repaintTimer != null)
			{
				repaintTimer.stop();
				repaintTimer = null;
			}

			if (window != null)
			{
				window.dispose();
				window = null;
			}
		});
	}

	/**
	 * Periodically re-assert always-on-top (Windows can silently drop it after long
	 * uptime). {@code setAlwaysOnTop(true)} is a no-op when already true, so flip it
	 * off/on to force re-apply; throttled, and does not steal focus.
	 */
	private void reassertAlwaysOnTop()
	{
		if (window == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastTopmostAssertMs < TOPMOST_REASSERT_INTERVAL_MS)
		{
			return;
		}
		lastTopmostAssertMs = now;
		if (window.isAlwaysOnTopSupported())
		{
			window.setAlwaysOnTop(false);
			window.setAlwaysOnTop(true);
		}
	}

	public void refreshVisibility()
	{
		show();
	}

	public boolean isCollapsed()
	{
		return collapsed;
	}

	/** Collapse the widget (hide the avatar, keep the info panel) and persist the state. */
	public void collapse()
	{
		setCollapsed(true, true);
	}

	/** Restore the avatar and persist the state. */
	public void expand()
	{
		setCollapsed(false, true);
	}

	void toggleCollapse()
	{
		setCollapsed(!collapsed, true);
	}

	private void setCollapsed(boolean value, boolean persist)
	{
		if (collapsed == value)
		{
			applyWindowSize();
			return;
		}
		collapsed = value;
		if (persist && configManager != null)
		{
			configManager.setConfiguration(IdleFamiliarConfig.GROUP, "widgetCollapsed", value);
		}
		applyWindowSize();
	}

	/** Nudge a repaint; the next paint's {@code fitWindow} resizes to the new content. */
	private void applyWindowSize()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (window != null)
			{
				window.repaint();
			}
		});
	}

	private final class PetComponent extends JComponent
	{
		private Point dragOffset;
		/** Hit area of the minimize/restore chevron, updated each paint. */
		private Rectangle caretBounds;

		private PetComponent()
		{
			MouseAdapter handler = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					dragOffset = event.getPoint();
				}

				@Override
				public void mouseDragged(MouseEvent event)
				{
					if (dragOffset != null && window != null)
					{
						window.setLocation(event.getXOnScreen() - dragOffset.x, event.getYOnScreen() - dragOffset.y);
					}
				}

				@Override
				public void mouseClicked(MouseEvent event)
				{
					// The chevron toggles the avatar; the info panel stays either way.
					if (controlBounds().contains(event.getPoint()))
					{
						toggleCollapse();
					}
				}

				@Override
				public void mouseEntered(MouseEvent event)
				{
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent event)
				{
					repaint();
				}
			};
			addMouseListener(handler);
			addMouseMotionListener(handler);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(MIN_WIDTH, MIN_HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			float alpha = clampAlpha(config != null ? config.avatarOpacityPercent() / 100.0f : 1.0f);
			FontMetrics metrics = g.getFontMetrics();

			// One client-thread-built view to paint from, so a paint never mixes ticks.
			// The live animation frame is pulled separately below.
			WidgetSnapshot snapshot = plugin != null ? plugin.getWidgetSnapshot() : null;

			// The skill icon rides in the XP/h row so it stays visible when collapsed.
			BufferedImage skillIcon = (config != null && config.showSkillIcon() && snapshot != null)
				? snapshot.getSkillIcon() : null;

			// Avatar is hidden while collapsed; the info panel is drawn in both states.
			BufferedImage frame = (!collapsed && plugin != null) ? plugin.getCurrentFrame() : null;
			int avatarWidth = 0;
			int avatarHeight = 0;
			if (frame != null)
			{
				double scale = avatarScaleMultiplier() * (BASE_AVATAR_SIZE / (double) frame.getHeight());
				avatarWidth = Math.max(1, (int) Math.round(frame.getWidth() * scale));
				avatarHeight = Math.max(1, (int) Math.round(frame.getHeight() * scale));
			}

			// Build the info-panel rows (label, value) honouring the per-stat toggles.
			boolean loggedInish = snapshot != null && snapshot.getMaxHitpoints() > 0;
			String[][] rows = new String[5][];
			int rowCount = 0;
			if (loggedInish && config.showHpOrb())
			{
				rows[rowCount++] = new String[]{"HP", snapshot.getHitpoints() + "/" + snapshot.getMaxHitpoints()};
			}
			if (loggedInish && config.showPrayerOrb())
			{
				rows[rowCount++] = new String[]{"Pray", snapshot.getPrayer() + "/" + snapshot.getMaxPrayer()};
			}
			if (loggedInish && config.showInventoryCount())
			{
				rows[rowCount++] = new String[]{"Inv", snapshot.getInventoryCount() + "/28"};
			}
			if (config.showAdvancedInfo())
			{
				String xpHr = snapshot != null ? snapshot.getXpHr() : null;
				rows[rowCount++] = new String[]{"XP/h", xpHr != null ? xpHr : "-"};
			}

			int labelWidth = 0;
			int valueWidth = 0;
			for (int i = 0; i < rowCount; i++)
			{
				labelWidth = Math.max(labelWidth, metrics.stringWidth(rows[i][0]));
				valueWidth = Math.max(valueWidth, metrics.stringWidth(rows[i][1]));
			}
			boolean hasPanel = rowCount > 0;
			int panelInnerWidth = labelWidth + PANEL_LABEL_GAP + valueWidth;
			int panelWidth = hasPanel ? panelInnerWidth + PANEL_PADDING * 2 : 0;
			int panelHeight = hasPanel ? rowCount * PANEL_ROW_HEIGHT + PANEL_PADDING * 2 : 0;

			// Optional status text under the avatar (only meaningful when expanded).
			String message = (!collapsed && config.showSpeechBubble() && snapshot != null) ? snapshot.getMessage() : null;
			boolean hasText = message != null && !message.isEmpty();
			int textWidth = hasText ? metrics.stringWidth(message) + TEXT_PADDING_X * 2 : 0;
			int textHeight = hasText ? TEXT_ROW_HEIGHT : 0;

			int bodyWidth = avatarWidth + (avatarWidth > 0 && hasPanel ? PANEL_GAP : 0) + panelWidth;
			int bodyHeight = Math.max(avatarHeight, panelHeight);
			int controlsWidth = CARET_SIZE;

			int contentWidth = Math.max(Math.max(bodyWidth, controlsWidth), textWidth);
			int contentHeight = CARET_SIZE + 4 + bodyHeight + (hasText ? TEXT_GAP + textHeight : 0);
			contentWidth = Math.max(contentWidth, CARET_SIZE);
			contentHeight = Math.max(contentHeight, CARET_SIZE);

			fitWindow(contentWidth, contentHeight);

			int originX = Math.max(0, (getWidth() - contentWidth) / 2);
			int originY = Math.max(0, (getHeight() - contentHeight) / 2);

			// Controls row anchored to the LEFT edge (stable originX), so the chevron
			// stays put regardless of panel-width or collapse changes.
			int controlsY = originY;
			int controlX = originX;
			caretBounds = new Rectangle(controlX, controlsY, CARET_SIZE, CARET_SIZE);
			// Points down when collapsed (click to restore the avatar), up when expanded.
			drawCaret(g, controlX, controlsY, CARET_SIZE, collapsed);

			int bodyY = controlsY + CARET_SIZE + 4;
			int bodyX = originX + (contentWidth - bodyWidth) / 2;

			// Avatar (left), faded by the opacity setting.
			if (frame != null)
			{
				int avatarY = bodyY + (bodyHeight - avatarHeight) / 2;
				Composite original = g.getComposite();
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
				g.drawImage(frame, bodyX, avatarY, avatarWidth, avatarHeight, null);
				g.setComposite(original);
			}

			// Info panel (right of the avatar), also faded by the opacity setting.
			if (hasPanel)
			{
				int panelX = bodyX + avatarWidth + (avatarWidth > 0 ? PANEL_GAP : 0);
				int panelY = bodyY + (bodyHeight - panelHeight) / 2;
				Composite original = g.getComposite();
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
				drawStonePanel(g, panelX, panelY, panelWidth, panelHeight);
				int rowY = panelY + PANEL_PADDING;
				for (int i = 0; i < rowCount; i++)
				{
					BufferedImage rowIcon = "XP/h".equals(rows[i][0]) ? skillIcon : null;
					drawPanelRow(g, rows[i][0], rows[i][1], rowIcon, panelX + PANEL_PADDING, rowY, panelInnerWidth, metrics);
					rowY += PANEL_ROW_HEIGHT;
				}
				// Attention shimmer: drawn inside the opacity composite so it fades
				// with the rest of the panel. Purely decorative — it only reads the
				// snapshot and paints on this window; it never touches the client.
				if (config.showAttentionShimmer() && snapshot != null && snapshot.isShimmerActive())
				{
					drawShimmer(g, panelX, panelY, panelWidth, panelHeight);
				}
				g.setComposite(original);
			}

			// Status pill under the body (faded with the rest of the panel).
			if (hasText)
			{
				int textY = bodyY + bodyHeight + TEXT_GAP;
				int textX = originX + (contentWidth - textWidth) / 2;
				Composite original = g.getComposite();
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
				drawActionText(g, message, metrics, textX, textY, textWidth, textHeight);
				g.setComposite(original);
			}

			g.dispose();
		}

		/** Draws one info-panel row: optional icon (else dim label) left, OSRS-yellow value right-aligned. */
		private void drawPanelRow(Graphics2D g, String label, String value, BufferedImage icon,
			int x, int top, int innerWidth, FontMetrics metrics)
		{
			int baseline = top + metrics.getAscent();
			if (icon != null)
			{
				int size = PANEL_ROW_HEIGHT - 2;
				int iconY = top + (PANEL_ROW_HEIGHT - size) / 2;
				g.drawImage(icon, x, iconY, size, size, null);
			}
			else
			{
				g.setColor(SHADOW);
				g.drawString(label, x + 1, baseline + 1);
				g.setColor(OSRS_BRONZE_LIGHT);
				g.drawString(label, x, baseline);
			}

			int valueX = x + innerWidth - metrics.stringWidth(value);
			g.setColor(SHADOW);
			g.drawString(value, valueX + 1, baseline + 1);
			g.setColor(OSRS_TEXT);
			g.drawString(value, valueX, baseline);
		}

		/**
		 * Draws the attention shimmer: a subtle diagonal golden band (an enchanted-item
		 * gleam) sweeping across the info panel, clipped to the panel's rounded rect.
		 * The 100&nbsp;ms repaint timer drives the animation; no extra timer is needed.
		 */
		private void drawShimmer(Graphics2D g, int x, int y, int w, int h)
		{
			double phase = (System.currentTimeMillis() % SHIMMER_PERIOD_MS) / (double) SHIMMER_PERIOD_MS;
			int band = Math.max(18, (w + h) / 3);

			// The band's centre line satisfies (px - x) + (py - y) = u, sweeping from
			// fully off the top-left corner to fully off the bottom-right corner.
			float u = (float) (phase * (w + h + 2 * band)) - band;
			float cx = x + u / 2f;
			float cy = y + u / 2f;
			// Half the band length along the (1,1) gradient axis.
			float k = (float) (band / (2 * Math.sqrt(2)));

			Color edge = new Color(SHIMMER_GOLD.getRed(), SHIMMER_GOLD.getGreen(), SHIMMER_GOLD.getBlue(), 0);
			Color core = new Color(SHIMMER_GOLD.getRed(), SHIMMER_GOLD.getGreen(), SHIMMER_GOLD.getBlue(), SHIMMER_MAX_ALPHA);
			LinearGradientPaint shimmer = new LinearGradientPaint(
				new Point2D.Float(cx - k, cy - k),
				new Point2D.Float(cx + k, cy + k),
				new float[]{0f, 0.5f, 1f},
				new Color[]{edge, core, edge});

			Shape oldClip = g.getClip();
			Paint oldPaint = g.getPaint();
			// Match drawStonePanel's rounded rect so the gleam never bleeds outside the bevel.
			g.clip(new RoundRectangle2D.Float(x, y, w, h, 5, 5));
			g.setPaint(shimmer);
			g.fillRect(x, y, w, h);
			g.setPaint(oldPaint);
			g.setClip(oldClip);
		}

		/** Draws the action/status text on an OSRS stone panel with left edge at {@code x}. */
		private void drawActionText(Graphics2D graphics, String message, FontMetrics metrics,
			int x, int top, int bubbleWidth, int bubbleHeight)
		{
			drawStonePanel(graphics, x, top, bubbleWidth, bubbleHeight);
			int baseline = top + (bubbleHeight - metrics.getHeight()) / 2 + metrics.getAscent();
			graphics.setColor(SHADOW);
			graphics.drawString(message, x + TEXT_PADDING_X + 1, baseline + 1);
			graphics.setColor(new Color(255, 255, 255, 240));
			graphics.drawString(message, x + TEXT_PADDING_X, baseline);
		}

		/** Draws an OSRS-style stone panel: warm brown fill with a light top-left and dark bottom-right bevel. */
		private void drawStonePanel(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(OSRS_PANEL);
			g.fillRoundRect(x, y, w, h, 5, 5);
			g.setStroke(new BasicStroke(1f));
			g.setColor(OSRS_BRONZE_LIGHT);
			g.drawLine(x + 1, y + 1, x + w - 2, y + 1);
			g.drawLine(x + 1, y + 1, x + 1, y + h - 2);
			g.setColor(OSRS_BRONZE);
			g.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
			g.drawLine(x + w - 2, y + 1, x + w - 2, y + h - 2);
			g.setColor(OSRS_PANEL_DARK);
			g.drawRoundRect(x, y, w - 1, h - 1, 5, 5);
		}

		private void fitWindow(int contentWidth, int contentHeight)
		{
			int targetWidth = Math.max(MIN_WIDTH, contentWidth + MARGIN * 2);
			int targetHeight = Math.max(MIN_HEIGHT, contentHeight + MARGIN * 2);
			ensureWindowSize(targetWidth, targetHeight);
		}

		private void ensureWindowSize(int targetWidth, int targetHeight)
		{
			if (window != null && (window.getWidth() != targetWidth || window.getHeight() != targetHeight))
			{
				// Resize off the paint pass to avoid layout-during-paint issues.
				SwingUtilities.invokeLater(() ->
				{
					if (window != null)
					{
						window.setSize(targetWidth, targetHeight);
					}
				});
			}
		}

		/** Hit area for the chevron, recomputed each paint; falls back to a top-right box before the first paint. */
		private Rectangle controlBounds()
		{
			if (caretBounds != null)
			{
				return caretBounds;
			}
			return new Rectangle(Math.max(0, getWidth() - CARET_SIZE), 0, CARET_SIZE, CARET_SIZE);
		}

		/** Draws the chevron minimize/restore control; points down to restore (collapsed), up to minimize (expanded). */
		private void drawCaret(Graphics2D graphics, int x, int y, int size, boolean pointDown)
		{
			graphics.setColor(new Color(20, 24, 31, 170));
			graphics.fillRoundRect(x, y, size, size, 6, 6);

			int pad = Math.max(6, size / 4);
			int left = x + pad;
			int right = x + size - pad;
			int cx = (left + right) / 2;
			int top = y + pad;
			int bottom = y + size - pad;

			graphics.setColor(new Color(235, 235, 235, 235));
			graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			if (pointDown)
			{
				graphics.drawLine(left, top, cx, bottom);
				graphics.drawLine(cx, bottom, right, top);
			}
			else
			{
				graphics.drawLine(left, bottom, cx, top);
				graphics.drawLine(cx, top, right, bottom);
			}
		}
	}

	private static float clampAlpha(float value)
	{
		if (value < 0f)
		{
			return 0f;
		}
		return Math.min(1f, value);
	}

	private double avatarScaleMultiplier()
	{
		ScaleMultiplier scale = config == null ? null : config.avatarScale();
		return scale == null ? ScaleMultiplier.X2.multiplier() : scale.multiplier();
	}
}
