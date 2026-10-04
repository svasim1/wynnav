package dev.wynnav.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import dev.wynnav.render.Icons;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A regular button with a 16x16 icon to the left of its label. */
final class IconButton extends Button {
	private final Identifier icon;

	IconButton(int x, int y, int width, int height, Identifier icon, Component label, OnPress onPress) {
		super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
		this.icon = icon;
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderDefaultSprite(graphics);
		var font = Minecraft.getInstance().font;
		int iconY = getY() + (getHeight() - 16) / 2;
		int contentWidth = 16 + 4 + font.width(getMessage());
		// Too narrow for the label: show only the icon (the caller adds a tooltip).
		if (getWidth() < contentWidth + 8) {
			Icons.sprite(graphics, icon, getX() + getWidth() / 2f, iconY + 8, 16, 16, 16, active ? 0xFFFFFFFF : 0xFFA0A0A0);
			return;
		}
		int left = getX() + (getWidth() - contentWidth) / 2;
		Icons.sprite(graphics, icon, left + 8, iconY + 8, 16, 16, 16, 0xFFFFFFFF);
		graphics.drawString(font, getMessage(), left + 20, getY() + (getHeight() - 8) / 2, active ? 0xFFFFFFFF : 0xFFA0A0A0);
	}
}
