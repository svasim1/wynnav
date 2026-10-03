package dev.wynnav.ui;

import dev.wynnav.config.Settings;
import dev.wynnav.map.Gathering;
import dev.wynnav.map.MapMarkers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Right-side panel with on/off switches for every map layer. Changes save immediately. */
final class LayersPanel {
	static final int WIDTH = 160;
	private static final int ROW = 13;
	private static final int BACKGROUND = 0xD0101418;
	private static final int BORDER = 0xFF3A4450;

	/** A header, a checkbox, or a "- value +" stepper. */
	private record Row(String label, boolean header, Supplier<Boolean> checked, Runnable toggle, Supplier<String> value,
		Consumer<Integer> step) {

		static Row header(String label) {
			return new Row(label, true, null, null, null, null);
		}

		static Row check(String label, Supplier<Boolean> checked, Runnable toggle) {
			return new Row(label, false, checked, toggle, null, null);
		}

		static Row stepper(String label, Supplier<String> value, Consumer<Integer> step) {
			return new Row(label, false, null, null, value, step);
		}
	}

	private final List<Row> rows = new ArrayList<>();
	private int x;
	private int top;
	private int bottom;
	private int scroll;

	LayersPanel() {
		Settings.MapLayers layers = Settings.get().layers;
		rows.add(Row.header("Markers"));
		for (MapMarkers.Category category : MapMarkers.Category.values()) {
			rows.add(Row.check(category.displayName(), () -> !layers.hiddenCategories.contains(category),
				() -> toggle(layers.hiddenCategories, category)));
		}
		rows.add(Row.header("Overlays"));
		rows.add(Row.check("Place names", () -> layers.placeNames, () -> layers.placeNames = !layers.placeNames));
		rows.add(Row.check("Guild territories", () -> layers.territories, () -> layers.territories = !layers.territories));
		rows.add(Row.check("Camps", () -> layers.camps, () -> layers.camps = !layers.camps));
		rows.add(Row.check("World events", () -> layers.worldEvents, () -> layers.worldEvents = !layers.worldEvents));
		rows.add(Row.check("Gathering nodes", () -> layers.gathering, () -> layers.gathering = !layers.gathering));
		rows.add(Row.header("Players"));
		rows.add(Row.check("Friends", () -> layers.friends, () -> layers.friends = !layers.friends));
		rows.add(Row.check("Party", () -> layers.party, () -> layers.party = !layers.party));
		rows.add(Row.check("Guild members", () -> layers.guild, () -> layers.guild = !layers.guild));
		rows.add(Row.header("Gathering"));
		for (Gathering.Profession profession : Gathering.Profession.values()) {
			rows.add(Row.check(profession.displayName(), () -> layers.gatheringProfessions.contains(profession),
				() -> toggle(layers.gatheringProfessions, profession)));
		}
		rows.add(Row.stepper("Min level", () -> String.valueOf(layers.gatheringMinLevel),
			delta -> layers.gatheringMinLevel = Mth.clamp(layers.gatheringMinLevel + delta * 5, 1, layers.gatheringMaxLevel)));
		rows.add(Row.stepper("Max level", () -> String.valueOf(layers.gatheringMaxLevel),
			delta -> layers.gatheringMaxLevel = Mth.clamp(layers.gatheringMaxLevel + delta * 5, layers.gatheringMinLevel, 120)));
	}

	private static <T> void toggle(Set<T> set, T value) {
		if (!set.remove(value)) {
			set.add(value);
		}
	}

	void layout(int x, int top, int bottom) {
		this.x = x;
		this.top = top;
		this.bottom = bottom;
	}

	boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= top && mouseY < bottom;
	}

	void scroll(double amount) {
		int maxScroll = Math.max(0, rows.size() * ROW + 8 - (bottom - top));
		scroll = Mth.clamp(scroll - (int) (amount * ROW), 0, maxScroll);
	}

	boolean mouseClicked(double mouseX, double mouseY) {
		int index = (int) ((mouseY - top - 4 + scroll) / ROW);
		if (index < 0 || index >= rows.size()) {
			return true;
		}
		Row row = rows.get(index);
		if (row.toggle() != null) {
			row.toggle().run();
		} else if (row.step() != null) {
			// Left half of the value area steps down, right half steps up.
			row.step().accept(mouseX < x + WIDTH - 30 ? -1 : 1);
		}
		Settings.get().save();
		return true;
	}

	void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
		graphics.fill(x, top, x + WIDTH, bottom, BACKGROUND);
		graphics.vLine(x, top - 1, bottom, BORDER);
		graphics.enableScissor(x, top, x + WIDTH, bottom);
		int hovered = contains(mouseX, mouseY) ? (int) ((mouseY - top - 4 + scroll) / ROW) : -1;
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			int y = top + 4 + i * ROW - scroll;
			if (row.header()) {
				graphics.drawString(font, row.label(), x + 6, y + 3, 0xFFFFD866);
				continue;
			}
			if (i == hovered) {
				graphics.fill(x + 1, y, x + WIDTH, y + ROW, 0x30FFFFFF);
			}
			if (row.checked() != null) {
				boolean on = row.checked().get();
				graphics.fill(x + 8, y + 3, x + 16, y + 11, 0xFF5A6878);
				graphics.fill(x + 9, y + 4, x + 15, y + 10, on ? 0xFF6CCB5F : 0xFF1A2028);
				graphics.drawString(font, row.label(), x + 22, y + 3, on ? 0xFFFFFFFF : 0xFF909AA4);
			} else {
				graphics.drawString(font, row.label(), x + 8, y + 3, 0xFFFFFFFF);
				String value = "- " + row.value().get() + " +";
				graphics.drawString(font, value, x + WIDTH - 8 - font.width(value), y + 3, 0xFFFFD866);
			}
		}
		graphics.disableScissor();
	}
}
