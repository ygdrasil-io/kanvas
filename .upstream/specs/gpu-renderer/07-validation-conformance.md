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
- `GPUMaterialProgramID`;
- `GPUMaterialAssemblyPlan`;
- `GPURenderPipelineKey`;
- `GPUComputePipelineKey`;
- WGSL module identity;
- payload gather plan identity;
- CPU-prepared artifact keys;
- texture/image descriptors and ownership plans;
- route diagnostics.

Equivalent inputs must produce equivalent keys. Different behavior-affecting
inputs must produce different keys.

### WGSL Validation Tests

Promoted WGSL routes must prove:

- assembled WGSL source is deterministic;
- material WGSL routes pass through `GPUMaterialDictionary` and
  `GPUMaterialAssemblyPlan`;
- `wgsl4k` validation and reflection succeed;
- binding layouts match Kotlin-side packing;
- parser diagnostics are captured when validation fails;
- generated modules do not depend on SkSL.

### Material Dictionary Tests

Tests must assert:

- equivalent material descriptors produce equivalent `MaterialKey` and
  `GPUMaterialProgramID` values within the same dictionary version;
- unsupported snippets and child-slot shapes refuse with stable diagnostics;
- snippet requirement propagation is deterministic and dumpable;
- material root roles are explicit and forbidden roles refuse;
- material assembly plans include dictionary version, snippet tree, ABI
  contributions, and WGSL fragment versions;
- material module assembly does not bypass registered `WGSLSnippet` metadata.

### WGSL ABI Tests

Tests must assert:

- bind group role and binding-number determinism;
- uniform, storage, texture, and sampler layout preimages;
- Kotlin packing offsets, sizes, alignment, and padding;
- reflection mismatch refusals;
- render and compute key preimages include ABI hashes.

### Payload Gathering Tests

Tests must assert:

- `GPUPayloadGatherPlan`, `GPUPayloadWritePlan`, `GPUPayloadBindingPlan`, and
  `GPUPayloadUploadPlan` dumps are deterministic;
- uniform payload bytes match `WGSLPackingPlan` offsets, sizes, padding, and
  numeric conversion;
- equal payload values de-duplicate to the same pass-local slot or scoped
  `GPUPayloadFingerprint`;
- distinct payload values do not change durable material or pipeline keys;
- resource binding order matches `WGSLResourceBindingPlan`;
- stale, missing, or incompatible resources refuse with stable diagnostics.

### Texture And Image Ownership Tests

Tests must assert:

- `GPUImageSourceDescriptor`, `GPUTextureDescriptor`,
  `GPUTextureViewDescriptor`, `GPUSamplerDescriptor`, and
  `GPUTextureOwnershipPlan` dumps are deterministic;
- `MaterialKey` excludes raw handles, imported handles, surface leases,
  `GPUTextureResourceRef`, `UploadedTextureArtifact` keys, and pixel contents;
- sampled texture binding order matches `WGSLResourceBindingPlan`;
- CPU pixel sources use `UploadedTextureArtifact` or refuse;
- GPU-native, imported, surface, offscreen, render-target, and atlas textures
  are not treated as `CPUPreparedGPU` unless a typed CPU-prepared artifact owns
  the prepared contents;
- imported textures refuse when owner, usage, lifetime, generation, or release
  policy is not dumpable;
- surface texture leases are frame/target-generation scoped;
- sampling the active color attachment refuses unless a validated intermediate
  route exists;
- stale device, target, atlas, upload, or surface generations rebuild, discard,
  or refuse deterministically.

### Blend And Color Tests

Tests must assert:

- `GPUBlendPlan` dumps for promoted blend modes;
- `GPUColorPlan` dumps for promoted color and alpha behavior;
- `GPUTargetState` key contribution;
- refusal for unsupported destination-read or color-conversion paths;
- layer elision only when blend and color plans prove equivalence.

### Execution And Submission Tests

Tests must assert:

- execution context and target dumps;
- device-generation validation;
- render, compute, copy, upload, and readback scope legality when used;
- stale resource refusal or rebuild behavior;
- skipped readback or timing lanes are reported explicitly.

### Route Policy Tests

Tests must cover:

- `GPUNative` success;
- `CPUPreparedGPU` success where allowed;
- `RefuseDiagnostic` for unsupported features;
- absence of silent CPU fallback;
- absence of CPU-rendered texture compatibility routes;
- stable route reason codes;
- capability-gated differences.

### GPU Evidence

GPU support claims require adapter-backed or accepted equivalent GPU evidence.

Evidence must include:

- command count;
- selected route count;
- pipeline key count;
- material key count;
- material dictionary version and material program count;
- material assembly plan count;
- payload slot counts, payload fingerprints, and upload bytes;
- texture provenance, ownership plan counts, sampled binding counts, and upload
  artifact counts when textures/images are used;
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
- execution context and device-generation facts;
- WGSL ABI validation status;
- blend/color/target-state plan counts;
- cache and pipeline counters when performance is claimed;
- telemetry and performance-gate state when realtime readiness is claimed;
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
- WGSL ABI, blend/color, target-state, and execution assumptions are validated
  for the route;
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
- material-dictionary failure;
- payload-gathering failure;
- WGSL validation failure;
- pipeline-key failure;
- resource-preparation failure;
- texture/image ownership failure;
- command-encoding failure;
- GPU execution/readback failure;
- WGSL ABI mismatch;
- blend/color plan refusal;
- execution context or device-generation failure;
- performance gate failure;
- CPU oracle mismatch;
- explicit unsupported feature.

Each class needs a stable reason code before it can appear in promoted
reports.

## Non-Goals

- Do not claim support from unit tests alone.
- Do not hide skipped adapter evidence.
- Do not replace explicit refusals with dashboard omissions.
- Do not treat CPU reference success as GPU product support.
- Do not accept CPU-rendered texture composition as product support.
- Do not retire legacy paths without route-level evidence.
