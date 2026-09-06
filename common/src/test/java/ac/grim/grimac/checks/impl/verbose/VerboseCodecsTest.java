package ac.grim.grimac.checks.impl.verbose;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.api.storage.verbose.VerboseBuf;
import ac.grim.grimac.api.storage.verbose.VerboseRenderContext;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerboseCodecsTest {
    private static EntityType entityWithId(int id) {
        return (EntityType) Proxy.newProxyInstance(EntityType.class.getClassLoader(), new Class<?>[]{EntityType.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getId")) return id;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void unsupportedEntityRoundTripsAsUnknownInsteadOfAnotherEntity() {
        int encoded = VerboseCodecs.entity(entityWithId(-1), ClientVersion.V_1_8);
        assertEquals(VerboseCodecs.ENTITY_UNKNOWN, encoded);
        Verbose template = Verbose.of("type={entity}");
        byte[] bytes = template.write(new VerboseBuf()).uint(encoded).end().toByteArray();
        assertEquals("type=unknown", template.render(bytes, new VerboseRenderContext(ClientVersion.V_1_8.getProtocolVersion(), null)));
    }

    @Test
    void supportedEntityKeepsItsProtocolIdIncludingZero() {
        assertEquals(0, VerboseCodecs.entity(entityWithId(0), ClientVersion.V_1_8));
        assertEquals(42, VerboseCodecs.entity(entityWithId(42), ClientVersion.V_1_8));
    }

    @Test
    void mappingLookupFailureUsesUnknownSentinel() {
        EntityType unmapped = (EntityType) Proxy.newProxyInstance(EntityType.class.getClassLoader(), new Class<?>[]{EntityType.class},
                (proxy, method, args) -> { throw new IllegalArgumentException("No mapping for this version"); });
        assertEquals(VerboseCodecs.ENTITY_UNKNOWN, VerboseCodecs.entity(unmapped, ClientVersion.V_1_8));
    }
}
