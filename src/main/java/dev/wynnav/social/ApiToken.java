package dev.wynnav.social;

import dev.wynnav.Wynnav;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The user's personal Wynncraft API token (created at wynncraft.com/account/dashboard). Kept in
 * its own file rather than settings.json so sharing settings never shares the token. It is only
 * ever sent to api.wynncraft.com.
 */
public final class ApiToken {
	private static final Path FILE = Wynnav.configDir().resolve("api-token.txt");
	private static String token;

	private ApiToken() {}

	public static String get() {
		if (token == null) {
			try {
				token = Files.exists(FILE) ? Files.readString(FILE).strip() : "";
			} catch (IOException e) {
				Wynnav.LOGGER.error("Could not read the API token file", e);
				token = "";
			}
		}
		return token;
	}

	public static boolean isSet() {
		return !get().isEmpty();
	}

	public static void set(String value) {
		token = value.strip();
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, token);
		} catch (IOException e) {
			Wynnav.LOGGER.error("Could not save the API token file", e);
		}
	}
}
