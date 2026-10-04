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
	private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

	/** An uploaded texture and its size in pixels. */
	record Loaded(Identifier id, int width, int height) {}

	private Textures() {}

	/**
	 * Decodes on the calling (background) thread, then uploads on the render thread. Smooth when
	 * shrunk, blocky when enlarged, so zoomed-in map tiles keep crisp block edges.
	 */
	static CompletableFuture<Loaded> register(Identifier id, byte[] png) {
		// Some servers answer a missing image with an HTML page and status 200.
		if (png.length < 4 || !java.util.Arrays.equals(java.util.Arrays.copyOf(png, 4), PNG_SIGNATURE)) {
			return CompletableFuture.failedFuture(new IOException("Not a PNG: " + id));
		}
		NativeImage image;
		try {
			image = NativeImage.read(png);
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
		int width = image.getWidth();
		int height = image.getHeight();
		return CompletableFuture.supplyAsync(() -> {
			Minecraft.getInstance().getTextureManager().register(id, new UploadedTexture(id, image));
			return new Loaded(id, width, height);
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
