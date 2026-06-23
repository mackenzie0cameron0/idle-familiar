package com.idlefamiliar;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
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

	/** Side length of the inline minimize / return-to-game control buttons. */
	private static final int CARET_SIZE = 18;

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
	private final BooleanSupplier gameClientFocusAction;
	private JWindow window;
	private Timer repaintTimer;
	/** Wall-clock millis the always-on-top flag was last re-asserted. */
	private long lastTopmostAssertMs;

	/** Whether the widget is collapsed (avatar hidden; the info panel stays visible). */
	private boolean collapsed;

	public DesktopPetWindow(IdleFamiliarPlugin plugin, IdleFamiliarConfig config, ConfigManager configManager)
	{
		this(plugin, config, configManager, null);
	}

	public DesktopPetWindow(IdleFamiliarPlugin plugin, IdleFamiliarConfig config,
		ConfigManager configManager, BooleanSupplier gameClientFocusAction)
	{
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		this.gameClientFocusAction = gameClientFocusAction;
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
	 * Periodically re-assert the window's always-on-top flag. Windows can silently
	 * drop a {@link JWindow}'s topmost state after other always-on-top windows
	 * appear or after long uptime - the reported "stops working after ~10 minutes"
	 * symptom, which toggling the plugin (recreating the window) otherwise fixes.
	 *
	 * <p>{@code setAlwaysOnTop(true)} is a no-op while the property is already
	 * {@code true}, so we flip it off and on to force the native flag to be
	 * re-applied. This does not steal keyboard focus. Throttled to once every few
	 * seconds so it is effectively free on the 100ms repaint timer.
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
		/** Hit area of the return-to-game button, updated each paint (null = hidden). */
		private Rectangle gameButtonBounds;

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
					Point point = event.getPoint();
					// The return-to-game button raises the RuneLite client window.
					if (gameButtonBounds != null && gameButtonBounds.contains(point))
					{
						bringGameClientToFront();
						return;
					}
					// The chevron toggles the avatar; the info panel stays either way.
					if (controlBounds().contains(point))
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

			// One coherent, client-thread-built view of the plugin state to paint from
			// (vitals, label, icon, message), so a paint never mixes values from
			// different ticks. The live animation frame is still pulled separately below.
			WidgetSnapshot snapshot = plugin != null ? plugin.getWidgetSnapshot() : null;

			// The active skill icon rides in the XP/h panel row so it stays visible even
			// when the avatar is collapsed (the panel is drawn in both states).
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

			boolean showGame = config.showGameButton();

			int bodyWidth = avatarWidth + (avatarWidth > 0 && hasPanel ? PANEL_GAP : 0) + panelWidth;
			int bodyHeight = Math.max(avatarHeight, panelHeight);
			int controlsWidth = (showGame ? CARET_SIZE + 6 : 0) + CARET_SIZE;

			int contentWidth = Math.max(Math.max(bodyWidth, controlsWidth), textWidth);
			int contentHeight = CARET_SIZE + 4 + bodyHeight + (hasText ? TEXT_GAP + textHeight : 0);
			contentWidth = Math.max(contentWidth, CARET_SIZE);
			contentHeight = Math.max(contentHeight, CARET_SIZE);

			fitWindow(contentWidth, contentHeight);

			int originX = Math.max(0, (getWidth() - contentWidth) / 2);
			int originY = Math.max(0, (getHeight() - contentHeight) / 2);

			// Controls row across the top, anchored to the LEFT edge (originX is a stable
			// position - the window grows from a fixed top-left), so the collapse chevron
			// stays put regardless of how the panel width changes or whether collapsed.
			// (It used to hug the right edge, which moved as stat text widths changed.)
			int controlsY = originY;
			int controlX = originX;
			caretBounds = new Rectangle(controlX, controlsY, CARET_SIZE, CARET_SIZE);
			// Points down when collapsed (click to restore the avatar), up when expanded.
			drawCaret(g, controlX, controlsY, CARET_SIZE, collapsed);
			controlX += CARET_SIZE + 6;
			gameButtonBounds = null;
			if (showGame)
			{
				gameButtonBounds = new Rectangle(controlX, controlsY, CARET_SIZE, CARET_SIZE);
				drawXButton(g, controlX, controlsY, CARET_SIZE);
			}

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

		/**
		 * Draws one info-panel row: an optional icon (else the dim text label) at the
		 * left, and the OSRS-yellow value right-aligned. The XP/h row passes the active
		 * skill icon so it replaces the "XP/h" label while skilling.
		 */
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
		 * Draws the return-to-game button: an X glyph on the same dark rounded
		 * backing as the minimize chevron. Clicking it raises the RuneLite client.
		 */
		private void drawXButton(Graphics2D g, int x, int y, int size)
		{
			g.setColor(new Color(20, 24, 31, 170));
			g.fillRoundRect(x, y, size, size, 6, 6);
			int pad = Math.max(6, size / 4);
			g.setColor(new Color(235, 235, 235, 235));
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(x + pad, y + pad, x + size - pad, y + size - pad);
			g.drawLine(x + size - pad, y + pad, x + pad, y + size - pad);
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

		/**
		 * Draws an Old School RuneScape style stone panel: warm brown fill with a
		 * light top-left bevel and dark bottom-right bevel, used for the status pill
		 * and the info panel so the widget reads as part of the OSRS interface.
		 */
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

		/**
		 * Raise the RuneLite client window to the front so the user can swap back to
		 * the game in one click. Finds the client frame by its "RuneLite" title via
		 * pure AWT (no version-specific API), de-iconifies and focuses it. No-op if
		 * the frame can't be found.
		 */
		private void bringGameClientToFront()
		{
			if (tryFocusGameClient())
			{
				return;
			}

			for (Frame frame : Frame.getFrames())
			{
				String title = frame.getTitle();
				if (isLikelyRuneLiteFrame(title) && frame.isDisplayable())
				{
					if (frame.getExtendedState() == Frame.ICONIFIED)
					{
						frame.setExtendedState(Frame.NORMAL);
					}
					frame.setVisible(true);
					frame.toFront();
					frame.requestFocus();
					return;
				}
			}
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

		/**
		 * Hit area for the minimize/restore chevron, recomputed each paint. Falls back
		 * to a top-right box before the first paint has positioned the chevron.
		 */
		private Rectangle controlBounds()
		{
			if (caretBounds != null)
			{
				return caretBounds;
			}
			return new Rectangle(Math.max(0, getWidth() - CARET_SIZE), 0, CARET_SIZE, CARET_SIZE);
		}

		/**
		 * Draws a minimal caret (chevron) minimize/restore control inside a
		 * {@code size x size} box at (x, y). Points down to restore (collapsed),
		 * up to minimize (expanded).
		 */
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

	boolean tryFocusGameClient()
	{
		if (gameClientFocusAction == null)
		{
			return false;
		}
		try
		{
			return gameClientFocusAction.getAsBoolean();
		}
		catch (RuntimeException ignored)
		{
			return false;
		}
	}

	static boolean isLikelyRuneLiteFrame(String title)
	{
		if (title == null)
		{
			return false;
		}
		String normalized = title.toLowerCase();
		return normalized.contains("runelite") || normalized.contains("old school runescape");
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
