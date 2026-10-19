package com.weaseltracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class WeaselTrackerOverlay extends Overlay
{
	private final Client client;
	private final WeaselTrackerPlugin plugin;
	private final WeaselTrackerConfig config;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	WeaselTrackerOverlay(Client client, WeaselTrackerPlugin plugin, WeaselTrackerConfig config, ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Color color = config.highlightColor();

		final WorldPoint trailEnd = plugin.getTrailEnd();
		if (config.highlightLastTrack() && trailEnd != null)
		{
			final LocalPoint local = LocalPoint.fromWorld(client, trailEnd);
			final Polygon poly = local == null ? null : Perspective.getCanvasTilePoly(client, local);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(graphics, poly, color);
			}
		}

		final TileObject target = plugin.getTarget();
		if (target == null)
		{
			return null;
		}

		modelOutlineRenderer.drawOutline(target, config.outlineWidth(), color, 4);

		if (config.fillClickbox())
		{
			final Shape clickbox = target.getClickbox();
			if (clickbox != null)
			{
				graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(color.getAlpha(), 50)));
				graphics.fill(clickbox);
			}
		}

		return null;
	}
}
