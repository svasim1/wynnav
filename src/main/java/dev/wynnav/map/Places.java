package dev.wynnav.map;

import dev.wynnav.Wynnav;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Names of provinces, towns and areas, taken from the labels on the official web map
 * (map.wynncraft.com/js/labels.js). The site has no JSON for these, so the script is parsed.
 */
public final class Places {
	private static final URI LABELS_URI = URI.create("https://map.wynncraft.com/js/labels.js");

	/** The web map styles labels by font size: provinces largest, then towns, then other places. */
	public enum Kind { PROVINCE, TOWN, PLACE }

	public record Place(String name, @Nullable Integer level, int x, int z, Kind kind) {}

	// fromWorldToLatLng(x, y, z, d)) ... html: e(<string>, "<size>", "<color>")
	private static final Pattern LABEL = Pattern.compile(
		"fromWorldToLatLng\\(\\s*(-?\\d+)\\s*,\\s*-?\\d+\\s*,\\s*(-?\\d+)\\s*,[^)]*\\).{0,200}?html:\\s*e\\(", Pattern.DOTALL);
	private static final Pattern SIZE = Pattern.compile("\\s*,\\s*\"(\\d+)\"");
	private static final Pattern LEVEL = Pattern.compile("\\[Lv\\.\\s*(\\d+)");

	private volatile List<Place> places = List.of();

	public List<Place> places() {
		return places;
	}

	public void load() {
		CachedFetcher.fetchWithMaxAge(LABELS_URI, Wynnav.cacheDir().resolve("labels.js"), Duration.ofDays(3))
			.thenAccept(bytes -> {
				List<Place> parsed = parse(new String(bytes, StandardCharsets.UTF_8));
				if (parsed.isEmpty()) {
					Wynnav.LOGGER.warn("No place names found in the official map labels; the page may have changed");
				}
				places = parsed;
			})
			.exceptionally(error -> {
				Wynnav.LOGGER.error("Could not load place names", error);
				return null;
			});
	}

	static List<Place> parse(String script) {
		List<Place> result = new ArrayList<>();
		Matcher label = LABEL.matcher(script);
		while (label.find()) {
			int x = Integer.parseInt(label.group(1));
			int z = Integer.parseInt(label.group(2));
			int[] end = new int[1];
			String html = readJsString(script, label.end(), end);
			if (html == null) {
				continue;
			}
			Matcher size = SIZE.matcher(script).region(end[0], Math.min(script.length(), end[0] + 20));
			int fontSize = size.lookingAt() ? Integer.parseInt(size.group(1)) : 14;

			Matcher level = LEVEL.matcher(html);
			Integer lv = level.find() ? Integer.parseInt(level.group(1)) : null;
			// The name is the text before the level line; drop any markup.
			String name = html.replaceAll("<div.*", "").replaceAll("<[^>]*>", "").strip();
			Kind kind = fontSize >= 20 ? Kind.PROVINCE : fontSize >= 16 ? Kind.TOWN : Kind.PLACE;
			if (!name.isEmpty()) {
				result.add(new Place(name, lv, x, z, kind));
			}
		}
		return List.copyOf(result);
	}

	/** Reads a '...' or "..." JavaScript string literal starting at {@code start} (after spaces). */
	private static @Nullable String readJsString(String text, int start, int[] endOut) {
		int i = start;
		while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		if (i >= text.length() || (text.charAt(i) != '\'' && text.charAt(i) != '"')) {
			return null;
		}
		char quote = text.charAt(i++);
		StringBuilder out = new StringBuilder();
		while (i < text.length()) {
			char c = text.charAt(i++);
			if (c == '\\' && i < text.length()) {
				out.append(text.charAt(i++));
			} else if (c == quote) {
				endOut[0] = i;
				return out.toString();
			} else {
				out.append(c);
			}
		}
		return null;
	}
}
