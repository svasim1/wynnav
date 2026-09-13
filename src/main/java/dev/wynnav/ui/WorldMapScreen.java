package dev.wynnav.ui;

import dev.wynnav.Wynnav;
import dev.wynnav.WynnavClient;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.render.Icons;
import dev.wynnav.render.MapPainter;
import dev.wynnav.render.MapView;
import dev.wynnav.render.Polygons;
import dev.wynnav.waypoint.Waypoint;
import dev.wynnav.waypoint.Waypoints;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * Full-screen world map.
 *
 * <p>Controls: left-drag pans, scroll zooms toward the cursor, left-click anything on the map for
 * its details and actions, right-click anywhere for actions at that spot. The "Center on me" button
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

	enum Panel { NONE, WAYPOINTS }

	// Remembered between openings so the map feels the same each time.
	private static double lastZoom = 1;
	private static Panel lastPanel = Panel.NONE;

	/**
	 * Something under the cursor: what to call it, what to offer when it is clicked, and what
	 * tracking it means. Without a target it is an area, and right-clicking it means "this spot".
	 */
	private record Hover(String label, Supplier<PopupMenu> menu, @Nullable Supplier<Waypoint> target) {
		boolean area() {
			return target == null;
		}
	}

	private final Waypoints waypoints = WynnavClient.waypoints();
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

	private @Nullable PopupMenu popup;
	private final WaypointSidebar sidebar = new WaypointSidebar(this);
	private Panel panel = lastPanel;
	private Button recenterButton;

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
		int top = 6;
		int right = width - 6;
		recenterButton = addRenderableWidget(new IconButton(right - 104, top, 104, 20, RECENTER_ICON,
			Component.translatable("wynnav.map.recenter"), button -> recenter()));
		addRenderableWidget(Button.builder(Component.translatable("wynnav.map.waypoints"), button -> togglePanel(Panel.WAYPOINTS))
			.bounds(right - 66, top, 66, 20).build());

		sidebar.layout(width - WaypointSidebar.WIDTH, TOP_BAR, height - 22);
	}

	MapView view() {
		return MapView.northUp(centerX, centerZ, zoom, width / 2f, height / 2f);
	}

	// ---------------------------------------------------------------- actions

	private void recenter() {
		following = true;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	/** Moves the view to a point and stops following the player. */
	void focusOn(double worldX, double worldZ) {
		following = false;
		centerX = worldX;
		centerZ = worldZ;
	}

	private void togglePanel(Panel target) {
		panel = panel == target ? Panel.NONE : target;
		lastPanel = panel;
	}

	void openPopup(PopupMenu menu, int mouseX, int mouseY) {
		popup = menu.openAt(font, mouseX, mouseY, width, height);
	}

	void openEditor(@Nullable Waypoint existing, Waypoint draft) {
		minecraft.setScreen(new WaypointEditScreen(this, existing, draft));
	}

	private void copy(String text) {
		minecraft.keyboardHandler.setClipboard(text);
	}

	// ---------------------------------------------------------------- menus

	PopupMenu waypointMenu(Waypoint waypoint) {
		PopupMenu menu = new PopupMenu(waypoint.name()).detail(waypoint.coordinates());
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			menu.detail(Math.round(waypoint.distanceFrom(player.getX(), player.getY(), player.getZ())) + " blocks away");
		}
		if (waypoints.isTracked(waypoint)) {
			menu.action("Stop tracking", () -> waypoints.track(null));
		} else {
			menu.action("Track", () -> waypoints.track(waypoint));
		}
		return menu
			.action("Edit...", () -> openEditor(waypoint, waypoint))
			.action("Copy coordinates", () -> copy(waypoint.coordinates()))
			.dangerousAction("Delete", () -> waypoints.remove(waypoint));
	}

	/** An unsaved waypoint for tracking a point of interest. */
	private static Waypoint pointTarget(String name, int x, @Nullable Integer y, int z) {
		return Waypoint.create(name, x, y, z, 0xFFFFFFFF);
	}

	/** Menu for a fixed point of interest: details, then track / save / copy. */
	PopupMenu pointMenu(String name, Iterable<String> details, int x, @Nullable Integer y, int z) {
		String coords = y == null ? x + ", " + z : x + ", " + y + ", " + z;
		Waypoint asWaypoint = pointTarget(name, x, y, z);
		PopupMenu menu = new PopupMenu(name);
		details.forEach(menu::detail);
		return menu.detail(coords)
			.action("Track", () -> waypoints.track(asWaypoint))
			.action("Add as waypoint...", () -> openEditor(null, asWaypoint))
			.action("Copy coordinates", () -> copy(coords));
	}

	PopupMenu markerMenu(MapMarkers.Marker marker) {
		return pointMenu(marker.name(), List.of(), marker.x(), marker.y(), marker.z());
	}

	private PopupMenu locationMenu(int worldX, int worldZ) {
		String coords = worldX + ", " + worldZ;
		int count = waypoints.all().size();
		Waypoint draft = Waypoint.create("Waypoint " + (count + 1), worldX, null, worldZ, Waypoint.PALETTE[count % Waypoint.PALETTE.length]);
		PopupMenu menu = new PopupMenu(coords);
		return menu
			.action("Add waypoint here...", () -> openEditor(null, draft))
			.action("Track this spot", () -> waypoints.track(draft.withValues("Map location", worldX, null, worldZ, 0xFFFFFFFF)))
			.action("Copy coordinates", () -> copy(coords));
	}

	// ---------------------------------------------------------------- hit testing

	private boolean near(MapView view, double worldX, double worldZ, double mouseX, double mouseY) {
		return Math.hypot(view.screenX(worldX, worldZ) - mouseX, view.screenY(worldX, worldZ) - mouseY) <= HOVER_RADIUS;
	}

	private @Nullable Hover hoverAt(double mouseX, double mouseY) {
		MapView view = view();
		for (Waypoint waypoint : waypoints.all()) {
			if (near(view, waypoint.x() + 0.5, waypoint.z() + 0.5, mouseX, mouseY)) {
				return new Hover(waypoint.name(), () -> waypointMenu(waypoint), () -> waypoint);
			}
		}
		for (MapMarkers.Marker marker : WynnavClient.markers().markers()) {
			if (marker.visibleAt(zoom) && near(view, marker.x() + 0.5, marker.z() + 0.5, mouseX, mouseY)) {
				return new Hover(marker.name(), () -> markerMenu(marker), () -> pointTarget(marker.name(), marker.x(), marker.y(), marker.z()));
			}
		}
		return null;
	}

	private boolean overPanel(double mouseX, double mouseY) {
		return switch (panel) {
			case WAYPOINTS -> sidebar.contains(mouseX, mouseY);
			case NONE -> false;
		};
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
		double mx = event.x();
		double my = event.y();
		if (popup != null) {
			if (popup.contains(mx, my)) {
				if (popup.click(mx, my)) {
					popup = null;
				}
				return true;
			}
			popup = null;
			if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				return true; // A click outside just dismisses the menu.
			}
		}
		if (super.mouseClicked(event, isDoubleClick)) {
			return true;
		}
		if (overPanel(mx, my)) {
			return sidebar.mouseClicked(mx, my, event.button());
		}

		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			pressedOnMap = true;
			dragging = false;
			pressX = mx;
			pressY = my;
			pressCenterX = centerX;
			pressCenterZ = centerZ;
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			Hover hover = hoverAt(mx, my);
			PopupMenu menu = hover != null && !hover.area()
				? hover.menu().get()
				: locationMenu(Mth.floor(view().worldX(mx, my)), Mth.floor(view().worldZ(mx, my)));
			openPopup(menu, (int) mx, (int) my);
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
			if (!dragging) {
				Hover hover = hoverAt(event.x(), event.y());
				if (hover != null) {
					openPopup(hover.menu().get(), (int) event.x(), (int) event.y());
				}
			}
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (overPanel(mouseX, mouseY)) {
			sidebar.scroll(scrollY);
			return true;
		}
		popup = null;
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
		if (event.isEscape() && popup != null) {
			popup = null;
			return true;
		}
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
		Polygons.Shape screenClip = Polygons.Shape.rectangle(0, 0, width, height);

		if (!MapPainter.drawTiles(graphics, view, screenClip, 1)) {
			graphics.drawCenteredString(font, Component.translatable("wynnav.map.loading"), width / 2, height / 2 - 20, 0xFFA0A8B0);
		}
		boolean interactive = popup == null && !dragging && !overPanel(mouseX, mouseY) && mouseY > TOP_BAR;
		Hover hover = interactive ? hoverAt(mouseX, mouseY) : null;
		renderMarkers(graphics, view);
		renderWaypoints(graphics, view);
		if (player != null) {
			renderPlayer(graphics, view, player, partialTick);
		}
		renderStatusBar(graphics, view, mouseX, mouseY);

		recenterButton.active = !following;
		// Each overlay gets its own stratum: within one, all text is drawn after all shapes, so
		// map labels would otherwise show through panel and popup backgrounds.
		graphics.nextStratum();
		super.render(graphics, mouseX, mouseY, partialTick);

		if (panel != Panel.NONE) {
			graphics.nextStratum();
			sidebar.render(graphics, font, mouseX, mouseY);
		}
		if (popup != null) {
			graphics.nextStratum();
			popup.render(graphics, font, mouseX, mouseY);
		} else if (hover != null) {
			graphics.setTooltipForNextFrame(font, Component.literal(hover.label()), mouseX, mouseY);
		}
	}

	/** Text centered on a fractional position, so labels move as smoothly as the map under them. */
	private void label(GuiGraphics graphics, String text, float x, float y, int color) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		graphics.drawCenteredString(font, text, 0, 0, color);
		pose.popMatrix();
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

	private void renderWaypoints(GuiGraphics graphics, MapView view) {
		for (Waypoint waypoint : waypoints.all()) {
			float sx = view.screenX(waypoint.x() + 0.5, waypoint.z() + 0.5);
			float sy = view.screenY(waypoint.x() + 0.5, waypoint.z() + 0.5);
			boolean tracked = waypoints.isTracked(waypoint);
			int size = tracked ? 14 : 10;
			Icons.draw(graphics, WAYPOINT_ICON, sx, sy, size, 16, waypoint.color(), 0);
			if (zoom >= 0.3 || tracked) {
				label(graphics, waypoint.name(), sx, sy + size / 2f + 2, 0xFFFFFFFF);
			}
		}
		// A tracked spot that is not a saved waypoint (e.g. a marker) still gets drawn.
		waypoints.tracked()
			.filter(tracked -> waypoints.find(tracked.id()).isEmpty())
			.ifPresent(tracked -> Icons.draw(graphics, WAYPOINT_ICON, view.screenX(tracked.x() + 0.5, tracked.z() + 0.5),
				view.screenY(tracked.x() + 0.5, tracked.z() + 0.5), 14, 16, 0xFFFFFFFF, 0));
	}

	private void renderPlayer(GuiGraphics graphics, MapView view, LocalPlayer player, float partialTick) {
		Vec3 pos = player.getPosition(partialTick);
		// Yaw 0 faces south (+Z, down on the map); the arrow texture points up.
		float rotation = (float) Math.toRadians(player.getViewYRot(partialTick) + 180);
		Icons.draw(graphics, PLAYER_ARROW, view.screenX(pos.x, pos.z), view.screenY(pos.x, pos.z), 16, 32, 0xFFFFFFFF, rotation);
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
