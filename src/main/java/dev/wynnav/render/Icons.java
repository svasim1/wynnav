package dev.wynnav.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws icons centered on fractional screen positions. Blitting at rounded coordinates makes icons
 * snap a whole GUI pixel (2-4 real pixels) at a time while the terrain under them moves smoothly,
 * which looks like jumping; translating the pose keeps them in step with the map.
 */
public final class Icons {
	private Icons() {}

	/** A whole texture (any size) scaled to {@code size}. */
	public static void draw(GuiGraphics graphics, Identifier texture, float x, float y, int size) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		graphics.blit(texture, -size / 2, -size / 2, size - size / 2, size - size / 2, 0, 1, 0, 1);
		pose.popMatrix();
	}

	/** A square texture of {@code textureSize} pixels, tinted and optionally rotated (radians). */
	public static void draw(GuiGraphics graphics, Identifier texture, float x, float y, int size, int textureSize, int color, float rotation) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		if (rotation != 0) {
			pose.rotate(rotation);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -size / 2, -size / 2, 0, 0, size, size, textureSize, textureSize,
			textureSize, textureSize, color);
		pose.popMatrix();
	}
}
