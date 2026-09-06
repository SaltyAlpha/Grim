package ac.grim.grimac.testsupport;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;

/** Bundled mapping resources only: never starts a server or injects a channel. */
public final class MappingTestApi extends PacketEventsAPI<Object> {
    public boolean isLoaded() { return false; }
    public void init() { throw new UnsupportedOperationException(); }
    public boolean isInitialized() { return false; }
    public boolean isTerminated() { return false; }
    public Object getPlugin() { return null; }
    public ServerManager getServerManager() { return () -> ServerVersion.V_1_21_4; }
    public ProtocolManager getProtocolManager() { throw new UnsupportedOperationException(); }
    public PlayerManager getPlayerManager() { throw new UnsupportedOperationException(); }
    public NettyManager getNettyManager() { throw new UnsupportedOperationException(); }
    public ChannelInjector getInjector() { throw new UnsupportedOperationException(); }
}
