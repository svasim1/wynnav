package dev.wynnav.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A small floating menu: a title, optional detail lines, then clickable actions. Used for every
 * "what can I do with this?" interaction on the map, so the controls stay the same everywhere.
 */
final class PopupMenu {
	private static final int PADDING = 4;
	private static final int ROW_HEIGHT = 12;
	private static final int BACKGROUND = 0xF4101418;
	private static final int BORDER = 0xFF3A4450;
	private static final int HOVER = 0x40FFFFFF;

	private record Action(String label, int color, Runnable run, boolean confirm) {}

	private final String title;
	private final List<String> details = new ArrayList<>();
	private final List<Action> actions = new ArrayList<>();
	private int x;
	private int y;
	private int width;
	private int height;
	private int armedConfirm = -1;

	PopupMenu(String title) {
		this.title = title;
	}

	PopupMenu detail(String line) {
		details.add(line);
		return this;
	}

	PopupMenu action(String label, Runnable run) {
		actions.add(new Action(label, 0xFFFFFFFF, run, false));
		return this;
	}

	/** An action that needs a second click, for things that are hard to undo. */
	PopupMenu dangerousAction(String label, Runnable run) {
		actions.add(new Action(label, 0xFFFF6B6B, run, true));
		return this;
	}

	/** Places the menu next to the cursor, kept inside the screen. */
	PopupMenu openAt(Font font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		int textWidth = font.width(title);
		for (String line : details) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		for (Action action : actions) {
			textWidth = Math.max(textWidth, font.width(confirmLabel(action)));
		}
		width = textWidth + PADDING * 2;
		height = PADDING * 2 + ROW_HEIGHT * (1 + details.size() + actions.size()) + (actions.isEmpty() ? 0 : 3);
		x = mouseX + width + 4 > screenWidth ? mouseX - width - 2 : mouseX + 2;
		y = Math.max(2, Math.min(mouseY, screenHeight - height - 2));
		return this;
	}

	boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Returns true when the menu should close. */
	boolean click(double mouseX, double mouseY) {
		int index = actionAt(mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		Action action = actions.get(index);
		if (action.confirm() && armedConfirm != index) {
			armedConfirm = index;
			return false;
		}
		action.run().run();
		return true;
	}

	void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.renderOutline(x, y, width, height, BORDER);

		int rowY = y + PADDING;
		graphics.drawString(font, title, x + PADDING, rowY + 2, 0xFFFFD866);
		rowY += ROW_HEIGHT;
		for (String line : details) {
			graphics.drawString(font, line, x + PADDING, rowY + 2, 0xFFA0A8B0);
			rowY += ROW_HEIGHT;
		}
		if (actions.isEmpty()) {
			return;
		}
		graphics.hLine(x + 1, x + width - 2, rowY + 1, BORDER);
		rowY += 3;
		int hovered = actionAt(mouseX, mouseY);
		for (int i = 0; i < actions.size(); i++) {
			Action action = actions.get(i);
			if (i == hovered) {
				graphics.fill(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, HOVER);
			}
			String label = i == armedConfirm ? confirmLabel(action) : action.label();
			graphics.drawString(font, label, x + PADDING, rowY + 2, action.color());
			rowY += ROW_HEIGHT;
		}
	}

	private int actionAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return -1;
		}
		int firstActionY = y + PADDING + ROW_HEIGHT * (1 + details.size()) + 3;
		int index = (int) Math.floor((mouseY - firstActionY) / ROW_HEIGHT);
		return index >= 0 && index < actions.size() ? index : -1;
	}

	private static String confirmLabel(Action action) {
		return action.confirm() ? "Click again to confirm" : action.label();
	}
}
