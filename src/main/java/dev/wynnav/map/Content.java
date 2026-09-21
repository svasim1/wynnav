package dev.wynnav.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wynnav.Wynnav;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Details about camps, raids and world events (level, difficulty, requirements, schedule) from the
 * Wynncraft API.
 */
public final class Content {
	private static final String BASE = "https://api.wynncraft.com/v3/map/";

	public enum Kind { CAMP, RAID, WORLD_EVENT }

	public record Info(Kind kind, String name, int level, String difficulty, String length, List<String> requirements,
		@Nullable Integer x, @Nullable Integer y, @Nullable Integer z, int radius, @Nullable Instant nextStart) {

		/** Short lines for the info popup. */
		public List<String> describe() {
			List<String> lines = new ArrayList<>();
			lines.add(kindName() + "  ·  Lv. " + level);
			lines.add(capitalize(difficulty) + " difficulty  ·  " + capitalize(length) + " length");
			if (nextStart != null) {
				long minutes = Duration.between(Instant.now(), nextStart).toMinutes();
				lines.add(minutes >= 0 ? "Starts in " + minutes + " min" : "Started " + -minutes + " min ago");
			}
			int shown = 0;
			for (String requirement : requirements) {
				if (shown++ == 4) {
					lines.add("  + " + (requirements.size() - 4) + " more requirements");
					break;
				}
				lines.add("Req: " + requirement);
			}
			return lines;
		}

		private String kindName() {
			return switch (kind) {
				case CAMP -> "Camp";
				case RAID -> "Raid";
				case WORLD_EVENT -> "World event";
			};
		}
	}

	private volatile List<Info> camps = List.of();
	private volatile List<Info> worldEvents = List.of();
	private final Map<String, Info> raidsByName = new ConcurrentHashMap<>();

	public List<Info> camps() {
		return camps;
	}

	public List<Info> worldEvents() {
		return worldEvents;
	}

	/** Raid details for a raid marker; raids are located in their own instance, so match by name. */
	public @Nullable Info raid(String markerName) {
		return raidsByName.get(markerName.toLowerCase(Locale.ROOT));
	}

	public void load() {
		fetch("camps", Duration.ofHours(1)).thenAccept(list -> camps = parse(list, Kind.CAMP));
		fetch("world-events", Duration.ofMinutes(10)).thenAccept(list -> worldEvents = parse(list, Kind.WORLD_EVENT));
		fetch("raids", Duration.ofHours(6)).thenAccept(list -> {
			for (Info raid : parse(list, Kind.RAID)) {
				raidsByName.put(raid.name().toLowerCase(Locale.ROOT), raid);
			}
		});
	}

	private static java.util.concurrent.CompletableFuture<JsonArray> fetch(String endpoint, Duration maxAge) {
		return CachedFetcher.fetchWithMaxAge(URI.create(BASE + endpoint), Wynnav.cacheDir().resolve("content-" + endpoint + ".json"), maxAge)
			.thenApply(bytes -> JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray())
			.exceptionally(error -> {
				Wynnav.LOGGER.error("Could not load {}", endpoint, error);
				return new JsonArray();
			});
	}

	private static List<Info> parse(JsonArray array, Kind kind) {
		List<Info> result = new ArrayList<>();
		for (JsonElement element : array) {
			try {
				result.add(parseOne(element.getAsJsonObject(), kind));
			} catch (RuntimeException e) {
				Wynnav.LOGGER.debug("Skipping malformed {} entry", kind);
			}
		}
		return List.copyOf(result);
	}

	private static Info parseOne(JsonObject obj, Kind kind) {
		List<String> requirements = new ArrayList<>();
		if (obj.get("requirements") instanceof JsonArray reqs) {
			for (JsonElement req : reqs) {
				JsonObject r = req.getAsJsonObject();
				// Quest names carry a private-use glyph and "^" for apostrophes.
				String value = r.get("value").getAsString().replace('^', '\'').replaceAll("[\\p{Co}\\u058e]", "").strip();
				requirements.add(requirementLabel(r.get("type").getAsString()) + value);
			}
		}

		Integer x = null;
		Integer y = null;
		Integer z = null;
		int radius = 0;
		JsonElement location = obj.get("location");
		JsonObject point = null;
		if (location instanceof JsonObject single) {
			point = single;
		} else if (location instanceof JsonArray many && !many.isEmpty()) {
			JsonObject first = many.get(0).getAsJsonObject();
			point = first.getAsJsonObject("event");
			radius = first.has("radius") ? first.get("radius").getAsInt() : 0;
		}
		if (point != null) {
			x = (int) Math.floor(point.get("x").getAsDouble());
			y = (int) Math.floor(point.get("y").getAsDouble());
			z = (int) Math.floor(point.get("z").getAsDouble());
		}

		Instant nextStart = null;
		if (obj.get("schedule") != null && !obj.get("schedule").isJsonNull()) {
			nextStart = OffsetDateTime.parse(obj.get("schedule").getAsString()).toInstant();
		}

		return new Info(kind, obj.get("name").getAsString(), obj.get("level").getAsInt(), text(obj, "difficulty"),
			text(obj, "length"), List.copyOf(requirements), x, y, z, radius, nextStart);
	}

	private static String requirementLabel(String type) {
		return switch (type) {
			case "COMBAT_LEVEL" -> "Combat level ";
			case "QUEST", "GLOBAL_QUEST" -> "";
			case "CAVE" -> "Cave ";
			default -> capitalize(type.replace('_', ' ')) + " ";
		};
	}

	private static String text(JsonObject obj, String key) {
		return obj.get(key) == null || obj.get(key).isJsonNull() ? "?" : obj.get(key).getAsString();
	}

	static String capitalize(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
