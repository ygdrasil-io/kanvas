---
id: KGPU-M32-018
title: "Legacy decommission: text-glyphs port (A8 text fill) / refuse (color/SDF/emoji text)"
<<<<<<< HEAD
status: done
=======
status: review
>>>>>>> master
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M32-001, KGPU-M6-002]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-018 - Legacy decommission: text-glyphs port (A8 text fill) / refuse (color/SDF/emoji text)

## PM Note

Le texte A8 (glyphes atlas en niveaux de gris) est porté sur le bridge Kanvas et
prouvé GPU↔CPU ; le texte couleur, SDF et emoji reste refusé et dépend de
KGPU-M6-002 (pipeline de police/couleur). Le PM suit ce ticket car il corrige
aussi l'ancienne affirmation périmée qui marquait `DrawTextRun` comme « refusé ».

## Problem

`GpuRendererLegacyRouteFamily.text-glyphs` is a partial family (decision matrix
row 9):

- A8 `DrawTextRun` is dispatched at `Surface.kt:152,432-466` via
  `TextRunDispatchPlanner` (`kanvas/.../TextRunDispatch.kt:91-148`) →
  `drawFullscreenTextureUniformPass` with `TextAtlasGlyphWgsl` — **ported**, with
  A8 GPU↔CPU parity proven in
  `kanvas/src/test/kotlin/org/graphiks/kanvas/TextGpuEvidenceMain.kt` (prints
  "PASS real GPU A8 text pixels with CPU parity").
- Color / SDF / emoji text is **refused**: the planner requires a `SolidColor`
  material + atlas glyph plan.

Note: the stale KGPU-M31-005 evidence marked `DrawTextRun` as "refuse" — that is
incorrect; A8 text landed post-M31-005 (matrix concern #5). This ticket binds the
A8 port to the family and formalizes the color/SDF/emoji refusal.

## Scope

Ported sub-cases (route_kind GPUNative):

- A8 atlas glyph text fill dispatched on the Kanvas bridge with real GPU↔CPU
  pixel parity (A8 atlas planner + CPU oracle).

## Non-Goals

- Do not port color / SDF / emoji text here.
- Do not add a short-lived color/SDF/emoji text substitute (per AGENTS.md:
  font/color gaps are dependency-gated until real deliveries land; refused
  sub-cases stay formal refusals linked to KGPU-M6-002).
- Do not add hidden CPU-rendered text texture compatibility.
- Color/SDF/emoji text emits a stable `refuse:text:unsupported_glyph_kind:<Kind>`
  diagnostic; it must not be silently served.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 9)

## Graphite Algorithm References

- n/a — not required for this slice (A8 atlas glyph dispatch already exists;
  color/SDF glyph algorithm study belongs to KGPU-M6-002).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative" (A8) / "RefuseDiagnostic" (color/SDF/emoji)
    val dumpRefs: List<String>,       // kanvas/src/test/.../TextGpuEvidenceMain.kt
    val diagnostics: List<String>,    // refuse:text:unsupported_glyph_kind:ColorBitmap, ...:SDF, ...:Emoji
)
```

## Acceptance Criteria

- [ ] A8 atlas glyph text fill dispatched on the Kanvas bridge with real GPU↔CPU
      pixel parity (CPU oracle), evidenced under `reports/gpu-renderer/` or via
      the existing `TextGpuEvidenceMain` proof.
- [ ] Color / SDF / emoji text emits a stable
      `refuse:text:unsupported_glyph_kind:<Kind>` diagnostic; a hermetic
      regression test asserts it and links KGPU-M6-002.
- [ ] No short-lived color/SDF/emoji text substitute is introduced (per
      AGENTS.md).

## Required Evidence

- A8 text already satisfied (independent review still required): A8 atlas glyph
  dispatch in `kanvas/src/main/kotlin/org/graphiks/kanvas/TextRunDispatch.kt`
  (TextRunDispatchPlanner + TextRunCpuOracle) and GPU↔CPU parity proof in
  `kanvas/src/test/kotlin/org/graphiks/kanvas/TextGpuEvidenceMain.kt`
  ("PASS real GPU A8 text pixels with CPU parity").
- Color/SDF/emoji port: font/color pipeline + independent CPU parity — **not
  produced yet** (refused; dependency-gated; tracked by KGPU-M6-002).
- Hermetic refuse regression test — **not produced yet**.

## Fallback / Refusal Behavior

- Color / SDF / emoji text emits a stable
  `refuse:text:unsupported_glyph_kind:<Kind>` diagnostic; no silent CPU-rendered
  text fallback.
- The `gpu-raster legacy` gate remains visible for the refused glyph kinds until
  KGPU-M6-002 delivers the font/color pipeline with parity evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.text-glyphs`
- Expected classification: `ImplementationCandidate` (A8 text) +
  `DependencyGated` (color/SDF/emoji)
- Claim promotion allowed: no, unless Required Evidence is attached and reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=a8-text-run -PsceneOutput=build/gpu-renderer-scenes/a8-text-run.png
rtk ./gradlew --no-daemon :kanvas:test --tests "*ColorTextRefuse*"
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 9 (partial — port A8
  text / refuse color/SDF/emoji). A8 GPU↔CPU parity already proven
  (`TextGpuEvidenceMain.kt`); kept `proposed` pending independent review. The
  stale KGPU-M31-005 "DrawTextRun refuse" claim is superseded (matrix concern
  #5). Color/SDF/emoji remainder refused and dependency-linked to KGPU-M6-002.
  No new evidence produced here.
- 2026-06-26 (Phase 2.B(ii), still `proposed`): added a hermetic text **clip**
  refuse test — a `DrawTextRun` with a non-`WideOpen`/`DeviceRect` clip refuses
  `unsupported_clip:<ClipKind>` via `TextRunDispatchPlanner.plan` (no GPU). The
  non-solid text **material** refuse is already covered by
  `TextRunDispatchTest.planner refuses a non-solid text material` (referenced,
  still green). Tests: `ClipRefuseTest`. Report:
  `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`. Color/SDF/emoji port
  remains dependency-gated (KGPU-M6-002).
- 2026-06-26 (Phase 2.C, still `proposed`): port-evidence consolidation. The
  **A8 text fill** sub-case is proven as **GPU↔CPU-oracle parity**
  (`TextGpuEvidenceMain.kt`, PASS) — note this is CPU-parity, **NOT** bridge↔legacy
  GPU pixel parity (strictly weaker than the rect/rrect/path evidence).
  Color/SDF/emoji refused. See
  `reports/gpu-renderer/2026-06-26-m32-port-evidence.md` §KGPU-M32-018.
  Documentation-only; no new evidence; independent review still owed.


- `review` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.
<<<<<<< HEAD
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
