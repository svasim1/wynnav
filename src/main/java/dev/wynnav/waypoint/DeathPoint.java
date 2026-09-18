package dev.wynnav.waypoint;

import dev.wynnav.config.Settings;
import dev.wynnav.mixin.BossHealthOverlayAccessor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps a single "Death point" waypoint where you last died. Vanilla deaths are seen through the
 * player's health; on Wynncraft the death screen is drawn as a boss bar made of three font glyphs,
 * which is what we look for. Raid and lootrun deaths don't show that screen.
 */
public final class DeathPoint {
	public static final UUID ID = UUID.nameUUIDFromBytes("wynnav:death-point".getBytes(StandardCharsets.UTF_8));
	private static final String WYNNCRAFT_DEATH_SCREEN = "";
	private static final int COLOR = 0xFFE53935;

	private final Waypoints waypoints;
	private boolean wasDead;

	public DeathPoint(Waypoints waypoints) {
		this.waypoints = waypoints;
	}

	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			wasDead = false;
			return;
		}
		boolean dead = player.isDeadOrDying() || deathScreenShown(minecraft);
		if (dead && !wasDead && Settings.get().deathWaypoints) {
			record(player);
		}
		wasDead = dead;
	}

	private static boolean deathScreenShown(Minecraft minecraft) {
		var events = ((BossHealthOverlayAccessor) minecraft.gui.getBossOverlay()).wynnav$events();
		for (LerpingBossEvent event : events.values()) {
			if (event.getName().getString().equals(WYNNCRAFT_DEATH_SCREEN)) {
				return true;
			}
		}
		return false;
	}

	private void record(LocalPlayer player) {
		Waypoint point = new Waypoint(ID, "Death point", player.getBlockX(), player.getBlockY(), player.getBlockZ(), COLOR);
		if (waypoints.find(ID).isPresent()) {
			waypoints.update(point);
		} else {
			waypoints.add(point);
		}
	}
}
