package dev.wynnav.map;

import com.google.gson.reflect.TypeToken;
import dev.wynnav.Wynnav;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Top-down terrain images of the world. Uses the tile index published by the Wynntils team
 * (one PNG per region, 1 pixel per block, with its world bounds and md5).
 *
 * <p>All PNGs are downloaded to disk up front (about 11 MB), but a tile is only decoded and put on
 * the GPU the first time it is drawn, and dropped again after a few minutes out of view. Most
 * tiles are quest instances that are rarely looked at, so this saves a few hundred MB.
 */
public final class MapTiles {
	private static final URI INDEX_URI = URI.create("https://cdn.wynntils.com/static/Reference/maps.json");
	private static final String TILE_BASE_URL = "https://cdn.wynntils.com/static/";
	private static final long UNLOAD_AFTER_MS = 3 * 60 * 1000;

	private record IndexEntry(String name, String path, int x1, int z1, int x2, int z2, String md5) {}

	/** A region of the world. Bounds are inclusive block coordinates. */
	public static final class Tile {
		private final IndexEntry entry;
		private final Identifier id;
		private final boolean mainWorld;
		private @Nullable CompletableFuture<?> loading;
		/** The startup download, reused by the first load; later loads read the disk cache. */
		private @Nullable CompletableFuture<byte[]> prefetch;
		private boolean ready;
		private long lastUsed;

		private Tile(IndexEntry entry) {
			this.entry = entry;
			this.id = Wynnav.id("tiles/" + entry.md5().toLowerCase(Locale.ROOT));
			this.mainWorld = entry.name().startsWith("Main ");
		}

		public int x1() {
			return entry.x1();
		}

		public int z1() {
			return entry.z1();
		}

		public int x2() {
			return entry.x2();
		}

		public int z2() {
			return entry.z2();
		}

		public boolean intersects(double minX, double minZ, double maxX, double maxZ) {
			return x1() <= maxX && x2() + 1 >= minX && z1() <= maxZ && z2() + 1 >= minZ;
		}

		/** The texture if it is on the GPU; otherwise starts loading it and returns null for now. */
		public @Nullable Identifier texture() {
			lastUsed = Util.getMillis();
			if (ready) {
				return id;
			}
			if (loading == null) {
				CompletableFuture<byte[]> bytes = prefetch != null ? prefetch : fetch(entry);
				prefetch = null;
				// Decode on a worker thread: a cached file completes immediately, which would
				// otherwise decode a large PNG in the middle of drawing a frame.
				loading = bytes
					.thenComposeAsync(png -> Textures.register(id, png), Util.backgroundExecutor())
					.thenRun(() -> ready = true)
					.exceptionally(error -> {
						Wynnav.LOGGER.warn("Could not load map tile {}", entry.name(), error);
						return null;
					});
			}
			return null;
		}

		private void unloadIfIdle(long now) {
			if (ready && now - lastUsed > UNLOAD_AFTER_MS) {
				ready = false;
				loading = null;
				Textures.release(id);
			}
		}
	}

	private volatile List<Tile> tiles = List.of();

	/** All tiles, quest/instance areas first so the main world draws on top of them. */
	public List<Tile> tiles() {
		return tiles;
	}

	public void load() {
		CachedFetcher.fetchWithMaxAge(INDEX_URI, Wynnav.cacheDir().resolve("tiles/index.json"), Duration.ofHours(6))
			.thenAccept(bytes -> {
				List<IndexEntry> entries = Wynnav.GSON.fromJson(
					new String(bytes, StandardCharsets.UTF_8), new TypeToken<List<IndexEntry>>() {}.getType());
				List<Tile> loaded = entries.stream().map(Tile::new).sorted(Comparator.comparing(tile -> tile.mainWorld)).toList();
				// Download (not decode) everything now, so tiles appear quickly later and work offline.
				loaded.forEach(tile -> tile.prefetch = fetch(tile.entry));
				tiles = loaded;
			})
			.exceptionally(error -> {
				Wynnav.LOGGER.error("Could not load the map tile index", error);
				return null;
			});
	}

	/** How many tiles are currently on the GPU. */
	public long loadedCount() {
		return tiles.stream().filter(tile -> tile.ready).count();
	}

	/** Call regularly on the render thread. */
	public void unloadIdleTiles() {
		long now = Util.getMillis();
		for (Tile tile : tiles) {
			tile.unloadIfIdle(now);
		}
	}

	private static CompletableFuture<byte[]> fetch(IndexEntry entry) {
		Path file = Wynnav.cacheDir().resolve("tiles/" + entry.md5() + ".png");
		return CachedFetcher.fetchByMd5(URI.create(TILE_BASE_URL + entry.path()), file, entry.md5());
	}
}
