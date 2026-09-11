package dev.wynnav.map;

import dev.wynnav.Wynnav;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Marker icons, downloaded on first use from the official web map. */
public final class MarkerIcons {
	private static final String ICON_BASE_URL = "https://map.wynncraft.com/img/";
	private static final Duration MAX_AGE = Duration.ofDays(7);

	private final Map<String, Identifier> loaded = new ConcurrentHashMap<>();
	private final Map<String, Boolean> requested = new ConcurrentHashMap<>();

	/** The icon texture, or null while it is still downloading (or failed). */
	public @Nullable Identifier get(String icon) {
		Identifier id = loaded.get(icon);
		if (id == null && requested.putIfAbsent(icon, Boolean.TRUE) == null) {
			request(icon);
		}
		return id;
	}

	private void request(String icon) {
		if (!icon.matches("[A-Za-z0-9_]+\\.png")) {
			return;
		}
		Identifier id = Wynnav.id("icons/" + icon.toLowerCase(Locale.ROOT));
		CachedFetcher.fetchWithMaxAge(URI.create(ICON_BASE_URL + icon), Wynnav.cacheDir().resolve("icons/" + icon), MAX_AGE)
			.thenCompose(bytes -> Textures.register(id, bytes))
			.thenAccept(texture -> loaded.put(icon, texture))
			.exceptionally(error -> {
				Wynnav.LOGGER.warn("Could not load marker icon {}", icon, error);
				return null;
			});
	}
}
