package ac.grim.grimac.utils.nmsutil;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.injector.ChannelInjector;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluidTypeFlowingTest {
    private static PacketEventsAPI<?> previousApi;

    @BeforeAll
    static void initializeMappingResources() {
        previousApi = PacketEvents.getAPI();
        // Load bundled mapping resources, without starting a server or injector.
        PacketEvents.setAPI(new PacketEventsAPI<Object>() {
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
        });
    }

    @AfterAll
    static void restoreApi() {
        PacketEvents.setAPI(previousApi);
    }

    @Test
    void missingLevelBehavesAsSourceWithoutReadingAbsentProperty() {
        WrappedBlockState state = new WrappedBlockState(null, Map.of(), 0, (byte) 0);
        assertEquals(0, FluidTypeFlowing.legacy$getLevel(state));
    }

    @Test
    void preservesEveryFlowingAndFallingLevel() {
        for (int level = 0; level < 16; level++) {
            WrappedBlockState state = new WrappedBlockState(null, Map.of(StateValue.LEVEL, level), 0, (byte) 0);
            assertEquals(level, FluidTypeFlowing.legacy$getLevel(state));
        }
    }
}
