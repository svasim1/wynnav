package dev.wynnav.ui;

import dev.wynnav.config.Settings;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Settings for the world marker and minimap. Changes apply immediately and are saved on close. */
public final class SettingsScreen extends Screen {
	private static final int ROW = 22;

	private final @Nullable Screen parent;
	private final Settings settings = Settings.get();
	private int columnWidth;
	private int[] columnX;
	private int[] columnY;
	private final String[] headers = {"World marker", "Minimap", "Minimap view"};

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("wynnav.settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		columnWidth = Math.min(150, (width - 40) / 3);
		int total = columnWidth * 3 + 20;
		int left = (width - total) / 2;
		int top = 44;
		columnX = new int[] {left, left + columnWidth + 10, left + 2 * (columnWidth + 10)};
		columnY = new int[] {top, top, top};

		Settings.WorldMarker marker = settings.worldMarker;
		add(0, toggle("Beam", marker.showBeam, value -> marker.showBeam = value));
		add(0, toggle("Floating icon", marker.showIcon, value -> marker.showIcon = value));
		add(0, slider("Size", marker.size, 0.5, 3, v -> String.format(Locale.ROOT, "%.1fx", v), v -> marker.size = v));
		add(0, slider("Opacity", marker.opacity, 0.1, 1, SettingsScreen::percent, v -> marker.opacity = v));
		add(0, toggle("Death waypoint", settings.deathWaypoints, value -> settings.deathWaypoints = value));

		Settings.Minimap minimap = settings.minimap;
		add(1, toggle("Minimap", minimap.enabled, value -> minimap.enabled = value));
		add(1, CycleButton.<Settings.MinimapShape>builder(shape -> Component.literal(shape == Settings.MinimapShape.ROUND ? "Round" : "Square"), minimap.shape)
			.withValues(Settings.MinimapShape.values())
			.create(0, 0, columnWidth, 20, Component.literal("Shape"), (button, value) -> minimap.shape = value));
		add(1, CycleButton.<Settings.MinimapRotation>builder(
				rotation -> Component.literal(rotation == Settings.MinimapRotation.NORTH_UP ? "North up" : "Follow view"), minimap.rotation)
			.withValues(Settings.MinimapRotation.values())
			.create(0, 0, columnWidth, 20, Component.literal("Rotation"), (button, value) -> minimap.rotation = value));
		add(1, CycleButton.<Settings.Corner>builder(corner -> Component.literal(cornerName(corner)), minimap.corner)
			.withValues(Settings.Corner.values())
			.create(0, 0, columnWidth, 20, Component.literal("Corner"), (button, value) -> minimap.corner = value));
		add(1, toggle("Coordinates", minimap.showCoordinates, value -> minimap.showCoordinates = value));

		add(2, slider("Size", minimap.size, 60, 220, v -> Math.round(v) + "px", v -> minimap.size = (int) Math.round(v)));
		add(2, slider("Zoom", minimap.zoom, 0.25, 4, v -> String.format(Locale.ROOT, "%.2fx", v), v -> minimap.zoom = v));
		add(2, slider("Opacity", minimap.opacity, 0.2, 1, SettingsScreen::percent, v -> minimap.opacity = v));
		add(2, toggle("Markers", minimap.showMarkers, value -> minimap.showMarkers = value));

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(width / 2 - 75, Math.max(top + 5 * ROW + 6, height - 28), 150, 20)
			.build());
	}

	private void add(int column, AbstractWidget widget) {
		widget.setPosition(columnX[column], columnY[column]);
		widget.setWidth(columnWidth);
		columnY[column] += ROW;
		addRenderableWidget(widget);
	}

	private CycleButton<Boolean> toggle(String label, boolean value, Consumer<Boolean> setter) {
		return CycleButton.onOffBuilder(value).create(0, 0, columnWidth, 20, Component.literal(label), (button, v) -> setter.accept(v));
	}

	private AbstractSliderButton slider(String label, double value, double min, double max, DoubleFunction<String> format,
		Consumer<Double> setter) {
		return new AbstractSliderButton(0, 0, columnWidth, 20, Component.empty(), (value - min) / (max - min)) {
			{
				updateMessage();
			}

			private double actual() {
				return Mth.lerp(this.value, min, max);
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label + ": " + format.apply(actual())));
			}

			@Override
			protected void applyValue() {
				setter.accept(actual());
			}
		};
	}

	private static String percent(double value) {
		return Math.round(value * 100) + "%";
	}

	private static String cornerName(Settings.Corner corner) {
		return switch (corner) {
			case TOP_LEFT -> "Top left";
			case TOP_RIGHT -> "Top right";
			case BOTTOM_LEFT -> "Bottom left";
			case BOTTOM_RIGHT -> "Bottom right";
		};
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
		for (int i = 0; i < headers.length; i++) {
			graphics.drawString(font, headers[i], columnX[i], 32, 0xFFFFD866);
		}
	}

	@Override
	public void removed() {
		settings.save();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
