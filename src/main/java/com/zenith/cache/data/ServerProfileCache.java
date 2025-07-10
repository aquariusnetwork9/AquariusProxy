package com.zenith.cache.data;

import com.zenith.cache.CacheResetType;
import com.zenith.cache.CachedData;
import lombok.Data;
import lombok.experimental.Accessors;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpSession;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

@Data
@Accessors(chain = true)
public class ServerProfileCache implements CachedData {

    protected GameProfile profile;

    @Override
    public void getPackets(@NonNull Consumer<Packet> consumer, final @NonNull TcpSession session) {}

    public @Nullable GameProfile getProfile() {
        return profile;
    }

    @Override
    public void reset(CacheResetType type) {
        if (type == CacheResetType.FULL)   {
            this.profile = null;
        }
    }

    @Override
    public String getSendingMessage()  {
        return String.format("Sending profile: %s", profile.toString());
    }

}
