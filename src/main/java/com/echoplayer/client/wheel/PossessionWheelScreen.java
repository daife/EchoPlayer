package com.echoplayer.client.wheel;

import com.echoplayer.client.ClientCommands;
import com.echoplayer.client.Keybinds;
import com.echoplayer.network.NetworkPackets;
import com.echoplayer.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public final class PossessionWheelScreen extends Screen {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final int MIN_RADIUS = 72;
    private static final int MAX_RADIUS = 120;
    private static final int WHEEL_COLOR = 0xCC20252C;
    private static final int DIVIDER_COLOR = 0xDDD8DEE9;
    private static final int SELECTED_TEXT_COLOR = 0x86D993;
    private static final int CANCEL_TEXT_COLOR = 0xDB8585;
    private static int nextRequestId;

    private final int requestId;
    private List<String> entries = List.of();
    private boolean loaded;
    private boolean finishing;
    private int selectedIndex = -1;
    private int centerX;
    private int centerY;
    private int innerRadius;
    private int outerRadius;

    private PossessionWheelScreen(int requestId) {
        super(Component.translatable("screen.echoplayer.possession_wheel"));
        this.requestId = requestId;
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        int requestId = ++nextRequestId;
        PossessionWheelScreen screen = new PossessionWheelScreen(requestId);
        minecraft.setScreen(screen);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(requestId);
        Services.PLATFORM.sendToServer(NetworkPackets.POSSESSION_WHEEL_REQUEST_PACKET, buf);
    }

    public static void acceptEntries(int requestId, List<String> echoNames) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof PossessionWheelScreen screen) || screen.requestId != requestId
            || minecraft.player == null) {
            return;
        }
        ArrayList<String> updatedEntries = new ArrayList<String>(echoNames.size() + 1);
        updatedEntries.add(minecraft.player.getGameProfile().getName());
        updatedEntries.addAll(echoNames);
        screen.entries = List.copyOf(updatedEntries);
        screen.loaded = true;
        screen.selectedIndex = -1;
        screen.centerCursor();
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;
        int available = Math.min(width, height);
        outerRadius = Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, (int)(available * 0.28f)));
        innerRadius = Math.max(24, (int)(outerRadius * 0.32f));
        centerCursor();
    }

    private void centerCursor() {
        if (minecraft == null) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, minecraft.getWindow().getScreenWidth() / 2.0,
            minecraft.getWindow().getScreenHeight() / 2.0);
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            finish(false);
            return;
        }
        if (!isWheelKeyPhysicallyDown()) {
            finish(true);
        }
    }

    private boolean isWheelKeyPhysicallyDown() {
        if (minecraft == null) {
            return false;
        }
        InputConstants.Key key = Keybinds.POSSESSION_WHEEL_KEY.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        // Scan-code bindings are closed by keyReleased, which preserves the
        // exact scan code needed to match the configured binding.
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (Keybinds.POSSESSION_WHEEL_KEY.matches(keyCode, scanCode)) {
            finish(true);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Keybinds.POSSESSION_WHEEL_KEY.matchesMouse(button)) {
            selectedIndex = findSelection(mouseX, mouseY);
            finish(true);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        selectedIndex = findSelection(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void onClose() {
        finish(false);
    }

    private void finish(boolean executeSelection) {
        if (finishing) {
            return;
        }
        finishing = true;
        // While a Screen is open, vanilla routes the release to the Screen and
        // does not clear the KeyMapping itself.
        Keybinds.POSSESSION_WHEEL_KEY.setDown(false);
        if (executeSelection && loaded && selectedIndex >= 0 && selectedIndex < entries.size()) {
            if (selectedIndex == 0) {
                ClientCommands.execute("echoplayer unpossess");
            } else {
                ClientCommands.execute("echoplayer control " + entries.get(selectedIndex));
            }
        }
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        if (!loaded) {
            selectedIndex = -1;
            graphics.drawCenteredString(font, Component.translatable("screen.echoplayer.possession_wheel.loading"),
                centerX, centerY - font.lineHeight / 2, 0xFFFFFF);
            return;
        }

        selectedIndex = findSelection(mouseX, mouseY);
        renderWheel(graphics);
        renderLabels(graphics);

        Component centerLabel = selectedIndex >= 0
            ? Component.literal(entries.get(selectedIndex))
            : Component.translatable("screen.echoplayer.possession_wheel.cancel");
        int centerLabelColor = selectedIndex >= 0 ? SELECTED_TEXT_COLOR : CANCEL_TEXT_COLOR;
        graphics.drawCenteredString(font, centerLabel, centerX, centerY - font.lineHeight / 2,
            centerLabelColor);
    }

    private void renderWheel(GuiGraphics graphics) {
        int count = entries.size();
        double step = TWO_PI / count;
        for (int i = 0; i < count; i++) {
            double middle = Math.PI + i * step;
            drawSector(graphics.pose(), middle - step / 2.0, middle + step / 2.0,
                innerRadius, outerRadius, WHEEL_COLOR);
        }
        for (int i = 0; i < count; i++) {
            double boundary = Math.PI - step / 2.0 + i * step;
            drawRadialDivider(graphics.pose(), boundary);
        }
        drawSector(graphics.pose(), 0.0, TWO_PI,
            innerRadius - 1, innerRadius + 1, DIVIDER_COLOR);
    }

    private void drawSector(PoseStack poseStack, double startAngle, double endAngle,
                            int sectorInnerRadius, int sectorOuterRadius, int color) {
        int segments = Math.max(4, (int)Math.ceil((endAngle - startAngle) * sectorOuterRadius / 8.0));
        float alpha = (color >>> 24 & 0xFF) / 255.0f;
        float red = (color >>> 16 & 0xFF) / 255.0f;
        float green = (color >>> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int segment = 0; segment < segments; segment++) {
            double first = startAngle + (endAngle - startAngle) * segment / segments;
            double second = startAngle + (endAngle - startAngle) * (segment + 1) / segments;
            float innerFirstX = centerX + (float)Math.cos(first) * sectorInnerRadius;
            float innerFirstY = centerY + (float)Math.sin(first) * sectorInnerRadius;
            float outerFirstX = centerX + (float)Math.cos(first) * sectorOuterRadius;
            float outerFirstY = centerY + (float)Math.sin(first) * sectorOuterRadius;
            float innerSecondX = centerX + (float)Math.cos(second) * sectorInnerRadius;
            float innerSecondY = centerY + (float)Math.sin(second) * sectorInnerRadius;
            float outerSecondX = centerX + (float)Math.cos(second) * sectorOuterRadius;
            float outerSecondY = centerY + (float)Math.sin(second) * sectorOuterRadius;

            vertex(builder, matrix, innerFirstX, innerFirstY, red, green, blue, alpha);
            vertex(builder, matrix, outerFirstX, outerFirstY, red, green, blue, alpha);
            vertex(builder, matrix, outerSecondX, outerSecondY, red, green, blue, alpha);
            vertex(builder, matrix, innerFirstX, innerFirstY, red, green, blue, alpha);
            vertex(builder, matrix, outerSecondX, outerSecondY, red, green, blue, alpha);
            vertex(builder, matrix, innerSecondX, innerSecondY, red, green, blue, alpha);
        }
        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
    }

    private void drawRadialDivider(PoseStack poseStack, double angle) {
        double perpendicularX = -Math.sin(angle) * 0.5;
        double perpendicularY = Math.cos(angle) * 0.5;
        double directionX = Math.cos(angle);
        double directionY = Math.sin(angle);
        float innerX = centerX + (float)(directionX * innerRadius);
        float innerY = centerY + (float)(directionY * innerRadius);
        float outerX = centerX + (float)(directionX * outerRadius);
        float outerY = centerY + (float)(directionY * outerRadius);
        float alpha = (DIVIDER_COLOR >>> 24 & 0xFF) / 255.0f;
        float red = (DIVIDER_COLOR >>> 16 & 0xFF) / 255.0f;
        float green = (DIVIDER_COLOR >>> 8 & 0xFF) / 255.0f;
        float blue = (DIVIDER_COLOR & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertex(builder, matrix, innerX + (float)perpendicularX, innerY + (float)perpendicularY,
            red, green, blue, alpha);
        vertex(builder, matrix, outerX + (float)perpendicularX, outerY + (float)perpendicularY,
            red, green, blue, alpha);
        vertex(builder, matrix, outerX - (float)perpendicularX, outerY - (float)perpendicularY,
            red, green, blue, alpha);
        vertex(builder, matrix, innerX - (float)perpendicularX, innerY - (float)perpendicularY,
            red, green, blue, alpha);
        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, float x, float y,
                               float red, float green, float blue, float alpha) {
        builder.vertex(matrix, x, y, 0.0f).color(red, green, blue, alpha).endVertex();
    }

    private void renderLabels(GuiGraphics graphics) {
        int count = entries.size();
        double step = TWO_PI / count;
        int labelRadius = (innerRadius + outerRadius) / 2;
        int maxLabelWidth = count <= 4 ? 90 : Math.max(28, (int)(TWO_PI * labelRadius / count) - 8);
        for (int i = 0; i < count; i++) {
            double middle = Math.PI + i * step;
            int x = centerX + (int)Math.round(Math.cos(middle) * labelRadius);
            int y = centerY + (int)Math.round(Math.sin(middle) * labelRadius) - font.lineHeight / 2;
            String label = font.plainSubstrByWidth(entries.get(i), maxLabelWidth);
            graphics.drawCenteredString(font, label, x, y, i == selectedIndex ? 0xFFFFFF : 0xD8D8D8);
        }
    }

    private int findSelection(double mouseX, double mouseY) {
        if (!loaded || entries.isEmpty()) {
            return -1;
        }
        double deltaX = mouseX - centerX;
        double deltaY = mouseY - centerY;
        if (deltaX * deltaX + deltaY * deltaY < innerRadius * innerRadius) {
            return -1;
        }
        double step = TWO_PI / entries.size();
        double angle = Math.atan2(deltaY, deltaX);
        double relative = normalizeAngle(angle - Math.PI + step / 2.0);
        return Math.min(entries.size() - 1, (int)(relative / step));
    }

    private static double normalizeAngle(double angle) {
        double normalized = angle % TWO_PI;
        return normalized < 0.0 ? normalized + TWO_PI : normalized;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
