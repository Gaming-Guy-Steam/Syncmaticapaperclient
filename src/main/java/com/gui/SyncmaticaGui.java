package com.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IButtonActionListener;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;

public class SyncmaticaGui extends GuiBase {

    public SyncmaticaGui() {
        super(Text.literal("Syncmatica Paper Client"));
    }

    @Override
    protected void initGui() {
        int y = 40;

        this.addButton(new ButtonGeneric(20, y, 200, 20, "Test Button", new IButtonActionListener() {
            @Override
            public void actionPerformed(ButtonGeneric button, int mouseButton) {
                // Voorbeeldactie: print naar console
                System.out.println("Button clicked!");
            }
        }));

        y += 25;

        this.addButton(new ButtonGeneric(20, y, 200, 20, "Nog een knop", (btn) -> {
            // Voorbeeldactie: gebruik MinecraftClient
            MinecraftClient client = MinecraftClient.getInstance();
            client.player.sendMessage(Text.literal("Knop ingedrukt!"), false);
        }));
    }
}