package ac.grim.grimac.command;

import ac.grim.grimac.api.storage.history.HistoryService;
import ac.grim.grimac.api.storage.history.SessionSummary;
import ac.grim.grimac.api.storage.query.Cursor;
import ac.grim.grimac.api.storage.query.Page;
import ac.grim.grimac.internal.storage.history.HistoryServiceImpl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Blocking history paging for the command's asynchronous worker. */
public final class HistoryPagination {
    private HistoryPagination() {
    }

    public static Page<SessionSummary> listPage(HistoryService history, UUID player, int pageSize, int page) throws Exception {
        // The bundled cursor-only implementation does not account for skipped
        // sessions when assigning global ordinals. Its indexed path does, and
        // avoids building summaries (including violation counts) for skipped pages.
        if (history instanceof HistoryServiceImpl impl) {
            return impl.listSessionsPaged(player, page, pageSize).toCompletableFuture().get(10, TimeUnit.SECONDS);
        }

        Cursor cursor = null;
        for (int i = 1; i < page; i++) {
            Page<SessionSummary> previous = history.listSessions(player, cursor, pageSize)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            cursor = previous.nextCursor();
            if (cursor == null) return new Page<>(List.of(), null);
        }
        return history.listSessions(player, cursor, pageSize).toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
