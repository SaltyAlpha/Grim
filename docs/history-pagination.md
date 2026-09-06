# History list pagination

The history command uses `HistoryServiceImpl.listSessionsPaged` for the bundled
storage implementation. The cursor-only `listSessions` implementation numbers
every page as if it were the newest page. On later pages this also made the
clickable session row open a different session, because the detail command uses
the global chronological session ordinal.

The indexed API already exists in the bundled GrimAPI dependency; this change
does not require a new API release. It walks session records for skipped pages
without loading their violation counts or startup metadata. Only the displayed
page is converted into summaries. Alternate HistoryService implementations
retain cursor-based paging according to the public interface contract; an
exhausted cursor returns an empty page instead of restarting from page one.

Regression tests exercise the actual bundled HistoryServiceImpl with an
in-memory query stub: first, middle, partial last and empty/out-of-range pages,
global ordinals matching session IDs, and violation-count query volume.

This is not a fix for all database timeouts reported in issues #2766 and #2808.
It does not change `session latest`, database connection pools or lease handling.
No live multi-server database test has been performed. Concurrent history changes
are not read under a transactional snapshot, so stable numbering during concurrent
inserts/deletions remains outside this fix.
