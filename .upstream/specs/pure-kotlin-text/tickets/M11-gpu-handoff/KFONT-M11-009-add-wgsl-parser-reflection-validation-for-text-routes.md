---
id: "KFONT-M11-009"
title: "Add WGSL parser/reflection validation for text routes"
status: "ready"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M11-004", "KFONT-M11-007"]
legacy_gate: ["dftext", "coloremoji_blendmodes"]
---

# KFONT-M11-009 - Add WGSL parser/reflection validation for text routes

## PM Note

Ce ticket vérifie que les shaders texte et leurs bindings restent alignés avec les plans GPU.

## Problem

Text render steps need WGSL modules and reflected binding layouts that match Kotlin resource, uniform, sampler, texture, and instance plans. The target architecture requires parser-validated WGSL through `wgsl4k`; handwritten or generated text shaders must not drift from the binding ABI. Without this ticket, A8/SDF/color route evidence could rely on unreflected layouts or SkSL-era assumptions.

## Scope

- Add parser/reflection validation for text WGSL modules or snippets used by `A8TextMaskStep` and any promoted text route snippets.
- Reflect bind groups, bindings, uniform structs, texture/sampler slots, instance input layout expectations, and SDF parameter uniforms where present.
- Emit `text-wgsl-reflection.json` and `text-wgsl-validation-report.json` with module hash, entry points, reflected bindings, Kotlin plan comparisons, and diagnostics.
- Ensure text shader paths use WGSL, not SkSL, and remain compatible with the high-performance WGSL pipeline target.
- Add refusal diagnostics for missing SDF params, binding layout mismatch, parser failure, and unregistered text WGSL modules.

## Non-Goals

- Do not rebuild Skia's SkSL compiler or accept arbitrary SkSL.
- Do not implement new color composite, SVG, bitmap, or SDF GPU routes solely through this validation ticket.
- Do not use parser validation as visual correctness proof.
- Do not generate a unique shader per glyph or uniform value.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`
- `.upstream/specs/wgsl4k-evolution/01-validation-reflection-contract.md`

## Design Sketch

```kotlin
data class TextWgslReflectionReport(
    val moduleId: TextWgslModuleId,
    val sourceHash: StableHash,
    val entryPoints: List<WgslEntryPoint>,
    val reflectedBindings: List<WgslBindingReflection>,
    val instanceLayoutHash: StableHash,
    val kotlinPlanComparison: WgslPlanComparison,
    val diagnostics: List<GPUTextDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Text WGSL modules parse successfully through `wgsl4k` before support is promoted.
- [ ] Reflected bindings match `GPUTextBinding`, resource plans, uniform packs, texture/sampler slots, and instance layout expectations.
- [ ] A8 text mask WGSL evidence is present before A8 route promotion.
- [ ] SDF validation refuses missing `GPUTextSDFParams` with `unsupported.text.sdf_params_missing` until SDF route support exists.
- [ ] No text shader validation path references SkSL or native Skia shader compilation.

## Required Evidence

- `text-wgsl-reflection.json` fixture for the A8 text mask module or snippet.
- `text-wgsl-validation-report.json` fixture comparing reflected layout to Kotlin text binding plans.
- Negative fixtures for parser failure, binding layout mismatch, missing SDF params, and unregistered text WGSL module.

## Fallback / Refusal Behavior

- Parser or reflection failure refuses the text route; it does not fall back to unvalidated shader code.
- SDF/color composite WGSL validation remains `GPU-gated` until the corresponding route evidence exists.
- Legacy gates `dftext` and `coloremoji_blendmodes` remain open until route-specific WGSL and GPU evidence are linked.

## Dashboard Impact

- Expected row: `Text WGSL parser/reflection validation`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless parser/reflection evidence is attached for each promoted route.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*TextWgsl*'
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest --tests '*TextWgsl*'
```

## Status Notes

- `proposed`: Ties M11 text routes to the WGSL parser/reflection architecture target.
- Move to `ready` only after reflection report fields and WGSL module registration rules are reviewed.
- wgsl4k dependency evolution is tracked by `.upstream/specs/wgsl4k-evolution/`.
- 2026-06-15 re-evaluation: merged wgsl4k SHA
  `72a35b58758f241756d984a84768ae77308730da` produced Kanvas dependency
  fixtures under `reports/wgsl4k-evolution/generated/`, including
  `text-wgsl-reflection.json` and `text-wgsl-validation-report.json`. The
  ticket remains `proposed` because its owning dependencies, real text route
  WGSL modules/snippets, A8 route evidence, SDF refusal evidence, and
  CPU/GPU/reference route evidence are not completed by dependency fixtures
  alone.
- `blocked` (2026-06-16): Readiness audit confirmed the wgsl4k dependency
  evolution is useful prerequisite evidence only. The current generated
  dependency fixtures still carry `routePromotion:"not-promoted"` and
  `productActivation:false`, and there is no real `A8TextMaskStep` module or
  snippet tied to a `GPUTextBinding`/resource plan. Remaining gate: finish
  `KFONT-M11-004` and `KFONT-M11-007`, then validate actual text WGSL modules
  against reflected bindings and Kotlin plan comparisons.
- `blocked` (2026-06-23): `KFONT-M11-004` and `KFONT-M11-006` are done, and
  `KFONT-M11-007` is ready but not implemented. This ticket stays blocked
  until the resource/upload/instance/binding dumps exist, then text WGSL
  modules can be validated against reflected bindings and Kotlin plan
  comparisons.
- `ready` (2026-06-23): `KFONT-M11-007` now lands binding layout hashes,
  resource slots, atlas generation facts, and material refs for the accepted
  A8 resource contract. This ticket can now validate text WGSL parser/
  reflection evidence against Kotlin binding and resource plan comparisons.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
- `legacy:dftext`
- `legacy:coloremoji_blendmodes`
