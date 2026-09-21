package dev.wynnav.hud;

import dev.wynnav.Wynnav;
import dev.wynnav.WynnavClient;
import dev.wynnav.config.Settings;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.map.Territories;
import dev.wynnav.render.MapPainter;
import dev.wynnav.render.Icons;
import dev.wynnav.render.MapView;
import dev.wynnav.render.Polygons;
import dev.wynnav.ui.WorldMapScreen;
import dev.wynnav.waypoint.Waypoint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/** Corner minimap: square or round, north-up or rotating with the player. */
public final class Minimap {
	private static final Identifier PLAYER_ARROW = Wynnav.id("textures/gui/player_arrow.png");
	private static final Identifier WAYPOINT_ICON = Wynnav.id("textures/gui/waypoint.png");
	private static final int MARGIN = 6;
	private static final int ICON = 8;

	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Settings.Minimap settings = Settings.get().minimap;
		if (!settings.enabled || player == null || minecraft.options.hideGui
			|| minecraft.screen instanceof WorldMapScreen || minecraft.gui.getDebugOverlay().showDebugScreen()) {
			return;
		}

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		Vec3 pos = player.getPosition(partialTick);
		float yaw = player.getViewYRot(partialTick);
		boolean round = settings.shape == Settings.MinimapShape.ROUND;
		boolean rotating = settings.rotation == Settings.MinimapRotation.FOLLOW_PLAYER;

		int size = settings.size;
		boolean left = settings.corner == Settings.Corner.TOP_LEFT || settings.corner == Settings.Corner.BOTTOM_LEFT;
		boolean top = settings.corner == Settings.Corner.TOP_LEFT || settings.corner == Settings.Corner.TOP_RIGHT;
		int textLines = (settings.showCoordinates ? 1 : 0) + 1;
		int textHeight = textLines * 10 + 2;
		int x0 = left ? MARGIN : graphics.guiWidth() - MARGIN - size;
		int y0 = top ? MARGIN : graphics.guiHeight() - MARGIN - size - textHeight;
		float cx = x0 + size / 2f;
		float cy = y0 + size / 2f;
		float radius = size / 2f;

		// Rotating view: the direction the player faces points up (yaw 0 faces +Z).
		double rotation = rotating ? Math.toRadians(180 - yaw) : 0;
		MapView view = new MapView(pos.x, pos.z, settings.zoom, rotation, cx, cy);
		Polygons.Shape clip = round ? Polygons.Shape.circle(cx, cy, radius, 48) : Polygons.Shape.rectangle(x0, y0, x0 + size, y0 + size);
		float alpha = (float) settings.opacity;

		Polygons.fill(graphics, clip, ARGB.color(alpha * 0.85f, 0x0E1A26));
		MapPainter.drawTiles(graphics, view, clip, alpha);

		if (settings.showMarkers) {
			for (MapMarkers.Marker marker : WynnavClient.markers().markers()) {
				if (!marker.visibleAt(settings.zoom)) {
					continue;
				}
				float sx = view.screenX(marker.x() + 0.5, marker.z() + 0.5);
				float sy = view.screenY(marker.x() + 0.5, marker.z() + 0.5);
				Identifier icon = WynnavClient.icons().get(marker.icon());
				if (icon != null && insideWithMargin(clip, round, cx, cy, radius, sx, sy)) {
					Icons.draw(graphics, icon, sx, sy, ICON);
				}
			}
		}

		var waypoints = WynnavClient.waypoints();
		for (Waypoint waypoint : waypoints.all()) {
			if (!waypoints.isTracked(waypoint)) {
				drawWaypoint(graphics, view, clip, round, cx, cy, radius, waypoint, false);
			}
		}
		// The tracked target sticks to the edge when out of range, so it always shows the way.
		waypoints.tracked().ifPresent(target -> drawWaypoint(graphics, view, clip, round, cx, cy, radius, target, true));

		drawPlayer(graphics, cx, cy, rotating ? 0 : yaw + 180);
		drawNorth(graphics, minecraft.font, view, round, cx, cy, radius);

		Polygons.outline(graphics, clip, 2, ARGB.color(alpha, 0x101418));
		Polygons.outline(graphics, clip, 1, ARGB.color(alpha, 0x5A6878));

		Font font = minecraft.font;
		int textY = y0 + size + 3;
		if (settings.showCoordinates) {
			String coords = player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
			graphics.drawCenteredString(font, coords, (int) cx, textY, 0xFFE0E0E0);
			textY += 10;
		}
		Territories.Territory territory = WynnavClient.territories().at(pos.x, pos.z);
		if (territory != null) {
			graphics.drawCenteredString(font, territory.name(), (int) cx, textY, 0xFFFFD866);
		}
	}

	private static boolean insideWithMargin(Polygons.Shape clip, boolean round, float cx, float cy, float radius, float sx, float sy) {
		if (round) {
			return Math.hypot(sx - cx, sy - cy) <= radius - ICON / 2f;
		}
		return clip.contains(sx, sy) && sx - ICON / 2f >= clip.x()[0] && sx + ICON / 2f <= clip.x()[1]
			&& sy - ICON / 2f >= clip.y()[0] && sy + ICON / 2f <= clip.y()[2];
	}

	private static void drawWaypoint(GuiGraphics graphics, MapView view, Polygons.Shape clip, boolean round, float cx, float cy,
		float radius, Waypoint waypoint, boolean pinToEdge) {
		float sx = view.screenX(waypoint.x() + 0.5, waypoint.z() + 0.5);
		float sy = view.screenY(waypoint.x() + 0.5, waypoint.z() + 0.5);
		int size = pinToEdge ? 10 : 8;
		if (!insideWithMargin(clip, round, cx, cy, radius, sx, sy)) {
			if (!pinToEdge) {
				return;
			}
			// Move the point toward the center until it fits inside the shape.
			float limit = radius - size / 2f - 1;
			float dx = sx - cx;
			float dy = sy - cy;
			float scale = round
				? limit / (float) Math.hypot(dx, dy)
				: limit / Math.max(Math.abs(dx), Math.abs(dy));
			sx = cx + dx * scale;
			sy = cy + dy * scale;
		}
		Icons.draw(graphics, WAYPOINT_ICON, sx, sy, size, 16, waypoint.color(), 0);
	}

	private static void drawPlayer(GuiGraphics graphics, float cx, float cy, float degrees) {
		Icons.draw(graphics, PLAYER_ARROW, cx, cy, 10, 32, 0xFFFFFFFF, (float) Math.toRadians(degrees));
	}

	private static void drawNorth(GuiGraphics graphics, Font font, MapView view, boolean round, float cx, float cy, float radius) {
		// Direction of north (-Z) on screen, pushed out to the border.
		float dx = view.screenX(view.centerX(), view.centerZ() - 1000) - cx;
		float dy = view.screenY(view.centerX(), view.centerZ() - 1000) - cy;
		float scale = round ? radius / (float) Math.hypot(dx, dy) : radius / Math.max(Math.abs(dx), Math.abs(dy));
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx + dx * scale, cy + dy * scale);
		graphics.fill(-5, -5, 5, 5, 0xE0101418);
		graphics.drawCenteredString(font, "N", 1, -4, 0xFFFF6B6B);
		pose.popMatrix();
	}
}
