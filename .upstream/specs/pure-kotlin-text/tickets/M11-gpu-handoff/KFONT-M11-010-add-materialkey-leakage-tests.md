---
id: "KFONT-M11-010"
title: "Add `MaterialKey` leakage tests"
status: "done"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M11-006", "KFONT-M11-007"]
legacy_gate: null
---

# KFONT-M11-010 - Add `MaterialKey` leakage tests

## PM Note

Ce ticket protège le cache pipeline: les coordonnées atlas et glyph IDs ne doivent pas devenir identité matériau.

## Problem

Text payloads include many pass-local facts: atlas rectangles, glyph IDs, atlas generations, entry refs, upload tokens, resource handles, and binding refs. Those facts are required for drawing, but they must not enter `MaterialKey` or pipeline/material cache identity unless they affect layout or shader code. Without leakage tests, every glyph batch can fragment material caches or make pipeline keys nondeterministic.

## Scope

- Add tests that compare `MaterialKey` preimages for text draws with identical material/shader/color facts but different glyph IDs, atlas coordinates, atlas generations, upload tokens, and resource handles.
- Assert that atlas/page/entry refs live in `GPUTextBinding`, `GPUTextResourcePlan`, or pass-local payloads, not in `MaterialKey`.
- Emit `text-material-key-leakage-report.json` with material preimage, forbidden text fields, binding payload refs, and diagnostics.
- Validate allowed key axes: render step layout facts, material descriptor, blend/color plan, clip/depth/stencil state where applicable, and pipeline-layout-affecting resource topology.
- Add negative fixtures proving leaks are detected.

## Non-Goals

- Do not remove legitimate layout-affecting or code-affecting specialization axes from pipeline keys.
- Do not implement new batching heuristics.
- Do not place glyph identity in a hashed string field to bypass the test.
- Do not change M9 artifact key definitions.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`

## Design Sketch

```kotlin
data class MaterialKeyLeakageCase(
    val caseId: String,
    val baselineMaterialKey: MaterialKeyPreimage,
    val variedTextBinding: GPUTextBinding,
    val expectedMaterialKey: MaterialKeyPreimage,
    val forbiddenFields: List<TextMaterialForbiddenField>,
)

data class TextMaterialForbiddenField(
    val fieldPath: FieldPath,
    val reason: String,
)
```

## Acceptance Criteria

- [ ] Changing glyph IDs, atlas UVs, atlas entry refs, atlas generations, live handles, or upload tokens does not change `MaterialKey`.
- [ ] Changing actual material/color/blend/shader facts does change `MaterialKey` when those facts are material identity.
- [ ] Forbidden field leaks fail with a diagnostic that names the field path and text subrun.
- [ ] `GPUTextBinding` and resource plan dumps retain pass-local glyph/atlas facts outside material identity.
- [ ] The leakage report is deterministic and references M11 subrun/resource plan fixtures.

## Required Evidence

- `text-material-key-leakage-report.json` fixtures for glyph ID variance, atlas coordinate variance, generation variance, upload token variance, and legitimate material color variance.
- Negative fixture where atlas UVs or glyph IDs are intentionally inserted into `MaterialKey` and rejected.
- Diff/stat evidence showing binding/resource payloads still carry required atlas facts outside the material key.

## Fallback / Refusal Behavior

- Material-key leakage refuses route promotion until the key preimage is corrected.
- The renderer must not fix cache churn by dropping glyph/atlas validation facts.
- CPU texture fallback remains forbidden for material-key conflicts.

## Dashboard Impact

- Expected row: `Text MaterialKey leakage tests`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless leakage fixtures prove material identity is separate from text resource payloads.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*MaterialKey*Text*'
```

## Status Notes

- `proposed`: Final M11 cache-identity guardrail before route promotion and batching claims.
- Move to `ready` only after allowed/forbidden key axes are reviewed.
- `blocked` (2026-06-16): Readiness audit confirmed this ticket depends on
  `KFONT-M11-006` and `KFONT-M11-007`. There is not yet a `GPUTextBinding`,
  `GPUTextResourcePlan`, text `MaterialKey` preimage, or fixture proving that
  glyph IDs, atlas UVs, atlas generations, upload tokens, and live handles stay
  out of material identity. Remaining gate: finish subrun and
  resource/binding contracts, then re-review allowed and forbidden key axes.
- `blocked` (2026-06-23): `KFONT-M11-006` now supplies subrun IDs and split
  evidence, and `KFONT-M11-007` is ready but not implemented. This ticket
  stays blocked until `GPUTextBinding`, `GPUTextResourcePlan`, and text
  material preimage fixtures exist for leakage validation.
- `ready` (2026-06-23): `KFONT-M11-007` now lands `GPUTextBinding` and
  `GPUTextResourcePlan` evidence with explicit `MaterialKey` excluded fields
  for glyph IDs, atlas rects, atlas generations, upload tokens, and live
  texture handles. This ticket can now add the full material preimage leakage
  validation and negative fixtures.
- `ready` (2026-06-23): `KFONT-M11-008` now links upload tokens, atlas
  generations, resource refs, and draw refs through deterministic ordering
  trace evidence. The full leakage tests can now prove those facts remain
  resource/order identity, not material identity.
- `ready` (2026-06-23): `KFONT-M11-009` now adds route-specific WGSL
  reflection and validation reports for the accepted A8 text mask route,
  including binding layout hashes, instance layout expectations, ordering token
  references, and non-promotional refusal diagnostics. This ticket remains the
  next M11 gate for proving glyph IDs, atlas coordinates/generations, upload
  tokens, and live handles stay out of `MaterialKey`.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
