# Memora 0.9.49 — Memory Tree / stage 2

Base: exact 0.9.48 source ZIP (Git blob 8f4d2d11efa2d8cbf36885984b0aacabfbb48028).
Cumulative candidate: extract ZIP, apply stage1/overlay, then stage2/overlay, then later overlays in numeric order. Version remains 0.9.49 / code 70 until final release. Do not run any new build until stage 5.

Stage 2 changes:
- Existing Home header, short/long cards, day trace card and navigation remain. The old tagline is removed. Home can scroll on constrained screens.
- Compose vector scene with six stages; growth factor based on total original memories, not on current canopy size. Up to 180 deterministic leaf glyphs, three source-specific colors, 4 moving canopy glyphs, 5 accessible ground slots.
- Scalar SQL projection of tree counts and five pending leaf identities; no full note or image payload used by the tree.
- Antique-style plaque and glass/brass jar with real counts and a bounded filling illustration. Light and dark palette, growth and opening animation, 2600 ms falling animation, restrained glow. Existing pending leaves on first load are shown without replay.
- Lifecycle RESUMED gating, system animator-disabled handling, 10 Hz ambient drawing on a separate layer; no continuous redrawing of the entire canopy. Animation is presentation only; never updates DB state.
- Stage 2 uses programmatically drawn vector artwork, not the photorealistic reference image as a background. Visual fidelity and small-screen/accessibility/device performance must be verified at the final stage.
- Existing reminder card is temporarily retained for reminders not represented by tree leaves. Note leaf opens existing reader; photo leaf currently opens the album, not a specific photo. The occurrence-aware reader/return, durable scheduling and jar action are still stages 3 and 4. No fake completed behavior or extra scheduler added.
- Scene geometry unit tests added. Android compilation and instrumentation are intentionally deferred to stage 5. The existing stage-1 CI is not a stage-2 build.
