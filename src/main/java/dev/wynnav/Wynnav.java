package dev.wynnav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants and locations. */
public final class Wynnav {
	public static final String MOD_ID = "wynnav";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Wynnav() {}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** User data that should survive cache wipes (waypoints, settings). */
	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
	}

	/** Downloaded data that can be thrown away and re-fetched. */
	public static Path cacheDir() {
		return FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("cache");
	}
}
