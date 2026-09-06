package ac.grim.grimac.utils.anticheat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Authenticates forwarded alerts before their text is interpreted as MiniMessage. */
public final class ProxyAlertCodec {
    private static final int MAGIC = 0x47524131; // GRA1: signed Grim alert protocol v1
    private static final int MAC_LENGTH = 32;
    private static final int MAX_PAYLOAD = 32700;
    private static final long MAX_AGE_MILLIS = 60_000;
    private static final int MAX_REPLAYS = 4096;
    private final Map<UUID, Long> received = new HashMap<>();

    public static byte[] encode(String alert, String secret, long now) throws IOException {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("Proxy alert secret is required");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeLong(now);
        UUID nonce = UUID.randomUUID();
        out.writeLong(nonce.getMostSignificantBits());
        out.writeLong(nonce.getLeastSignificantBits());
        out.writeUTF(alert);
        if (bytes.size() + MAC_LENGTH > MAX_PAYLOAD) throw new IOException("Proxy alert is too large");
        byte[] signature = sign(bytes.toByteArray(), secret);
        out.write(signature);
        return bytes.toByteArray();
    }

    /** Returns null for unsigned, malformed, expired, tampered or replayed messages. */
    public synchronized String decode(byte[] forwarded, String secret, long now) {
        if (secret == null || secret.isBlank() || forwarded == null || forwarded.length > MAX_PAYLOAD + 10) return null;
        try {
            DataInputStream frame = new DataInputStream(new ByteArrayInputStream(forwarded));
            if (!"GRIMAC".equals(frame.readUTF())) return null;
            int length = frame.readUnsignedShort();
            if (length < 30 + MAC_LENGTH || length > MAX_PAYLOAD || length != frame.available()) return null;
            byte[] payload = new byte[length - MAC_LENGTH];
            frame.readFully(payload);
            byte[] signature = new byte[MAC_LENGTH];
            frame.readFully(signature);
            if (!MessageDigest.isEqual(signature, sign(payload, secret))) return null;

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != MAGIC) return null;
            long timestamp = in.readLong();
            if (timestamp < now - MAX_AGE_MILLIS || timestamp > now + MAX_AGE_MILLIS) return null;
            UUID nonce = new UUID(in.readLong(), in.readLong());
            String alert = in.readUTF();
            if (in.available() != 0) return null;
            // Keep future-dated messages until they are no longer valid, too.
            received.values().removeIf(time -> time < now - MAX_AGE_MILLIS);
            if (received.containsKey(nonce) || received.size() >= MAX_REPLAYS) return null;
            received.put(nonce, timestamp);
            return alert;
        } catch (IOException exception) {
            // Network input is untrusted; malformed messages must not flood the log.
            return null;
        }
    }

    private static byte[] sign(byte[] payload, String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is required by the Java runtime", exception);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }
}
