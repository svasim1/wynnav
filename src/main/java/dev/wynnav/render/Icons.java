package dev.wynnav.render;

import dev.wynnav.Wynnav;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Pixel-perfect sprite drawing. Every texel covers exactly k x k real screen pixels and sprites are
 * placed on the screen's pixel grid, so pixel art stays crisp at any GUI scale. Positions are only
 * rounded to real pixels (not GUI pixels), so icons still move smoothly with the map.
 */
public final class Icons {
	public static final Identifier PLAYER_ARROW = Wynnav.id("textures/gui/player_arrow.png");
	public static final Identifier WAYPOINT = Wynnav.id("textures/gui/waypoint.png");
	public static final Identifier WAYPOINT_SHADE = Wynnav.id("textures/gui/waypoint_shade.png");
	public static final Identifier WAYPOINT_RING = Wynnav.id("textures/gui/waypoint_ring.png");
	public static final Identifier CAMP = Wynnav.id("textures/gui/camp.png");
	public static final Identifier EVENT = Wynnav.id("textures/gui/event.png");

	private static final int ARROW_TEXTURE = 32;
	private static final int WAYPOINT_SIZE = 9;
	private static final int RING_SIZE = 13;

	private Icons() {}

	public static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	/** Rounds a GUI coordinate to the nearest real screen pixel. */
	public static float snap(float value) {
		int scale = guiScale();
		return Math.round(value * scale) / (float) scale;
	}

	/** Screen pixels per texel that makes a sprite of {@code texels} closest to {@code targetGui} GUI pixels. */
	public static int pixelScale(int texels, float targetGui) {
		return Math.max(1, Math.round(targetGui * guiScale() / texels));
	}

	/**
	 * Draws a {@code w} x {@code h} region of a texture centered on (x, y) with {@code k} screen
	 * pixels per texel.
	 */
	public static void sprite(GuiGraphics graphics, Identifier texture, float x, float y, int u, int v, int w, int h,
		int textureWidth, int textureHeight, int k, int color) {
		int scale = guiScale();
		// Top-left corner on a whole screen pixel; the sprite is a whole number of pixels wide.
		float left = Math.round(x * scale - w * k / 2f) / (float) scale;
		float top = Math.round(y * scale - h * k / 2f) / (float) scale;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(left, top);
		pose.scale(k / (float) scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, u, v, w, h, textureWidth, textureHeight, color);
		pose.popMatrix();
	}

	/** A whole texture of the given size, about {@code targetGui} GUI pixels tall. */
	public static void sprite(GuiGraphics graphics, Identifier texture, float x, float y, int width, int height, float targetGui, int color) {
		sprite(graphics, texture, x, y, 0, 0, width, height, width, height, pixelScale(Math.max(width, height), targetGui), color);
	}

	/**
	 * The player arrow, pointing {@code headingDegrees} clockwise from north. Unlike the other
	 * sprites it rotates smoothly, so it is a smooth (not pixel-art) texture drawn at any angle.
	 */
	public static void playerArrow(GuiGraphics graphics, float x, float y, float headingDegrees, int size) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate((float) Math.toRadians(headingDegrees));
		graphics.blit(RenderPipelines.GUI_TEXTURED, PLAYER_ARROW, -size / 2, -size / 2, 0, 0, size, size, ARROW_TEXTURE, ARROW_TEXTURE,
			ARROW_TEXTURE, ARROW_TEXTURE, 0xFFFFFFFF);
		pose.popMatrix();
	}

	/**
	 * A waypoint diamond in its color, with untinted outline and shading on top; {@code tracked}
	 * adds a gold ring around it.
	 */
	public static void waypoint(GuiGraphics graphics, float x, float y, int color, float targetGui, boolean tracked) {
		int k = pixelScale(WAYPOINT_SIZE, targetGui);
		if (tracked) {
			sprite(graphics, WAYPOINT_RING, x, y, 0, 0, RING_SIZE, RING_SIZE, RING_SIZE, RING_SIZE, k, 0xFFFFFFFF);
		}
		int alpha = color >>> 24;
		sprite(graphics, WAYPOINT, x, y, 0, 0, WAYPOINT_SIZE, WAYPOINT_SIZE, WAYPOINT_SIZE, WAYPOINT_SIZE, k, color);
		sprite(graphics, WAYPOINT_SHADE, x, y, 0, 0, WAYPOINT_SIZE, WAYPOINT_SIZE, WAYPOINT_SIZE, WAYPOINT_SIZE, k,
			(alpha << 24) | 0xFFFFFF);
	}
}
