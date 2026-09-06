package ac.grim.grimac.utils.data.tags;

import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTags;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SyncedTagTest {
    private static final ResourceLocation KEY = ResourceLocation.minecraft("climbable");

    @Test
    void unknownEntriesAreSkippedWithoutLosingKnownEntries() {
        Object old = new Object();
        Object first = new Object();
        Object last = new Object();
        SyncedTag<Object> tag = SyncedTag.builder(KEY).defaults(Set.of(old))
                .remapper(id -> id == 1 ? first : id == 3 ? last : null).build();
        tag.readTagValues(new WrapperPlayServerTags.Tag(KEY, List.of(1, 999, 3, 1)));
        assertTrue(tag.contains(first));
        assertTrue(tag.contains(last));
        assertFalse(tag.contains(old));
        assertFalse(tag.contains(null), "Unknown ids are not tag members");
    }

    @Test
    void allUnknownReplacementRemovesOldDefaults() {
        Object old = new Object();
        SyncedTag<Object> tag = SyncedTag.builder(KEY).defaults(Set.of(old)).remapper(id -> null).build();
        tag.readTagValues(new WrapperPlayServerTags.Tag(KEY, List.of(999)));
        assertFalse(tag.contains(old));
        assertFalse(tag.contains(null));
    }

    @Test
    void emptyReplacementClearsPreviousValues() {
        Object old = new Object();
        SyncedTag<Object> tag = SyncedTag.builder(KEY).defaults(Set.of(old)).remapper(id -> old).build();
        tag.readTagValues(new WrapperPlayServerTags.Tag(KEY, List.of()));
        assertFalse(tag.contains(old));
    }

    @Test
    void unsupportedClientKeepsDefaultsAndDoesNotResolveIds() {
        Object old = new Object();
        SyncedTag<Object> tag = SyncedTag.builder(KEY).defaults(Set.of(old)).supported(false)
                .remapper(id -> { throw new AssertionError("unsupported tag must not be remapped"); }).build();
        tag.readTagValues(new WrapperPlayServerTags.Tag(KEY, List.of(999)));
        assertTrue(tag.contains(old));
    }
}
