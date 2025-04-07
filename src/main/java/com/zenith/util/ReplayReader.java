package com.zenith.util;

import com.zenith.cache.data.mcpl.CachedChunkSectionCountProvider;
import com.zenith.feature.replay.ReplayMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.SneakyThrows;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.zenith.Globals.GSON;

public class ReplayReader {
    private final File mcprFile;
    private final File packetLogOutputFile;
    private static final ByteBufAllocator ALLOC = PooledByteBufAllocator.DEFAULT;

    public ReplayReader(File replayFile, File packetLogOutputFile) {
        this.mcprFile = replayFile;
        this.packetLogOutputFile = packetLogOutputFile;
    }

    @SneakyThrows
    public void read() {
        // validate file exists
        if (!mcprFile.exists()) throw new RuntimeException("Replay file does not exist");

        // validate file ends in .mcpr
        if (!mcprFile.getName().endsWith(".mcpr")) throw new RuntimeException("Replay file does not have .mcpr extension");

        // validate file is a valid zip file
        try (var zip = new ZipFile(mcprFile)) {
            // validate zip file contains metaData.json
            ZipEntry metadataEntry = zip.getEntry("metaData.json");
            if (metadataEntry == null) throw new RuntimeException("Replay file does not contain metaData.json");
            try (var metadataEntryStream = zip.getInputStream(metadataEntry)) {
                try (Reader reader = new InputStreamReader(metadataEntryStream)) {
                    ReplayMetadata metadata = GSON.fromJson(reader, ReplayMetadata.class);
                    // validate metaData.json mcversion matches our version
                    if (!metadata.getMcversion().equals(MinecraftCodec.CODEC.getMinecraftVersion()))
                        throw new RuntimeException("Replay file mcversion does not match current version. Expected: " + MinecraftCodec.CODEC.getMinecraftVersion() + " Actual: " + metadata.getMcversion());
                }
            }
            // validate zip file contains recording.tmcpr
            ZipEntry recordingEntry = zip.getEntry("recording.tmcpr");
            if (recordingEntry == null) throw new RuntimeException("Replay file does not contain recording.tmcpr");
            try (var recordingEntryStream = zip.getInputStream(recordingEntry)) {
                try (var recordingStream = new DataInputStream(new BufferedInputStream(recordingEntryStream))) {
                    read(recordingStream);
                }
            }
        }
    }

    @SneakyThrows
    private void read(final DataInputStream recordingStream) {
        MinecraftProtocol packetProtocol = new MinecraftProtocol();
        packetProtocol.setTargetState(ProtocolState.GAME);
        packetProtocol.setUseDefaultListeners(false);
        packetProtocol.setInboundState(ProtocolState.LOGIN);
        CachedChunkSectionCountProvider chunkSectionCountProvider = new CachedChunkSectionCountProvider();
        MinecraftConstants.CHUNK_SECTION_COUNT_PROVIDER = chunkSectionCountProvider;
        try (var outputWriter = new BufferedOutputStream(new FileOutputStream(packetLogOutputFile))) {
            while (recordingStream.available() > 0) {
                readRecordingEntry(recordingStream, packetProtocol, chunkSectionCountProvider, outputWriter);
            }
        }
    }

    @SneakyThrows
    private void readRecordingEntry(final DataInputStream recordingStream, final MinecraftProtocol packetProtocol, final CachedChunkSectionCountProvider chunkSectionCountProvider, final BufferedOutputStream outputWriter) {
        int t = recordingStream.readInt();
        int len = recordingStream.readInt();
        ByteBuf byteBuf = ALLOC.buffer();
        try {
            byteBuf.writeBytes(recordingStream, len);
            int packetId = packetProtocol.getPacketHeader().readPacketId(byteBuf);
            Packet packet = packetProtocol.getInboundPacketRegistry().createClientboundPacket(packetId, byteBuf);
            String out = "\n[" + t + "] " + packet.toString();
            outputWriter.write(out.getBytes(StandardCharsets.UTF_8));
            switch (packetProtocol.getInboundState()) {
                case ProtocolState.LOGIN -> {
                    if (packet instanceof ClientboundGameProfilePacket) {
                        packetProtocol.setInboundState(ProtocolState.CONFIGURATION);
                    }
                }
                case ProtocolState.CONFIGURATION -> {
                    if (packet instanceof ClientboundFinishConfigurationPacket) {
                        packetProtocol.setInboundState(ProtocolState.GAME);
                    }
                }
                case ProtocolState.GAME -> {
                    if (packet instanceof ClientboundStartConfigurationPacket) {
                        packetProtocol.setInboundState(ProtocolState.CONFIGURATION);
                    }
                }
            }
            if (packet instanceof ClientboundLoginPacket loginPacket) {
                chunkSectionCountProvider.updateDimension(loginPacket.getCommonPlayerSpawnInfo());
            } else if (packet instanceof ClientboundRespawnPacket respawnPacket) {
                chunkSectionCountProvider.updateDimension(respawnPacket.getCommonPlayerSpawnInfo());
            }
        } catch (final Throwable e) {
            outputWriter.write("\nError reading recording entry".getBytes(StandardCharsets.UTF_8));
            throw e;
        } finally {
            byteBuf.release();
        }
    }
}
