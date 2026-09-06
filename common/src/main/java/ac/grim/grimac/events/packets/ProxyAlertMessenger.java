package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.anticheat.ProxyAlertCodec;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;

// TODO (Cross-Platform) ensure this is correct, and modify to only check appropriate files for each platform
public class ProxyAlertMessenger extends PacketListenerAbstract {
    @Getter private static boolean usingProxy;
    private final ProxyAlertCodec codec = new ProxyAlertCodec();

    public ProxyAlertMessenger() {
        usingProxy = ProxyAlertMessenger.getBooleanFromFile("spigot.yml", "settings.bungeecord")
                || ProxyAlertMessenger.getBooleanFromFile("paper.yml", "settings.velocity-support.enabled")
                || (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19) && ProxyAlertMessenger.getBooleanFromFile("config/paper-global.yml", "proxies.velocity.enabled"));

        if (usingProxy) {
            LogUtil.info("Registering an outgoing plugin channel...");
            GrimAPI.INSTANCE.getPlatformServer().registerOutgoingPluginChannel("BungeeCord");
        }
    }

    public static void sendPluginMessage(String message) {
        if (!canSendAlerts())
            return;
        String secret = proxySecret();
        if (secret.isBlank()) return; // A reload may have disabled sharing since the check above.

        final byte[] messageBytes;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ONLINE");
        out.writeUTF("GRIMAC");

        try {
            messageBytes = ProxyAlertCodec.encode(message, secret, System.currentTimeMillis());
        } catch (IOException exception) {
            LogUtil.error("Something went wrong whilst forwarding an alert to other servers!", exception);
            return;
        }

        out.writeShort(messageBytes.length);
        out.write(messageBytes);

        var carrier = Iterables.getFirst(GrimAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers(), null);
        if (carrier != null) carrier.sendPluginMessage("BungeeCord", out.toByteArray());
    }

    public static boolean canSendAlerts() {
        var config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        return usingProxy && config != null && !proxySecret().isBlank() && config.getBooleanElse("alerts.proxy.send", false) && !GrimAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers().isEmpty();
    }

    public static boolean canReceiveAlerts() {
        var config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        return usingProxy && config != null && !proxySecret().isBlank() && config.getBooleanElse("alerts.proxy.receive", false) && GrimAPI.INSTANCE.getAlertManager().hasAlertListeners();
    }

    private static String proxySecret() {
        var config = GrimAPI.INSTANCE.getConfigManager().getConfig();
        return config == null ? "" : config.getStringElse("alerts.proxy.secret", "");
    }

    // TODO (Cross-Platform) check if new getBooleanFromFile impl is correct
    private static boolean getBooleanFromFile(String pathToFile, String pathToValue) {
        File file = new File(pathToFile);
        if (!file.exists()) return false;

        try (InputStream in = new FileInputStream(file)) {
            Object current = new Yaml().load(in);

            for (String part : pathToValue.split("\\.")) {
                if (!(current instanceof Map map)) return false;
                current = map.get(part);
            }

            return Boolean.TRUE.equals(current);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE || !ProxyAlertMessenger.canReceiveAlerts())
            return;

        WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);

        if (!wrapper.getChannelName().equals("BungeeCord") && !wrapper.getChannelName().equals("bungeecord:main"))
            return;

        String alert = codec.decode(wrapper.getData(), proxySecret(), System.currentTimeMillis());
        if (alert == null) return;
        Component message = MessageUtil.miniMessage(alert);
        GrimAPI.INSTANCE.getAlertManager().sendAlert(message, null);
    }
}
