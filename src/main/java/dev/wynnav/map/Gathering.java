package dev.wynnav.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wynnav.Wynnav;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Gathering nodes (ores, trees, crops, fishing spots) from the Wynncraft API. */
public final class Gathering {
	private static final URI NODES_URI = URI.create("https://api.wynncraft.com/v3/map/gathering-nodes");

	public enum Profession {
		MINING(0xFFB0B8C0),
		WOODCUTTING(0xFF8BC34A),
		FARMING(0xFFFFD54F),
		FISHING(0xFF4FC3F7);

		public final int color;

		Profession(int color) {
			this.color = color;
		}

		public String displayName() {
			String name = name().toLowerCase(Locale.ROOT);
			return Character.toUpperCase(name.charAt(0)) + name.substring(1);
		}
	}

	public record Node(int x, int y, int z, String resource, int level, Profession profession) {
		public String displayName() {
			String name = resource.toLowerCase(Locale.ROOT);
			return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " (Lv. " + level + " " + profession.displayName() + ")";
		}
	}

	private static final Set<String> WOOD = Set.of(
		"OAK", "BIRCH", "WILLOW", "ACACIA", "SPRUCE", "JUNGLE", "DARK", "LIGHT", "PINE", "AVO", "SKY", "MAPLE", "REDWOOD");
	private static final Set<String> CROPS = Set.of(
		"WHEAT", "BARLEY", "OAT", "MALT", "HOPS", "RYE", "MILLET", "DECAY", "RICE", "SORGHUM", "HEMP", "JUTE", "HEATHER");
	private static final Set<String> FISH = Set.of(
		"GUDGEON", "TROUT", "SALMON", "CARP", "ICEFISH", "PIRANHA", "KOI", "GYLIA", "BASS", "STARFISH", "STURGEON", "MAHSEER");

	private volatile List<Node> nodes = List.of();
	private volatile SpatialGrid<Node> grid = SpatialGrid.empty();

	public List<Node> nodes() {
		return nodes;
	}

	/** Visits the nodes in (roughly) the given world box; there are ~17k nodes in total. */
	public void forEachIn(double minX, double minZ, double maxX, double maxZ, java.util.function.Consumer<Node> action) {
		grid.forEachIn(minX, minZ, maxX, maxZ, action);
	}

	public void load() {
		CachedFetcher.fetchWithMaxAge(NODES_URI, Wynnav.cacheDir().resolve("gathering-nodes.json"), Duration.ofHours(12))
			.thenAccept(bytes -> {
				List<Node> parsed = parse(new String(bytes, StandardCharsets.UTF_8));
				grid = new SpatialGrid<>(parsed, Node::x, Node::z);
				nodes = parsed;
			})
			.exceptionally(error -> {
				Wynnav.LOGGER.error("Could not load gathering nodes", error);
				return null;
			});
	}

	static Profession professionOf(String resource, String type) {
		if (WOOD.contains(resource)) {
			return Profession.WOODCUTTING;
		}
		if (CROPS.contains(resource)) {
			return Profession.FARMING;
		}
		if (FISH.contains(resource)) {
			return Profession.FISHING;
		}
		// "Molten" is both an ore and a fish. Ores also come as CORNER/WALL veins; a plain NODE of
		// molten is the fishing spot (level 90 fishing has no other resource).
		if (resource.equals("MOLTEN") && type.equals("NODE")) {
			return Profession.FISHING;
		}
		return Profession.MINING;
	}

	private static List<Node> parse(String json) {
		List<Node> result = new ArrayList<>();
		for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
			JsonObject obj = element.getAsJsonObject();
			try {
				String resource = obj.get("resource").getAsString();
				String type = obj.get("type").getAsString();
				result.add(new Node(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt(), resource,
					obj.get("level").getAsInt(), professionOf(resource, type)));
			} catch (RuntimeException e) {
				Wynnav.LOGGER.debug("Skipping malformed gathering node {}", obj);
			}
		}
		return List.copyOf(result);
	}
}
