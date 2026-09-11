package dev.wynnav.net;

import dev.wynnav.Wynnav;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Downloads files into an on-disk cache. A cached copy is reused while it is fresh (by md5 or by
 * age), and a stale copy is still returned when the network is unavailable.
 */
public final class CachedFetcher {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private CachedFetcher() {}

	/** Fetch a file whose expected content is known by md5; the cached copy never expires. */
	public static CompletableFuture<byte[]> fetchByMd5(URI uri, Path file, String md5) {
		return fetch(uri, file, md5, null);
	}

	/** Fetch a file that is considered fresh for {@code maxAge} after it was downloaded. */
	public static CompletableFuture<byte[]> fetchWithMaxAge(URI uri, Path file, Duration maxAge) {
		return fetch(uri, file, null, maxAge);
	}

	private static CompletableFuture<byte[]> fetch(URI uri, Path file, @Nullable String md5, @Nullable Duration maxAge) {
		byte[] cached = readIfPresent(file);
		if (cached != null && isFresh(file, cached, md5, maxAge)) {
			return CompletableFuture.completedFuture(cached);
		}

		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(30))
			.header("User-Agent", "wynnav-mod")
			.build();

		return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					throw new IllegalStateException("HTTP " + response.statusCode() + " for " + uri);
				}
				byte[] body = response.body();
				if (md5 != null && !md5.equalsIgnoreCase(md5(body))) {
					throw new IllegalStateException("md5 mismatch for " + uri);
				}
				write(file, body);
				return body;
			})
			.exceptionally(error -> {
				if (cached != null) {
					Wynnav.LOGGER.warn("Using stale cache for {}: {}", uri, error.getMessage());
					return cached;
				}
				throw new IllegalStateException("Could not download " + uri, error);
			});
	}

	private static boolean isFresh(Path file, byte[] cached, @Nullable String md5, @Nullable Duration maxAge) {
		if (md5 != null) {
			return md5.equalsIgnoreCase(md5(cached));
		}
		try {
			Instant modified = Files.getLastModifiedTime(file).toInstant();
			return maxAge != null && modified.plus(maxAge).isAfter(Instant.now());
		} catch (IOException e) {
			return false;
		}
	}

	private static byte @Nullable [] readIfPresent(Path file) {
		try {
			return Files.exists(file) ? Files.readAllBytes(file) : null;
		} catch (IOException e) {
			Wynnav.LOGGER.warn("Could not read cache file {}", file, e);
			return null;
		}
	}

	private static void write(Path file, byte[] data) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.write(tmp, data);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Wynnav.LOGGER.warn("Could not write cache file {}", file, e);
		}
	}

	private static String md5(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
