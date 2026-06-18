package com.aquarius.feature.bridge;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeProtocolTest {

    private static BridgeProtocol.Reader envelope(byte[] data, String expectedTopic) {
        BridgeProtocol.Reader r = new BridgeProtocol.Reader(data);
        assertEquals(BridgeProtocol.PROTOCOL_VERSION, r.readVarInt());
        assertEquals(expectedTopic, r.readString());
        return r;
    }

    @Test
    void varIntRoundTripIncludingNegatives() {
        int[] values = {0, 1, 127, 128, 255, 300, 16384, -1, -2048, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int v : values) {
            BridgeProtocol.Writer w = new BridgeProtocol.Writer();
            w.writeVarInt(v);
            assertEquals(v, new BridgeProtocol.Reader(w.toByteArray()).readVarInt(), "varint " + v);
        }
    }

    @Test
    void intAndStringRoundTrip() {
        BridgeProtocol.Writer w = new BridgeProtocol.Writer();
        w.writeInt(-123456);
        w.writeInt(2_000_000_000);
        w.writeString("héllo ✦ world");
        w.writeBoolean(true);
        BridgeProtocol.Reader r = new BridgeProtocol.Reader(w.toByteArray());
        assertEquals(-123456, r.readInt());
        assertEquals(2_000_000_000, r.readInt());
        assertEquals("héllo ✦ world", r.readString());
        assertTrue(r.readBoolean());
        assertFalse(r.hasRemaining());
    }

    @Test
    void helloRoundTrip() {
        byte[] data = BridgeProtocol.encodeHello("proxy", "1.0.0", List.of("pearldrop.empties", "cmd/invoke"));
        BridgeProtocol.Reader r = envelope(data, BridgeProtocol.TOPIC_HELLO);
        assertEquals("proxy", r.readString());
        assertEquals("1.0.0", r.readString());
        assertEquals(2, r.readVarInt());
        assertEquals("pearldrop.empties", r.readString());
        assertEquals("cmd/invoke", r.readString());
        assertFalse(r.hasRemaining());
    }

    @Test
    void waypointSyncRoundTrip() {
        List<BridgeWaypoint> wps = List.of(
            new BridgeWaypoint("pd_1_64_-2", "Pearl", "minecraft:the_nether", 1, 64, -2, BridgeWaypoint.COLOR_GREEN, 0),
            new BridgeWaypoint("pd_9_70_3", "Pearl", "minecraft:the_nether", 9, 70, 3, 0xFF00FF, 200)
        );
        byte[] data = BridgeProtocol.encodeWaypointSync("pearldrop.empties", wps);
        BridgeProtocol.Reader r = envelope(data, BridgeProtocol.TOPIC_WP_SYNC);
        assertEquals("pearldrop.empties", r.readString());
        int n = r.readVarInt();
        assertEquals(2, n);
        for (BridgeWaypoint expected : wps) {
            assertEquals(expected, BridgeProtocol.readWaypoint(r));
        }
        assertFalse(r.hasRemaining());
    }

    @Test
    void cmdInvokeRoundTrip() {
        byte[] data = BridgeProtocol.encodeCmdInvoke("pearldrop", "pull 100 64 -200");
        BridgeProtocol.Reader r = envelope(data, BridgeProtocol.TOPIC_CMD_INVOKE);
        assertEquals("pearldrop", r.readString());
        assertEquals("pull 100 64 -200", r.readString());
    }

    @Test
    void registerContainsAndStrip() {
        byte[] payload = "minecraft:brand\0proxybridge:main\0fabric:registry/sync"
            .getBytes(StandardCharsets.UTF_8);
        assertTrue(BridgeProtocol.registerContainsChannel(payload, BridgeProtocol.CHANNEL_ID));

        byte[] stripped = BridgeProtocol.stripChannelFromRegister(payload, BridgeProtocol.CHANNEL_ID);
        assertArrayEquals("minecraft:brand\0fabric:registry/sync".getBytes(StandardCharsets.UTF_8), stripped);
        assertFalse(BridgeProtocol.registerContainsChannel(stripped, BridgeProtocol.CHANNEL_ID));
    }

    @Test
    void stripReturnsNullWhenChannelAbsent() {
        byte[] payload = "minecraft:brand\0fabric:registry/sync".getBytes(StandardCharsets.UTF_8);
        assertNull(BridgeProtocol.stripChannelFromRegister(payload, BridgeProtocol.CHANNEL_ID));
    }

    @Test
    void stripOnlyChannelYieldsEmpty() {
        byte[] payload = "proxybridge:main".getBytes(StandardCharsets.UTF_8);
        byte[] stripped = BridgeProtocol.stripChannelFromRegister(payload, BridgeProtocol.CHANNEL_ID);
        assertEquals(0, stripped.length);
    }
}
