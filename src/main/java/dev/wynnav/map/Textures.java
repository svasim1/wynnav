package dev.wynnav.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** Turns downloaded PNG bytes into GPU textures. */
final class Textures {
	private Textures() {}

	/**
	 * Decodes on the calling (background) thread, then uploads on the render thread. Smooth when
	 * shrunk, blocky when enlarged, so zoomed-in map tiles keep crisp block edges.
	 */
	static CompletableFuture<Identifier> register(Identifier id, byte[] png) {
		NativeImage image;
		try {
			image = NativeImage.read(png);
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
		return CompletableFuture.supplyAsync(() -> {
			Minecraft.getInstance().getTextureManager().register(id, new UploadedTexture(id, image));
			return id;
		}, Minecraft.getInstance()).whenComplete((result, error) -> {
			if (error != null) {
				image.close();
			}
		});
	}

	static void release(Identifier id) {
		Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(id));
	}

	/**
	 * A texture that keeps no copy of its pixels in memory after uploading them, unlike vanilla's
	 * DynamicTexture. Map tiles are never edited, so the CPU copy would only waste memory.
	 */
	private static final class UploadedTexture extends AbstractTexture {
		UploadedTexture(Identifier id, NativeImage image) {
			try (image) {
				GpuDevice device = RenderSystem.getDevice();
				texture = device.createTexture(id::toString, 5, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);
				textureView = device.createTextureView(texture);
				sampler = RenderSystem.getSamplerCache()
					.getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, false);
				device.createCommandEncoder().writeToTexture(texture, image);
			}
		}
	}
}
