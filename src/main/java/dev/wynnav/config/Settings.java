package dev.wynnav.config;

import dev.wynnav.Wynnav;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * All user settings, saved as {@code config/wynnav/settings.json}. Fields are public and mutable
 * so the settings screen can bind to them directly; call {@link #save()} after changing them.
 */
public final class Settings {
	public enum MinimapShape { SQUARE, ROUND }

	public enum MinimapRotation { NORTH_UP, FOLLOW_PLAYER }

	public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	public static final class WorldMarker {
		public boolean showBeam = true;
		public boolean showIcon = true;
		/** Scale of the floating icon and label. */
		public double size = 1.0;
		/** 0..1, applies to both beam and label. */
		public double opacity = 0.85;
	}

	public static final class Minimap {
		public boolean enabled = true;
		public MinimapShape shape = MinimapShape.SQUARE;
		public MinimapRotation rotation = MinimapRotation.NORTH_UP;
		// Top-left: vanilla shows potion effects top-right and Wynncraft's scoreboard sits on the right.
		public Corner corner = Corner.TOP_LEFT;
		/** Side length (square) or diameter (round) in GUI pixels. */
		public int size = 110;
		/** Screen pixels per block. */
		public double zoom = 1.0;
		public double opacity = 1.0;
		public boolean showCoordinates = true;
		public boolean showMarkers = true;
	}

	public WorldMarker worldMarker = new WorldMarker();
	public Minimap minimap = new Minimap();

	private static final Path FILE = Wynnav.configDir().resolve("settings.json");
	private static Settings instance;

	public static Settings get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Settings load() {
		if (Files.exists(FILE)) {
			try {
				Settings loaded = Wynnav.GSON.fromJson(Files.readString(FILE), Settings.class);
				if (loaded != null) {
					return loaded.withDefaultsFilled();
				}
			} catch (IOException | RuntimeException e) {
				Wynnav.LOGGER.error("Could not read settings from {}, using defaults", FILE, e);
			}
		}
		return new Settings();
	}

	/** Older or hand-edited files may miss whole sections. */
	private Settings withDefaultsFilled() {
		Settings defaults = new Settings();
		if (worldMarker == null) {
			worldMarker = defaults.worldMarker;
		}
		if (minimap == null) {
			minimap = defaults.minimap;
		}
		return this;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("settings.json.tmp");
			Files.writeString(tmp, Wynnav.GSON.toJson(this));
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Wynnav.LOGGER.error("Could not save settings to {}", FILE, e);
		}
	}
}
