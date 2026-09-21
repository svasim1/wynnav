package dev.wynnav.render;

import dev.wynnav.WynnavClient;
import dev.wynnav.map.MapTiles;
import dev.wynnav.map.Territories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Draws world-anchored layers (terrain, territory areas) for any {@link MapView} and clip shape. */
public final class MapPainter {
	private MapPainter() {}

	/** World-space bounding box of what the clip shape can show. */
	private static double[] visibleWorldBounds(MapView view, Polygons.Shape clip) {
		double minX = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (int i = 0; i < clip.size(); i++) {
			double wx = view.worldX(clip.x()[i], clip.y()[i]);
			double wz = view.worldZ(clip.x()[i], clip.y()[i]);
			minX = Math.min(minX, wx);
			minZ = Math.min(minZ, wz);
			maxX = Math.max(maxX, wx);
			maxZ = Math.max(maxZ, wz);
		}
		return new double[] {minX, minZ, maxX, maxZ};
	}

	private static Polygons.Shape worldQuad(MapView view, double x0, double z0, double x1, double z1) {
		return Polygons.Shape.of(
			new float[] {view.screenX(x0, z0), view.screenX(x1, z0), view.screenX(x1, z1), view.screenX(x0, z1)},
			new float[] {view.screenY(x0, z0), view.screenY(x1, z0), view.screenY(x1, z1), view.screenY(x0, z1)});
	}

	/** @return false when no tile has finished loading yet. */
	public static boolean drawTiles(GuiGraphics graphics, MapView view, Polygons.Shape clip, float alpha) {
		var tiles = WynnavClient.tiles().tiles();
		if (tiles.isEmpty()) {
			return false;
		}
		double[] b = visibleWorldBounds(view, clip);
		int color = ARGB.white(alpha);
		for (MapTiles.Tile tile : tiles) {
			if (!tile.intersects(b[0], b[1], b[2], b[3])) {
				continue;
			}
			Identifier texture = tile.texture();
			if (texture == null) {
				continue; // still loading
			}
			Polygons.Shape quad = worldQuad(view, tile.x1(), tile.z1(), tile.x2() + 1, tile.z2() + 1);
			Polygons.Shape textured = new Polygons.Shape(quad.x(), quad.y(), new float[] {0, 1, 1, 0}, new float[] {0, 0, 1, 1});
			Polygons.Shape clipped = Polygons.clip(textured, clip);
			if (clipped != null) {
				Polygons.textured(graphics, texture, clipped, color);
			}
		}
		return true;
	}

	public static void drawTerritories(GuiGraphics graphics, MapView view, Polygons.Shape clip) {
		double[] b = visibleWorldBounds(view, clip);
		for (Territories.Territory territory : WynnavClient.territories().territories()) {
			if (territory.maxX() + 1 < b[0] || territory.minX() > b[2] || territory.maxZ() + 1 < b[1] || territory.minZ() > b[3]) {
				continue;
			}
			Polygons.Shape area = Polygons.clip(worldQuad(view, territory.minX(), territory.minZ(), territory.maxX() + 1, territory.maxZ() + 1), clip);
			if (area == null) {
				continue;
			}
			int rgb = territory.color() & 0xFFFFFF;
			Polygons.fill(graphics, area, 0x38000000 | rgb);
			Polygons.outline(graphics, area, 1, 0xC0000000 | rgb);
		}
	}

	/** A filled circle in world space, e.g. a world event's area. */
	public static void drawWorldCircle(GuiGraphics graphics, MapView view, Polygons.Shape clip, double x, double z, double radius, int color) {
		float sx = view.screenX(x, z);
		float sy = view.screenY(x, z);
		float r = (float) (radius * view.zoom());
		if (r < 2) {
			return;
		}
		Polygons.Shape circle = Polygons.clip(Polygons.Shape.circle(sx, sy, r, 32), clip);
		if (circle != null) {
			Polygons.fill(graphics, circle, color);
		}
	}
}
