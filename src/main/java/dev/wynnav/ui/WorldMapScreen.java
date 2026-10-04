package dev.wynnav.ui;

import dev.wynnav.Wynnav;
import dev.wynnav.WynnavClient;
import dev.wynnav.config.Settings;
import dev.wynnav.map.Content;
import dev.wynnav.map.Gathering;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.map.MarkerIcons;
import dev.wynnav.map.Places;
import dev.wynnav.map.Territories;
import dev.wynnav.render.Icons;
import dev.wynnav.render.MapPainter;
import dev.wynnav.render.MapView;
import dev.wynnav.render.Polygons;
import dev.wynnav.social.PlayerHeads;
import dev.wynnav.waypoint.Waypoint;
import dev.wynnav.waypoint.Waypoints;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
	private static final Identifier RECENTER_ICON = Wynnav.id("textures/gui/recenter.png");

	private static final double MIN_ZOOM = 0.05;
	private static final double MAX_ZOOM = 8;
	private static final double ZOOM_STEP = 1.2;
	private static final double GATHERING_MIN_ZOOM = 0.35;
	private static final int DRAG_THRESHOLD = 3;
	private static final int HOVER_RADIUS = 7;
	private static final int ICON_SIZE = 14;
	private static final int BACKGROUND = 0xFF0E1A26;
	private static final int TOP_BAR = 30;

	enum Panel { NONE, WAYPOINTS, LAYERS }

	// Remembered between openings so the map feels the same each time.
	private static double lastZoom = 1;
	private static Panel lastPanel = Panel.NONE;

	/**
	 * Something under the cursor: what to call it, what to offer when it is clicked, and what a
	 * middle-click tracks. Areas (territories) cover the whole map, so right- or middle-clicking one
	 * still means "this spot".
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
	private final LayersPanel layersPanel = new LayersPanel();
	private Panel panel = lastPanel;
	private SearchBox search;
	private Button recenterButton;
	private final MovementPassthrough movement = new MovementPassthrough();

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
		// On narrow screens (down to Minecraft's 320 GUI pixels) "Center on me" shrinks to its icon so
		// the search box still fits.
		int recenterWidth = width < 440 ? 24 : 104;
		recenterButton = addRenderableWidget(new IconButton(right - recenterWidth, top, recenterWidth, 20, RECENTER_ICON,
			Component.translatable("wynnav.map.recenter"), button -> recenter()));
		if (recenterWidth < 104) {
			recenterButton.setTooltip(Tooltip.create(Component.translatable("wynnav.map.recenter")));
		}
		right -= recenterWidth + 4;
		addRenderableWidget(Button.builder(Component.translatable("wynnav.map.waypoints"), button -> togglePanel(Panel.WAYPOINTS))
			.bounds(right - 66, top, 66, 20).build());
		right -= 70;
		addRenderableWidget(Button.builder(Component.translatable("wynnav.map.layers"), button -> togglePanel(Panel.LAYERS))
			.bounds(right - 50, top, 50, 20).build());
		right -= 54;
		addRenderableWidget(Button.builder(Component.translatable("wynnav.map.settings"), button -> minecraft.setScreen(new SettingsScreen(this)))
			.bounds(right - 56, top, 56, 20).build());
		right -= 60;

		String previousQuery = search == null ? "" : search.query();
		search = new SearchBox(this, font, 6, top, Math.max(80, Math.min(180, right - 12)), previousQuery);
		addRenderableWidget(search.box());

		sidebar.layout(width - WaypointSidebar.WIDTH, TOP_BAR, height - 22);
		layersPanel.layout(width - LayersPanel.WIDTH, TOP_BAR, height - 22);
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

	/** Centers on a point, zooming in if needed so markers there are visible. */
	void jumpTo(double worldX, double worldZ, @Nullable PopupMenu menu) {
		focusOn(worldX, worldZ);
		zoom = Math.max(zoom, 1);
		lastZoom = zoom;
		if (menu != null) {
			openPopup(menu, width / 2 + 4, height / 2 + 4);
		}
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
		Content.Info raid = marker.category() == MapMarkers.Category.DUNGEONS ? WynnavClient.content().raid(marker.name()) : null;
		return pointMenu(marker.name(), raid != null ? raid.describe() : List.of(), marker.x(), marker.y(), marker.z());
	}

	PopupMenu contentMenu(Content.Info info) {
		return pointMenu(info.name(), info.describe(), info.x(), info.y(), info.z());
	}

	private static Waypoint playerTarget(PlayerHeads.Placed placed) {
		return pointTarget(placed.member().name(), (int) Math.floor(placed.x()), null, (int) Math.floor(placed.z()));
	}

	/** A friend: who and where; tracking marks where they are right now. */
	PopupMenu playerMenu(PlayerHeads.Placed placed) {
		Waypoint target = playerTarget(placed);
		PopupMenu menu = new PopupMenu(placed.member().name()).detail(placed.label().substring(placed.member().name().length()).strip());
		if (!placed.sameWorld() && placed.member().server() != null) {
			menu.detail("On another world");
		}
		return menu.detail(target.coordinates())
			.action("Track current position", () -> waypoints.track(target))
			.action("Copy coordinates", () -> copy(target.coordinates()));
	}

	PopupMenu territoryMenu(Territories.Territory territory) {
		PopupMenu menu = new PopupMenu(territory.name());
		if (territory.guild() != null) {
			menu.detail(territory.guild() + (territory.guildPrefix() != null ? " [" + territory.guildPrefix() + "]" : ""));
		}
		if (territory.headquarters()) {
			menu.detail("Guild headquarters");
		}
		return menu;
	}

	private PopupMenu locationMenu(int worldX, int worldZ) {
		String coords = worldX + ", " + worldZ;
		int count = waypoints.all().size();
		Waypoint draft = Waypoint.create("Waypoint " + (count + 1), worldX, null, worldZ, Waypoint.PALETTE[count % Waypoint.PALETTE.length]);
		PopupMenu menu = new PopupMenu(coords);
		Territories.Territory territory = WynnavClient.territories().at(worldX, worldZ);
		if (territory != null) {
			menu.detail(territory.name());
		}
		return menu
			.action("Add waypoint here...", () -> openEditor(null, draft))
			.action("Track this spot", () -> waypoints.track(draft.withValues("", worldX, null, worldZ, 0xFFFFFFFF)))
			.action("Copy coordinates", () -> copy(coords));
	}

	// ---------------------------------------------------------------- hit testing

	private boolean near(MapView view, double worldX, double worldZ, double mouseX, double mouseY) {
		return Math.hypot(view.screenX(worldX, worldZ) - mouseX, view.screenY(worldX, worldZ) - mouseY) <= HOVER_RADIUS;
	}

	private @Nullable Hover hoverAt(double mouseX, double mouseY) {
		MapView view = view();
		for (PlayerHeads.Placed placed : PlayerHeads.visible(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false))) {
			if (near(view, placed.x(), placed.z(), mouseX, mouseY)) {
				return new Hover(placed.label(), () -> playerMenu(placed), () -> playerTarget(placed));
			}
		}
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
		Settings.MapLayers layers = Settings.get().layers;
		if (layers.camps) {
			for (Content.Info camp : WynnavClient.content().camps()) {
				if (camp.x() != null && near(view, camp.x() + 0.5, camp.z() + 0.5, mouseX, mouseY)) {
					return new Hover(camp.name() + " (camp, Lv. " + camp.level() + ")", () -> contentMenu(camp),
						() -> pointTarget(camp.name(), camp.x(), camp.y(), camp.z()));
				}
			}
		}
		if (layers.worldEvents) {
			for (Content.Info event : WynnavClient.content().worldEvents()) {
				if (event.x() != null && near(view, event.x() + 0.5, event.z() + 0.5, mouseX, mouseY)) {
					return new Hover(event.name() + " (world event, Lv. " + event.level() + ")", () -> contentMenu(event),
						() -> pointTarget(event.name(), event.x(), event.y(), event.z()));
				}
			}
		}
		if (layers.gathering && zoom >= GATHERING_MIN_ZOOM) {
			double r = HOVER_RADIUS / zoom + 1;
			double wx = view.worldX(mouseX, mouseY);
			double wz = view.worldZ(mouseX, mouseY);
			Gathering.Node[] found = new Gathering.Node[1];
			WynnavClient.gathering().forEachIn(wx - r, wz - r, wx + r, wz + r, node -> {
				if (found[0] == null && gatheringVisible(node, layers) && near(view, node.x() + 0.5, node.z() + 0.5, mouseX, mouseY)) {
					found[0] = node;
				}
			});
			Gathering.Node node = found[0];
			if (node != null) {
				return new Hover(node.displayName(), () -> pointMenu(node.displayName(), List.of(), node.x(), node.y(), node.z()),
					() -> pointTarget(node.displayName(), node.x(), node.y(), node.z()));
			}
		}
		if (layers.territories) {
			Territories.Territory territory = WynnavClient.territories().at(view.worldX(mouseX, mouseY), view.worldZ(mouseX, mouseY));
			if (territory != null) {
				String label = territory.guildPrefix() != null ? territory.name() + " [" + territory.guildPrefix() + "]" : territory.name();
				return new Hover(label, () -> territoryMenu(territory), null);
			}
		}
		return null;
	}

	private static boolean gatheringVisible(Gathering.Node node, Settings.MapLayers layers) {
		return layers.gatheringProfessions.contains(node.profession())
			&& node.level() >= layers.gatheringMinLevel && node.level() <= layers.gatheringMaxLevel;
	}

	private boolean overPanel(double mouseX, double mouseY) {
		return switch (panel) {
			case WAYPOINTS -> sidebar.contains(mouseX, mouseY);
			case LAYERS -> layersPanel.contains(mouseX, mouseY);
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
		if (search.mouseClickedResults(mx, my)) {
			return true;
		}
		if (super.mouseClicked(event, isDoubleClick)) {
			return true;
		}
		setFocused(null);
		if (overPanel(mx, my)) {
			return panel == Panel.WAYPOINTS ? sidebar.mouseClicked(mx, my, event.button()) : layersPanel.mouseClicked(mx, my);
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
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
			trackAt(mx, my);
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

	/** Middle-click: track whatever is under the cursor, or the spot itself. Again to stop. */
	private void trackAt(double mouseX, double mouseY) {
		Hover hover = hoverAt(mouseX, mouseY);
		Waypoint target;
		if (hover != null && !hover.area()) {
			target = hover.target().get();
		} else {
			int x = Mth.floor(view().worldX(mouseX, mouseY));
			int z = Mth.floor(view().worldZ(mouseX, mouseY));
			// A bare spot has no name; the in-world label then shows just the distance.
			target = pointTarget("", x, null, z);
		}
		Waypoint current = waypoints.tracked().orElse(null);
		boolean sameSpot = current != null && current.x() == target.x() && current.z() == target.z();
		waypoints.track(sameSpot ? null : target);
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
			if (panel == Panel.WAYPOINTS) {
				sidebar.scroll(scrollY);
			} else {
				layersPanel.scroll(scrollY);
			}
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

	private boolean typing() {
		return getFocused() == search.box();
	}

	@Override
	public void tick() {
		movement.update(!typing());
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (!typing() && movement.isMovementKey(event)) {
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape() && popup != null) {
			popup = null;
			return true;
		}
		// Movement keys walk the player (see MovementPassthrough); they must not also press a
		// focused button, e.g. Space re-clicking "Layers".
		if (!typing() && movement.isMovementKey(event)) {
			movement.update(true);
			return true;
		}
		if (typing()) {
			if (event.isEscape()) {
				search.clear();
				setFocused(null);
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				search.openFirst();
				setFocused(null);
				return true;
			}
			// Typing in the search box must not trigger map hotkeys.
			return super.keyPressed(event);
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
		// Also per frame, so key presses shorter than a tick still register.
		movement.update(!typing());
		LocalPlayer player = Minecraft.getInstance().player;
		if (following && player != null) {
			Vec3 pos = player.getPosition(partialTick);
			centerX = pos.x;
			centerZ = pos.z;
		}
		MapView view = view();
		Polygons.Shape screenClip = Polygons.Shape.rectangle(0, 0, width, height);
		Settings.MapLayers layers = Settings.get().layers;

		if (!MapPainter.drawTiles(graphics, view, screenClip, 1)) {
			graphics.drawCenteredString(font, Component.translatable("wynnav.map.loading"), width / 2, height / 2 - 20, 0xFFA0A8B0);
		}
		if (layers.territories) {
			MapPainter.drawTerritories(graphics, view, screenClip);
			renderTerritoryLabels(graphics, view);
		}
		if (layers.worldEvents) {
			for (Content.Info event : WynnavClient.content().worldEvents()) {
				if (event.x() != null && event.radius() > 0) {
					MapPainter.drawWorldCircle(graphics, view, screenClip, event.x() + 0.5, event.z() + 0.5, event.radius(), 0x30FF5252);
				}
			}
		}
		if (layers.gathering && zoom >= GATHERING_MIN_ZOOM) {
			renderGathering(graphics, view, layers);
		}

		boolean interactive = popup == null && !dragging && !overPanel(mouseX, mouseY) && mouseY > TOP_BAR && !search.isOverResults(mouseX, mouseY);
		Hover hover = interactive ? hoverAt(mouseX, mouseY) : null;
		renderMarkers(graphics, view);
		renderContent(graphics, view, layers);
		if (layers.placeNames) {
			renderPlaceNames(graphics, view);
		}
		renderWaypoints(graphics, view);
		for (PlayerHeads.Placed placed : PlayerHeads.visible(partialTick)) {
			PlayerHeads.draw(graphics, placed, view.screenX(placed.x(), placed.z()), view.screenY(placed.x(), placed.z()), 12);
		}
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
			if (panel == Panel.WAYPOINTS) {
				sidebar.render(graphics, font, mouseX, mouseY);
			} else {
				layersPanel.render(graphics, font, mouseX, mouseY);
			}
		}
		graphics.nextStratum();
		search.renderResults(graphics, mouseX, mouseY);
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
		pose.translate(Icons.snap(x), Icons.snap(y));
		graphics.drawCenteredString(font, text, 0, 0, color);
		pose.popMatrix();
	}

	// Which territories contain a place label; recomputed only when either data set is reloaded.
	private List<Territories.Territory> namedTerritoriesFor = List.of();
	private List<Places.Place> namedPlacesFor = List.of();
	private Set<Territories.Territory> namedByPlace = Set.of();

	private Set<Territories.Territory> territoriesNamedByPlaces() {
		List<Territories.Territory> territories = WynnavClient.territories().territories();
		List<Places.Place> places = WynnavClient.places().places();
		if (territories != namedTerritoriesFor || places != namedPlacesFor) {
			Set<Territories.Territory> named = new HashSet<>();
			for (Territories.Territory territory : territories) {
				for (Places.Place place : places) {
					if (territory.contains(place.x(), place.z())) {
						named.add(territory);
						break;
					}
				}
			}
			namedByPlace = named;
			namedTerritoriesFor = territories;
			namedPlacesFor = places;
		}
		return namedByPlace;
	}

	private void renderTerritoryLabels(GuiGraphics graphics, MapView view) {
		if (zoom < 0.25) {
			return;
		}
		Set<Territories.Territory> named = Settings.get().layers.placeNames ? territoriesNamedByPlaces() : Set.of();
		for (Territories.Territory territory : WynnavClient.territories().territories()) {
			float x0 = view.screenX(territory.minX(), territory.minZ());
			float x1 = view.screenX(territory.maxX() + 1, territory.minZ());
			float y0 = view.screenY(territory.minX(), territory.minZ());
			float y1 = view.screenY(territory.minX(), territory.maxZ() + 1);
			if (x1 < 0 || x0 > width || y1 < 0 || y0 > height || font.width(territory.name()) + 6 > x1 - x0 || y1 - y0 < 12) {
				continue;
			}
			float cx = (x0 + x1) / 2;
			float cy = (y0 + y1) / 2;
			// Where a place label already names this area, show only the guild tag.
			if (named.contains(territory)) {
				if (territory.guildPrefix() != null) {
					label(graphics, "[" + territory.guildPrefix() + "]", cx, y0 + 4, territory.color());
				}
				continue;
			}
			label(graphics, territory.name(), cx, cy - 4, 0xFFFFFFFF);
			if (territory.guildPrefix() != null && y1 - y0 >= 24) {
				label(graphics, "[" + territory.guildPrefix() + "]", cx, cy + 6, territory.color());
			}
		}
	}

	/**
	 * Province names when zoomed far out, town names at almost every zoom, smaller areas once
	 * zoomed in. Areas always show their level; towns from 0.3x.
	 */
	private void renderPlaceNames(GuiGraphics graphics, MapView view) {
		for (Places.Place place : WynnavClient.places().places()) {
			float scale;
			int color;
			boolean showLevel;
			switch (place.kind()) {
				case PROVINCE -> {
					// Only once zoomed out far enough to see most of a province.
					if (zoom >= 0.2) {
						continue;
					}
					scale = 1.5f;
					color = 0xFFFFE066;
					showLevel = false;
				}
				case TOWN -> {
					scale = zoom >= 0.5 ? 1.25f : 1;
					color = 0xFFFFFFFF;
					showLevel = zoom >= 0.3;
				}
				default -> {
					if (zoom < 0.3) {
						continue;
					}
					// Areas always show their level together with their name.
					scale = 1;
					color = 0xFFD8DEE4;
					showLevel = true;
				}
			}
			float sx = view.screenX(place.x() + 0.5, place.z() + 0.5);
			float sy = view.screenY(place.x() + 0.5, place.z() + 0.5);
			int halfWidth = (int) (font.width(place.name()) * scale / 2);
			if (sx + halfWidth < 0 || sx - halfWidth > width || sy < -20 || sy > height + 20) {
				continue;
			}
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(Icons.snap(sx), Icons.snap(sy));
			pose.scale(scale);
			graphics.drawCenteredString(font, place.name(), 0, -4, color);
			if (showLevel && place.level() != null) {
				graphics.drawCenteredString(font, "Lv. " + place.level(), 0, 6, 0xFFB0B8C0);
			}
			pose.popMatrix();
		}
	}

	private void renderGathering(GuiGraphics graphics, MapView view, Settings.MapLayers layers) {
		int size = zoom >= 1.5 ? 4 : 3;
		int half = size / 2;
		var pose = graphics.pose();
		double[] b = view.worldBounds(-size, -size, width + size, height + size);
		WynnavClient.gathering().forEachIn(b[0], b[1], b[2], b[3], node -> {
			if (!gatheringVisible(node, layers)) {
				return;
			}
			float sx = view.screenX(node.x() + 0.5, node.z() + 0.5);
			float sy = view.screenY(node.x() + 0.5, node.z() + 0.5);
			if (sx < -size || sy < -size || sx > width + size || sy > height + size) {
				return;
			}
			pose.pushMatrix();
			pose.translate(sx, sy);
			graphics.fill(-half - 1, -half - 1, size - half + 1, size - half + 1, 0xC0000000);
			graphics.fill(-half, -half, size - half, size - half, node.profession().color);
			pose.popMatrix();
		});
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
			MarkerIcons.Icon icon = WynnavClient.icons().get(marker.icon());
			if (icon != null) {
				Icons.sprite(graphics, icon.texture(), sx, sy, icon.width(), icon.height(), ICON_SIZE, 0xFFFFFFFF);
			} else {
				Icons.waypoint(graphics, sx, sy, 0xFFFFFFFF, 4, false);
			}
		}
	}

	private void renderContent(GuiGraphics graphics, MapView view, Settings.MapLayers layers) {
		if (layers.camps) {
			for (Content.Info camp : WynnavClient.content().camps()) {
				drawContentIcon(graphics, view, camp, Icons.CAMP, 0xFFFFFFFF);
			}
		}
		if (layers.worldEvents) {
			for (Content.Info event : WynnavClient.content().worldEvents()) {
				// Events with a known start time are shown solid, the rest a little faded.
				drawContentIcon(graphics, view, event, Icons.EVENT, event.nextStart() != null ? 0xFFFFFFFF : 0xB0FFFFFF);
			}
		}
	}

	private void drawContentIcon(GuiGraphics graphics, MapView view, Content.Info info, Identifier icon, int color) {
		if (info.x() == null) {
			return;
		}
		float sx = view.screenX(info.x() + 0.5, info.z() + 0.5);
		float sy = view.screenY(info.x() + 0.5, info.z() + 0.5);
		if (sx < -ICON_SIZE || sy < -ICON_SIZE || sx > width + ICON_SIZE || sy > height + ICON_SIZE) {
			return;
		}
		Icons.sprite(graphics, icon, sx, sy, 11, 11, 12, color);
		if (zoom >= 0.6) {
			label(graphics, info.name(), sx, sy + 8, 0xFFFFFFFF);
		}
	}

	private void renderWaypoints(GuiGraphics graphics, MapView view) {
		for (Waypoint waypoint : waypoints.all()) {
			float sx = view.screenX(waypoint.x() + 0.5, waypoint.z() + 0.5);
			float sy = view.screenY(waypoint.x() + 0.5, waypoint.z() + 0.5);
			boolean tracked = waypoints.isTracked(waypoint);
			Icons.waypoint(graphics, sx, sy, waypoint.color(), 10, tracked);
			if (zoom >= 0.3 || tracked) {
				label(graphics, waypoint.name(), sx, sy + 8, 0xFFFFFFFF);
			}
		}
		// A tracked spot that is not a saved waypoint (e.g. a marker) still gets drawn.
		waypoints.tracked()
			.filter(tracked -> waypoints.find(tracked.id()).isEmpty())
			.ifPresent(tracked -> Icons.waypoint(graphics, view.screenX(tracked.x() + 0.5, tracked.z() + 0.5),
				view.screenY(tracked.x() + 0.5, tracked.z() + 0.5), 0xFFFFFFFF, 10, true));
	}

	private void renderPlayer(GuiGraphics graphics, MapView view, LocalPlayer player, float partialTick) {
		Vec3 pos = player.getPosition(partialTick);
		// Yaw 0 faces south (+Z, down on the map), so the heading from north is yaw + 180.
		Icons.playerArrow(graphics, view.screenX(pos.x, pos.z), view.screenY(pos.x, pos.z), player.getViewYRot(partialTick) + 180, 16);
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
