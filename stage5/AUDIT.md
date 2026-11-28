# Final integration audit — Memora 0.9.49

Source: original user-provided 20-section Xotira daraxti concept, reviewed in the conversation. Source code is pinned to 0.9.48 ZIP and cumulative overlays 1–5. Main untouched.

| Concept | Code evidence | Verification still needed |
|---|---|---|
| 1 Philosophy | TreeScene / source keys | Visual acceptance |
| 2 Home | HomeScreen / MemoraApp | Screen layout, reference fidelity |
| 3 Three types | VisualCounts, geometry palette | Render, new-leaf motion |
| 4 Growth | TreeCounts and Geometry | Boundary tests, gradual appearance |
| 5 Motion and theme | TreeScene | Actual frame timing; thin branch motion not implemented individually |
| 6 Existing reminders | MemoryScheduler + atomic deliverPlanned | Alarm/reboot/device scheduling |
| 7 Pending | Unique currentKey + LIMIT 5 | Persistence/queue tests |
| 8 Open | Occurrence screen / original reader/photo | Navigation and media rendering |
| 9 Like | Occurrence-level persisted timestamp | Duplicate tests and UI |
| 10 Reflections | Ledger / reflection screen | Edit and history |
| 11 Completion | Explicit reader exit + arrival | Actual navigation animation |
| 12 Plaque | On-tree count | Layout |
| 13 Jar | Treasure count and click | Layout |
| 14 Treasure | Paging + deliveredAt sorting | Pagination/UI |
| 15 Return | Atomic SQL + bounded motion | Return/history tests |
| 16 History | Durable ledger, historical count | Persistence |
| 17 Accounting | SQL counts / invariants | 100–100k instrumentation |
| 18 Existing data | Room 10–11 migration, triggers, backup v8 | Migration and end-to-end export/import |
| 19 Performance | Scalar Home data, bounded glyphs/animation, paging | CPU/RAM/frame/battery physical-device measurements |
| 20 Non-punitive | No streak requirement or decay in tree | Acceptance |

Stage 5 fixes: recall-date (not completion-date) sorting; scalar photo count and existence instead of materializing album at Home; actual daily delivery cap across delayed plans; explicit historical count. Retained backup limits and conflict refusal. Full v8 file-level restore, reference fidelity and physical-device performance are NOT yet certified. Workflow success is required before sharing APK. CI uses stable existing GitHub DEBUG signing/package, not production Play signing. No fake 20/20 pass; tests and device review are separate gates.
