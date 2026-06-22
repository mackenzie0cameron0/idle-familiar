package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Exercises the collapse/expand state machine without realising any Swing
 * window. The constructor tolerates null collaborators so the pure state logic
 * is unit-testable; the on-screen carrot interaction is covered by the manual
 * smoke test in the implementation plan.
 */
public class DesktopPetWindowTest
{
	@Test
	public void startsExpandedByDefault()
	{
		DesktopPetWindow widget = new DesktopPetWindow(null, null, null);
		assertFalse(widget.isCollapsed());
	}

	@Test
	public void collapseAndExpandToggleState()
	{
		DesktopPetWindow widget = new DesktopPetWindow(null, null, null);
		widget.collapse();
		assertTrue(widget.isCollapsed());
		widget.expand();
		assertFalse(widget.isCollapsed());
	}

	@Test
	public void toggleFlipsState()
	{
		DesktopPetWindow widget = new DesktopPetWindow(null, null, null);
		widget.toggleCollapse();
		assertTrue(widget.isCollapsed());
		widget.toggleCollapse();
		assertFalse(widget.isCollapsed());
	}

	@Test
	public void carrotSizeIsCollapsedDimension()
	{
		assertEquals(28, DesktopPetWindow.CARROT_SIZE);
	}

	@Test
	public void returnToGameUsesInjectedFocusAction()
	{
		AtomicInteger calls = new AtomicInteger();
		DesktopPetWindow widget = new DesktopPetWindow(null, null, null, () ->
		{
			calls.incrementAndGet();
			return true;
		});

		assertTrue(widget.tryFocusGameClient());
		assertEquals(1, calls.get());
	}

	@Test
	public void failedFocusActionFallsBack()
	{
		DesktopPetWindow widget = new DesktopPetWindow(null, null, null, () ->
		{
			throw new IllegalStateException("not ready");
		});

		assertFalse(widget.tryFocusGameClient());
	}

	@Test
	public void fallbackFrameTitleAcceptsRuneLiteAndOsrsTitles()
	{
		assertTrue(DesktopPetWindow.isLikelyRuneLiteFrame("RuneLite"));
		assertTrue(DesktopPetWindow.isLikelyRuneLiteFrame("Old School RuneScape"));
		assertFalse(DesktopPetWindow.isLikelyRuneLiteFrame("Idle Familiar"));
		assertFalse(DesktopPetWindow.isLikelyRuneLiteFrame(null));
	}
}
