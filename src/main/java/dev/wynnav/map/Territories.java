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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Guild territories: named rectangular areas of the world and the guild currently holding them. */
public final class Territories {
	private static final URI TERRITORIES_URI = URI.create("https://api.wynncraft.com/v3/guild/list/territory");

	public record Territory(String name, int minX, int minZ, int maxX, int maxZ, @Nullable String guild,
		@Nullable String guildPrefix, boolean headquarters) {

		public boolean contains(double x, double z) {
			return x >= minX && x <= maxX + 1 && z >= minZ && z <= maxZ + 1;
		}

		/** A stable color per guild so neighbouring territories of one guild read as one area. */
		public int color() {
			if (guild == null) {
				return 0xFF9E9E9E;
			}
			float hue = (guild.hashCode() & 0xFFFF) / 65535f;
			return Mth.hsvToRgb(hue, 0.65f, 0.95f) | 0xFF000000;
		}
	}

	private volatile List<Territory> territories = List.of();
	// The minimap asks for the player's territory every frame; it rarely changes between frames.
	private @Nullable Territory lastHit;

	public List<Territory> territories() {
		return territories;
	}

	public @Nullable Territory at(double x, double z) {
		Territory cached = lastHit;
		if (cached != null && cached.contains(x, z)) {
			return cached;
		}
		for (Territory territory : territories) {
			if (territory.contains(x, z)) {
				lastHit = territory;
				return territory;
			}
		}
		return null;
	}

	/** Ownership changes often; refreshes at most every few minutes. */
	public void load() {
		CachedFetcher.fetchWithMaxAge(TERRITORIES_URI, Wynnav.cacheDir().resolve("territories.json"), Duration.ofMinutes(5))
			.thenAccept(bytes -> {
				territories = parse(new String(bytes, StandardCharsets.UTF_8));
				lastHit = null;
			})
			.exceptionally(error -> {
				Wynnav.LOGGER.error("Could not load territories", error);
				return null;
			});
	}

	private static List<Territory> parse(String json) {
		List<Territory> result = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(json).getAsJsonObject().entrySet()) {
			try {
				JsonObject obj = entry.getValue().getAsJsonObject();
				JsonObject location = obj.getAsJsonObject("location");
				JsonArray start = location.getAsJsonArray("start");
				JsonArray end = location.getAsJsonArray("end");
				int x1 = start.get(0).getAsInt();
				int z1 = start.get(1).getAsInt();
				int x2 = end.get(0).getAsInt();
				int z2 = end.get(1).getAsInt();
				String guild = null;
				String prefix = null;
				if (obj.get("guild") instanceof JsonObject guildObj && guildObj.get("name") != null && !guildObj.get("name").isJsonNull()) {
					guild = guildObj.get("name").getAsString();
					prefix = guildObj.get("prefix") == null || guildObj.get("prefix").isJsonNull() ? null : guildObj.get("prefix").getAsString();
				}
				boolean hq = obj.get("hq") != null && obj.get("hq").getAsBoolean();
				result.add(new Territory(entry.getKey(), Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2),
					guild, prefix, hq));
			} catch (RuntimeException e) {
				Wynnav.LOGGER.debug("Skipping malformed territory {}", entry.getKey());
			}
		}
		return List.copyOf(result);
	}
}
