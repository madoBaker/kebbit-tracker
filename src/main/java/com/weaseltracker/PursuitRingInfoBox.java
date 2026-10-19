package com.weaseltracker;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.infobox.InfoBox;

class PursuitRingInfoBox extends InfoBox
{
	private final WeaselTrackerPlugin plugin;

	PursuitRingInfoBox(BufferedImage image, WeaselTrackerPlugin plugin)
	{
		super(image, plugin);
		this.plugin = plugin;
	}

	@Override
	public String getText()
	{
		final Integer charges = plugin.getRingCharges();
		return charges == null ? "?" : String.valueOf(charges);
	}

	@Override
	public Color getTextColor()
	{
		final Integer charges = plugin.getRingCharges();
		return charges != null && charges <= 1 ? Color.RED : Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		final Integer charges = plugin.getRingCharges();
		return charges == null
			? "Ring of pursuit: charges unknown, right-click the ring and Check it"
			: "Ring of pursuit: " + charges + " charge" + (charges == 1 ? "" : "s") + " left";
	}
}
