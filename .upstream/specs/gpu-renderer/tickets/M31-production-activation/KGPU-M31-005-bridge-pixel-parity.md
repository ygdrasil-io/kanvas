---
id: KGPU-M31-005
title: "SkCanvas-bridge ↔ legacy gpu-raster pixel/GM parity (blocks M30-003/M31-003 sign-off)"
status: in-progress
milestone: M31
priority: P0
owner_area: product-validation
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: true
adapter_required: true
depends_on: [KGPU-M30-003, KGPU-M31-003, KGPU-M31-006]
legacy_gate: gpu-raster-legacy-path
---

# KGPU-M31-005 - SkCanvas-bridge ↔ legacy gpu-raster pixel/GM parity (blocks M30-003/M31-003 sign-off)

## PM Note

Le bridge `SkCanvas → KanvasCanvas` est activé par défaut en production (M30-002
+ M31-001), mais **aucune preuve de parité PIXEL** n'existe entre la sortie du
bridge Kanvas et le chemin legacy `gpu-raster` (Skia/`SkWebGpuDevice`). Les
« parity tests » actuels ne comparent que le **nombre de tâches enregistrées**,
pas l'image produite. Ce ticket exige une vraie comparaison image-à-image avant
de signer la production. Tant qu'il n'est pas fait, M30-003 et M31-003 ne doivent
pas passer `done`.

## Problem

