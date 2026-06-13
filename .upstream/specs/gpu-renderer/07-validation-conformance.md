# Validation And Conformance

Status: Draft
Date: 2026-06-13

## Purpose

Define the evidence required before the new GPU renderer can claim support,
promote routes, or retire legacy behavior.

The validation model keeps Kanvas' existing discipline: support claims need
CPU/GPU evidence or explicit refusal, deterministic diagnostics, and PM-visible
artifacts.

## Validation Layers

### Command Normalization Tests

Tests must assert:

- captured transform facts;
- captured clip facts;
- captured layer facts;
- material descriptor normalization;
- conservative bounds;
- ordering hints;
- stable unsupported normalization reasons;
- absence of direct `Sk*` types at the core boundary.

### Key Determinism Tests

Tests must assert canonical preimages and hashes for:

- `MaterialKey`;
- `PipelineKey`;
- WGSL module identity;
- CPU-prepared artifact keys;
- route diagnostics.

Equivalent inputs must produce equivalent keys. Different behavior-affecting
inputs must produce different keys.

### WGSL Validation Tests

Promoted WGSL routes must prove:

- assembled WGSL source is deterministic;
- `wgsl4k` validation and reflection succeed;
- binding layouts match Kotlin-side packing;
- parser diagnostics are captured when validation fails;
- generated modules do not depend on SkSL.

### Route Policy Tests

Tests must cover:

- `GPUNative` success;
- `CPUPreparedGPU` success where allowed;
- `RefuseDiagnostic` for unsupported features;
- absence of silent CPU fallback;
- stable route reason codes;
- capability-gated differences.

### GPU Evidence

GPU support claims require adapter-backed or accepted equivalent GPU evidence.

Evidence must include:

- command count;
- selected route count;
- pipeline key count;
- material key count;
- WGSL module validation result;
- output artifact, checksum, diff, or readback where applicable;
- capability facts;
- refusal counts.

Skipped GPU lanes must be reported as skipped or risk states, not as support.

### CPU Reference Evidence

CPU reference behavior is used for:

- oracle generation;
- diffs;
- support/refusal comparison;
- migration confidence.

CPU reference does not imply product fallback support.

## Existing Commands

Until this pack has dedicated tasks, the existing validation commands remain
the minimum evidence layer:

- `rtk ./gradlew --no-daemon pipelineConformance`
- `rtk ./gradlew --no-daemon pipelineConformanceReport`

New GPU renderer tickets may add narrower tasks, but they must not bypass the
existing conformance discipline without an accepted replacement.

## PM Evidence

PM/report artifacts must show:

- route taxonomy;
- counts by route kind;
- stable unsupported reasons;
- representative support artifacts;
- GPU capability facts;
- cache and pipeline counters when performance is claimed;
- known limitations.

Reports must distinguish:

- supported GPU rendering;
- CPU-prepared GPU rendering;
- CPU reference-only evidence;
- explicit refusal;
- skipped or unavailable adapter evidence.

## Promotion Criteria

A route can be promoted only when:

- normalized command contract tests pass;
- key determinism tests pass;
- WGSL validation passes when WGSL is used;
- GPU evidence exists for GPU support claims;
- CPU reference or explicit refusal evidence exists;
- route diagnostics are stable;
- PM/report artifacts expose support and refusal state;
- rollback to the legacy path is documented for migrated slices.

## Cleanup Acceptance

Cleanup-only changes are accepted only when they prove:

- no default route change;
- no pixel change;
- diagnostics are compatible or intentionally versioned;
- new shadow output is deterministic;
- tests cover the touched boundary.

## Failure Policy

Failures must be classified as:

- normalization failure;
- material-key failure;
- WGSL validation failure;
- pipeline-key failure;
- resource-preparation failure;
- command-encoding failure;
- GPU execution/readback failure;
- CPU oracle mismatch;
- explicit unsupported feature.

Each class needs a stable reason code before it can appear in promoted
reports.

## Non-Goals

- Do not claim support from unit tests alone.
- Do not hide skipped adapter evidence.
- Do not replace explicit refusals with dashboard omissions.
- Do not treat CPU reference success as GPU product support.
- Do not retire legacy paths without route-level evidence.
