package dev.wynnav.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.wynnav.Wynnav;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Where your friends, party and guild members are, from the official Wynncraft API
 * ({@code /v3/map/locations/player}). Wynncraft answers this for the player the API token belongs
 * to while they are online, so nobody else needs a mod. The API caches it for 15 seconds, so we
 * poll at that rate; nearby players are drawn at their live position instead (see
 * {@link PlayerHeads#visible}).
 */
public final class FriendLocations {
	private static final URI LOCATIONS_URI = URI.create("https://api.wynncraft.com/v3/map/locations/player");
	private static final long POLL_MS = 15_000;
	// When Wynncraft refuses token access to this route, check again only now and then.
	private static final long UNSUPPORTED_POLL_MS = 10 * 60_000;
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public enum Relation {
		// Order matters: someone who is both in your party and a friend is shown as party.
		PARTY, FRIEND, GUILD
	}

	public record Member(UUID uuid, String name, @Nullable String server, int x, int y, int z, Relation relation) {}

	public enum Status { NO_TOKEN, OK, OFFLINE, INVALID_TOKEN, UNSUPPORTED, ERROR }

	private volatile List<Member> members = List.of();
	private volatile Status status = ApiToken.isSet() ? Status.OK : Status.NO_TOKEN;
	private volatile @Nullable String myServer;
	private long nextPoll;
	private boolean polling;
	private boolean usePost;

	public List<Member> members() {
		return members;
	}

	public Status status() {
		return status;
	}

	/** The world the API says you are on, to tell which members are on the same world. */
	public @Nullable String myServer() {
		return myServer;
	}

	/** Call every client tick; polls while in game and a token is set. */
	public void tick(boolean inGame) {
		if (!ApiToken.isSet()) {
			status = Status.NO_TOKEN;
			return;
		}
		long now = Util.getMillis();
		if (!inGame || polling || now < nextPoll) {
			return;
		}
		polling = true;
		nextPoll = now + POLL_MS;
		fetch().whenComplete((result, error) -> polling = false);
	}

	/** Forces a fetch on the next tick, e.g. after the token was changed. */
	public void refreshSoon() {
		nextPoll = 0;
		usePost = false;
		if (ApiToken.isSet()) {
			status = Status.OK;
		} else {
			status = Status.NO_TOKEN;
			members = List.of();
		}
	}

	private CompletableFuture<Void> fetch() {
		HttpRequest.Builder request = HttpRequest.newBuilder(LOCATIONS_URI)
			.timeout(Duration.ofSeconds(15))
			.header("Authorization", "Bearer " + ApiToken.get())
			.header("User-Agent", "wynnav-mod");
		// The web map uses GET; the route has also answered "only POST allowed", so fall back.
		request = usePost ? request.POST(HttpRequest.BodyPublishers.noBody()) : request.GET();
		return CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				int code = response.statusCode();
				if (code == 405 && !usePost) {
					usePost = true;
					nextPoll = 0;
					return;
				}
				if (code == 200) {
					parse(response.body());
				} else if (response.body().contains("CSRF")) {
					// As of October 2026 the live route only serves the website's own login session
					// (cookie + CSRF), even though the docs describe token access.
					status = Status.UNSUPPORTED;
					members = List.of();
					nextPoll = Util.getMillis() + UNSUPPORTED_POLL_MS;
				} else if (code == 401 || (code == 403 && response.body().contains("Token"))) {
					status = Status.INVALID_TOKEN;
					members = List.of();
				} else if (code == 403 || code == 404) {
					// "No eligible online player location could be resolved": you are not online.
					status = Status.OFFLINE;
					members = List.of();
				} else {
					status = Status.ERROR;
					Wynnav.LOGGER.warn("Friend locations request failed with HTTP {}", code);
				}
			})
			.exceptionally(error -> {
				status = Status.ERROR;
				Wynnav.LOGGER.warn("Friend locations request failed: {}", error.getMessage());
				return null;
			});
	}

	public void parse(String body) {
		JsonElement root = JsonParser.parseString(body);
		JsonObject me = root instanceof JsonArray array && !array.isEmpty() ? array.get(0).getAsJsonObject()
			: root instanceof JsonObject obj ? obj : null;
		if (me == null) {
			status = Status.OFFLINE;
			members = List.of();
			return;
		}
		myServer = text(me, "server");
		Map<UUID, Member> byPlayer = new LinkedHashMap<>();
		addAll(byPlayer, me.get("party"), Relation.PARTY);
		addAll(byPlayer, me.get("friends"), Relation.FRIEND);
		addAll(byPlayer, me.get("guild"), Relation.GUILD);
		members = List.copyOf(byPlayer.values());
		status = myServer == null ? Status.OFFLINE : Status.OK;
	}

	private static void addAll(Map<UUID, Member> into, @Nullable JsonElement list, Relation relation) {
		if (!(list instanceof JsonArray array)) {
			return;
		}
		for (JsonElement element : array) {
			try {
				JsonObject obj = element.getAsJsonObject();
				UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
				into.putIfAbsent(uuid, new Member(uuid, obj.get("name").getAsString(), text(obj, "server"),
					obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt(), relation));
			} catch (RuntimeException e) {
				Wynnav.LOGGER.debug("Skipping unreadable player location {}", element);
			}
		}
	}

	private static @Nullable String text(JsonObject obj, String key) {
		JsonElement value = obj.get(key);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}

	/** For tests: show these members as if the API had returned them. */
	public void setForTesting(List<Member> testMembers, @Nullable String server) {
		members = List.copyOf(testMembers);
		myServer = server;
	}
}
