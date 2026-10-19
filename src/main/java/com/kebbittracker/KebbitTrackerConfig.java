package com.kebbittracker;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KebbitTrackerConfig.GROUP)
public interface KebbitTrackerConfig extends Config
{
	String GROUP = "kebbittracker";
	String RING_CHARGES_KEY = "pursuitRingCharges";

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Color of the outline around the hiding spot at the end of the track",
		position = 0
	)
	default Color highlightColor()
	{
		return Color.CYAN;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline width",
		description = "Width of the outline around the hiding spot",
		position = 1
	)
	default int outlineWidth()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "fillClickbox",
		name = "Fill clickbox",
		description = "Also shade the clickbox of the hiding spot",
		position = 2
	)
	default boolean fillClickbox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRingCharges",
		name = "Ring of pursuit charges",
		description = "Show an infobox with the charges left while wearing a ring of pursuit",
		position = 4
	)
	default boolean showRingCharges()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nooseWarning",
		name = "Noose wand warning",
		description = "Warn above the hiding spot when following a track without a noose wand equipped",
		position = 5
	)
	default boolean nooseWarning()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightLastTrack",
		name = "Highlight last track",
		description = "Also mark the tile of the newest piece of track",
		position = 3
	)
	default boolean highlightLastTrack()
	{
		return false;
	}
}
