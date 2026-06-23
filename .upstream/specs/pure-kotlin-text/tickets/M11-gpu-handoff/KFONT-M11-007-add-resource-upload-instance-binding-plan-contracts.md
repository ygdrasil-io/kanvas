---
id: "KFONT-M11-007"
title: "Add resource/upload/instance/binding plan contracts"
status: "ready"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M11-006"]
legacy_gate: ["dftext"]
---

# KFONT-M11-007 - Add resource/upload/instance/binding plan contracts

## PM Note

Ce ticket décrit comment un subrun texte devient ressources, uploads, instances et bindings GPU vérifiables.

## Problem

Subrun route selection is not enough. The GPU renderer needs typed plans for atlas/bitmap textures, upload dependencies, instance buffers, uniforms, samplers, texture views, bindings, and lifetime owner scopes. Without these contracts, payload gathering can hide uploads, mix material identity with resource refs, or sample stale atlas generations.

## Scope

- Define `GPUTextResourcePlan`, `GPUTextUploadPlan`, `GPUTextInstanceLayout`, `GPUTextInstanceBufferPlan`, `GPUTextBinding`, `GPUTextAtlasDescriptor`, `GPUTextAtlasPageDescriptor`, and `GPUTextAtlasEntryRef` dump schemas.
- Record texture ownership plans, sampler descriptors, view descriptor hashes, upload byte ranges, row stride, staging buffer requirements, instance stride/alignment, binding layout hash, and lifetime scope.
- Keep pass-local resource refs and atlas entry refs out of `MaterialKey`.
- Emit `gpu-text-resource-plan.json`, `gpu-text-upload-plan.json`, `gpu-text-instance-layout.json`, and `gpu-text-binding-plan.json`.
- Add refusals for missing upload plan, upload budget overflow, unavailable atlas page, missing entry, and binding layout unavailable.

## Non-Goals

- Do not execute WebGPU uploads or submit command buffers here.
- Do not define material-key content; KFONT-M11-010 validates leakage separately.
- Do not merge glyph atlas, image, path, and coverage atlas lifetimes.
- Do not retire `dftext` without upload ordering and SDF sampling evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GPUTextResourcePlan(
    val subRunId: GPUTextSubRunId,
    val textureOwnership: List<GPUTextureOwnershipPlanRef>,
    val uploadDependencies: List<GPUTextUploadPlan>,
    val instanceBuffer: GPUTextInstanceBufferPlan,
    val bindings: List<GPUTextBinding>,
    val lifetimeScope: GPUResourceLifetimeScope,
    val diagnostics: List<GPUTextDiagnostic>,
)

data class GPUTextBinding(
    val subRunId: GPUTextSubRunId,
    val renderStep: GPUTextRenderStep,
    val artifactType: TextArtifactType,
    val bindingLayoutHash: StableHash,
    val atlasGenerationFacts: List<AtlasGenerationFact>,
    val materialPlanRef: MaterialPlanRef,
)
```

## Acceptance Criteria

- [ ] Resource, upload, instance, and binding dumps are deterministic and reference subrun IDs.
- [ ] Upload plans record source artifact key, destination texture plan, byte ranges, row stride, page/region, staging needs, and upload-before-sample dependency.
- [ ] Instance layout dumps include glyph target rect/origin, UV/source rect, page index, SDF params where relevant, representation flags, stride, and alignment.
- [ ] Bindings reference atlas/page generations and resource slots without entering material identity.
- [ ] Missing upload plan, upload budget overflow, missing atlas page, and binding layout failure emit route-specific diagnostics.

## Required Evidence

- `gpu-text-resource-plan.json`, `gpu-text-upload-plan.json`, `gpu-text-instance-layout.json`, and `gpu-text-binding-plan.json` fixtures for an A8 atlas subrun.
- Refusal snapshots for `unsupported.text.upload_plan_missing`, `unsupported.text.upload_budget_exceeded`, `unsupported.text.atlas_page_unavailable`, and `unsupported.text.binding_layout_unavailable`.
- Evidence that `GPUTextBinding` stays payload/resource data, not `MaterialKey` data.

## Fallback / Refusal Behavior

- Missing or over-budget upload plans refuse route execution; they do not trigger CPU texture fallback.
- Stale atlas/page generations refuse or request an explicit rebuild within budget.
- Legacy gate `dftext` remains open until SDF resource/upload/binding plans and sampling evidence are complete.

## Dashboard Impact

- Expected row: `Text GPU resource/upload/instance/binding contracts`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless plan dumps and refusal diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextResource*'
```

## Status Notes

- `proposed`: Creates the resource planning contract needed before ordering and material-key leakage tests.
- Move to `ready` only after each plan schema and resource lifetime rule are reviewed.
- `blocked` (2026-06-16): Readiness audit confirmed this ticket depends on
  `KFONT-M11-006`. The existing `GPUTextUploadPlan` is CPU-side artifact
  metadata only; it is not a renderer resource/upload/instance/binding plan
  with destination texture facts, row stride, staging requirements, binding
  layout hash, or lifetime scope. Remaining gate: finish `GPUTextSubRunPlan`
  planning first, then re-review the resource/upload/instance/binding schemas.
- `ready` (2026-06-23): `KFONT-M11-006` now lands deterministic
  `GPUTextSubRunPlan` evidence with stable subrun IDs, split reasons, route
  outcomes, atlas page/generation facts, clip/layer/destination-read barriers,
  and ordering-token placeholders. The resource/upload/instance/binding
  contract scope remains valid and no external decision is pending; keep route
  promotion, upload execution, and `dftext` retirement gated on this ticket's
  own dumps and refusal evidence.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
- `legacy:dftext`
