package dev.wynnav.waypoint;

import com.google.gson.reflect.TypeToken;
import dev.wynnav.Wynnav;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Saved waypoints plus the one currently being tracked (which may be a temporary location). */
public final class Waypoints {
	private final Path file = Wynnav.configDir().resolve("waypoints.json");
	private final List<Waypoint> waypoints = new ArrayList<>();
	/** Read many times per frame by the map and minimap, so the copy is only made on changes. */
	private List<Waypoint> snapshot = List.of();
	private @Nullable Waypoint tracked;

	public List<Waypoint> all() {
		return snapshot;
	}

	public Optional<Waypoint> tracked() {
		if (tracked == null) {
			return Optional.empty();
		}
		// Prefer the saved copy so edits made after tracking started are reflected.
		return Optional.of(find(tracked.id()).orElse(tracked));
	}

	public boolean isTracked(Waypoint waypoint) {
		return tracked != null && tracked.id().equals(waypoint.id());
	}

	public void track(@Nullable Waypoint waypoint) {
		tracked = waypoint;
	}

	public void add(Waypoint waypoint) {
		waypoints.add(waypoint);
		save();
	}

	/** Adds several at once with a single save. */
	public void addAll(List<Waypoint> added) {
		waypoints.addAll(added);
		save();
	}

	public void update(Waypoint waypoint) {
		waypoints.replaceAll(existing -> existing.id().equals(waypoint.id()) ? waypoint : existing);
		save();
	}

	public void remove(Waypoint waypoint) {
		waypoints.removeIf(existing -> existing.id().equals(waypoint.id()));
		if (isTracked(waypoint)) {
			tracked = null;
		}
		save();
	}

	public Optional<Waypoint> find(UUID id) {
		return waypoints.stream().filter(w -> w.id().equals(id)).findFirst();
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}
		try {
			List<Waypoint> loaded = Wynnav.GSON.fromJson(Files.readString(file), new TypeToken<List<Waypoint>>() {}.getType());
			waypoints.clear();
			if (loaded != null) {
				waypoints.addAll(loaded);
			}
			snapshot = List.copyOf(waypoints);
		} catch (IOException | RuntimeException e) {
			Wynnav.LOGGER.error("Could not read waypoints from {}", file, e);
		}
	}

	private void save() {
		snapshot = List.copyOf(waypoints);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("waypoints.json.tmp");
			Files.writeString(tmp, Wynnav.GSON.toJson(waypoints));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Wynnav.LOGGER.error("Could not save waypoints to {}", file, e);
		}
	}
}
