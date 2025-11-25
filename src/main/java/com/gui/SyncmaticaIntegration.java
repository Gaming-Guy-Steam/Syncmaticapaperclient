package com.example.syncmatica.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IButtonActionListener;
import net.minecraft.client.MinecraftClient;

public class SyncmaticaIntegration {

    public static void registerButton() {
        // Adds a top-level button to Litematica UI; adjust placement to where you want it
        GuiBase.addButton(new ButtonGeneric(5, 5, 140, 20, "Syncmatica Menu", new IButtonActionListener() {
            @Override
            public void actionPerformed(ButtonGeneric button, int mouseButton) {
                MinecraftClient.getInstance().setScreen(new SyncmaticaGui());
            }
        }));
    }
}
