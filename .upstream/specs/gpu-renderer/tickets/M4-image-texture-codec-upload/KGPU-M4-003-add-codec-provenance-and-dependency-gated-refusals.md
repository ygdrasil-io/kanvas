---
id: KGPU-M4-003
title: "Add codec provenance and dependency-gated refusals"
status: done
milestone: M4
priority: P1
owner_area: images-codecs
claim_impact: DependencyGated
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M4-002]
legacy_gate: "codec legacy"
---

# KGPU-M4-003 - Add codec provenance and dependency-gated refusals

## PM Note

Ce ticket empêche de confondre provenance codec et support image complet.

## Problem

Encoded image support depends on codec descriptors, conformance tiers, decode
facts, and nondeterminism policy.

## Scope

- Add codec registry/provenance dumps for target formats.
- Add dependency-gated refusals for missing codec capabilities.

## Non-Goals

- Do not implement all codecs.
- Do not accept platform decoder behavior without descriptors.
- Do not promote a `CPUPreparedGPU` uploaded-texture route from provenance
  metadata alone.

## Spec Sources

- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

## Graphite Algorithm References

- [`GFX-TEXTURE-UPLOAD-ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - source [TextureUtils.cpp:251](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:251); Reference where decoded pixels become GPU upload sources after codec work is complete.
- [`GFX-IMAGE-COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-copy) - source [Image_Graphite.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Image_Graphite.cpp:90); Use copy eligibility as vocabulary for refusing unsupported image provenance.
- [`GFX-RESOURCE-KEYED-CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - source [ResourceProvider.cpp:113](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceProvider.cpp:113); Study resource-key validation before treating image artifacts as reusable.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class CodecProvenanceEvidence(val codecId: String, val conformanceTier: String)
```

## Acceptance Criteria

- [ ] Codec ID/version/capability facts are dumpable.
- [ ] Unsupported codecs refuse with `dependency.image.*` diagnostics.
- [ ] Decode output is not accepted without provenance.

## Required Evidence

- Codec registry dumps and refusal matrix.

## Fallback / Refusal Behavior

Missing codec capability stays `DependencyGated`; no substitute decoder is
silently used.

## Dashboard Impact

- Expected row: `gpu-renderer.codec-provenance`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added `GPUImageCodecRegistrySnapshot` dependency-gated provenance
  evidence for encoded image codec descriptors. Registry dumps include codec
  ID, descriptor version, supported formats, implementation kind,
  determinism, color policy, and dependency gate. Provenance planning emits
  `DependencyGated` diagnostics for unregistered formats, external/platform
  codecs disallowed by the conformance tier, nondeterministic versions/output
  policy, and planned codecs whose real delivery remains gated. Decode output
  without registry-backed codec provenance refuses with
  `dependency.image.decode.provenance_missing`. Evidence is provenance/refusal
  only and does not implement codecs, accept decode output, promote an uploaded
  texture route from metadata, use platform decoder substitutes, or activate
  product routing. Post-remediation independent review
  `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95` accepted the evidence for `done` and
  confirmed no hidden activation, support-claim widening, package-cycle risk,
  material-key/resource-handle leak, or M4-004 promotion.

## Linear Labels

- `gpu-renderer`
- `milestone:M4`
- `area:codecs`
