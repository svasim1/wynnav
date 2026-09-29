package dev.wynnav;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wynnav.hud.Minimap;
import dev.wynnav.hud.WorldMarker;
import dev.wynnav.map.Content;
import dev.wynnav.map.Gathering;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.map.MapTiles;
import dev.wynnav.map.MarkerIcons;
import dev.wynnav.map.Places;
import dev.wynnav.map.Territories;
import dev.wynnav.ui.WorldMapScreen;
import dev.wynnav.waypoint.DeathPoint;
import dev.wynnav.waypoint.Waypoints;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class WynnavClient implements ClientModInitializer {
	private static final MapTiles TILES = new MapTiles();
	private static final MapMarkers MARKERS = new MapMarkers();
	private static final MarkerIcons ICONS = new MarkerIcons();
	private static final Territories TERRITORIES = new Territories();
	private static final Gathering GATHERING = new Gathering();
	private static final Content CONTENT = new Content();
	private static final Places PLACES = new Places();
	private static final Waypoints WAYPOINTS = new Waypoints();
	// Territories and world event schedules change while playing.
	private static final int REFRESH_TICKS = 20 * 60 * 5;
	private static KeyMapping openMapKey;

	public static MapTiles tiles() {
		return TILES;
	}

	public static MapMarkers markers() {
		return MARKERS;
	}

	public static MarkerIcons icons() {
		return ICONS;
	}

	public static Territories territories() {
		return TERRITORIES;
	}

	public static Gathering gathering() {
		return GATHERING;
	}

	public static Content content() {
		return CONTENT;
	}

	public static Places places() {
		return PLACES;
	}

	public static Waypoints waypoints() {
		return WAYPOINTS;
	}

	public static KeyMapping openMapKey() {
		return openMapKey;
	}

	@Override
	public void onInitializeClient() {
		WAYPOINTS.load();
		TILES.load();
		MARKERS.load();
		TERRITORIES.load();
		GATHERING.load();
		CONTENT.load();
		PLACES.load();

		KeyMapping.Category category = KeyMapping.Category.register(Wynnav.id("keys"));
		openMapKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.wynnav.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, category));

		WorldMarker worldMarker = new WorldMarker(WAYPOINTS);
		DeathPoint deathPoint = new DeathPoint(WAYPOINTS);
		int[] ticks = {0};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMapKey.consumeClick()) {
				if (client.screen == null && client.player != null) {
					client.setScreen(new WorldMapScreen());
				}
			}
			worldMarker.tick(client);
			deathPoint.tick(client);
			ticks[0]++;
			if (ticks[0] % REFRESH_TICKS == 0) {
				TERRITORIES.load();
				CONTENT.load();
			}
		});

		Minimap minimap = new Minimap();
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Wynnav.id("world_marker"), worldMarker::renderLabel);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Wynnav.id("minimap"), minimap::render);
		WorldRenderEvents.BEFORE_ENTITIES.register(worldMarker::renderBeam);
	}
}
