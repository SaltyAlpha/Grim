package ac.grim.grimac.command;

import ac.grim.grimac.api.storage.DataStore;
import ac.grim.grimac.api.storage.history.HistoryService;
import ac.grim.grimac.api.storage.history.SessionSummary;
import ac.grim.grimac.api.storage.model.SessionRecord;
import ac.grim.grimac.api.storage.query.Cursor;
import ac.grim.grimac.api.storage.query.Page;
import ac.grim.grimac.api.storage.query.Queries;
import ac.grim.grimac.internal.storage.checks.CheckRegistry;
import ac.grim.grimac.internal.storage.checks.InMemoryCheckCatalogPersistence;
import ac.grim.grimac.internal.storage.history.HistoryServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal") // Exercise the legacy query surface used by the bundled service.
class HistoryPaginationTest {
    private final UUID player = UUID.randomUUID();
    private final List<SessionRecord> sessions = new ArrayList<>();
    private int violationCountQueries;

    private HistoryServiceImpl history(int count) {
        for (int i = count; i > 0; i--) {
            sessions.add(new SessionRecord(new UUID(0, i), player, "server", i * 1000L,
                    i * 1000L + 100, i * 1000L + 100, "test", "vanilla", 47, "1.8.8",
                    UUID.randomUUID(), UUID.randomUUID(), List.of()));
        }
        DataStore store = (DataStore) Proxy.newProxyInstance(DataStore.class.getClassLoader(),
                new Class<?>[]{DataStore.class}, (proxy, method, args) -> {
                    Object result;
                    switch (method.getName()) {
                        case "countSessionsByPlayer" -> result = (long) sessions.size();
                        case "countViolationsInSession" -> {
                            violationCountQueries++;
                            result = 0L;
                        }
                        case "countUniqueChecksInSession" -> result = 0L;
                        case "query" -> {
                            Queries.ListSessionsByPlayer query = (Queries.ListSessionsByPlayer) args[1];
                            assertEquals(player, query.player());
                            int start = query.cursor() == null ? 0 : Integer.parseInt(query.cursor().token());
                            int end = Math.min(start + query.pageSize(), sessions.size());
                            result = new Page<>(sessions.subList(start, end),
                                    end < sessions.size() ? new Cursor(Integer.toString(end)) : null);
                        }
                        default -> throw new AssertionError("Unexpected datastore call: " + method.getName());
                    }
                    return CompletableFuture.completedFuture(result);
                });
        return new HistoryServiceImpl(store, new CheckRegistry(new InMemoryCheckCatalogPersistence()), 2, 1000);
    }

    @Test
    void firstPageRetainsNewestOrdinals() throws Exception {
        Page<SessionSummary> page = HistoryPagination.listPage(history(5), player, 2, 1);
        assertEquals(List.of(5, 4), page.items().stream().map(SessionSummary::sessionOrdinal).toList());
    }

    @Test
    void secondPageUsesGlobalOrdinalsMatchingSessionIdentity() throws Exception {
        Page<SessionSummary> page = HistoryPagination.listPage(history(5), player, 2, 2);
        assertEquals(List.of(3, 2), page.items().stream().map(SessionSummary::sessionOrdinal).toList());
        assertEquals(List.of(new UUID(0, 3), new UUID(0, 2)), page.items().stream().map(SessionSummary::sessionId).toList());
    }

    @Test
    void skippedPagesDoNotLoadViolationCounts() throws Exception {
        HistoryPagination.listPage(history(5), player, 2, 3);
        assertEquals(1, violationCountQueries);
    }

    @Test
    void finalPartialPageContainsOldestSession() throws Exception {
        Page<SessionSummary> page = HistoryPagination.listPage(history(5), player, 2, 3);
        assertEquals(List.of(1), page.items().stream().map(SessionSummary::sessionOrdinal).toList());
        assertNull(page.nextCursor());
    }

    @Test
    void emptyHistoryStaysEmpty() throws Exception {
        assertTrue(HistoryPagination.listPage(history(0), player, 2, 1).items().isEmpty());
    }

    @Test
    void pastEndDoesNotWrapToFirstPage() throws Exception {
        assertTrue(HistoryPagination.listPage(history(5), player, 2, 4).items().isEmpty());
    }

    @Test
    void alternateServiceFollowsCursor() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Cursor next = new Cursor("next");
        HistoryService alternate = (HistoryService) Proxy.newProxyInstance(HistoryService.class.getClassLoader(),
                new Class<?>[]{HistoryService.class}, (proxy, method, args) -> {
                    assertEquals("listSessions", method.getName());
                    assertEquals(player, args[0]);
                    assertEquals(2, args[2]);
                    if (calls.getAndIncrement() == 0) {
                        assertNull(args[1]);
                        return CompletableFuture.completedFuture(new Page<SessionSummary>(List.of(), next));
                    }
                    assertEquals(next, args[1]);
                    return CompletableFuture.completedFuture(new Page<SessionSummary>(List.of(), null));
                });
        assertTrue(HistoryPagination.listPage(alternate, player, 2, 2).items().isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void alternateServiceStopsAtExhaustedCursor() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HistoryService alternate = (HistoryService) Proxy.newProxyInstance(HistoryService.class.getClassLoader(),
                new Class<?>[]{HistoryService.class}, (proxy, method, args) -> {
                    assertEquals("listSessions", method.getName());
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(new Page<SessionSummary>(List.of(), null));
                });
        assertTrue(HistoryPagination.listPage(alternate, player, 2, 3).items().isEmpty());
        assertEquals(1, calls.get());
    }
}
