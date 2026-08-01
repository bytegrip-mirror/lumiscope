package com.lumijiez.lumiscope.gui;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import com.lumijiez.lumiscope.network.packets.RadarScanRequestPacket;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import com.lumijiez.lumiscope.util.ScopeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.List;

public class RadarGuiScreen extends GuiScreen {

    private static final int SCOPE_RADIUS = 68;
    private static final int BUTTON_SCAN = 0;

    // Static result storage (written by packet handler, read by GUI)
    private static List<RadarBlip> scanBlips = null;
    private static byte scanStatus = RadarScanResultPacket.STATUS_COOLDOWN;
    private static long scanTimestamp = 0;

    private int scopeCenterX;
    private int scopeCenterY;
    private GuiButton scanButton;
    private long cooldownEndMs = 0;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;
    private int animationTick = 0;

    public RadarGuiScreen() {
        // Refresh cooldown from server-side cache (best-effort, client-side approximation)
        if (Minecraft.getMinecraft().player != null) {
            long remaining = RadarNetworkHandler.getCooldownRemainingMs(
                    Minecraft.getMinecraft().player.getUniqueID());
            if (remaining > 0) {
                cooldownEndMs = System.currentTimeMillis() + remaining;
            }
        }
    }

    // Called from packet handler on client thread
    public static void onScanResult(List<RadarBlip> blips, byte status, long timestamp) {
        scanBlips = blips;
        scanStatus = status;
        scanTimestamp = timestamp;
        RadarNetworkHandler.cacheResults(blips, status, timestamp);
    }

