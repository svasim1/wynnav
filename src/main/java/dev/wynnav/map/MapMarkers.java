package dev.wynnav.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wynnav.Wynnav;
import dev.wynnav.config.Settings;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Points of interest from the official Wynncraft API (quests, dungeons, merchants, ...). */
public final class MapMarkers {
	private static final URI MARKERS_URI = URI.create("https://api.wynncraft.com/v3/map/locations/markers");

	public enum Category {
		TRAVEL(0.1),
		DUNGEONS(0.1),
		QUESTS(0.25),
		CAVES(0.25),
		OTHER(0.25),
		SERVICES(0.6),
		MERCHANTS(0.9),
		PROFESSIONS(0.9);

		/** Hidden below this zoom (screen pixels per block) to keep the map readable. */
		public final double minZoom;

		Category(double minZoom) {
			this.minZoom = minZoom;
		}

		public String displayName() {
			return switch (this) {
				case TRAVEL -> "Fast travel";
				case DUNGEONS -> "Dungeons & raids";
				case QUESTS -> "Quests";
				case CAVES -> "Caves";
				case OTHER -> "Other";
				case SERVICES -> "Services";
				case MERCHANTS -> "Merchants";
				case PROFESSIONS -> "Crafting stations";
			};
		}

		static Category of(String icon) {
			if (icon.startsWith("Special_FastTravel") || icon.startsWith("Special_Seaskipper") || icon.startsWith("Special_HousingAirBalloon")) {
				return TRAVEL;
			}
			if (icon.startsWith("Content_Dungeon") || icon.startsWith("Content_CorruptedDungeon") || icon.startsWith("Content_Raid")) {
				return DUNGEONS;
			}
			if (icon.startsWith("Content_Quest") || icon.startsWith("Content_Miniquest")) {
				return QUESTS;
			}
			if (icon.startsWith("Content_Cave")) {
				return CAVES;
			}
			if (icon.startsWith("Merchant_")) {
				return MERCHANTS;
			}
			if (icon.startsWith("Profession_")) {
				return PROFESSIONS;
			}
			if (icon.startsWith("NPC_")) {
				return SERVICES;
			}
			return OTHER;
		}
	}

	public record Marker(String name, String icon, int x, int y, int z, Category category) {
		/** Respects the user's category filter and hides minor markers when zoomed far out. */
		public boolean visibleAt(double zoom) {
			return zoom >= category.minZoom && !Settings.get().layers.hiddenCategories.contains(category);
		}
	}

	private volatile List<Marker> markers = List.of();

	public List<Marker> markers() {
		return markers;
	}

	public void load() {
		CachedFetcher.fetchWithMaxAge(MARKERS_URI, Wynnav.cacheDir().resolve("markers.json"), Duration.ofHours(1))
			.thenAccept(bytes -> markers = parse(new String(bytes, StandardCharsets.UTF_8)))
			.exceptionally(error -> {
				Wynnav.LOGGER.error("Could not load map markers", error);
				return null;
			});
	}

	private static List<Marker> parse(String json) {
		List<Marker> result = new ArrayList<>();
		for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
			JsonObject obj = element.getAsJsonObject();
			try {
				String icon = obj.get("icon").getAsString();
				// The API sends coordinates as strings, so parse them leniently.
				result.add(new Marker(
					obj.get("name").getAsString(),
					icon,
					(int) Double.parseDouble(obj.get("x").getAsString()),
					(int) Double.parseDouble(obj.get("y").getAsString()),
					(int) Double.parseDouble(obj.get("z").getAsString()),
					Category.of(icon)));
			} catch (RuntimeException e) {
				Wynnav.LOGGER.debug("Skipping malformed marker {}", obj);
			}
		}
		// Less important categories first, so important ones are drawn on top.
		result.sort((a, b) -> Double.compare(b.category().minZoom, a.category().minZoom));
		return List.copyOf(result);
	}
}
