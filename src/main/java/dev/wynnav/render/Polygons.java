package dev.wynnav.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Draws convex polygons into the GUI, optionally textured and clipped to another convex polygon.
 * This is what lets the map rotate and be cut to a circle without stencil buffers.
 */
public final class Polygons {
	private Polygons() {}

	/** A convex polygon in screen space with per-vertex texture coordinates. */
	public record Shape(float[] x, float[] y, float[] u, float[] v) {
		public int size() {
			return x.length;
		}

		public static Shape of(float[] x, float[] y) {
			return new Shape(x, y, new float[x.length], new float[x.length]);
		}

		public static Shape rectangle(float x0, float y0, float x1, float y1) {
			return of(new float[] {x0, x1, x1, x0}, new float[] {y0, y0, y1, y1});
		}

		public static Shape circle(float cx, float cy, float radius, int segments) {
			float[] xs = new float[segments];
			float[] ys = new float[segments];
			for (int i = 0; i < segments; i++) {
				double angle = 2 * Math.PI * i / segments;
				xs[i] = cx + (float) (Math.cos(angle) * radius);
				ys[i] = cy + (float) (Math.sin(angle) * radius);
			}
			return of(xs, ys);
		}

		public boolean contains(double px, double py) {
			int n = size();
			int sign = 0;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				double cross = (x[j] - x[i]) * (py - y[i]) - (y[j] - y[i]) * (px - x[i]);
				int s = cross > 0 ? 1 : cross < 0 ? -1 : 0;
				if (s != 0) {
					if (sign != 0 && s != sign) {
						return false;
					}
					sign = s;
				}
			}
			return true;
		}
	}

	/** Clips {@code subject} to the convex {@code clip} polygon (Sutherland-Hodgman), keeping UVs. */
	public static @Nullable Shape clip(Shape subject, Shape clip) {
		float orientation = signedArea(clip) >= 0 ? 1 : -1;
		float[] xs = subject.x();
		float[] ys = subject.y();
		float[] us = subject.u();
		float[] vs = subject.v();
		int n = subject.size();
		for (int e = 0; e < clip.size() && n > 0; e++) {
			float ax = clip.x()[e];
			float ay = clip.y()[e];
			float bx = clip.x()[(e + 1) % clip.size()];
			float by = clip.y()[(e + 1) % clip.size()];
			float[] nx = new float[n * 2];
			float[] ny = new float[n * 2];
			float[] nu = new float[n * 2];
			float[] nv = new float[n * 2];
			int count = 0;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				float di = orientation * ((bx - ax) * (ys[i] - ay) - (by - ay) * (xs[i] - ax));
				float dj = orientation * ((bx - ax) * (ys[j] - ay) - (by - ay) * (xs[j] - ax));
				if (di >= 0) {
					nx[count] = xs[i];
					ny[count] = ys[i];
					nu[count] = us[i];
					nv[count] = vs[i];
					count++;
				}
				if ((di >= 0) != (dj >= 0)) {
					float t = di / (di - dj);
					nx[count] = xs[i] + (xs[j] - xs[i]) * t;
					ny[count] = ys[i] + (ys[j] - ys[i]) * t;
					nu[count] = us[i] + (us[j] - us[i]) * t;
					nv[count] = vs[i] + (vs[j] - vs[i]) * t;
					count++;
				}
			}
			xs = java.util.Arrays.copyOf(nx, count);
			ys = java.util.Arrays.copyOf(ny, count);
			us = java.util.Arrays.copyOf(nu, count);
			vs = java.util.Arrays.copyOf(nv, count);
			n = count;
		}
		return n >= 3 ? new Shape(xs, ys, us, vs) : null;
	}

	private static float signedArea(Shape shape) {
		float area = 0;
		for (int i = 0; i < shape.size(); i++) {
			int j = (i + 1) % shape.size();
			area += shape.x()[i] * shape.y()[j] - shape.x()[j] * shape.y()[i];
		}
		return area;
	}

	public static void fill(GuiGraphics graphics, Shape shape, int color) {
		submit(graphics, RenderPipelines.GUI, TextureSetup.noTexture(), shape, color, false);
	}

	public static void textured(GuiGraphics graphics, Identifier texture, Shape shape, int color) {
		AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
		submit(graphics, RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()), shape, color, true);
	}

	/** An outline drawn as thin quads along each edge. */
	public static void outline(GuiGraphics graphics, Shape shape, float thickness, int color) {
		int n = shape.size();
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float dx = shape.x()[j] - shape.x()[i];
			float dy = shape.y()[j] - shape.y()[i];
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len == 0) {
				continue;
			}
			// Extend along the edge a little so corners join without gaps.
			float ex = dx / len * thickness / 2;
			float ey = dy / len * thickness / 2;
			float ox = -dy / len * thickness / 2;
			float oy = dx / len * thickness / 2;
			float x0 = shape.x()[i] - ex;
			float y0 = shape.y()[i] - ey;
			float x1 = shape.x()[j] + ex;
			float y1 = shape.y()[j] + ey;
			fill(graphics, Shape.of(new float[] {x0 + ox, x1 + ox, x1 - ox, x0 - ox}, new float[] {y0 + oy, y1 + oy, y1 - oy, y0 - oy}), color);
		}
	}

	private static void submit(GuiGraphics graphics, RenderPipeline pipeline, TextureSetup textures, Shape shape, int color, boolean textured) {
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle bounds = bounds(shape).transformMaxBounds(pose);
		graphics.guiRenderState.submitGuiElement(new PolygonState(pipeline, textures, pose, shape, color, textured, bounds));
	}

	private static ScreenRectangle bounds(Shape shape) {
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < shape.size(); i++) {
			minX = Math.min(minX, shape.x()[i]);
			minY = Math.min(minY, shape.y()[i]);
			maxX = Math.max(maxX, shape.x()[i]);
			maxY = Math.max(maxY, shape.y()[i]);
		}
		int x = (int) Math.floor(minX);
		int y = (int) Math.floor(minY);
		return new ScreenRectangle(x, y, (int) Math.ceil(maxX) - x, (int) Math.ceil(maxY) - y);
	}

	private record PolygonState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, Shape shape, int color,
		boolean textured, ScreenRectangle bounds) implements GuiElementRenderState {

		@Override
		public void buildVertices(VertexConsumer consumer) {
			// The GUI pipelines draw quads with back faces culled, so emit vertices in the same
			// winding as vanilla GUI quads (negative signed area on a y-down screen), as a fan of
			// degenerate quads.
			boolean reverse = signedArea(shape) > 0;
			int n = shape.size();
			for (int i = 1; i + 1 < n; i++) {
				int a = reverse ? n - i : i;
				int b = reverse ? n - i - 1 : i + 1;
				vertex(consumer, 0);
				vertex(consumer, a);
				vertex(consumer, b);
				vertex(consumer, b);
			}
		}

		private void vertex(VertexConsumer consumer, int i) {
			VertexConsumer v = consumer.addVertexWith2DPose(pose, shape.x()[i], shape.y()[i]);
			if (textured) {
				v.setUv(shape.u()[i], shape.v()[i]);
			}
			v.setColor(color);
		}

		@Override
		public @Nullable ScreenRectangle scissorArea() {
			return null;
		}
	}
}