    @Override
    public void initGui() {
        super.initGui();
        scopeCenterX = width / 2;
        scopeCenterY = height / 2 - 10;

        // Scan button
        int buttonW = 80;
        int buttonH = 20;
        scanButton = new GuiButton(BUTTON_SCAN,
                width / 2 - buttonW / 2,
                height - 50,
                buttonW, buttonH,
                "Scan");
        addButton(scanButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        animationTick++;

        // Update button state
        boolean onCooldown = System.currentTimeMillis() < cooldownEndMs;
        boolean hasResults = scanBlips != null && !scanBlips.isEmpty()
                && scanStatus == RadarScanResultPacket.STATUS_SUCCESS
                && !RadarNetworkHandler.areResultsStale();

        if (onCooldown) {
            long remaining = (cooldownEndMs - System.currentTimeMillis()) / 1000 + 1;
            scanButton.displayString = "Cooldown: " + remaining + "s";
            scanButton.enabled = false;
            statusMessage = TextFormatting.GRAY + "Scanner recharging...";
            statusColor = 0x888888;
        } else if (hasResults) {
            scanButton.displayString = "Scan Again";
            scanButton.enabled = true;
            statusMessage = TextFormatting.GREEN + "Scan active — " + scanBlips.size() + " signal(s) detected";
            statusColor = 0x00FF00;
        } else {
            scanButton.displayString = "Scan";
            scanButton.enabled = true;
            statusMessage = TextFormatting.YELLOW + "Ready to scan";
            statusColor = 0xFFFF00;
        }

        // Handle status responses from last scan
        if (scanStatus == RadarScanResultPacket.STATUS_NO_FUEL) {
            statusMessage = TextFormatting.RED + "No fuel! Requires 1 Ender Pearl in inventory.";
            statusColor = 0xFF4444;
            scanBlips = null;
        } else if (scanStatus == RadarScanResultPacket.STATUS_JAMMED) {
            statusMessage = TextFormatting.DARK_RED + "JAMMED! Cannot operate scanner.";
            statusColor = 0xFF0000;
            scanBlips = null;
        }

        // Reset status after displaying
        if (scanStatus != RadarScanResultPacket.STATUS_SUCCESS) {
            // Keep showing error for a few seconds
            if (animationTick % 60 == 0 && animationTick > 120) {
                scanStatus = RadarScanResultPacket.STATUS_SUCCESS;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Title
        drawCenteredString(fontRenderer,
                TextFormatting.GOLD + "Lumiscope" + TextFormatting.RESET + " Radar Device",
                width / 2, 10, 0xFFFFFF);

        // Draw scope area background border
        drawRect(scopeCenterX - SCOPE_RADIUS - 4, scopeCenterY - SCOPE_RADIUS - 4,
                scopeCenterX + SCOPE_RADIUS + 4, scopeCenterY + SCOPE_RADIUS + 4, 0xFF222222);
        drawRect(scopeCenterX - SCOPE_RADIUS - 2, scopeCenterY - SCOPE_RADIUS - 2,
                scopeCenterX + SCOPE_RADIUS + 2, scopeCenterY + SCOPE_RADIUS + 2, 0xFF444444);

        // Render the scope
        ScopeRenderer.renderScope(scopeCenterX, scopeCenterY, SCOPE_RADIUS, scanBlips, partialTicks);

        // Compass labels over the scope
        drawCompassLabel(scopeCenterX, scopeCenterY - SCOPE_RADIUS - 8, "N"); // North
        drawCompassLabel(scopeCenterX + SCOPE_RADIUS + 8, scopeCenterY, "E");  // East
        drawCompassLabel(scopeCenterX, scopeCenterY + SCOPE_RADIUS + 8, "S");  // South
        drawCompassLabel(scopeCenterX - SCOPE_RADIUS - 18, scopeCenterY, "W"); // West

        // Player count badges for multi-player blips
        if (scanBlips != null) {
            for (RadarBlip blip : scanBlips) {
                if (blip.playerCount > 1) {
                    double angle = blip.direction;
                    float distRatio;
                    switch (blip.distanceTier) {
                        case 0: distRatio = 0.95f; break;
                        case 1: distRatio = 0.80f; break;
                        case 2: distRatio = 0.60f; break;
                        case 3: distRatio = 0.40f; break;
                        case 4: distRatio = 0.25f; break;
                        default: distRatio = 0.12f; break;
                    }
                    int bx = (int)(scopeCenterX + Math.cos(angle) * SCOPE_RADIUS * distRatio);
                    int by = (int)(scopeCenterY + Math.sin(angle) * SCOPE_RADIUS * distRatio);
                    drawCenteredString(fontRenderer,
                            TextFormatting.WHITE + "x" + blip.playerCount,
                            bx + 8, by - 4, 0xFFFFFF);
                }
            }
        }

        // Results decay warning
        if (scanBlips != null && !scanBlips.isEmpty() && RadarNetworkHandler.areResultsStale()) {
            drawCenteredString(fontRenderer,
                    TextFormatting.GRAY + "" + TextFormatting.ITALIC + "Signal data stale — rescan recommended",
                    width / 2, scopeCenterY - SCOPE_RADIUS - 22, 0x888888);
        }

        // Left panel: Fuel indicator
        int fuelX = 20;
        int fuelY = height / 2 - 30;
        drawString(fontRenderer, TextFormatting.GOLD + "Fuel Required:", fuelX, fuelY - 14, 0xFFFFFF);
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        renderItem.renderItemAndEffectIntoGUI(new ItemStack(Items.ENDER_PEARL), fuelX + 5, fuelY);
        RenderHelper.disableStandardItemLighting();
        drawString(fontRenderer, "Ender Pearl", fuelX + 25, fuelY + 6, 0xAAAAAA);

        // Distance tier legend (right side)
        int legendX = width - 120;
        int legendY = height / 2 - 60;
        drawString(fontRenderer, TextFormatting.GOLD + "Distance Legend:", legendX, legendY, 0xFFFFFF);
        String[] tierNames = {"Very Close", "Close", "Moderate", "Far", "Very Far", "Extremely Far"};
        int[] tierColors = {0xFFFFB000, 0xFFFFD700, 0xFFADFF2F, 0xFF00CED1, 0xFF4169E1, 0xFF191970};
        for (int i = 0; i < tierNames.length; i++) {
            int y = legendY + 12 + i * 12;
            // Color swatch
            drawRect(legendX, y, legendX + 8, y + 8, tierColors[i]);
            drawString(fontRenderer, tierNames[i], legendX + 14, y, tierColors[i]);
        }

        // Cooldown bar (below scope)
        int barX = width / 2 - 60;
        int barY = scopeCenterY + SCOPE_RADIUS + 12;
        int barW = 120;
        int barH = 8;
        drawRect(barX, barY, barX + barW, barY + barH, 0xFF333333); // BG
        if (cooldownEndMs > System.currentTimeMillis()) {
            long totalCd = RadarNetworkHandler.COOLDOWN_TICKS * 50L;
            long remaining = cooldownEndMs - System.currentTimeMillis();
            float progress = 1.0f - (float) remaining / totalCd;
            int fillW = (int)(barW * progress);
            drawGradientRect(barX, barY, barX + fillW, barY + barH, 0xFF4488FF, 0xFF2266DD);
        } else {
            // Full — ready
            drawGradientRect(barX, barY, barX + barW, barY + barH, 0xFF44FF44, 0xFF22DD22);
        }
        drawCenteredString(fontRenderer, "Cooldown", width / 2, barY - 11, 0xAAAAAA);

        // Status message at bottom
        drawCenteredString(fontRenderer, statusMessage, width / 2, height - 25, statusColor);

        // Cooldown tooltip
        if (System.currentTimeMillis() < cooldownEndMs) {
            long remainingSec = (cooldownEndMs - System.currentTimeMillis()) / 1000 + 1;
            drawCenteredString(fontRenderer,
                    TextFormatting.GRAY + "Next scan available in " + remainingSec + "s",
                    width / 2, barY + barH + 4, 0x888888);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCompassLabel(int x, int y, String label) {
        // Draw label with slight glow (shadow behind)
        drawString(fontRenderer, TextFormatting.GREEN + label, x - fontRenderer.getStringWidth(label) / 2, y - 4, 0x00FF00);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_SCAN) {
            // Send scan request to server
            RadarNetworkHandler.getNetworkChannel().sendToServer(new RadarScanRequestPacket());

            // Set local cooldown immediately for UI responsiveness
            cooldownEndMs = System.currentTimeMillis() + RadarNetworkHandler.COOLDOWN_TICKS * 50L;
            scanButton.enabled = false;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) {
                mc.setIngameFocus();
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
