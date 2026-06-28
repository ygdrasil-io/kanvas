---
id: KGPU-M36-004
title: "Hardware codec descriptor"
status: proposed
milestone: M36
priority: P1
owner_area: images
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M36-004 - Hardware codec descriptor

## PM Note

Les codecs hardware/plateforme sont acceptés derrière des descripteurs
explicites avec politique de nondéterminisme.

## Problem

Hardware and platform codecs (e.g., platform image decoders, hardware
accelerated codec paths) vary in capability, output quality, and
determinism. Without explicit descriptors and a nondeterminism policy,
hardware codecs cannot be safely accepted into the GPU pipeline. Their
use must be gated on approved profiles and documented sources of variation.

## Scope

- `GPUHardwareCodecDescriptor`: codec ID, implementation kind (platform,
  hardware, hybrid), vendor, version, capability flags, approved profiles
  list.
- `GPUHardwareCodecNondeterminismPolicy`: documented sources of
  nondeterminism (driver version, GPU vendor, decode variation across
  hardware), accepted tolerance thresholds, reproducibility contract.
- `GPUHardwareCodecFallbackPlan`: hardware-to-software fallback dispatch
  with reason code, fallback priority order, diagnostic emission on
  fallback.

## Non-Goals

- Do not leak Android Bitmap/MediaCodec abstractions into `:gpu-renderer`.
- Do not accept any hardware codec without an explicit approved profile
  entry.
- Do not claim hardware codec results are pixel-exact across devices.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

## Graphite Algorithm References

- `GFX-IMAGE-CODEC-DISPATCH` from `../GRAPHITE-ALGORITHM-REFERENCES.md` —
  Study codec dispatch tables and hardware/software fallback chains.
- Boundary: references are for algorithm study only; do not port Graphite or
  Ganesh and do not treat them as Kanvas acceptance criteria.

## Design Sketch

```kotlin
data class GPUHardwareCodecDescriptor(
    val codecId: String,
    val implementationKind: CodecImplementationKind,
    val vendor: String,
    val version: String,
    val capabilityFlags: Set<CodecCapabilityFlag>,
    val approvedProfiles: Set<String>,
)

data class GPUHardwareCodecNondeterminismPolicy(
    val sources: Set<NondeterminismSource>,
    val toleranceThresholds: Map<String, ToleranceRange>,
    val reproducibilityContract: ReproducibilityLevel,
)

data class GPUHardwareCodecFallbackPlan(
    val priorityOrder: List<CodecId>,
    val reason: FallbackReason,
    val diagnostic: DiagnosticCode,
)
```

## Acceptance Criteria

- [ ] `GPUHardwareCodecDescriptor` contract is defined and accepted.
- [ ] `GPUHardwareCodecNondeterminismPolicy` lists specific nondeterminism
      sources and tolerance thresholds.
- [ ] `GPUHardwareCodecFallbackPlan` dispatches hardware-to-software
      fallback with reason codes.
- [ ] Unapproved hardware codecs are refused with stable diagnostics.
- [ ] No hardware codec is accepted without a documented nondeterminism
      policy.

## Required Evidence

- `GPUHardwareCodecDescriptor` contract dump with at least one approved
  profile entry.
- `GPUHardwareCodecNondeterminismPolicy` document listing known sources of
  variation.
- `GPUHardwareCodecFallbackPlan` dispatch evidence (hardware refusal →
  software fallback with reason code).
- Refusal diagnostic dumps for unapproved codec IDs.

## Fallback / Refusal Behavior

- Unapproved codec: `unsupported.image.hardware_codec_unapproved`.
- Nondeterministic codec without policy:
  `unsupported.image.hardware_codec_nondeterministic`.
- Hardware decode failure: fallback to software path with reason diagnostic.
- No silent fallback to CPU-rendered complete decode for GPU composition.

## Dashboard Impact

- Expected row: `gpu-renderer.image.hardware-codec`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no, unless approved profile registry entry and
  nondeterminism policy are accepted and all Required Evidence is linked.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HardwareCodec*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M36`
- `area:images`
