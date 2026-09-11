package dev.wynnav.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Turns downloaded PNG bytes into GPU textures. */
final class Textures {
	private Textures() {}

	/**
	 * Decodes off-thread, then uploads on the render thread. Smooth when shrunk, blocky when
	 * enlarged, so zoomed-in map tiles keep crisp block edges.
	 */
	static CompletableFuture<Identifier> register(Identifier id, byte[] png) {
		NativeImage image;
		try {
			image = NativeImage.read(png);
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
		return CompletableFuture.supplyAsync(() -> {
			Minecraft.getInstance().getTextureManager().register(id, new ClampedTexture(id, image));
			return id;
		}, Minecraft.getInstance());
	}

	private static final class ClampedTexture extends DynamicTexture {
		ClampedTexture(Identifier id, NativeImage image) {
			super(id::toString, image);
			this.sampler = RenderSystem.getSamplerCache()
				.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, false);
		}
	}
}
