package dev.wynnav.hud;

import dev.wynnav.WynnavClient;
import dev.wynnav.config.Settings;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.map.MarkerIcons;
import dev.wynnav.map.Territories;
import dev.wynnav.render.MapPainter;
import dev.wynnav.render.Icons;
import dev.wynnav.render.MapView;
import dev.wynnav.render.Polygons;
import dev.wynnav.social.PlayerHeads;
import dev.wynnav.ui.WorldMapScreen;
import dev.wynnav.waypoint.Waypoint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/** Corner minimap: square or round, north-up or rotating with the player. */
public final class Minimap {
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
		int textHeight = textLines * 10 + 7;
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
				MarkerIcons.Icon icon = WynnavClient.icons().get(marker.icon());
				if (icon != null && insideWithMargin(clip, round, cx, cy, radius, sx, sy)) {
					Icons.sprite(graphics, icon.texture(), sx, sy, icon.width(), icon.height(), ICON, 0xFFFFFFFF);
				}
			}
		}

		for (PlayerHeads.Placed placed : PlayerHeads.visible(partialTick)) {
			float sx = view.screenX(placed.x(), placed.z());
			float sy = view.screenY(placed.x(), placed.z());
			if (insideWithMargin(clip, round, cx, cy, radius, sx, sy)) {
				PlayerHeads.draw(graphics, placed, sx, sy, 8);
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
		Polygons.outline(graphics, clip, 2, ARGB.color(alpha, 0x101418));
		Polygons.outline(graphics, clip, 1, ARGB.color(alpha, 0x5A6878));
		// The letters sit on the border, so they get their own layer on top of it.
		graphics.nextStratum();
		drawCompass(graphics, minecraft.font, view, round, cx, cy, radius);

		Font font = minecraft.font;
		int textY = y0 + size + 8; // clear of the S compass tile on the bottom edge
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
		Icons.waypoint(graphics, sx, sy, waypoint.color(), size, false);
	}

	private static void drawPlayer(GuiGraphics graphics, float cx, float cy, float degrees) {
		Icons.playerArrow(graphics, cx, cy, degrees, 10);
	}

	private record Cardinal(String letter, int dx, int dz, int color) {}

	private static final Cardinal[] CARDINALS = {
		new Cardinal("N", 0, -1, 0xFFFF6B6B),
		new Cardinal("E", 1, 0, 0xFFFFFFFF),
		new Cardinal("S", 0, 1, 0xFFFFFFFF),
		new Cardinal("W", -1, 0, 0xFFFFFFFF),
	};

	/**
	 * Unit vector on screen for a world direction (+X east, +Z south), so the compass letters
	 * follow the map when it rotates.
	 */
	public static float[] screenDirection(MapView view, int worldDx, int worldDz) {
		float dx = view.screenX(view.centerX() + worldDx, view.centerZ() + worldDz) - view.screenX(view.centerX(), view.centerZ());
		float dy = view.screenY(view.centerX() + worldDx, view.centerZ() + worldDz) - view.screenY(view.centerX(), view.centerZ());
		float length = (float) Math.hypot(dx, dy);
		return new float[] {dx / length, dy / length};
	}

	private static void drawCompass(GuiGraphics graphics, Font font, MapView view, boolean round, float cx, float cy, float radius) {
		var pose = graphics.pose();
		for (Cardinal cardinal : CARDINALS) {
			float[] dir = screenDirection(view, cardinal.dx(), cardinal.dz());
			// Out to the border: the circle's edge, or the square's edge along that direction.
			float scale = round ? radius : radius / Math.max(Math.abs(dir[0]), Math.abs(dir[1]));
			pose.pushMatrix();
			pose.translate(Icons.snap(cx + dir[0] * scale), Icons.snap(cy + dir[1] * scale));
			graphics.fill(-6, -6, 6, 6, 0xFF5A6878);
			graphics.fill(-5, -5, 5, 5, 0xFF101418);
			graphics.drawCenteredString(font, cardinal.letter(), 1, -4, cardinal.color());
			pose.popMatrix();
		}
	}
}
