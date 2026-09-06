package ac.grim.grimac.utils.data.tags;

import ac.grim.grimac.testsupport.MappingTestApi;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SyncedTagsMappingTest {
    private static PacketEventsAPI<?> previousApi;

    @BeforeAll
    static void setupMappings() {
        previousApi = PacketEvents.getAPI();
        PacketEvents.setAPI(new MappingTestApi());
    }

    @AfterAll
    static void restoreApi() {
        PacketEvents.setAPI(previousApi);
    }

    @Test
    void unavailableBlockIdNoLongerDereferencesNullMapping() {
        ClientVersion version = ClientVersion.V_1_21_4;
        // Exercise a genuinely absent mapping, without depending on the upstream
        // convenience method continuing to throw in future PacketEvents versions.
        assertNull(StateTypes.getMappedById(version, Integer.MAX_VALUE));
        assertNull(SyncedTags.resolveBlockId(version, Integer.MAX_VALUE));
        assertNull(SyncedTags.resolveBlockId(version, -1));
        assertNull(SyncedTags.resolveBlockId(version, Integer.MIN_VALUE));
    }

    @Test
    void mappedVanillaBlockKeepsItsIdentityAcrossVersions() {
        for (ClientVersion version : new ClientVersion[]{ClientVersion.V_1_16, ClientVersion.V_1_21_4, ClientVersion.V_26_2}) {
            int stoneId = StateTypes.STONE.getMapped().getId(version);
            assertSame(StateTypes.STONE, SyncedTags.resolveBlockId(version, stoneId));
        }
    }

    @Test
    void mixedPacketTagPreservesKnownBlocksAndSkipsUnmappedIds() {
        ClientVersion version = ClientVersion.V_26_2;
        ResourceLocation key = ResourceLocation.minecraft("mineable/pickaxe");
        int stoneId = StateTypes.STONE.getMapped().getId(version);
        SyncedTag<StateType> tag = SyncedTag.<StateType>builder(key).defaults(Set.of(StateTypes.DIRT))
                .remapper(id -> SyncedTags.resolveBlockId(version, id)).build();
        tag.readTagValues(new WrapperPlayServerTags.Tag(key, List.of(stoneId, -1, Integer.MAX_VALUE, stoneId)));
        assertTrue(tag.contains(StateTypes.STONE));
        assertFalse(tag.contains(StateTypes.DIRT));
        assertFalse(tag.contains(null));
    }
}
