package com.aquarius.feature.bridge;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Wire protocol for the ProxyBridge channel ({@code proxybridge:main}) — the single source of truth for how
 * AquariusProxy and the ProxyBridge client mod (a Meteor addon, see the {@code ProxyBridge} repo) frame the
 * messages they exchange. This class must stay byte-for-byte compatible with the mod's mirror implementation;
 * the format is documented in the mod repo's {@code PROTOCOL.md}.
 *
 * <p><b>Envelope</b> (every message): {@code VarInt protocolVersion} · {@code String topic} · {@code byte[] body}.
 * The {@code body} layout is topic-specific (see the {@code encode*}/{@code TOPIC_*} members).
 *
 * <p><b>Primitives</b> match Minecraft's {@code PacketByteBuf} so the Fabric side is trivial: {@code VarInt} is the
 * standard 7-bit-continuation varint, {@code String} is {@code VarInt(byteLength)} + UTF-8 bytes, {@code int} is a
 * fixed 4-byte big-endian value, {@code boolean} is one byte.
 */
public final class BridgeProtocol {
    private BridgeProtocol() {}

    public static final String CHANNEL_NAMESPACE = "proxybridge";
    public static final String CHANNEL_PATH = "main";
    /** Fully-qualified channel id as it appears in {@code minecraft:register} lists. */
    public static final String CHANNEL_ID = CHANNEL_NAMESPACE + ":" + CHANNEL_PATH;

    public static final int PROTOCOL_VERSION = 1;

    // ---- topics --------------------------------------------------------------------------------
    /** Handshake, both directions: {@code String side, String version, VarInt featureCount, String[] features}. */
    public static final String TOPIC_HELLO = "hello";
    /** Proxy -> client: replace the entire contents of a waypoint group. {@code String group, VarInt n, Waypoint[n]}. */
    public static final String TOPIC_WP_SYNC = "wp/sync";
    /** Proxy -> client: add/update one waypoint in a group. {@code String group, Waypoint}. */
    public static final String TOPIC_WP_UPSERT = "wp/upsert";
    /** Proxy -> client: remove one waypoint. {@code String group, String id}. */
    public static final String TOPIC_WP_REMOVE = "wp/remove";
    /** Proxy -> client: remove all waypoints in a group. {@code String group}. */
    public static final String TOPIC_WP_CLEAR = "wp/clear";
    /** Client -> proxy: request the proxy run a command. {@code String name, String args}. */
    public static final String TOPIC_CMD_INVOKE = "cmd/invoke";
    /** Proxy -> client: small text feedback. {@code String message}. */
    public static final String TOPIC_TOAST = "toast";

    // ---- encoders ------------------------------------------------------------------------------

    public static byte[] encodeHello(String side, String version, List<String> features) {
        Writer w = startMessage(TOPIC_HELLO);
        w.writeString(side);
        w.writeString(version);
        w.writeVarInt(features.size());
        for (String f : features) w.writeString(f);
        return w.toByteArray();
    }

    /** Full-group replace — the primary publish operation (atomic on the client). */
    public static byte[] encodeWaypointSync(String group, List<BridgeWaypoint> waypoints) {
        Writer w = startMessage(TOPIC_WP_SYNC);
        w.writeString(group);
        w.writeVarInt(waypoints.size());
        for (BridgeWaypoint wp : waypoints) writeWaypoint(w, wp);
        return w.toByteArray();
    }

    public static byte[] encodeWaypointClear(String group) {
        Writer w = startMessage(TOPIC_WP_CLEAR);
        w.writeString(group);
        return w.toByteArray();
    }

    public static byte[] encodeToast(String message) {
        Writer w = startMessage(TOPIC_TOAST);
        w.writeString(message);
        return w.toByteArray();
    }

    /** Client -> proxy helper (also used by tests + the mod's mirror). */
    public static byte[] encodeCmdInvoke(String name, String args) {
        Writer w = startMessage(TOPIC_CMD_INVOKE);
        w.writeString(name);
        w.writeString(args == null ? "" : args);
        return w.toByteArray();
    }

    private static Writer startMessage(String topic) {
        Writer w = new Writer();
        w.writeVarInt(PROTOCOL_VERSION);
        w.writeString(topic);
        return w;
    }

    static void writeWaypoint(Writer w, BridgeWaypoint wp) {
        w.writeString(wp.id());
        w.writeString(wp.name());
        w.writeString(wp.dimension());
        w.writeInt(wp.x());
        w.writeInt(wp.y());
        w.writeInt(wp.z());
        w.writeInt(wp.color());
        w.writeInt(wp.ttlTicks());
    }

    public static BridgeWaypoint readWaypoint(Reader r) {
        String id = r.readString();
        String name = r.readString();
        String dimension = r.readString();
        int x = r.readInt();
        int y = r.readInt();
        int z = r.readInt();
        int color = r.readInt();
        int ttl = r.readInt();
        return new BridgeWaypoint(id, name, dimension, x, y, z, color, ttl);
    }

    // ---- minecraft:register helpers ------------------------------------------------------------
    // The REGISTER plugin message payload is a list of channel ids encoded as UTF-8 and separated by NUL (0x00).

    public static boolean registerContainsChannel(byte[] data, String channelId) {
        for (String c : new String(data, StandardCharsets.UTF_8).split("\0")) {
            if (c.equals(channelId)) return true;
        }
        return false;
    }

    /**
     * Returns a new REGISTER payload with {@code channelId} removed, or {@code null} if it was not present
     * (caller should forward the original unchanged). An empty result means no channels remain.
     */
    public static byte[] stripChannelFromRegister(byte[] data, String channelId) {
        String[] parts = new String(data, StandardCharsets.UTF_8).split("\0");
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String p : parts) {
            if (p.equals(channelId)) { found = true; continue; }
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\0');
            sb.append(p);
        }
        if (!found) return null;
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---- buffer primitives ---------------------------------------------------------------------

    /** Minimal growable writer producing the raw custom-payload {@code byte[]}. */
    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

        public void writeVarInt(int value) {
            while ((value & ~0x7F) != 0) {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            out.write(value & 0x7F);
        }

        public void writeString(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            writeVarInt(b.length);
            out.write(b, 0, b.length);
        }

        public void writeInt(int v) {
            out.write((v >>> 24) & 0xFF);
            out.write((v >>> 16) & 0xFF);
            out.write((v >>> 8) & 0xFF);
            out.write(v & 0xFF);
        }

        public void writeBoolean(boolean b) {
            out.write(b ? 1 : 0);
        }

        public byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    /** Minimal reader over a custom-payload {@code byte[]}. */
    public static final class Reader {
        private final byte[] data;
        private int pos;

        public Reader(byte[] data) {
            this.data = data;
        }

        public int readVarInt() {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (pos >= data.length) throw new IndexOutOfBoundsException("VarInt past end of buffer");
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IllegalArgumentException("VarInt too big");
            } while ((b & 0x80) != 0);
            return value;
        }

        public String readString() {
            int len = readVarInt();
            if (len < 0 || pos + len > data.length) throw new IndexOutOfBoundsException("String past end of buffer");
            String s = new String(data, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        public int readInt() {
            if (pos + 4 > data.length) throw new IndexOutOfBoundsException("Int past end of buffer");
            int v = ((data[pos] & 0xFF) << 24)
                | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8)
                | (data[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        public boolean readBoolean() {
            if (pos >= data.length) throw new IndexOutOfBoundsException("Boolean past end of buffer");
            return data[pos++] != 0;
        }

        public boolean hasRemaining() {
            return pos < data.length;
        }
    }
}
