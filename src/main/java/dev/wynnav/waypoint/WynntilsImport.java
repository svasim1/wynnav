package dev.wynnav.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wynnav.Wynnav;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Imports waypoints from text copied with the Export button in Wynntils' waypoint manager (a JSON
 * list of points with name, color and location). Nothing is read from disk; the user pastes what
 * they exported. Points that already exist are skipped, so pasting twice is harmless.
 */
public final class WynntilsImport {
	/** {@code valid} is false when the text was not a Wynntils export at all. */
	public record Result(boolean valid, int imported, int skipped) {}

	private WynntilsImport() {}

	public static Result fromText(String text, Waypoints waypoints) {
		List<Waypoint> parsed = parse(text);
		if (parsed == null) {
			return new Result(false, 0, 0);
		}

		Set<String> existing = new HashSet<>();
		for (Waypoint waypoint : waypoints.all()) {
			existing.add(key(waypoint.name(), waypoint.x(), waypoint.z()));
		}
		List<Waypoint> added = new ArrayList<>();
		for (Waypoint waypoint : parsed) {
			if (existing.add(key(waypoint.name(), waypoint.x(), waypoint.z()))) {
				added.add(waypoint);
			}
		}
		if (!added.isEmpty()) {
			waypoints.addAll(added);
		}
		return new Result(true, added.size(), parsed.size() - added.size());
	}

	private static List<Waypoint> parse(String text) {
		JsonElement root;
		try {
			root = JsonParser.parseString(text == null ? "" : text.strip());
		} catch (RuntimeException e) {
			return null;
		}
		if (!(root instanceof JsonArray points)) {
			return null;
		}
		List<Waypoint> result = new ArrayList<>();
		for (JsonElement element : points) {
			try {
				JsonObject poi = element.getAsJsonObject();
				JsonObject location = poi.getAsJsonObject("location");
				Integer y = location.has("y") && !location.get("y").isJsonNull() ? location.get("y").getAsInt() : null;
				result.add(Waypoint.create(poi.get("name").getAsString(), location.get("x").getAsInt(), y,
					location.get("z").getAsInt(), parseColor(poi.get("color"))));
			} catch (RuntimeException e) {
				Wynnav.LOGGER.debug("Skipping unreadable Wynntils point {}", element);
			}
		}
		// An array with nothing usable in it is not a Wynntils export either.
		return result.isEmpty() && !points.isEmpty() ? null : result;
	}

	/** Wynntils writes colors as "#RRGGBBAA". */
	static int parseColor(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return 0xFFFFFFFF;
		}
		String hex = element.getAsString().replace("#", "");
		try {
			return 0xFF000000 | Integer.parseUnsignedInt(hex.substring(0, 6), 16);
		} catch (RuntimeException e) {
			return 0xFFFFFFFF;
		}
	}

	private static String key(String name, int x, int z) {
		return name + "|" + x + "|" + z;
	}
}
