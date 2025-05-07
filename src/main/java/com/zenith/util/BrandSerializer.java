package com.zenith.util;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.experimental.UtilityClass;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;

@UtilityClass
public class BrandSerializer {
    private static final String BRAND = "ZenithProxy";

    public static byte[] defaultBrand() {
        return serializeBrand(BRAND);
    }

    public static byte[] serializeBrand(String brand) {
        final var byteBuf = Unpooled.buffer(ByteBufUtil.utf8Bytes(brand) + 16);
        MinecraftTypes.writeString(byteBuf, brand);
        final var bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        byteBuf.release();
        return bytes;
    }

    // append ' (ZenithProxy)' to the end of the original brand
    public static byte[] appendBrand(final byte[] original) {
        final var inBuf = Unpooled.wrappedBuffer(original);
        final var outBuf = Unpooled.buffer(100);
        final var originalStr = MinecraftTypes.readString(inBuf);
        final var appendedStr = originalStr + " (" + BRAND + ")";
        MinecraftTypes.writeString(outBuf, appendedStr);
        final var bytes = new byte[outBuf.readableBytes()];
        outBuf.readBytes(bytes);
        outBuf.release();
        return bytes;
    }
}
