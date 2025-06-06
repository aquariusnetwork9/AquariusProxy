package com.zenith.cache.data.info;

import com.zenith.cache.CacheResetType;
import com.zenith.cache.CachedData;
import lombok.Data;
import lombok.experimental.Accessors;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;

import static com.zenith.Globals.CONFIG;

@Data
@Accessors(chain = true)
public class ClientInfoCache implements CachedData {
    private String locale = "en_US";
    private int renderDistance = 25;
    private ChatVisibility chatVisibility = ChatVisibility.FULL;
    private boolean chatColors = true;
    private List<SkinPart> skinParts = List.of(SkinPart.values());
    private HandPreference handPreference = HandPreference.RIGHT_HAND;
    private boolean textFilteringEnabled = false;
    private boolean allowsListing = false;
    private ParticleStatus particleStatus = ParticleStatus.MINIMAL;

    @Override
    public void getPackets(@NonNull final Consumer<Packet> consumer, final @NonNull TcpSession session) {}

    public Packet getClientInfoPacket() {
        return new ServerboundClientInformationPacket(
            locale,
            renderDistance,
            chatVisibility,
            chatColors,
            skinParts,
            handPreference,
            textFilteringEnabled,
            allowsListing,
            particleStatus
        );
    }

    @Override
    public void reset(final CacheResetType type) {
        if (type == CacheResetType.FULL) {
            locale = "en_US";
            renderDistance = CONFIG.client.defaultClientRenderDistance;
            chatVisibility = ChatVisibility.FULL;
            chatColors = true;
            skinParts = List.of(SkinPart.values());
            handPreference = HandPreference.RIGHT_HAND;
            textFilteringEnabled = false;
            allowsListing = false;
            particleStatus = ParticleStatus.MINIMAL;
        }
    }
}
