package dev.wynnav.ui;

import dev.wynnav.WynnavClient;
import dev.wynnav.waypoint.Waypoint;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Waypoint list on the right of the map. Clicking a row shows it on the map and opens the same
 * action menu as clicking the waypoint itself.
 */
final class WaypointSidebar {
	static final int WIDTH = 160;
	private static final int ROW_HEIGHT = 22;
	private static final int HEADER_HEIGHT = 34;
	private static final int BACKGROUND = 0xD0101418;
	private static final int BORDER = 0xFF3A4450;

	private final WorldMapScreen screen;
	private int x;
	private int top;
	private int bottom;
	private int scroll;

	WaypointSidebar(WorldMapScreen screen) {
		this.screen = screen;
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
		int maxScroll = Math.max(0, WynnavClient.waypoints().all().size() * ROW_HEIGHT - (bottom - top - HEADER_HEIGHT));
		scroll = Mth.clamp(scroll - (int) (amount * ROW_HEIGHT), 0, maxScroll);
	}

	boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isOverAddButton(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			addAtPlayer();
			return true;
		}
		List<Waypoint> list = WynnavClient.waypoints().all();
		int index = rowAt(mouseY);
		if (index >= 0 && index < list.size()) {
			Waypoint waypoint = list.get(index);
			screen.focusOn(waypoint.x() + 0.5, waypoint.z() + 0.5);
			screen.openPopup(screen.waypointMenu(waypoint), (int) mouseX, (int) mouseY);
		}
		return true;
	}

	private void addAtPlayer() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int count = WynnavClient.waypoints().all().size();
		Waypoint draft = Waypoint.create("Waypoint " + (count + 1), player.getBlockX(), player.getBlockY(), player.getBlockZ(),
			Waypoint.PALETTE[count % Waypoint.PALETTE.length]);
		screen.openEditor(null, draft);
	}

	void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
		graphics.fill(x, top, x + WIDTH, bottom, BACKGROUND);
		graphics.vLine(x, top - 1, bottom, BORDER);
		graphics.drawString(font, "Waypoints", x + 6, top + 6, 0xFFFFD866);

		boolean addHovered = isOverAddButton(mouseX, mouseY);
		graphics.fill(x + 6, top + 17, x + WIDTH - 6, top + 30, addHovered ? 0xFF2E5E3A : 0xFF24402C);
		graphics.drawCenteredString(font, "+ Add at my position", x + WIDTH / 2, top + 20, 0xFFFFFFFF);

		List<Waypoint> list = WynnavClient.waypoints().all();
		int listTop = top + HEADER_HEIGHT;
		if (list.isEmpty()) {
			graphics.drawString(font, "Right-click the map", x + 6, listTop + 4, 0xFFA0A8B0);
			graphics.drawString(font, "to add one.", x + 6, listTop + 16, 0xFFA0A8B0);
			return;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		int hovered = contains(mouseX, mouseY) ? rowAt(mouseY) : -1;
		graphics.enableScissor(x, listTop, x + WIDTH, bottom);
		for (int i = 0; i < list.size(); i++) {
			int rowY = listTop + i * ROW_HEIGHT - scroll;
			if (rowY + ROW_HEIGHT < listTop || rowY > bottom) {
				continue;
			}
			Waypoint waypoint = list.get(i);
			if (i == hovered) {
				graphics.fill(x + 1, rowY, x + WIDTH, rowY + ROW_HEIGHT, 0x30FFFFFF);
			}
			graphics.fill(x + 6, rowY + 6, x + 16, rowY + 16, waypoint.color());
			String name = font.plainSubstrByWidth(waypoint.name(), WIDTH - 30);
			int nameColor = WynnavClient.waypoints().isTracked(waypoint) ? 0xFFFFD866 : 0xFFFFFFFF;
			graphics.drawString(font, name, x + 22, rowY + 3, nameColor);
			String info = player == null ? waypoint.coordinates()
				: Math.round(waypoint.distanceFrom(player.getX(), player.getY(), player.getZ())) + "m  ·  " + waypoint.coordinates();
			graphics.drawString(font, font.plainSubstrByWidth(info, WIDTH - 30), x + 22, rowY + 12, 0xFF909AA4);
		}
		graphics.disableScissor();
	}

	private boolean isOverAddButton(double mouseX, double mouseY) {
		return mouseX >= x + 6 && mouseX < x + WIDTH - 6 && mouseY >= top + 17 && mouseY < top + 30;
	}

	private int rowAt(double mouseY) {
		int listTop = top + HEADER_HEIGHT;
		if (mouseY < listTop) {
			return -1;
		}
		return (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
	}
}
