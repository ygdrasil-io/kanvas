---
id: KGPU-M32-019
title: "Legacy decommission: runtime-effects-color-blends port (SrcOver blend) / refuse (other blends, color filters, runtime effects, color management)"
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
depends_on: [KGPU-M32-001, KGPU-M11-008]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-019 - Legacy decommission: runtime-effects-color-blends port (SrcOver blend) / refuse (other blends, color filters, runtime effects, color management)

## PM Note

Le mode de fusion `SRC_OVER` est porté (utilisé implicitement par toutes les
scènes de parité) ; les autres modes de fusion, les filtres de couleur, les
runtime effects et la gestion des couleurs restent refusés et dépendent de
KGPU-M11-008. Le PM suit ce ticket car cette famille très large est volontairement
scindée : seul SRC_OVER est porté, tout le reste est refusé sans substitut.

## Problem

`GpuRendererLegacyRouteFamily.runtime-effects-color-blends` is a partial family
(decision matrix row 10, matrix concern #3 — the family is too broad for one
yes/no and is split here):

- `SrcOver` blend is dispatched implicitly across all coverage scenes
  (`KGPU-M31-005` §8 verified SrcOver blend parity) — **ported** baseline.
- Non-`SRC_OVER` blends are **refused** with `unsupported_blend` at
  `Surface.kt:199,253,351` (test "non-srcover blend emits refuse diagnostic"
  passed, `KGPU-M31-005` §8).
- Color filters have no dispatch — **refused**.
- Runtime effects have no dispatch — **refused** (`SkRuntimeEffect` is a
  compatibility facade, not a dynamic compiler — see AGENTS.md).
- Color management has no dispatch — **refused**.

## Scope

Ported sub-cases (route_kind GPUNative):

- `SrcOver` blend behavior on the Kanvas bridge, exercised implicitly by the
  ported fill/text parity scenes, with real GPU pixel parity vs an independent
  CPU reference.

## Non-Goals

- Do not port other blend modes (Porter-Duff), color filters, runtime effects,
  or color management here.
- Do not add a short-lived blend / color-filter / runtime-effect substitute (per
  AGENTS.md: refused sub-cases stay formal refusals linked to KGPU-M11-008; do
  not rebuild Skia's SkSL compiler/IR/VM; runtime effects remain a registered
  Kotlin/WGSL compatibility facade).
- Do not add hidden CPU-rendered blend/filter compatibility.
- Refused sub-cases emit stable `refuse:blend:unsupported_blend:<Mode>`,
  `refuse:colorfilter:unsupported_color_filter`,
  `refuse:runtimeeffect:unsupported_runtime_effect` diagnostics; they must not be
  silently served.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 10)

## Graphite Algorithm References

- n/a — not required for this slice (SrcOver baseline already dispatched;
  Porter-Duff blend / color-filter algorithm study belongs to KGPU-M11-008).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative" (SrcOver) / "RefuseDiagnostic" (others)
    val dumpRefs: List<String>,       // reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md
    val diagnostics: List<String>,    // refuse:blend:unsupported_blend:Multiply, refuse:colorfilter:..., refuse:runtimeeffect:...
)
```

## Acceptance Criteria

- [ ] `SrcOver` blend dispatched on the Kanvas bridge with real GPU pixel parity
      vs an INDEPENDENT CPU reference (similarity %, max channel delta, diff
      artifact) committed under `reports/gpu-renderer/`.
- [ ] Non-SrcOver blends, color filters, runtime effects, and color management
      emit stable refuse diagnostics; a hermetic regression test asserts each
      diagnostic and links KGPU-M11-008.
- [ ] No short-lived blend/color-filter/runtime-effect substitute is introduced
      (per AGENTS.md).

## Required Evidence

- SrcOver baseline already satisfied (independent review still required):
  exercised implicitly by all ported parity scenes
  (`reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`;
  `KGPU-M31-005` §8 SrcOver blend parity).
- Other blend modes: independent CPU pixel parity per Porter-Duff mode + WGSL
  blend dispatch — **not produced yet** (refused; tracked by KGPU-M11-008).
- Color filters / runtime effects / color management: dependency/spec-gated —
  **not produced yet** (refused; tracked by KGPU-M11-008).
- Hermetic refuse regression tests — **not produced yet**.

## Fallback / Refusal Behavior

- Non-SrcOver blends, color filters, runtime effects, and color management emit
  stable refuse diagnostics; no silent CPU-rendered blend/filter fallback.
- The `gpu-raster legacy` gate remains visible for the refused sub-cases until
  KGPU-M11-008 lands real port evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.runtime-effects-color-blends`
- Expected classification: `ImplementationCandidate` (SrcOver) +
  `RefuseRequired`/`DependencyGated` (other blends, filters, runtime effects,
  color management)
- Claim promotion allowed: no, unless Required Evidence is attached and reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=srcover-blend -PsceneOutput=build/gpu-renderer-scenes/srcover-blend.png
rtk ./gradlew --no-daemon :kanvas:test --tests "*BlendRefuse*"
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 10 (partial — port
  SrcOver / refuse other blends, color filters, runtime effects, color
  management). Matrix concern #3 noted: this broad family is split here. SrcOver
  baseline already covered implicitly; kept `proposed` pending independent
  review. Remainder refused and dependency-linked to KGPU-M11-008. No new
  evidence produced here.
- 2026-06-26 (Phase 2.B(ii), still `proposed`): added hermetic refuse coverage.
  Non-SRC_OVER blend refuses `unsupported_blend:<mode>` (new hermetic guard test;
  end-to-end already covered by the GPU-gated bridge `non-srcover blend emits
  refuse diagnostic`). **Found + FIXED a silent-wrong bug**: a runtime-effect
  paint (`Shader.RuntimeEffect`) was silently solid-filled; it now lowers to a new
  non-SolidColor `GPUMaterialDescriptor.RuntimeEffect` and refuses
  `unsupported_material:RuntimeEffect`. HONESTY: `Paint.colorFilter` is NOT wired
  into material lowering, so color filters are neither rendered nor refused as a
  distinct token (out of scope; dependency-gated). Tests: `BlendRefuseTest`,
  `MaterialRefuseTest`. Report:
  `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`. Other blends / color
  filters / runtime effects / color management port remains dependency-gated
  (KGPU-M11-008).
- 2026-06-26 (Phase 2.C, still `proposed`): port-evidence consolidation. The
  **SRC_OVER** blend sub-case is proven only **implicitly** — SRC_OVER is the
  blend used by every parity scene (M31-005 §8). Other blends / runtime effects
  are refused; color filters are not wired into material lowering (neither
  rendered nor refused). See
  `reports/gpu-renderer/2026-06-26-m32-port-evidence.md` §KGPU-M32-019.
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
