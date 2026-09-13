package dev.wynnav.ui;

import dev.wynnav.WynnavClient;
import dev.wynnav.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Create or edit a waypoint: name, coordinates (height optional) and color. */
final class WaypointEditScreen extends Screen {
	private static final int SWATCH = 16;

	private final Screen parent;
	private final @Nullable Waypoint existing;
	private final Waypoint draft;
	private int color;

	private EditBox nameBox;
	private EditBox xBox;
	private EditBox yBox;
	private EditBox zBox;
	private Button saveButton;

	/** @param existing the saved waypoint being edited, or null to create {@code draft} as new. */
	WaypointEditScreen(Screen parent, @Nullable Waypoint existing, Waypoint draft) {
		super(Component.translatable(existing == null ? "wynnav.waypoint.new" : "wynnav.waypoint.edit"));
		this.parent = parent;
		this.existing = existing;
		this.draft = draft;
		this.color = draft.color();
	}

	@Override
	protected void init() {
		int left = width / 2 - 100;
		int y = height / 2 - 70;

		nameBox = addRenderableWidget(new EditBox(font, left, y + 12, 200, 20, Component.literal("Name")));
		nameBox.setMaxLength(48);
		nameBox.setValue(draft.name());
		nameBox.setResponder(text -> validate());

		y += 48;
		xBox = addRenderableWidget(coordinateBox(left, y, String.valueOf(draft.x()), "X"));
		yBox = addRenderableWidget(coordinateBox(left + 70, y, draft.y() == null ? "" : String.valueOf(draft.y()), "Y"));
		yBox.setHint(Component.literal("optional"));
		zBox = addRenderableWidget(coordinateBox(left + 140, y, String.valueOf(draft.z()), "Z"));

		y += 86;
		saveButton = addRenderableWidget(Button.builder(Component.translatable("wynnav.waypoint.save"), button -> save())
			.bounds(left, y, 98, 20)
			.build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
			.bounds(left + 102, y, 98, 20)
			.build());

		setInitialFocus(nameBox);
		validate();
	}

	private EditBox coordinateBox(int x, int y, String value, String label) {
		EditBox box = new EditBox(font, x, y + 12, 60, 20, Component.literal(label));
		box.setFilter(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d{1,8}"));
		box.setValue(value);
		box.setResponder(text -> validate());
		return box;
	}

	private void validate() {
		if (saveButton != null) {
			saveButton.active = !nameBox.getValue().isBlank() && parse(xBox) != null && parse(zBox) != null;
		}
	}

	private static @Nullable Integer parse(EditBox box) {
		try {
			return Integer.parseInt(box.getValue());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void save() {
		Integer x = parse(xBox);
		Integer z = parse(zBox);
		if (x == null || z == null || nameBox.getValue().isBlank()) {
			return;
		}
		String name = nameBox.getValue().strip();
		if (existing == null) {
			WynnavClient.waypoints().add(draft.withValues(name, x, parse(yBox), z, color));
		} else {
			WynnavClient.waypoints().update(existing.withValues(name, x, parse(yBox), z, color));
		}
		onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
		int index = swatchAt(event.x(), event.y());
		if (index >= 0) {
			color = Waypoint.PALETTE[index];
			return true;
		}
		return super.mouseClicked(event, isDoubleClick);
	}

	private int swatchLeft() {
		return width / 2 - (Waypoint.PALETTE.length * (SWATCH + 4) - 4) / 2;
	}

	private int swatchTop() {
		return height / 2 - 70 + 48 + 56;
	}

	private int swatchAt(double mouseX, double mouseY) {
		int top = swatchTop();
		if (mouseY < top || mouseY >= top + SWATCH) {
			return -1;
		}
		int offset = (int) mouseX - swatchLeft();
		int index = offset / (SWATCH + 4);
		return offset >= 0 && offset % (SWATCH + 4) < SWATCH && index < Waypoint.PALETTE.length ? index : -1;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = width / 2 - 100;
		int y = height / 2 - 70;
		graphics.drawCenteredString(font, title, width / 2, y - 20, 0xFFFFFFFF);
		graphics.drawString(font, "Name", left, y, 0xFFA0A8B0);
		graphics.drawString(font, "X", left, y + 48, 0xFFA0A8B0);
		graphics.drawString(font, "Y", left + 70, y + 48, 0xFFA0A8B0);
		graphics.drawString(font, "Z", left + 140, y + 48, 0xFFA0A8B0);
		graphics.drawString(font, "Color", left, swatchTop() - 12, 0xFFA0A8B0);

		int sx = swatchLeft();
		for (int i = 0; i < Waypoint.PALETTE.length; i++) {
			int x = sx + i * (SWATCH + 4);
			int swatchY = swatchTop();
			if (Waypoint.PALETTE[i] == color) {
				graphics.fill(x - 2, swatchY - 2, x + SWATCH + 2, swatchY + SWATCH + 2, 0xFFFFFFFF);
			}
			graphics.fill(x, swatchY, x + SWATCH, swatchY + SWATCH, Waypoint.PALETTE[i]);
		}
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
