# Memora tree, stage 1: 0.9.49 candidate

Base: Memora 0.9.48 ZIP, blob 8f4d2d11efa2d8cbf36885984b0aacabfbb48028.
Overlay is applied only to a fresh extraction. The existing main branch is not altered.
Room 10 -> 11 adds a permanent recall occurrence ledger, separate from the existing 120-day operational reminder cache. Source rows remain authoritative, no duplicated note or photo body.
NOTE:<id> and PHOTO:<id> distinguish sources; short/long type is derived from notes, so a type edit does not duplicate history. Legacy read history is imported as returned historical occurrences (completion time unknown); one oldest outstanding legacy delivery per source is preserved.
No new scheduler. The repository exposes idempotent delivery, current-key uniqueness, five visible items, permanent reactions and impressions, counts, history, and return-all. Existing scheduler, UI callbacks, backup and restore must be connected in stages 3-4; no APK is claimed ready. Existing album-capture prompts are not recall events.
Delete triggers remove history when source rows are permanently deleted, consistent with the current hard-delete implementation. Backup/restore of tree history is still required before a production release.
