package dev.wynnav.social;

import dev.wynnav.Wynnav;
import dev.wynnav.WynnavClient;
import dev.wynnav.config.Settings;
import dev.wynnav.render.Icons;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;

/** Friends' heads for the map and minimap: where to draw them and their skin faces. */
public final class PlayerHeads {
	/** A member resolved to a drawable position. */
	public record Placed(FriendLocations.Member member, double x, double z, boolean sameWorld) {
		public String label() {
			String relation = switch (member.relation()) {
				case PARTY -> "Party";
				case FRIEND -> "Friend";
				case GUILD -> "Guild";
			};
			String world = member.server() != null && !sameWorld ? "  ·  " + member.server() : "";
			return member.name() + " (" + relation + ")" + world;
		}
	}

	private static final Map<UUID, Supplier<PlayerSkin>> SKINS = new ConcurrentHashMap<>();

	private PlayerHeads() {}

	/**
	 * Members to show, filtered by the layer toggles. Someone in render distance is placed at their
	 * live, smoothly interpolated position; everyone else at the last position from the API.
	 */
	public static List<Placed> visible(float partialTick) {
		Settings.MapLayers layers = Settings.get().layers;
		FriendLocations locations = WynnavClient.friends();
		Minecraft minecraft = Minecraft.getInstance();
		String myServer = locations.myServer();
		List<Placed> result = new ArrayList<>();
		for (FriendLocations.Member member : locations.members()) {
			boolean shown = switch (member.relation()) {
				case PARTY -> layers.party;
				case FRIEND -> layers.friends;
				case GUILD -> layers.guild;
			};
			if (!shown) {
				continue;
			}
			boolean sameWorld = myServer == null || member.server() == null || myServer.equalsIgnoreCase(member.server());
			Player entity = minecraft.level == null || !sameWorld ? null : minecraft.level.getPlayerByUUID(member.uuid());
			if (entity != null && entity != minecraft.player) {
				Vec3 pos = entity.getPosition(partialTick);
				result.add(new Placed(member, pos.x, pos.z, true));
			} else {
				result.add(new Placed(member, member.x() + 0.5, member.z() + 0.5, sameWorld));
			}
		}
		return result;
	}

	/**
	 * Draws the 8x8 face centered on (x, y), pixel-perfect like the other sprites (whole screen
	 * pixels per skin pixel); heads on another world are faded.
	 */
	public static void draw(GuiGraphics graphics, Placed placed, float x, float y, int size) {
		int scale = Icons.guiScale();
		int k = Icons.pixelScale(8, size);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(x * scale - 4 * k) / (float) scale, Math.round(y * scale - 4 * k) / (float) scale);
		int alpha = placed.sameWorld() ? 0xFF : 0x80;
		// One real pixel of dark border around the face.
		float border = 1f / scale;
		pose.pushMatrix();
		pose.translate(-border, -border);
		pose.scale(border);
		graphics.fill(0, 0, 8 * k + 2, 8 * k + 2, ARGB.color(alpha, 0x101418));
		pose.popMatrix();
		pose.scale(k / (float) scale);
		PlayerFaceRenderer.draw(graphics, skin(placed.member().uuid()), 0, 0, 8, ARGB.color(alpha, 0xFFFFFF));
		pose.popMatrix();
	}

	private static PlayerSkin skin(UUID uuid) {
		return SKINS.computeIfAbsent(uuid, PlayerHeads::lookup).get();
	}

	/**
	 * Uses the tab list when the player is on our server; otherwise asks Mojang for the profile in
	 * the background. Shows the default skin until the real one arrives.
	 */
	private static Supplier<PlayerSkin> lookup(UUID uuid) {
		Minecraft minecraft = Minecraft.getInstance();
		PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(uuid);
		if (info != null) {
			return info::getSkin;
		}
		PlayerSkin fallback = DefaultPlayerSkin.get(uuid);
		CompletableFuture<Supplier<PlayerSkin>> loaded = CompletableFuture
			.supplyAsync(() -> minecraft.services().sessionService().fetchProfile(uuid, false), Util.nonCriticalIoPool())
			.thenApply(profile -> profile == null ? null : minecraft.getSkinManager().createLookup(profile.profile(), false))
			.exceptionally(error -> {
				Wynnav.LOGGER.debug("Could not load the skin of {}", uuid, error);
				return null;
			});
		return () -> {
			Supplier<PlayerSkin> real = loaded.getNow(null);
			return real != null ? real.get() : fallback;
		};
	}
}
