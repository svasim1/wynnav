package dev.wynnav.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wynnav.config.Settings;
import dev.wynnav.render.Icons;
import dev.wynnav.waypoint.Waypoint;
import dev.wynnav.waypoint.Waypoints;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * The marker you follow in the world: a light beam at the tracked waypoint plus a floating icon
 * with name and distance that shows through walls. Tracking stops when you arrive.
 */
public final class WorldMarker {
	private static final double ARRIVAL_DISTANCE = 4;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float BEAM_HEIGHT = 1024;

	private final Waypoints waypoints;

	public WorldMarker(Waypoints waypoints) {
		this.waypoints = waypoints;
	}

	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		Waypoint target = waypoints.tracked().orElse(null);
		if (player != null && target != null
			&& Math.hypot(target.x() + 0.5 - player.getX(), target.z() + 0.5 - player.getZ()) < ARRIVAL_DISTANCE) {
			waypoints.track(null);
		}
	}

	// ---------------------------------------------------------------- beam (world pass)

	public void renderBeam(WorldRenderContext context) {
		Settings.WorldMarker settings = Settings.get().worldMarker;
		Waypoint target = waypoints.tracked().orElse(null);
		Minecraft minecraft = Minecraft.getInstance();
		if (!settings.showBeam || target == null || minecraft.level == null) {
			return;
		}

		Camera camera = context.gameRenderer().getMainCamera();
		Vec3 cam = camera.position();
		double dx = target.x() + 0.5 - cam.x;
		double dz = target.z() + 0.5 - cam.z;
		double distance = Math.hypot(dx, dz);

		// Past the render distance the beam would be cut by the far plane; draw it closer instead,
		// in the same direction, so it still marks the way.
		double maxDistance = minecraft.options.getEffectiveRenderDistance() * 16 * 0.8;
		double drawDistance = Math.min(distance, maxDistance);
		double scale = distance > 0 ? drawDistance / distance : 0;
		float bottom = target.y() != null && distance <= maxDistance ? target.y() : minecraft.level.getMinY();

		// Wider when far away so it stays visible instead of shrinking to a hairline.
		float radius = (float) (0.2 * Math.max(1, drawDistance / 48));
		int color = ARGB.color((float) settings.opacity, target.color() & 0xFFFFFF);
		float time = (minecraft.level.getGameTime() % 4000) + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float scroll = -(time * 0.02f % 1f);

		PoseStack pose = context.matrices();
		pose.pushPose();
		pose.translate(dx * scale, bottom - cam.y, dz * scale);
		context.commandQueue().submitCustomGeometry(pose, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(p, consumer) -> beamPrism(p, consumer, radius, BEAM_HEIGHT, color, scroll));
		pose.popPose();
	}

	private static void beamPrism(PoseStack.Pose pose, VertexConsumer consumer, float r, float height, int color, float scroll) {
		float v0 = scroll;
		float v1 = scroll + height / (r * 8);
		float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
		for (int i = 0; i < 4; i++) {
			float[] a = corners[i];
			float[] b = corners[(i + 1) % 4];
			vertex(pose, consumer, a[0], height, a[1], 0, v0, color);
			vertex(pose, consumer, a[0], 0, a[1], 0, v1, color);
			vertex(pose, consumer, b[0], 0, b[1], 1, v1, color);
			vertex(pose, consumer, b[0], height, b[1], 1, v0, color);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float u, float v, int color) {
		consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
			.setNormal(pose, 0, 1, 0);
	}

	// ---------------------------------------------------------------- floating label (HUD pass)

	public void renderLabel(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Settings.WorldMarker settings = Settings.get().worldMarker;
		Minecraft minecraft = Minecraft.getInstance();
		Waypoint target = waypoints.tracked().orElse(null);
		LocalPlayer player = minecraft.player;
		if (!settings.showIcon || target == null || player == null || minecraft.options.hideGui) {
			return;
		}

		Camera camera = minecraft.gameRenderer.getMainCamera();
		Vec3 cam = camera.position();
		// Float the label a little above the target; without a known height use eye level.
		double y = target.y() != null ? target.y() + 2.5 : cam.y;
		Vec3 point = new Vec3(target.x() + 0.5, y, target.z() + 0.5);
		Vec3 toPoint = point.subtract(cam);
		Vector3fc forward = camera.forwardVector();
		boolean behind = toPoint.x * forward.x() + toPoint.y * forward.y() + toPoint.z * forward.z() <= 0;

		Vec3 ndc = minecraft.gameRenderer.projectPointToScreen(point);
		double nx = ndc.x;
		double ny = ndc.y;
		if (behind) {
			// Projection mirrors points behind the camera; flip and push them to the screen edge.
			nx = -nx;
			ny = -ny;
		}
		boolean offScreen = behind || Math.abs(nx) > 0.95 || Math.abs(ny) > 0.9;
		if (offScreen) {
			double scale = 1 / Math.max(Math.abs(nx) / 0.95, Math.abs(ny) / 0.9);
			if (Double.isFinite(scale)) {
				nx *= scale;
				ny *= scale;
			}
		}
		float alpha = (float) Math.max(0.1, settings.opacity);
		float size = (float) settings.size;
		// Keep the whole icon + label box on screen (icon above the point, label below it).
		float sx = Mth.clamp((float) ((nx + 1) / 2 * graphics.guiWidth()), 40 * size, graphics.guiWidth() - 40 * size);
		float sy = Mth.clamp((float) ((1 - ny) / 2 * graphics.guiHeight()), 8 * size, graphics.guiHeight() - 31 * size);
		Font font = minecraft.font;
		String name = target.name();
		String distance = Math.round(target.distanceFrom(player.getX(), player.getY(), player.getZ())) + "m";

		Icons.waypoint(graphics, sx, sy, ARGB.color(alpha, target.color() & 0xFFFFFF), 12 * size, false);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Icons.snap(sx), Icons.snap(sy));
		pose.scale(size);
		int textColor = ARGB.color(alpha, 0xFFFFFF);
		int nameWidth = font.width(name);
		int distWidth = font.width(distance);
		int boxWidth = Math.max(nameWidth, distWidth) + 6;
		// A tracked spot without a name (picked on the map) only shows its distance.
		boolean named = !name.isBlank();
		int distY = named ? 20 : 10;
		graphics.fill(-boxWidth / 2, 8, boxWidth / 2, distY + 9, ARGB.color(alpha * 0.55f, 0x000000));
		if (named) {
			graphics.drawString(font, name, -nameWidth / 2, 10, textColor, false);
		}
		graphics.drawString(font, distance, -distWidth / 2, distY, ARGB.color(alpha, 0xFFD866), false);
		pose.popMatrix();
	}
}
