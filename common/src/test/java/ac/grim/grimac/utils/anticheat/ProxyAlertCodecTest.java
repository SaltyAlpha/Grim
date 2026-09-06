package ac.grim.grimac.utils.anticheat;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ProxyAlertCodecTest {
    private static final String SECRET = "test-only-shared-secret-not-for-production";
    private static final long NOW = 1_800_000_000_000L;

    private static byte[] frame(byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("GRIMAC");
        out.writeShort(payload.length);
        out.write(payload);
        return bytes.toByteArray();
    }

    @Test void acceptsAuthenticatedUnicodeAndFormatting() throws IOException {
        String alert = "<red>Grim</red> Spieler äöü 世界";
        assertEquals(alert, new ProxyAlertCodec().decode(frame(ProxyAlertCodec.encode(alert, SECRET, NOW)), SECRET, NOW));
    }

    @Test void rejectsUnsignedLegacyAlerts() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeUTF("<click:run_command:'/op attacker'>Forged</click>");
        assertNull(new ProxyAlertCodec().decode(frame(bytes.toByteArray()), SECRET, NOW));
    }

    @Test void rejectsWrongKeyTamperingAndReplay() throws IOException {
        byte[] packet = frame(ProxyAlertCodec.encode("alert", SECRET, NOW));
        ProxyAlertCodec codec = new ProxyAlertCodec();
        assertNull(codec.decode(packet, "wrong secret", NOW));
        byte[] changed = packet.clone();
        changed[changed.length - 1] ^= 1;
        assertNull(codec.decode(changed, SECRET, NOW));
        assertEquals("alert", codec.decode(packet, SECRET, NOW));
        assertNull(codec.decode(packet, SECRET, NOW));
    }

    @Test void rejectsExpiredAndFutureMessages() throws IOException {
        assertNull(new ProxyAlertCodec().decode(frame(ProxyAlertCodec.encode("old", SECRET, NOW - 60_001)), SECRET, NOW));
        assertNull(new ProxyAlertCodec().decode(frame(ProxyAlertCodec.encode("future", SECRET, NOW + 60_001)), SECRET, NOW));
        assertNull(new ProxyAlertCodec().decode(frame(ProxyAlertCodec.encode("overflow", SECRET, Long.MAX_VALUE)), SECRET, NOW));
    }

    @Test void rejectsEveryTruncationAndTrailingData() throws IOException {
        byte[] packet = frame(ProxyAlertCodec.encode("alert", SECRET, NOW));
        ProxyAlertCodec codec = new ProxyAlertCodec();
        for (int size = 0; size < packet.length; size++) assertNull(codec.decode(Arrays.copyOf(packet, size), SECRET, NOW));
        assertNull(codec.decode(Arrays.copyOf(packet, packet.length + 1), SECRET, NOW));
    }

    @Test void rejectsUnconfiguredAndOversizedMessages() {
        assertNull(new ProxyAlertCodec().decode(null, SECRET, NOW));
        assertNull(new ProxyAlertCodec().decode(new byte[0], "", NOW));
        assertNull(new ProxyAlertCodec().decode(new byte[40_000], SECRET, NOW));
        assertThrows(IllegalArgumentException.class, () -> ProxyAlertCodec.encode("alert", " ", NOW));
        assertThrows(IOException.class, () -> ProxyAlertCodec.encode("a".repeat(33_000), SECRET, NOW));
    }

    @Test void malformedFramesNeverEscapeTheDecoder() {
        Random random = new Random(2869);
        ProxyAlertCodec codec = new ProxyAlertCodec();
        for (int i = 0; i < 1000; i++) {
            byte[] packet = new byte[random.nextInt(1000)];
            random.nextBytes(packet);
            assertNull(codec.decode(packet, SECRET, NOW));
        }
    }

    @Test void replayCacheIsBoundedAndExpires() throws IOException {
        ProxyAlertCodec codec = new ProxyAlertCodec();
        for (int i = 0; i < 4096; i++) assertEquals("alert", codec.decode(frame(ProxyAlertCodec.encode("alert", SECRET, NOW)), SECRET, NOW));
        assertNull(codec.decode(frame(ProxyAlertCodec.encode("full", SECRET, NOW)), SECRET, NOW));
        assertEquals("later", codec.decode(frame(ProxyAlertCodec.encode("later", SECRET, NOW + 60_001)), SECRET, NOW + 60_001));
    }
}
