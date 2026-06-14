---
id: KGPU-M4-003
title: "Add codec provenance and dependency-gated refusals"
status: proposed
milestone: M4
priority: P1
owner_area: images-codecs
claim_impact: DependencyGated
route_kind: CPUPreparedGPU
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

## Spec Sources

- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

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

- `proposed`: Codec provenance first, implementation later.

## Linear Labels

- `gpu-renderer`
- `milestone:M4`
- `area:codecs`
