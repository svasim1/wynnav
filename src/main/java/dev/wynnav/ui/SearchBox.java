package dev.wynnav.ui;

import dev.wynnav.WynnavClient;
import dev.wynnav.map.Content;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.map.Places;
import dev.wynnav.map.Territories;
import dev.wynnav.social.PlayerHeads;
import dev.wynnav.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Search field for the map. Matches waypoints, markers, camps, world events and territories by
 * name; picking a result jumps there and opens its menu.
 */
final class SearchBox {
	private static final int MAX_RESULTS = 8;
	private static final int ROW = 12;

	/** {@code source} breaks ties between equally good matches: places, waypoints, markers, ... */
	private record Result(String name, String kind, double x, double z, Supplier<PopupMenu> menu, int rank, int source) {}

	private final WorldMapScreen screen;
	private final Font font;
	private final EditBox box;
	private List<Result> results = List.of();

	SearchBox(WorldMapScreen screen, Font font, int x, int y, int width, String initial) {
		this.screen = screen;
		this.font = font;
		this.box = new EditBox(font, x, y, width, 20, Component.literal("Search"));
		box.setHint(Component.literal("Search places..."));
		box.setMaxLength(64);
		box.setValue(initial);
		box.setResponder(this::update);
		update(initial);
	}

	EditBox box() {
		return box;
	}

	String query() {
		return box.getValue();
	}

	void clear() {
		box.setValue("");
	}

	private void update(String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (q.length() < 2) {
			results = List.of();
			return;
		}
		List<Result> found = new ArrayList<>();
		for (PlayerHeads.Placed placed : PlayerHeads.visible(0)) {
			add(found, 0, q, placed.member().name(), "Player", placed.x(), placed.z(), () -> screen.playerMenu(placed));
		}
		for (Places.Place place : WynnavClient.places().places()) {
			String kind = switch (place.kind()) {
				case PROVINCE -> "Province";
				case TOWN -> "Town";
				case PLACE -> "Place";
			};
			List<String> details = place.level() != null ? List.of("Lv. " + place.level()) : List.of();
			add(found, 0, q, place.name(), kind, place.x(), place.z(),
				() -> screen.pointMenu(place.name(), details, place.x(), null, place.z()));
		}
		for (Waypoint waypoint : WynnavClient.waypoints().all()) {
			add(found, 1, q, waypoint.name(), "Waypoint", waypoint.x(), waypoint.z(), () -> screen.waypointMenu(waypoint));
		}
		for (MapMarkers.Marker marker : WynnavClient.markers().markers()) {
			add(found, 2, q, marker.name(), marker.category().displayName(), marker.x(), marker.z(), () -> screen.markerMenu(marker));
		}
		for (Content.Info camp : WynnavClient.content().camps()) {
			if (camp.x() != null) {
				add(found, 3, q, camp.name(), "Camp", camp.x(), camp.z(), () -> screen.contentMenu(camp));
			}
		}
		for (Content.Info event : WynnavClient.content().worldEvents()) {
			if (event.x() != null) {
				add(found, 3, q, event.name(), "World event", event.x(), event.z(), () -> screen.contentMenu(event));
			}
		}
		for (Territories.Territory territory : WynnavClient.territories().territories()) {
			add(found, 4, q, territory.name(), "Area", (territory.minX() + territory.maxX()) / 2.0, (territory.minZ() + territory.maxZ()) / 2.0,
				() -> screen.territoryMenu(territory));
		}
		// Many markers share a name ("Blacksmith"); prefer exact and prefix matches, then the closest.
		var player = net.minecraft.client.Minecraft.getInstance().player;
		// Equally good matches: places before waypoints, markers, content and territories; then nearest.
		Comparator<Result> order = Comparator.comparingInt(Result::rank).thenComparingInt(Result::source);
		if (player != null) {
			order = order.thenComparingDouble(r -> Math.hypot(r.x() - player.getX(), r.z() - player.getZ()));
		}
		results = found.stream().sorted(order).limit(MAX_RESULTS).toList();
	}

	private static void add(List<Result> found, int source, String query, String name, String kind, double x, double z,
		Supplier<PopupMenu> menu) {
		String lower = name.toLowerCase(Locale.ROOT);
		int rank = lower.equals(query) ? 0 : lower.startsWith(query) ? 1 : lower.contains(" " + query) ? 2 : lower.contains(query) ? 3 : -1;
		if (rank >= 0) {
			found.add(new Result(name, kind, x, z, menu, rank, source));
		}
	}

	void openFirst() {
		if (!results.isEmpty()) {
			open(results.getFirst());
		}
	}

	private void open(Result result) {
		screen.jumpTo(result.x(), result.z(), result.menu().get());
		clear();
	}

	private boolean showing() {
		return box.isFocused() && !results.isEmpty();
	}

	boolean isOverResults(double mouseX, double mouseY) {
		return showing() && mouseX >= box.getX() && mouseX < box.getX() + resultsWidth()
			&& mouseY >= box.getY() + 22 && mouseY < box.getY() + 22 + results.size() * ROW + 4;
	}

	boolean mouseClickedResults(double mouseX, double mouseY) {
		if (!isOverResults(mouseX, mouseY)) {
			return false;
		}
		int index = (int) ((mouseY - box.getY() - 24) / ROW);
		if (index >= 0 && index < results.size()) {
			open(results.get(index));
		}
		return true;
	}

	private int resultsWidth() {
		return Math.max(box.getWidth(), 200);
	}

	void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!showing()) {
			return;
		}
		int x = box.getX();
		int y = box.getY() + 22;
		int w = resultsWidth();
		graphics.fill(x, y, x + w, y + results.size() * ROW + 4, 0xE8101418);
		graphics.renderOutline(x, y, w, results.size() * ROW + 4, 0xFF3A4450);
		for (int i = 0; i < results.size(); i++) {
			Result result = results.get(i);
			int rowY = y + 2 + i * ROW;
			if (mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW) {
				graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW, 0x40FFFFFF);
			}
			int kindWidth = font.width(result.kind());
			graphics.drawString(font, font.plainSubstrByWidth(result.name(), w - kindWidth - 14), x + 4, rowY + 2, 0xFFFFFFFF);
			graphics.drawString(font, result.kind(), x + w - kindWidth - 4, rowY + 2, 0xFF909AA4);
		}
	}
}
