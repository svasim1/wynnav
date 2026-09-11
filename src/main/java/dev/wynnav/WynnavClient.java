package dev.wynnav;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wynnav.map.MapMarkers;
import dev.wynnav.map.MapTiles;
import dev.wynnav.map.MarkerIcons;
import dev.wynnav.ui.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class WynnavClient implements ClientModInitializer {
	private static final MapTiles TILES = new MapTiles();
	private static final MapMarkers MARKERS = new MapMarkers();
	private static final MarkerIcons ICONS = new MarkerIcons();
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

	public static KeyMapping openMapKey() {
		return openMapKey;
	}

	@Override
	public void onInitializeClient() {
		TILES.load();
		MARKERS.load();

		KeyMapping.Category category = KeyMapping.Category.register(Wynnav.id("keys"));
		openMapKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.wynnav.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMapKey.consumeClick()) {
				if (client.screen == null && client.player != null) {
					client.setScreen(new WorldMapScreen());
				}
			}
		});
	}
}
