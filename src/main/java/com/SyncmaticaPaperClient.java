package com.example.syncmatica.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

public class SyncmaticaPaperClient implements ClientModInitializer {

    private static final Identifier CHANNEL = new Identifier("syncmatica", "main");

    private static final Map<String, List<byte[]>> fileBuffers = new HashMap<>();
    private static final Map<String, Integer> expectedChunks = new HashMap<>();

    @Override
    public void onInitializeClient() {
        // Listen for server messages
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, responseSender) -> {
            String payload = buf.readString();
            client.execute(() -> handleServerMessage(payload));
        });

        // Register only functional commands (no GUI command)
        registerCommands(ClientCommandManager.DISPATCHER);

        // Register Litematica menu button
        com.example.syncmatica.client.gui.SyncmaticaIntegration.registerButton();
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("syncclient")
            .then(ClientCommandManager.literal("download")
                .then(ClientCommandManager.argument("id", StringArgumentType.string())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        requestDownload(id);
                        ctx.getSource().sendFeedback(() -> Text.literal("§aDownload request sent for " + id));
                        return 1;
                    })))
            .then(ClientCommandManager.literal("upload")
                .then(ClientCommandManager.argument("filename", StringArgumentType.string())
                    .executes(ctx -> {
                        String filename = StringArgumentType.getString(ctx, "filename");
                        uploadSchematic(filename);
                        ctx.getSource().sendFeedback(() -> Text.literal("§aUpload started for " + filename));
                        return 1;
                    }))));
    }

    private void handleServerMessage(String payload) {
        if (payload.startsWith("META;")) {
            Map<String, String> kv = parseKv(payload.substring(5));
            String id = kv.get("id");
            fileBuffers.put(id, new ArrayList<>());
            expectedChunks.put(id, 0);
        } else if (payload.startsWith("DATA;")) {
            String[] parts = payload.split(";", 3);
            if (parts.length < 3) return;
            String id = parts[1].substring("id=".length());
            String chunkInfoAndBytes = parts[2];
            int sep = chunkInfoAndBytes.indexOf(';');
            if (sep < 0) return;
            String chunkInfo = chunkInfoAndBytes.substring(0, sep);

            // Extract raw bytes after header; in real impl, prefer binary packets not string concat
            byte[] bytes = payload.substring(payload.indexOf(chunkInfo) + chunkInfo.length() + 1).getBytes();

            String[] ci = chunkInfo.replace("chunk=", "").split("/");
            int index = Integer.parseInt(ci[0]);
            int total = Integer.parseInt(ci[1]);
            expectedChunks.put(id, total);

            fileBuffers.computeIfAbsent(id, k -> new ArrayList<>());
            fileBuffers.get(id).add(bytes);

            if (index + 1 == total) {
                saveFile(id);
            }
        } else if (payload.startsWith("UPLOAD_DONE;")) {
            System.out.println("[SyncmaticaPaperClient] Upload done: " + payload);
        }
    }

    private void saveFile(String id) {
        try {
            File schematicsDir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
            if (!schematicsDir.exists()) schematicsDir.mkdirs();

            File outFile = new File(schematicsDir, id + ".litematic");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                for (byte[] chunk : fileBuffers.get(id)) {
                    fos.write(chunk);
                }
            }
            System.out.println("[SyncmaticaPaperClient] Saved: " + outFile.getAbsolutePath());
            fileBuffers.remove(id);
            expectedChunks.remove(id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, String> parseKv(String s) {
        Map<String, String> map = new HashMap<>();
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                map.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return map;
    }

    private void requestDownload(String id) {
        String msg = "REQUEST_DOWNLOAD;id=" + id;
        send(msg);
    }

    private void uploadSchematic(String filename) {
        try {
            File schematicsDir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
            File file = new File(schematicsDir, filename);
            if (!file.exists()) {
                System.out.println("[SyncmaticaPaperClient] File not found: " + file.getAbsolutePath());
                return;
            }
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String hash = sha256Hex(fileBytes);
            requestUpload(filename.replace(".litematic", ""), fileBytes, hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void requestUpload(String id, byte[] fileBytes, String hash) {
        String meta = "REQUEST_UPLOAD;id=" + id + ";size=" + fileBytes.length + ";hash=" + hash;
        send(meta);

        int chunkSize = 32 * 1024;
        int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(fileBytes.length, start + chunkSize);
            byte[] chunk = Arrays.copyOfRange(fileBytes, start, end);
            String header = "DATA;id=" + id + ";chunk=" + i + "/" + totalChunks + ";";
            byte[] headerBytes = header.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] packet = new byte[headerBytes.length + chunk.length];
            System.arraycopy(headerBytes, 0, packet, 0, headerBytes.length);
            System.arraycopy(chunk, 0, packet, headerBytes.length, chunk.length);
            send(packet);
        }
    }

    private static void send(String s) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(s);
        ClientPlayNetworking.send(CHANNEL, buf);
    }

    private static void send(byte[] bytes) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBytes(bytes);
        ClientPlayNetworking.send(CHANNEL, buf);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
