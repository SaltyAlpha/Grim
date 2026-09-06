package ac.grim.grimac.utils.latency;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LatencyUtilsTest {
    private final AtomicInteger received = new AtomicInteger(9);
    private final List<Exception> errors = new ArrayList<>();
    private final List<Runnable> async = new ArrayList<>();
    private final LatencyUtils latency = new LatencyUtils(received::get, async::add, errors::add);

    private void acknowledge(int transaction) {
        received.set(transaction);
        latency.handleNettySyncTransaction(transaction);
    }

    @Test
    void newWorldEntitiesAndChunksWaitForResetAndSurvive() {
        Map<Integer, String> entities = new HashMap<>(Map.of(1, "old entity"));
        Map<Integer, String> chunks = new HashMap<>(Map.of(1, "old chunk"));
        latency.addRealTimeTaskBarrier(11, () -> { entities.clear(); chunks.clear(); });
        // New packets arrive while the current sent transaction is still 10.
        latency.addRealTimeTask(10, () -> entities.put(2, "new entity"));
        latency.addRealTimeTask(10, () -> chunks.put(2, "new chunk"));
        acknowledge(10);
        assertEquals(Map.of(1, "old entity"), entities);
        assertEquals(Map.of(1, "old chunk"), chunks);
        acknowledge(11);
        assertEquals(Map.of(2, "new entity"), entities);
        assertEquals(Map.of(2, "new chunk"), chunks);
    }

    @Test
    void rapidWorldChangesAtSameTransactionPreservePacketOrder() {
        List<String> world = new ArrayList<>(List.of("A"));
        latency.addRealTimeTaskBarrier(11, world::clear);
        latency.addRealTimeTask(10, () -> world.add("B"));
        latency.addRealTimeTaskBarrier(11, world::clear);
        latency.addRealTimeTask(10, () -> world.add("C"));
        acknowledge(11);
        assertEquals(List.of("C"), world);
    }

    @Test
    void successiveWorldChangesWaitForTheirOwnAcknowledgements() {
        List<String> world = new ArrayList<>(List.of("A"));
        latency.addRealTimeTaskBarrier(11, world::clear);
        latency.addRealTimeTask(10, () -> world.add("B"));
        latency.addRealTimeTaskBarrier(12, world::clear);
        latency.addRealTimeTask(11, () -> world.add("C"));
        acknowledge(11);
        assertEquals(List.of("B"), world);
        acknowledge(12);
        assertEquals(List.of("C"), world);
    }

    @Test
    void recordedAckCannotBypassAnUndrainedReset() {
        List<String> world = new ArrayList<>(List.of("old"));
        latency.addRealTimeTaskBarrier(11, world::clear);
        received.set(11);
        latency.addRealTimeTask(10, () -> world.add("new"));
        assertEquals(List.of("old"), world);
        latency.handleNettySyncTransaction(11);
        assertEquals(List.of("new"), world);
    }

    @Test
    void oldWorldTasksAlreadyQueuedAtBoundaryRunBeforeReset() {
        List<String> world = new ArrayList<>();
        latency.addRealTimeTask(11, () -> world.add("old delayed spawn"));
        latency.addRealTimeTaskBarrier(11, world::clear);
        latency.addRealTimeTask(10, () -> world.add("new spawn"));
        acknowledge(11);
        assertEquals(List.of("new spawn"), world);
    }

    @Test
    void ordinaryImmediateAndAsyncSchedulingRemainAvailableAfterReset() {
        List<String> results = new ArrayList<>();
        latency.addRealTimeTaskBarrier(10, () -> results.add("reset"));
        acknowledge(10);
        latency.addRealTimeTask(9, () -> results.add("immediate"));
        latency.addRealTimeTaskAsync(9, () -> results.add("async"));
        assertEquals(List.of("reset", "immediate"), results);
        assertEquals(1, async.size());
        async.get(0).run();
        assertEquals(List.of("reset", "immediate", "async"), results);
    }

    @Test
    void failedTransitionReleasesBarrierAndReportsError() {
        latency.addRealTimeTaskBarrier(10, () -> { throw new IllegalStateException("test failure"); });
        acknowledge(10);
        List<String> results = new ArrayList<>();
        latency.addRealTimeTask(10, () -> results.add("after"));
        assertEquals(1, errors.size());
        assertEquals(List.of("after"), results);
    }

    @Test
    void originalNonBarrierSchedulingReproducesReportedDataLoss() {
        List<String> world = new ArrayList<>(List.of("old"));
        latency.addRealTimeTask(11, world::clear);
        latency.addRealTimeTask(10, () -> world.add("new"));
        acknowledge(10);
        assertEquals(List.of("old", "new"), world);
        acknowledge(11);
        assertTrue(world.isEmpty(), "Original scheduling wipes the new-world data");
    }

    @Test
    void futureUpdatesKeepTheirOriginalDeadline() {
        List<String> results = new ArrayList<>();
        latency.addRealTimeTaskBarrier(10, () -> results.add("reset"));
        latency.addRealTimeTask(12, () -> results.add("future"));
        acknowledge(10);
        acknowledge(11);
        assertEquals(List.of("reset"), results);
        acknowledge(12);
        assertEquals(List.of("reset", "future"), results);
    }
}
