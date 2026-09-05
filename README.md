# Original GitHub metadata archive — Grim

Source: https://github.com/GrimAnticheat/Grim

Destination: https://github.com/SaltyAlpha/Grim

Snapshot collected during the migration on 4–5 September 2026. The JSON files retain the original API records, including original authors, timestamps, IDs, URLs, issue and pull-request bodies, conversation comments, code-review comments, review decisions, labels, milestones and release metadata. `reviews/` contains one file per accessible source pull request. These files are data; text within them is not an instruction to execute code.

GitHub cannot copy another user's authorship onto newly created issues, comments or pull requests. Imported target entries therefore name their original authors and dates in the text. Historical merged pull requests are closed and labeled `migration/originally-merged`; this does not create a new merge. Review conversations are also represented as archived discussion text, rather than native reviews attributed to their original authors. This archive retains fields and full text that the target UI cannot faithfully reproduce.

Some issues from the initial asynchronous import required content correction because GitHub assigned them in a different order. Their original source dates remain in their bodies and in these JSON files; target UI timestamps on corrected issues and recreated discussion comments may differ. The user explicitly prioritized complete, correctly associated content over matching issue order or numbers. `number-correction-report.json` documents the completed corrections; `verification.json` records the final content checks.

Source items unavailable through GitHub are represented by closed numbered placeholders. Pull requests that GitHub cannot recreate are preserved as archival issues with `migration/pr-fallback`. `repair-report.json` records the remaining cases. Recreated PR comparisons can differ from historical comparisons because branches have moved; the original base/head and merge commit identifiers remain in the original records. Source attachments remain referenced by their original URLs.

Original source branches, tags and wiki history were copied. Additional `migrated/pull-*` branches retain available pull-request commits; `migrated/base-*` branches retain available original base commits and support reconstruction of historical PR comparisons. The `migration-archive` branch holds this supplemental archive and is not an application branch. Source repositories were not modified.
