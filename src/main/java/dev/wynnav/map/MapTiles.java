package dev.wynnav.map;

import com.google.gson.reflect.TypeToken;
import dev.wynnav.Wynnav;
import dev.wynnav.net.CachedFetcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;

/**
 * Top-down terrain images of the world. Uses the tile index published by the Wynntils team
 * (one PNG per region, 1 pixel per block, with its world bounds and md5).
 */
public final class MapTiles {
	private static final URI INDEX_URI = URI.create("https://cdn.wynntils.com/static/Reference/maps.json");
	private static final String TILE_BASE_URL = "https://cdn.wynntils.com/static/";

	/** A region of the world with its loaded texture. Bounds are inclusive block coordinates. */
	public record Tile(String name, int x1, int z1, int x2, int z2, Identifier texture, boolean mainWorld) {
		public int width() {
			return x2 - x1 + 1;
		}

		public int height() {
			return z2 - z1 + 1;
		}

		public boolean intersects(double minX, double minZ, double maxX, double maxZ) {
			return x1 <= maxX && x2 + 1 >= minX && z1 <= maxZ && z2 + 1 >= minZ;
		}
	}

	private record IndexEntry(String name, String path, int x1, int z1, int x2, int z2, String md5) {}

	private final List<Tile> tiles = new CopyOnWriteArrayList<>();

	/** Loaded tiles, with quest/instance areas first so the main world draws on top of them. */
	public List<Tile> tiles() {
		return tiles;
	}

	public void load() {
		CachedFetcher.fetchWithMaxAge(INDEX_URI, Wynnav.cacheDir().resolve("tiles/index.json"), Duration.ofHours(6))
			.thenAccept(bytes -> {
				List<IndexEntry> entries = Wynnav.GSON.fromJson(
					new String(bytes, StandardCharsets.UTF_8), new TypeToken<List<IndexEntry>>() {}.getType());
				for (IndexEntry entry : entries) {
					loadTile(entry);
				}
			})
			.exceptionally(error -> {
				Wynnav.LOGGER.error("Could not load the map tile index", error);
				return null;
			});
	}

	private void loadTile(IndexEntry entry) {
		URI uri = URI.create(TILE_BASE_URL + entry.path());
		Identifier id = Wynnav.id("tiles/" + entry.md5().toLowerCase(Locale.ROOT));
		CachedFetcher.fetchByMd5(uri, Wynnav.cacheDir().resolve("tiles/" + entry.md5() + ".png"), entry.md5())
			.thenCompose(bytes -> Textures.register(id, bytes))
			.thenAccept(texture -> {
				boolean main = entry.name().startsWith("Main ");
				tiles.add(new Tile(entry.name(), entry.x1(), entry.z1(), entry.x2(), entry.z2(), texture, main));
				tiles.sort(Comparator.comparing(Tile::mainWorld));
			})
			.exceptionally(error -> {
				Wynnav.LOGGER.warn("Could not load map tile {}", entry.name(), error);
				return null;
			});
	}
}
