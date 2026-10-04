package dev.wynnav.gametest;

import dev.wynnav.WynnavClient;
import dev.wynnav.config.Settings;
import dev.wynnav.hud.Minimap;
import dev.wynnav.render.MapView;
import dev.wynnav.social.FriendLocations;
import java.util.List;
import java.util.UUID;
import dev.wynnav.ui.WorldMapScreen;
import dev.wynnav.waypoint.DeathPoint;
import dev.wynnav.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Drives a real client through the main features and saves screenshots. Cursor positions are
 * window pixels; the default test window is 854x480 at GUI scale 2.
 */
public class MapScreenGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			// Spectators don't fall, so the player survives being placed in the air.
			singleplayer.getServer().runCommand("gamemode spectator @a");
			// Near the Ragni fast-travel point, inside the downloaded map tiles.
			singleplayer.getServer().runCommand("tp @a -720 120 -1590 135 0");
			context.waitTicks(20);
			context.waitFor(client -> WynnavClient.tiles().tiles().size() >= 18
				&& !WynnavClient.markers().markers().isEmpty()
				&& !WynnavClient.territories().territories().isEmpty()
				&& !WynnavClient.gathering().nodes().isEmpty()
				&& !WynnavClient.content().camps().isEmpty()
				&& WynnavClient.places().places().size() >= 100, 20 * 180);
			context.waitTicks(40); // marker icons

			// Friends: parse the documented API response shape, then show two players for real.
			context.runOnClient(client -> {
				FriendLocations parsed = new FriendLocations();
				parsed.parse(API_SAMPLE);
				if (parsed.members().size() != 2 || !"WC1".equals(parsed.myServer())
					|| parsed.members().get(0).relation() != FriendLocations.Relation.PARTY) {
					throw new AssertionError("Unexpected parse result: " + parsed.members());
				}
				WynnavClient.friends().setForTesting(List.of(
					new FriendLocations.Member(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "Notch", "WC1",
						-735, 70, -1610, FriendLocations.Relation.FRIEND),
					new FriendLocations.Member(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), "jeb_", "WC7",
						-690, 70, -1575, FriendLocations.Relation.FRIEND)), "WC1");
			});
			context.waitTicks(60); // skins download from Mojang
			context.takeScreenshot("hud-1-minimap-square");
			// Only tiles that have been on screen are decoded and uploaded.
			context.runOnClient(client -> {
				long loaded = WynnavClient.tiles().loadedCount();
				if (loaded == 0 || loaded > 6) {
					throw new AssertionError("Expected only the tiles around the player to be loaded, got " + loaded);
				}
			});
			context.runOnClient(client -> {
				Settings.get().minimap.shape = Settings.MinimapShape.ROUND;
				Settings.get().minimap.rotation = Settings.MinimapRotation.FOLLOW_PLAYER;
			});
			context.waitTicks(2);
			context.takeScreenshot("hud-2-minimap-round-follow");
			checkCompass();

			// Something ~60 blocks ahead (the player faces north-west).
			context.runOnClient(client -> WynnavClient.waypoints().track(Waypoint.create("Test target", -762, 70, -1632, 0xFF1E88E5)));
			context.waitTicks(5);
			context.takeScreenshot("hud-3-world-marker");
			// Behind the player: the label should stick to the screen edge.
			context.runOnClient(client -> WynnavClient.waypoints().track(Waypoint.create("Behind you", -600, 70, -1480, 0xFFFDD835)));
			context.waitTicks(5);
			context.takeScreenshot("hud-4-world-marker-behind");

			// Map with layers.
			context.runOnClient(client -> {
				Settings.get().layers.territories = true;
				Settings.get().layers.gathering = true;
			});
			context.getInput().pressKey(WynnavClient.openMapKey());
			context.waitForScreen(WorldMapScreen.class);

			// Walking with the map open moves the player...
			double[] before = playerXZ(context);
			context.getInput().holdKeyFor(options -> options.keyUp, 20);
			double[] after = playerXZ(context);
			if (Math.hypot(after[0] - before[0], after[1] - before[1]) < 2) {
				throw new AssertionError("Player did not move with the map open");
			}
			// ...but not while typing in the search box, where W is just a letter.
			context.getInput().setCursorPos(60, 30);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(40); // spectator flight glides on after the walk above
			before = playerXZ(context);
			context.getInput().holdKeyFor(options -> options.keyUp, 20);
			after = playerXZ(context);
			context.takeScreenshot("map-00-typing-w-in-search");
			if (Math.hypot(after[0] - before[0], after[1] - before[1]) > 0.5) {
				throw new AssertionError("Player moved while typing in search");
			}
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // clears and leaves the search box
			context.waitTicks(2);

			// Middle-click tracks the spot under the cursor; again stops tracking.
			context.runOnClient(client -> WynnavClient.waypoints().track(null));
			context.getInput().setCursorPos(300, 300);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (WynnavClient.waypoints().tracked().isEmpty()) {
					throw new AssertionError("Middle-click did not track anything");
				}
			});
			context.takeScreenshot("map-0-middle-click-track");
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (WynnavClient.waypoints().tracked().isPresent()) {
					throw new AssertionError("Second middle-click did not stop tracking");
				}
			});

			// Place names at two zoom levels (the map opens at 1.0x and follows the player).
			context.getInput().setCursorPos(427, 240);
			context.getInput().scroll(-11); // ~0.13x: provinces and towns
			context.waitTicks(3);
			context.takeScreenshot("map-names-far");
			context.getInput().scroll(3); // ~0.23x: towns only, provinces hidden
			context.waitTicks(3);
			context.takeScreenshot("map-names-mid");
			context.getInput().scroll(5); // ~0.58x: towns with levels and smaller places
			context.waitTicks(3);
			context.takeScreenshot("map-names-near");
			context.getInput().scroll(3);

			context.clickScreenButton("wynnav.map.layers");
			context.waitTicks(5);
			context.takeScreenshot("map-1-layers");

			// Search: click the box, type, pick the first result.
			context.getInput().setCursorPos(60, 30);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.getInput().typeChars("Detlas");
			context.waitTicks(3);
			context.takeScreenshot("map-2-search");
			context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
			context.waitTicks(10);
			context.takeScreenshot("map-3-search-result");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			assertScreen(context, WorldMapScreen.class);

			context.clickScreenButton("wynnav.map.settings");
			context.waitTicks(5);
			context.takeScreenshot("settings");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitForScreen(WorldMapScreen.class);

			// Paste what Wynntils' Export button puts on the clipboard.
			context.runOnClient(client -> client.keyboardHandler.setClipboard(WYNNTILS_EXPORT));
			context.clickScreenButton("wynnav.map.waypoints");
			context.waitTicks(2);
			context.getInput().setCursorPos(694, 136);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			context.takeScreenshot("map-4-wynntils-import");
			context.runOnClient(client -> {
				long imported = WynnavClient.waypoints().all().stream().filter(w -> w.name().startsWith("Loot Chest")).count();
				if (imported != 2) {
					throw new AssertionError("Expected 2 imported waypoints, got " + imported);
				}
			});

			// Death point.
			context.setScreen(() -> null);
			singleplayer.getServer().runCommand("gamemode survival @a");
			singleplayer.getServer().runCommand("kill @a");
			context.waitForScreen(DeathScreen.class);
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (WynnavClient.waypoints().find(DeathPoint.ID).isEmpty()) {
					throw new AssertionError("No death point was created");
				}
			});
		}
	}

	private static final String WYNNTILS_EXPORT = """
		[{"name": "Loot Chest 4", "color": "#ffaa00ff", "icon": "chestT4", "visibility": "default", "location": {"x": -700, "y": 70, "z": -1600}},
		 {"name": "Loot Chest 3", "color": "#00ffffff", "icon": "chestT3", "visibility": "default", "location": {"x": -740, "z": -1560}}]
		""";

	/**
	 * Compass directions on a rotating minimap. Facing north-west (yaw 135), north must be up and
	 * to the right, east down-right, south down-left and west up-left; facing south (yaw 0), north
	 * must be straight down. On a north-up map they sit at top, right, bottom, left.
	 */
	private static void checkCompass() {
		float[][] facingNorthWest = compass(135, true);
		expect("N facing NW", facingNorthWest[0], 0.707f, -0.707f);
		expect("E facing NW", facingNorthWest[1], 0.707f, 0.707f);
		expect("S facing NW", facingNorthWest[2], -0.707f, 0.707f);
		expect("W facing NW", facingNorthWest[3], -0.707f, -0.707f);
		expect("N facing S", compass(0, true)[0], 0, 1);
		expect("N facing E", compass(-90, true)[0], -1, 0);
		float[][] northUp = compass(135, false);
		expect("N north-up", northUp[0], 0, -1);
		expect("E north-up", northUp[1], 1, 0);
		expect("S north-up", northUp[2], 0, 1);
		expect("W north-up", northUp[3], -1, 0);
	}

	private static float[][] compass(float yaw, boolean rotating) {
		// The same view the minimap builds.
		MapView view = new MapView(0, 0, 1, rotating ? Math.toRadians(180 - yaw) : 0, 0, 0);
		return new float[][] {
			Minimap.screenDirection(view, 0, -1), Minimap.screenDirection(view, 1, 0),
			Minimap.screenDirection(view, 0, 1), Minimap.screenDirection(view, -1, 0)};
	}

	private static void expect(String what, float[] actual, float x, float y) {
		if (Math.abs(actual[0] - x) > 0.01 || Math.abs(actual[1] - y) > 0.01) {
			throw new AssertionError(what + ": expected (" + x + ", " + y + ") on screen, got (" + actual[0] + ", " + actual[1] + ")");
		}
	}

	private static final String API_SAMPLE = """
		[{"uuid": "98bc2236-ada5-4039-b560-8aaa4cc50384", "name": "Me", "nickname": null, "character": "x", "server": "WC1",
		  "x": 1, "y": 2, "z": 3,
		  "friends": [{"uuid": "11111111-1111-1111-1111-111111111111", "name": "Pal", "nickname": null, "character": "c", "server": "WC2", "x": 10, "y": 64, "z": -10},
		              {"uuid": "22222222-2222-2222-2222-222222222222", "name": "Both", "nickname": null, "character": "c", "server": "WC1", "x": 5, "y": 64, "z": 5}],
		  "party": [{"uuid": "22222222-2222-2222-2222-222222222222", "name": "Both", "nickname": null, "character": "c", "server": "WC1", "x": 5, "y": 64, "z": 5}],
		  "guild": []}]
		""";

	private static double[] playerXZ(ClientGameTestContext context) {
		return context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getZ()});
	}

	private static void assertScreen(ClientGameTestContext context, Class<? extends Screen> expected) {
		context.runOnClient(client -> {
			if (!expected.isInstance(client.screen)) {
				throw new AssertionError("Expected " + expected.getSimpleName() + " but was " + client.screen);
			}
		});
	}
}
