package dev.wynnav.map;

import dev.wynnav.Wynnav;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** Marker icons, downloaded on first use from the official web map. */
public final class MarkerIcons {
	private static final String ICON_BASE_URL = "https://map.wynncraft.com/img/";
	private static final Duration MAX_AGE = Duration.ofDays(7);

	/** An icon texture and its size, which the map needs to draw it pixel-perfect. */
	public record Icon(Identifier texture, int width, int height) {}

	private final Map<String, Icon> loaded = new ConcurrentHashMap<>();
	private final Map<String, Boolean> requested = new ConcurrentHashMap<>();

	/** The icon, or null while it is still downloading (or failed). */
	public @Nullable Icon get(String icon) {
		Icon loadedIcon = loaded.get(icon);
		if (loadedIcon == null && requested.putIfAbsent(icon, Boolean.TRUE) == null) {
			request(icon);
		}
		return loadedIcon;
	}

	private void request(String icon) {
		if (!icon.matches("[A-Za-z0-9_]+\\.png")) {
			return;
		}
		Identifier id = Wynnav.id("icons/" + icon.toLowerCase(Locale.ROOT));
		CachedFetcher.fetchWithMaxAge(URI.create(ICON_BASE_URL + icon), Wynnav.cacheDir().resolve("icons/" + icon), MAX_AGE)
			.thenComposeAsync(bytes -> Textures.register(id, bytes), Util.backgroundExecutor())
			.thenAccept(texture -> loaded.put(icon, new Icon(texture.id(), texture.width(), texture.height())))
			.exceptionally(error -> {
				Wynnav.LOGGER.warn("Could not load marker icon {}", icon, error);
				return null;
			});
	}
}
