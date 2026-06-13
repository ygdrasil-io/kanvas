# Routing Policy

Status: Draft
Date: 2026-06-13

## Purpose

Define route selection for normalized draw commands in the GPU-first renderer.

The policy is explicit: prefer GPU-native execution, allow CPU preparation only
when the GPU consumes the prepared artifact, use CPU reference for evidence,
and refuse unsupported routes with stable diagnostics.

## Route Kinds

### `GPUNative`

The draw is executed by GPU render or compute work without CPU rasterization of
the draw result.

Examples:

- analytic rect coverage;
- gradient material WGSL;
- image sampling through GPU texture bindings;
- stencil/depth clip preparation on GPU;
- GPU tessellation or generated geometry buffers when owned by GPU-side logic.

### `CPUPreparedGPU`

The CPU prepares an artifact that the GPU consumes.

Examples:

- coverage mask;
- path atlas entry;
- uploaded bitmap texture;
- precomputed geometry buffer;
- packed uniform payload;
- glyph mask atlas entry when text infrastructure owns glyph rasterization.

This route is Graphite-like in spirit for small-path or raster-atlas behavior,
but it is not a full CPU fallback. The final draw still goes through GPU
composition or GPU command submission.

### `CPUReferenceOnly`

The CPU computes oracle behavior for tests, diffs, reports, or local
debugging. This route is not used as a production fallback for unsupported GPU
features.

### `RefuseDiagnostic`

The renderer refuses the command with a stable reason because no supported GPU
or CPU-prepared GPU route exists.

Refusal is a valid product behavior when it is explicit, deterministic, and
covered by tests or PM evidence.

## Route Precedence

Route selection follows this order:

1. Validate normalized command invariants.
2. Try a supported `GPUNative` route.
3. Try a supported `CPUPreparedGPU` route.
4. Return `RefuseDiagnostic`.

`CPUReferenceOnly` is invoked by validation/evidence harnesses, not by product
route fallback.

## No Silent CPU Fallback

The renderer must not silently render an unsupported command fully on CPU and
composite it as though it were GPU support.

A future explicit CPU-rendered texture compatibility route is outside this
kernel. If it is introduced later, it must be a separate route kind with
separate diagnostics, performance policy, and user-visible support semantics.

## Route Inputs

Route selection may use:

- command family;
- geometry complexity;
- transform facts;
- clip classification;
- material classification;
- target format;
- destination-read requirements;
- `GPUCapabilities`;
- resource availability;
- atlas budget;
- feature gates;
- conformance maturity.

Route selection must not use nondeterministic facts such as object identity,
current cache occupancy without a stable budget policy, or hidden environment
flags.

## Diagnostics

Every command must produce a route diagnostic in debug/conformance modes.

Diagnostic fields:

- command ID;
- command family;
- selected route or refusal;
- stable reason code;
- material key or material refusal;
- pipeline key or pipeline refusal;
- render step or render-step refusal;
- capability facts that affected selection;
- CPU-prepared artifact facts when used;
- fallback/refusal source.

Stable reason-code examples:

- `unsupported.clip.complex_stack`
- `unsupported.material.unregistered_runtime_effect`
- `unsupported.wgsl.validation_error`
- `unsupported.pipeline.capability_missing`
- `unsupported.atlas.capacity`
- `unsupported.geometry.perspective_path`
- `unsupported.resource.device_lost`

## Promotion Gates

A route may be promoted only when:

- command normalization tests pass;
- route diagnostics are deterministic;
- key preimages are stable;
- WGSL validation passes when WGSL is used;
- CPU reference evidence or explicit refusal exists;
- GPU evidence exists for GPU claims;
- PM/report output identifies route counts and refusals.

## Non-Goals

- Do not infer support from missing diagnostics.
- Do not treat CPU reference as production rendering.
- Do not route broad unsupported features through one generic compatibility
  path.
- Do not weaken refusal diagnostics to make dashboards look greener.
