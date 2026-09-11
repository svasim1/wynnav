package dev.wynnav.ui;

import dev.wynnav.Wynnav;
import dev.wynnav.WynnavClient;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.render.Icons;
import dev.wynnav.render.MapPainter;
import dev.wynnav.render.MapView;
import dev.wynnav.render.Polygons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Full-screen world map. Left-drag pans, scroll zooms toward the cursor. The "Center on me" button
 * recenters and follows the player until the map is dragged again.
 */
public final class WorldMapScreen extends Screen {
	private static final Identifier PLAYER_ARROW = Wynnav.id("textures/gui/player_arrow.png");
	private static final Identifier WAYPOINT_ICON = Wynnav.id("textures/gui/waypoint.png");
	private static final Identifier RECENTER_ICON = Wynnav.id("textures/gui/recenter.png");

	private static final double MIN_ZOOM = 0.05;
	private static final double MAX_ZOOM = 8;
	private static final double ZOOM_STEP = 1.2;
	private static final int DRAG_THRESHOLD = 3;
	private static final int HOVER_RADIUS = 7;
	private static final int ICON_SIZE = 14;
	private static final int BACKGROUND = 0xFF0E1A26;
	private static final int TOP_BAR = 30;

	// Remembered between openings so the map feels the same each time.
	private static double lastZoom = 1;

	private double centerX;
	private double centerZ;
	private double zoom = lastZoom;
	private boolean following = true;

	private boolean pressedOnMap;
	private boolean dragging;
	private double pressX;
	private double pressY;
	private double pressCenterX;
	private double pressCenterZ;
	private IconButton recenterButton;

	public WorldMapScreen() {
		super(Component.translatable("wynnav.screen.map"));
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	@Override
	protected void init() {
		recenterButton = addRenderableWidget(new IconButton(width - 110, 6, 104, 20, RECENTER_ICON,
			Component.translatable("wynnav.map.recenter"), button -> recenter()));
	}

	MapView view() {
		return MapView.northUp(centerX, centerZ, zoom, width / 2f, height / 2f);
	}

	private void recenter() {
		following = true;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	private MapMarkers.@Nullable Marker markerAt(double mouseX, double mouseY) {
		MapView view = view();
		for (MapMarkers.Marker marker : WynnavClient.markers().markers()) {
			if (marker.visibleAt(zoom) && Math.hypot(view.screenX(marker.x() + 0.5, marker.z() + 0.5) - mouseX,
				view.screenY(marker.x() + 0.5, marker.z() + 0.5) - mouseY) <= HOVER_RADIUS) {
				return marker;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
		if (super.mouseClicked(event, isDoubleClick)) {
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressedOnMap = true;
			dragging = false;
			pressX = event.x();
			pressY = event.y();
			pressCenterX = centerX;
			pressCenterZ = centerZ;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (pressedOnMap && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (!dragging && Math.hypot(event.x() - pressX, event.y() - pressY) > DRAG_THRESHOLD) {
				dragging = true;
				following = false;
			}
			if (dragging) {
				centerX = pressCenterX - (event.x() - pressX) / zoom;
				centerZ = pressCenterZ - (event.y() - pressY) / zoom;
			}
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedOnMap && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressedOnMap = false;
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double newZoom = Mth.clamp(zoom * Math.pow(ZOOM_STEP, scrollY), MIN_ZOOM, MAX_ZOOM);
		if (following) {
			// Keep the player centered while following; zoom around the screen center.
			zoom = newZoom;
		} else {
			MapView before = view();
			double anchorX = before.worldX(mouseX, mouseY);
			double anchorZ = before.worldZ(mouseX, mouseY);
			zoom = newZoom;
			centerX = anchorX - (mouseX - width / 2.0) / zoom;
			centerZ = anchorZ - (mouseY - height / 2.0) / zoom;
		}
		lastZoom = zoom;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (WynnavClient.openMapKey().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, BACKGROUND);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (following && player != null) {
			Vec3 pos = player.getPosition(partialTick);
			centerX = pos.x;
			centerZ = pos.z;
		}
		MapView view = view();
		if (!MapPainter.drawTiles(graphics, view, Polygons.Shape.rectangle(0, 0, width, height), 1)) {
			graphics.drawCenteredString(font, Component.translatable("wynnav.map.loading"), width / 2, height / 2 - 20, 0xFFA0A8B0);
		}
		renderMarkers(graphics, view);
		if (player != null) {
			Vec3 pos = player.getPosition(partialTick);
			// Yaw 0 faces south (+Z, down on the map); the arrow texture points up.
			float rotation = (float) Math.toRadians(player.getViewYRot(partialTick) + 180);
			Icons.draw(graphics, PLAYER_ARROW, view.screenX(pos.x, pos.z), view.screenY(pos.x, pos.z), 16, 32, 0xFFFFFFFF, rotation);
		}
		renderStatusBar(graphics, view, mouseX, mouseY);

		recenterButton.active = !following;
		graphics.nextStratum();
		super.render(graphics, mouseX, mouseY, partialTick);

		MapMarkers.Marker hovered = !dragging && mouseY > TOP_BAR ? markerAt(mouseX, mouseY) : null;
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, Component.literal(hovered.name()), mouseX, mouseY);
		}
	}

	private void renderMarkers(GuiGraphics graphics, MapView view) {
		for (MapMarkers.Marker marker : WynnavClient.markers().markers()) {
			if (!marker.visibleAt(zoom)) {
				continue;
			}
			float sx = view.screenX(marker.x() + 0.5, marker.z() + 0.5);
			float sy = view.screenY(marker.x() + 0.5, marker.z() + 0.5);
			if (sx < -ICON_SIZE || sy < -ICON_SIZE || sx > width + ICON_SIZE || sy > height + ICON_SIZE) {
				continue;
			}
			Identifier icon = WynnavClient.icons().get(marker.icon());
			if (icon != null) {
				Icons.draw(graphics, icon, sx, sy, ICON_SIZE);
			} else {
				Icons.draw(graphics, WAYPOINT_ICON, sx, sy, 4, 16, 0xFFFFFFFF, 0);
			}
		}
	}

	private void renderStatusBar(GuiGraphics graphics, MapView view, int mouseX, int mouseY) {
		String cursor = "X " + Mth.floor(view.worldX(mouseX, mouseY)) + "  Z " + Mth.floor(view.worldZ(mouseX, mouseY));
		String scale = String.format("%.2fx", zoom);
		String text = following ? cursor + "   " + scale + "   Following you" : cursor + "   " + scale;
		int textWidth = font.width(text);
		graphics.fill(4, height - 16, 4 + textWidth + 8, height - 4, ARGB.color(0xB0, 0x101418));
		graphics.drawString(font, text, 8, height - 14, 0xFFE0E0E0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