Surfaced by the 2026-06-25 independent review of M30/M31 (PRs #1882 / #1883):

- `kanvas-skia-bridge/.../SkiaBridgeParityTest.kt` (KGPU-M30-003) asserts only
  `frame.recording.taskList.tasks.size == N` / `isNotEmpty()` — i.e. **structural
  task-count equivalence**, not pixel output. Its `pixelDiff` field is never
  populated.
- `reports/gpu-renderer/2026-06-25-M31-003-evidence.md` (the production-readiness
  PM bundle) nonetheless states *“Render snapshots → ✅ Task-level parity verified”*
  and *“parity verified for all 5 draw families”*, then defers *“Full GPU pixel
  comparison … to execution pipeline completion”* (already deferred once from
  M30-003 → M31-003).
- Kanvas is now the production **default** renderer (`isKanvasRendererEnabled()`
  default-on; `productActivation=true`) with **no evidence** that its output is
  visually equivalent to the legacy `gpu-raster` / Skia reference.

Activating a renderer as the production default without visual-equivalence proof
contradicts the validation discipline (`07-validation-conformance.md`, AGENTS.md:
no “supported” without reference/CPU-GPU evidence).

**Update (2026-06-25 feasibility check):** the bridge path is **record-only** —
`:kanvas` `Surface.flush()` returns a `GPURecording` that is never executed to
pixels (verified: `Frame` exposes no pixels; nothing in `:kanvas`/bridge/integration
consumes the recording). The bridge therefore produces **no pixel output**, so pixel
parity is **impossible to measure** until the GPU execution-to-pixels path lands.
This ticket is consequently **blocked on KGPU-M31-006** (execute the KanvasSurface
recording to pixels). It also confirms the production-default activation
(M30-002/M31-001) is currently a **pixel-level no-op** for this path.

## Scope

- Render a representative GM / scene set covering the 5 supported bridge draw
  families (drawRect, drawRRect, drawPath, drawImage, drawTextBlob) and the
  blend modes through BOTH paths: (a) the Kanvas `SkiaKanvasSurface` bridge and
  (b) the legacy `gpu-raster` (`SkWebGpuDevice`) / a Skia raster reference.
- Decode both outputs to RGBA and diff them with a documented per-channel
  tolerance + similarity threshold (reuse the `:cpu-raster` `TestUtils.compareBitmapsDetailed`
  pattern used by the M28 offscreen harness where applicable).
- Commit per-family pixel-diff reports (similarity %, max channel delta, diff
  image) as evidence.
- For any family/mode that cannot reach parity, mark it unsupported on the bridge
  with a stable `RefuseDiagnostic` — it must not be silently served by the
  production-default path.
- Once real parity evidence exists, correct the “parity verified” wording in the
  M31-003 evidence and allow M30-003 / M31-003 to move to `done`.

## Non-Goals

- Not the M28 offscreen-scene parity harness (that validates `GPURendererScene`
  output, a different path than the `SkCanvas` bridge).
- No performance benchmarks (M23 / future).
- No new draw families or scenes.

## Spec Sources

- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/tickets/M30-skia-wrapper-legacy-retirement/KGPU-M30-003-regression-parity-tests.md`
- `.upstream/specs/gpu-renderer/tickets/M31-production-activation/KGPU-M31-003-pm-evidence-bundle.md`
- `reports/gpu-renderer/2026-06-25-M31-003-evidence.md`

## Design Sketch

```kotlin
// For each GM: render via both backends, decode to RGBA, diff.
val kanvas = renderViaBridge(gm)          // SkiaKanvasSurface.bridge.draw...
val legacy = renderViaLegacy(gm)          // SkWebGpuDevice / Skia raster reference
val cmp = TestUtils.compareBitmapsDetailed(kanvas, legacy, tolerance = ...)
require(cmp.similarity >= MIN_SIMILARITY) { "bridge↔legacy parity below threshold for ${gm.id}: $cmp" }
```

## Acceptance Criteria

- [ ] Each supported bridge draw family (rect, rrect, path, image, textBlob)
      compared pixel-for-pixel: Kanvas bridge vs legacy/Skia reference, within a
      documented tolerance + similarity threshold.
- [ ] Blend-mode coverage compared at the pixel level (not just task-count).
- [ ] Per-family pixel-diff report committed (similarity %, max channel delta,
      diff artifact).
- [ ] Any family/mode failing parity is marked unsupported with a stable
      `RefuseDiagnostic`; the production-default path does not silently serve it.
- [ ] The “parity verified” wording in the M31-003 evidence is corrected to
      “structural / task-level coverage” until this real parity lands.
- [ ] M30-003 and M31-003 are promoted to `done` only after this parity evidence
      is attached and reviewed.

## Required Evidence

- Per-family pixel-diff report (similarity, max channel delta) under `reports/gpu-renderer/`.
- Diff images / artifacts for at least one representative GM per family.
- Documented tolerance + threshold rationale.
- List of any families/modes refused for lack of parity (with diagnostic code).

## Fallback / Refusal Behavior

A draw family without proven pixel parity must NOT be activated as the production
default silently. It emits a stable `RefuseDiagnostic` and either rolls back to
the legacy path for that family or is excluded from the bridge route. Missing
parity blocks promotion of M30-003 / M31-003 / the production sign-off.

## Dashboard Impact

- Expected row: `gpu-renderer.m31.bridge-pixel-parity`
- Expected classification: `PromotedSupported` (after acceptance)
- Claim promotion allowed: only after real pixel/GM parity evidence is attached
  and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test
# Once implemented: the bridge↔legacy pixel-parity comparison task / test
rtk git diff --check
```

## Status Notes

- `proposed`: Initial ticket — follow-up surfaced by the 2026-06-25 independent
  review of M30/M31. M30-003/M31-003 “parity” is structural task-count only; real
  pixel/GM parity is required before the production-default activation can be
  signed off.
- `in-progress`: M31-006 (GPU execution→pixels) done. FillRRect dispatch
  implemented:
  - `Surface.renderToRgba()` now dispatches `NormalizedDrawCommand.FillRRect`
    via `drawFullscreenRawUniformPass` with SDF-based coverage in WGSL
    (reuses `rrect_cov` from `RRectCoverageSnippet`).
  - Constraints: SolidColor material, Identity transform, Root layer,
    WideOpen/DeviceRect clip, uniform corner radii. Non-uniform radii
    or unsupported state emits stable `refuse:` diagnostic.
  - Pixel parity proven: `solid-rrect` scene (320×240, 220×140 rect with
    20px radii) → `similarity=100%`, `matching=76800/76800`,
    `maxDiff=(R=0,G=0,B=0,A=0)` at tolerance=1 (WGSL/Kotlin f32 rounding).
  - Next family: FillPath (blocked — stencil-cover or tessellator dispatch).

## Linear Labels

- `gpu-renderer`
- `milestone:M31`
- `area:product-validation`
