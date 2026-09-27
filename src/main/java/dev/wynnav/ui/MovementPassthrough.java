package dev.wynnav.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.KeyEvent;

/**
 * Lets the player keep moving while a screen is open. Minecraft stops feeding keys to the game
 * when a screen opens, but player movement still reads the movement key states every tick, so we
 * copy the physical keyboard state into those keys.
 *
 * <p>Only changes are forwarded: sprint and sneak can be toggle keys, which flip on every press.
 */
final class MovementPassthrough {
	private final Map<KeyMapping, Boolean> forwarded = new HashMap<>();

	private static List<KeyMapping> keys(Options options) {
		return List.of(options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keyShift,
			options.keySprint);
	}

	/** @param enabled false while the player is typing, which releases every movement key. */
	void update(boolean enabled) {
		Minecraft minecraft = Minecraft.getInstance();
		for (KeyMapping key : keys(minecraft.options)) {
			boolean held = enabled && isPhysicallyDown(minecraft, key);
			if (held != forwarded.getOrDefault(key, false)) {
				key.setDown(held);
				forwarded.put(key, held);
			}
		}
	}

	boolean isMovementKey(KeyEvent event) {
		for (KeyMapping key : keys(Minecraft.getInstance().options)) {
			if (key.matches(event)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPhysicallyDown(Minecraft minecraft, KeyMapping key) {
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		// Mouse buttons belong to the map (dragging, clicking), so only keyboard keys pass through.
		return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() != InputConstants.UNKNOWN.getValue()
			&& InputConstants.isKeyDown(minecraft.getWindow(), bound.getValue());
	}
}
