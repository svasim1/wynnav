package dev.wynnav.waypoint;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A user-placed point. {@code y} is null when the waypoint was placed on the map without a known
 * height; distances then ignore height.
 */
public record Waypoint(UUID id, String name, int x, @Nullable Integer y, int z, int color) {
	public static final int[] PALETTE = {
		0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFFFFFFF
	};

	public static Waypoint create(String name, int x, @Nullable Integer y, int z, int color) {
		return new Waypoint(UUID.randomUUID(), name, x, y, z, color);
	}

	public Waypoint withValues(String name, int x, @Nullable Integer y, int z, int color) {
		return new Waypoint(id, name, x, y, z, color);
	}

	public double distanceFrom(double px, double py, double pz) {
		double dx = x + 0.5 - px;
		double dz = z + 0.5 - pz;
		double dy = y == null ? 0 : y - py;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public String coordinates() {
		return y == null ? x + ", " + z : x + ", " + y + ", " + z;
	}
}
