package dev.wynnav.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Buckets points into square world cells so drawing and hover checks only look at what is near
 * the view instead of every gathering node or marker each frame. Immutable once built.
 */
public final class SpatialGrid<T> {
	private static final int CELL = 128;

	private final Map<Long, List<T>> cells = new HashMap<>();

	public SpatialGrid(List<T> items, ToIntFunction<T> x, ToIntFunction<T> z) {
		for (T item : items) {
			cells.computeIfAbsent(key(Math.floorDiv(x.applyAsInt(item), CELL), Math.floorDiv(z.applyAsInt(item), CELL)),
				k -> new ArrayList<>()).add(item);
		}
	}

	public static <T> SpatialGrid<T> empty() {
		return new SpatialGrid<>(List.of(), t -> 0, t -> 0);
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	/** Visits every item in cells overlapping the box (items slightly outside may be included). */
	public void forEachIn(double minX, double minZ, double maxX, double maxZ, Consumer<T> action) {
		int cx0 = (int) Math.floor(minX / CELL);
		int cz0 = (int) Math.floor(minZ / CELL);
		int cx1 = (int) Math.floor(maxX / CELL);
		int cz1 = (int) Math.floor(maxZ / CELL);
		// Very far zoomed out the box spans many empty cells; walking the map is cheaper then.
		if ((long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1) > cells.size()) {
			cells.values().forEach(list -> list.forEach(action));
			return;
		}
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				List<T> list = cells.get(key(cx, cz));
				if (list != null) {
					list.forEach(action);
				}
			}
		}
	}
}
