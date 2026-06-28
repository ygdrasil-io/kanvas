---
id: KGPU-M32-016
title: "Legacy decommission: images-bitmap-codecs-uploads formal refusal (dependency-gated)"
status: done
milestone: M32
priority: P1
owner_area: legacy-cleanup
claim_impact: DependencyGated
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M32-001, KGPU-M11-004]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-016 - Legacy decommission: images-bitmap-codecs-uploads formal refusal (dependency-gated)

## PM Note

Les images, shaders bitmap, codecs et uploads de texture n'ont aucun chemin GPU
sur le bridge Kanvas : `DrawImage` est déjà refusé. Ce ticket formalise le refus
avec un test de régression et renvoie le portage à KGPU-M11-004, bloqué par les
livraisons de codecs/textures. Le PM suit ce ticket pour savoir que les images
ne sont pas supportées et qu'aucun substitut temporaire ne sera ajouté.

## Problem

`GpuRendererLegacyRouteFamily.images-bitmap-codecs-uploads` is a **full refuse**,
dependency-gated family (decision matrix row 7). `DrawImage` is lowered to
`FillRect` with an `ImageDraw` material at `Canvas.kt:170-187`, and
`dispatchFillRect` already refuses it with
`refuse:...:unsupported_material:ImageDraw` at `Surface.kt:183`. No texture
upload or sampling dispatch exists. The port is blocked on codec / image-delivery
gaps and is owned by KGPU-M11-004.

## Scope

- Keep / harden the existing stable
  `refuse:image:unsupported_material:ImageDraw` diagnostic on the bridge for
  image / bitmap-shader / texture-upload draws.
- Add a hermetic regression test asserting the diagnostic and linking
  KGPU-M11-004 as the dependency-gated port owner.

## Non-Goals

- Do NOT add a short-lived image/texture substitute (per AGENTS.md:
  dependency-gated families must not get short-lived substitutes; treat
  font/codec gaps as dependency-gated until real deliveries land).
- Do not implement texture upload, bitmap sampling, or codec decode here.
- Do not add hidden CPU-rendered image texture compatibility (the previous
  silent solid-color rect was already removed in KGPU-M31-005).

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 7)

## Graphite Algorithm References

- n/a — not required for this slice (refuse-only; texture sampling / codec
  algorithm study belongs to KGPU-M11-004).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "RefuseDiagnostic"
    val dumpRefs: List<String>,       // hermetic refuse-test log
    val diagnostics: List<String>,    // refuse:image:unsupported_material:ImageDraw
)
```

## Acceptance Criteria

- [ ] Image / bitmap-shader / texture-upload draws emit a stable
      `refuse:image:unsupported_material:ImageDraw` diagnostic on the bridge (no
      silent solid-color rect, no silent drop).
- [ ] A hermetic regression test asserts the diagnostic and references
      KGPU-M11-004 as the dependency-gated port ticket.
- [ ] No short-lived image substitute is introduced (per AGENTS.md).

## Required Evidence

- The `refuse:...:unsupported_material:ImageDraw` diagnostic already exists
  (`Surface.kt:183`; refuse captured in KGPU-M31-005 status notes). A dedicated
  hermetic regression test binding it to this family — **not produced yet**
  (`proposed`).
- Dependency link to KGPU-M11-004 for the future image/codec/upload port.

## Fallback / Refusal Behavior

- Image routes emit a stable `refuse:image:unsupported_material:ImageDraw`
  diagnostic; silent CPU-rendered image compatibility is not allowed.
- The `gpu-raster legacy` gate remains visible for this family until KGPU-M11-004
  delivers codec/texture support with real parity evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.images-bitmap-codecs-uploads`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no — dependency-gated refuse; support is claimed only
  by a future KGPU-M11-004 port.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test --tests "*ImagesBitmapRefuse*"
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 7 (`refuse`,
  dependency-gated). ImageDraw refuse diagnostic already exists; a hermetic
  regression test binding it to this family is still owed. Port deferred to
  KGPU-M11-004 (blocked on codec / texture deliveries). No new evidence here.
- 2026-06-26 (Phase 2.B(ii), still `proposed`): added a hermetic kanvas-level
  ImageDraw refuse test (`unsupported_material:ImageDraw`), complementing the
  existing GPU-gated bridge `drawImage` test. **Found + FIXED a silent-wrong
  bug**: a bitmap image **shader** (`Shader.Bitmap`, bridge-reachable) was
  silently solid-filled; it now lowers to `ImageDraw` and refuses. Tests:
  `MaterialRefuseTest`. Report:
  `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`. Codec/upload port
  remains dependency-gated (KGPU-M11-004).


- `review` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
