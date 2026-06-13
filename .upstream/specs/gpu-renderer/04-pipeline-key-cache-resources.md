# PipelineKey, Cache, And Resources

Status: Draft
Date: 2026-06-13

## Purpose

Define the executable pipeline identity and resource-provider responsibilities
for the new GPU renderer.

`PipelineKey` is separate from `MaterialKey`. `MaterialKey` identifies material
logic. `PipelineKey` identifies the GPU-executable combination of material,
render step, target state, fixed state, and capabilities.

## `PipelineKey`

`PipelineKey` includes:

- `GPURenderStep` identity and version;
- `MaterialKey` identity;
- vertex layout;
- primitive topology;
- target color format;
- target alpha/color-space write assumptions;
- depth/stencil state;
- blend state;
- sample count or coverage mode;
- bind group layout identity;
- WGSL module identity;
- capability requirements;
- renderer version salt.

It does not include:

- per-draw uniform values;
- command ID;
- transient buffer offsets;
- texture contents;
- atlas coordinates;
- resource object addresses.

## Key Determinism

Pipeline key generation must be:

- deterministic across equivalent commands;
- stable across process runs when inputs are equivalent;
- explicit about version changes;
- independent of Kotlin object identity;
- backed by test fixtures that assert canonical preimages.

Every promoted key family must expose a diagnostic preimage and a compact hash.

## `GPUCapabilities`

`GPUCapabilities` describes the selected `GPU` facade implementation and
adapter limits relevant to route selection and pipeline creation.

It includes:

- texture formats and usages;
- shader feature support;
- limits affecting buffers, bindings, and workgroup sizes;
- depth/stencil support;
- multisample support;
- storage buffer support;
- timestamp/query support when used for evidence;
- implementation identity such as browser/native/Dawn/pure Kotlin when
  available.

Capabilities may change between implementations of the same facade. Pipeline
keys must include only the capability facts that affect validity or behavior.

## `GPUResourceProvider`

`GPUResourceProvider` owns creation, lookup, and lifetime of GPU resources.

It is responsible for:

- render pipelines;
- shader modules;
- bind group layouts;
- bind groups;
- buffers;
- textures;
- samplers;
- coverage/path/glyph atlases when owned by this renderer;
- upload staging resources;
- cache eviction and diagnostics;
- device generation validation.

It must report stable diagnostics for unsupported usage, allocation failure,
validation failure, and device-generation mismatch.

## Cache Layers

Expected cache layers:

- material module cache keyed by `MaterialKey` plus WGSL fragment versions;
- pipeline cache keyed by `PipelineKey`;
- layout cache keyed by bind group and uniform layout identity;
- sampler cache keyed by sampler descriptor;
- texture/resource cache keyed by explicit resource descriptors;
- atlas caches with explicit ownership and eviction rules.

Cache hits and misses must be observable in conformance or PM evidence before
performance claims are made.

## Resource Lifetimes

Resources must be tied to:

- device generation;
- target generation when applicable;
- recording lifetime when one-shot;
- cache lifetime when reusable;
- atlas generation for CPU-prepared GPU artifacts.

No command may hold a stale resource silently. Stale resources must trigger
rebuild, discard, or refusal with diagnostics.

## CPU-Prepared Artifacts

`CPUPreparedGPU` artifacts are resources too. Their key must include all facts
that affect their contents:

- source geometry or image identity;
- transform facts when rasterization depends on transform;
- clip facts when preparation is clipped;
- style and stroke facts;
- coverage quality;
- color-space or alpha handling when pixels are prepared;
- atlas generation and coordinates.

CPU-prepared artifacts must be invalidated when any content-affecting fact
changes.

## Non-Goals

- Do not build a cache that assumes only one future `GPU` implementation.
- Do not put per-draw values in `PipelineKey`.
- Do not make cache success a correctness dependency.
- Do not hide resource allocation failures by falling back to CPU rendering.
- Do not claim persistent pipeline storage until measured and specified.
