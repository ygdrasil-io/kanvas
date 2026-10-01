# W7 Faithful InverseClip GM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Restore Skia's inverseclip scene and measure its native fidelity without changing renderer admission.
**Architecture:** Use the existing public inverse-winding clipPath with AA, then the original blue full-viewport rectangle. Keep a separate architectural diagnosis for mixed ordinary/direct-inverse inventory.
**Tech Stack:** Kotlin/JVM, public Surface/GmCanvas, native wgpu/Metal.
**Spec:** Bounded design below, matching clean upstream Skia `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`, `gm/inverseclip.cpp`, read in full. The provenance SHA of the reference PNG is not inferred from this clone.

## Global Constraints

- Worktree `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branch `codex/w7-inverseclip-port`, base `1ca2bca5b82a4a4412bc3d1192d05f6eb3a265c8` (draft #2429).
- Product/test scope: InverseClipGm.kt and a new InverseClipGmSurfacePixelTest.kt only. No renderer, GmCanvas, math, registry, reference, threshold, composition-domain, budget, cap or exclusion changes.
- Fonts, external decoding/codecs and jpg-color-cube remain excluded. No CPU fallback, GM-name renderer routing, fake Picture or infrastructure/mock/forwarding/source-text tests.
- Controller alone runs Gradle/native/corpus, one bounded240s process group at a time, retaining actual handle until terminal. Workers run no builds/native and spawn no helpers.
- Implementer owns product/test edits; controller owns documents/evidence/generated artifacts/publication. RTK shell, apply_patch edits.
- Existing linked worktree is clean and prior publication is pushed; default cbf6 is untouched. Stack draft on #2429, no merge. Keep evidence archives.

## Design and diagnosis

The port currently substitutes a full-frame clipRect, blue draw and white inverse-path draw for upstream's inverse clipPath followed by blue draw. This is a proven semantic error, including exchanged interior/exterior colors. The cubic path and all dimensions are already the upstream constants; preserve them. Set explicit ClipOp.INTERSECT and antiAlias=true; remove the substitute white draw and unnecessary full-frame clipRect.

SkiaGmRenderer records an opaque white non-AA viewport rectangle before gm.onOnceBeforeDraw and gm.draw. The regression reproduces that public setup directly; Surface alone is not assumed white. Use the actual GM, not copied geometry or a fake Canvas. The original GM uses AA clipping but a non-AA blue paint; make the latter explicit.

The current refusal is invalid.native-core-primitive.w4e-resource. Read-only source diagnosis suggests a distinct mixed ordinary/direct-inverse inventory classification gap, but its exact failing runtime conjunct is unobserved. Correcting this port does not close that architectural gap. Follow-up will compare single inverse draw, ordinary+inverse draw and faithful inverse clip through public Surface before any guard/authority redesign.

## Review Focus

- Inverse versus ordinary clip: white interior and blue exterior on multiple sides.
- Correct untouched background: same white setup as the real GM runner, never an implicit Surface default.
- AA retained: at least one genuinely partial blue/white coverage pixel; no edge count/position guessed from output.
- Actual native execution and clean repeat: Render/Readback, positive dispatch, no refusals/diagnostics, byte-identical fresh second Surface render, no capability skip.
- No accidental scope expansion: exact upstream geometry/colors; mixed-direct inventory refusal remains a separate issue, not removed to make the GM pass.

### Task 1: Restore the real inverse clip with public native pixels

**Files:**
- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/clip/InverseClipGm.kt`.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/InverseClipGmSurfacePixelTest.kt`.

**Interfaces:** Surface(gm.width,gm.height,config=gm.compositionConfig()), Surface.canvas/render, GmCanvas, gm.onOnceBeforeDraw/draw, nativeEvidenceScopeKinds/stats/diagnostics. Follow the existing ClipGmPortSurfacePixelTest evidence/repeat/dispose pattern, adding the runner's white rectangle setup explicitly. No shared helper refactor.

- [ ] Write `inverseClipPreservesWhiteInteriorAndBlueExterior` before port edits. Record white viewport Paint(ColorARGB.White,antiAlias=false), call actual InverseClipGm through GmCanvas and render Surface. @AfterEach GPUBackendRuntimeFactory.dispose; no GpuAvailability or conditional skip.
- [ ] Assert literal RGBA white[255,255,255,255] at(195,197),(195,100),(195,300),(100,197),(300,197), and blue[0,0,255,255] at(0,0),(399,399),(0,197),(399,197),(195,0),(195,399). These are far from the pinned cubic boundary; no product helper or reference PNG computes expectations.
- [ ] Assert a partial-coverage pixel exists: some pixel has red=green in1..254, blue=255, alpha=255. This is a semantic AA witness, not an exact edge/color-space oracle. Assert Render+Readback evidence, positive dispatched count, zero refused and empty diagnostics.
- [ ] Render the same actual GM on a fresh second Surface with the same explicit background, assert native evidence/clean stats and full byte equality.
- [ ] Freeze tests only; report READY_FOR_RED. Controller runs `:integration-tests:skia:test --tests '*InverseClipGmSurfacePixelTest'` in the standard private wrapper/init. Expected causal native W4e inventory refusal or wrong inside/outside pixels; compilation failure is not RED. Await authorization before port edits.
- [ ] After verified RED, replace full-frame clipRect with explicit `canvas.clipPath(clip, ClipOp.INTERSECT, antiAlias=true)`; retain blue full400x400 rectangle with antiAlias=false; remove inverse white draw. Keep path/constants/metadata/domain/tolerance/minSimilarity unchanged. Adjust KDoc only if needed.
- [ ] Freeze/self-review, wait controller GREEN and covering controls. If any assertion fails, report evidence before changing anything; no expected-pixel or AA relaxation.
- [ ] After controller validation, commit only the two product/test files. Write report with causal RED/GREEN, exact commands/results supplied by controller, self-review/concerns. No builds/reviewer delegation.
- [ ] Sol task review; same implementer handles any fix and scoped re-review follows.

## Controller qualification and publication

- [ ] Run new1 + prior GM13 + W7ClipProducerScissor27 =41unique targeted tests, no skip/abort; keep renderer-global evidence at be813afd7, not relabelled as fresh port-SHA global.
- [ ] Regenerate only inverseclip by exact gm.name; run SkiaGmRunner by exact kanvas.gm.name. Verify only one PNG/score entry plus generated timestamp change. Do not reuse sorted parity indices for registry selectors.
- [ ] Full final-source-SHA corpus631/443, serial slices0–607/607–608/608–631, strict existing aggregator versus clip-gm-ports-6e0fca2df.json. Preserve18invariants/presence; inspect every new/changed image/metric and all old-render stability.
- [ ] Independent final Astra review with one source package and final evidence delta; correct/consolidate findings. Publish/attach draft stacked on codex/w7-clip-gm-ports (#2429).
- [ ] Carry mixed-direct inverse inventory investigation into the next architectural lot with the exact unresolved predicate; do not claim it solved by faithful GM replacement. W7 remains active.

## Self-review and delegated decision

One bounded existing-scene repair with one public test; every source change maps to the pinned Skia operations. Interior/exterior, background, AA, native and repeat contracts all have explicit witnesses. Controller uses the user's carte blanche for W7 design/plan decisions and retained SDD execution, without a new approval loop. The ledger is still needed for this long-running work even though this is a bounded design, not a new subsystem.
