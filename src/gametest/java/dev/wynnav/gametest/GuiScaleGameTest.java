package dev.wynnav.gametest;

import dev.wynnav.WynnavClient;
import dev.wynnav.ui.SettingsScreen;
import dev.wynnav.ui.WorldMapScreen;
import dev.wynnav.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * Screenshots the HUD, the map (with the layers panel and a popup) and the settings screen at a
 * 1920x1080 window for every GUI scale, to catch layout that breaks at small or large scales.
 */
public class GuiScaleGameTest implements FabricClientGameTest {
	private static final int[] SCALES = {1, 2, 3, 4, 0}; // 0 = Auto

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode spectator @a");
			singleplayer.getServer().runCommand("tp @a -720 120 -1590 135 0");
			context.waitFor(client -> WynnavClient.tiles().tiles().size() >= 18 && !WynnavClient.places().places().isEmpty(), 20 * 180);
			context.runOnClient(client -> WynnavClient.waypoints().track(Waypoint.create("", -762, 70, -1632, 0xFFFFFFFF)));
			context.waitTicks(60);

			for (int scale : SCALES) {
				String name = scale == 0 ? "auto" : "x" + scale;
				context.runOnClient(client -> {
					client.options.guiScale().set(scale);
					client.resizeDisplay();
				});
				context.waitTicks(5);
				context.takeScreenshot("scale-" + name + "-hud");

				context.setScreen(WorldMapScreen::new);
				context.waitTicks(5);
				context.clickScreenButton("wynnav.map.layers");
				// Right-click near the middle of the window for a popup.
				context.getInput().setCursorPos(900, 520);
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				context.waitTicks(5);
				context.takeScreenshot("scale-" + name + "-map");

				context.setScreen(() -> new SettingsScreen(null));
				context.waitTicks(5);
				context.takeScreenshot("scale-" + name + "-settings");
				context.setScreen(() -> null);
			}

			// Minecraft's smallest GUI: 1280x960 at scale 4 is exactly 320x240 GUI pixels.
			context.getInput().resizeWindow(1280, 960);
			context.runOnClient(client -> {
				client.options.guiScale().set(4);
				client.resizeDisplay();
			});
			context.setScreen(WorldMapScreen::new);
			context.waitTicks(5);
			context.takeScreenshot("scale-min-320x240-map");
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("scale-min-320x240-settings");
			context.setScreen(() -> null);
		}
	}
}
