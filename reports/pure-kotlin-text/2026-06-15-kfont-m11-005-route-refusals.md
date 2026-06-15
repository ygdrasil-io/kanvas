# KFONT-M11-005 Route Refusals Evidence

Date: 2026-06-15
Status: implemented, pending review.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-005-wire-dependency-gated-diagnostics-for-unsupported-routes.md`

## Scope

This slice adds deterministic text GPU route refusal evidence only. It does not implement SDF, outline, color glyph, bitmap glyph, SVG glyph, A8 atlas, upload, binding, or draw execution routes.

## Files

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusals.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusalTest.kt`

## Evidence

- `defaultGPUTextRouteRefusalReport()` emits the fixture-equivalent `gpu-text-route-refusals.json` report through deterministic canonical JSON.
- The report covers SDF, outline, color glyph, bitmap glyph, SVG glyph, unregistered artifact, missing upload plan, stale artifact generation, unsupported SDF transform, and CPU-rendered full text texture refusal.
- Every refusal carries artifact type, attempted route, source text/glyph range, artifact key hash, blocker owner, handoff diagnostic, renderer diagnostic, legacy gates, and `claimPromotionAllowed=false`.
- `DependencyGated` appears only for `MISSING_RENDERER_CAPABILITY`; unregistered artifacts, missing upload plans, and stale generations stay `GPU-gated`; CPU-rendered full text texture stays `expected-unsupported`.
- Legacy gates remain traceable: `dftext`, `scaledemoji_rendering`, and `coloremoji_blendmodes`.

## Diagnostic Mapping

| Artifact type | Handoff diagnostic | Renderer diagnostic | Classification |
|---|---|---|---|
| `SDFGlyphAtlasArtifact` | `text.gpu.capability-missing` | `unsupported.text.sdf_route_unavailable` | `DependencyGated` |
| `OutlineGlyphPlan` | `text.gpu.capability-missing` | `unsupported.text.outline_route_unavailable` | `DependencyGated` |
| `ColorGlyphPlan` | `text.gpu.color-plan-unsupported` | `unsupported.text.color_plan_unsupported` | `DependencyGated` |
| `BitmapGlyphPlan` | `text.gpu.capability-missing` | `unsupported.text.bitmap_route_unsupported` | `DependencyGated` |
| `SVGGlyphPlan` | `text.gpu.SVG-plan-unsupported` | `unsupported.text.svg_plan_unsupported` | `DependencyGated` |
| `UnregisteredTextArtifact` | `text.gpu.artifact-unregistered` | `unsupported.text.artifact_unregistered` | `GPU-gated` |
| `GlyphUploadPlan` | `text.gpu.upload-plan-missing` | `unsupported.text.upload_plan_missing` | `GPU-gated` |
| `GlyphAtlasArtifact` | `text.gpu.atlas-generation-stale` | `unsupported.text.artifact_generation_stale` | `GPU-gated` |
| `SDFGlyphAtlasArtifact` | `text.gpu.transform-unsupported` | `unsupported.text.sdf_transform_unsupported` | `DependencyGated` |
| `CPURenderedTextTexture` | `text.gpu.CPU-rendered-texture-forbidden` | `unsupported.text.cpu_rendered_texture_forbidden` | `expected-unsupported` |

## TDD Evidence

Red result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*TextRouteRefusal*'
```

Result: failed as expected at `:font:gpu-api:compileTestKotlin` because `defaultGPUTextRouteRefusalReport`, `GPUTextRouteBlocker`, and refusal fields did not exist.

Green result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*TextRouteRefusal*'
```

Result: passed; `GPUTextRouteRefusalTest` executed 4 tests successfully.

## Remaining Gate

KFONT-M11-005 is diagnostic/refusal evidence only. Route rendering, A8 atlas proof, subrun splitting, resource/upload/instance/binding plans, WGSL reflection, CPU/GPU/reference evidence, and material-key leakage checks remain owned by later M11 tickets.
