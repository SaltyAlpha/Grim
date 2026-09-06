package ac.grim.grimac.utils.nmsutil;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import ac.grim.grimac.testsupport.MappingTestApi;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluidTypeFlowingTest {
    private static PacketEventsAPI<?> previousApi;

    @BeforeAll
    static void initializeMappingResources() {
        previousApi = PacketEvents.getAPI();
        // Load bundled mapping resources, without starting a server or injector.
        PacketEvents.setAPI(new MappingTestApi());
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
