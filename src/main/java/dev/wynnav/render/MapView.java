package dev.wynnav.render;

/**
 * Maps world X/Z to screen coordinates: {@code screen = rotate(world - center) * zoom + screenCenter}.
 * A rotation of 0 means north (-Z) is up. Sine and cosine are computed once per view, since these
 * conversions run for every visible point every frame.
 */
public final class MapView {
	private final double centerX;
	private final double centerZ;
	private final double zoom;
	private final float screenX;
	private final float screenY;
	private final double cos;
	private final double sin;

	public MapView(double centerX, double centerZ, double zoom, double rotation, float screenX, float screenY) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.zoom = zoom;
		this.screenX = screenX;
		this.screenY = screenY;
		this.cos = Math.cos(rotation);
		this.sin = Math.sin(rotation);
	}

	public static MapView northUp(double centerX, double centerZ, double zoom, float screenX, float screenY) {
		return new MapView(centerX, centerZ, zoom, 0, screenX, screenY);
	}

	public double centerX() {
		return centerX;
	}

	public double centerZ() {
		return centerZ;
	}

	public double zoom() {
		return zoom;
	}

	public float screenX(double worldX, double worldZ) {
		return (float) (((worldX - centerX) * cos - (worldZ - centerZ) * sin) * zoom + screenX);
	}

	public float screenY(double worldX, double worldZ) {
		return (float) (((worldX - centerX) * sin + (worldZ - centerZ) * cos) * zoom + screenY);
	}

	public double worldX(double sx, double sy) {
		double dx = (sx - screenX) / zoom;
		double dy = (sy - screenY) / zoom;
		return centerX + dx * cos + dy * sin;
	}

	public double worldZ(double sx, double sy) {
		double dx = (sx - screenX) / zoom;
		double dy = (sy - screenY) / zoom;
		return centerZ - dx * sin + dy * cos;
	}

	/** World-space box that covers the screen rectangle (all four corners, so rotation is handled). */
	public double[] worldBounds(float x0, float y0, float x1, float y1) {
		double minX = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		float[][] corners = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
		for (float[] c : corners) {
			double wx = worldX(c[0], c[1]);
			double wz = worldZ(c[0], c[1]);
			minX = Math.min(minX, wx);
			minZ = Math.min(minZ, wz);
			maxX = Math.max(maxX, wx);
			maxZ = Math.max(maxZ, wz);
		}
		return new double[] {minX, minZ, maxX, maxZ};
	}
}
