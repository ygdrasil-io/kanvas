# GPU Renderer Handoff

Status: Draft
Date: 2026-06-13

## Purpose

Define the boundary between the pure Kotlin text stack and the new GPU
renderer. The handoff lets `:gpu-renderer` draw text without parsing fonts,
shaping text, choosing fallback fonts, decoding embedded glyph payloads, or
building glyph atlases itself.

This spec depends on `.upstream/specs/gpu-renderer/` route policy and command
contracts.

## Boundary Rule

The text stack produces two layers of output:

1. semantic output: `TextLayoutResult` and `GlyphRunDescriptor`;
2. GPU-consumable artifacts: `GlyphArtifactPlan`, `GlyphAtlasArtifact`,
   `SDFGlyphAtlasArtifact`, `ColorGlyphPlan`, `BitmapGlyphPlan`,
   `SVGGlyphPlan`, and upload plans.

The GPU renderer consumes these outputs through `NormalizedDrawCommand.DrawTextRun`.
It must not re-shape, re-layout, re-resolve fallback fonts, re-parse font
tables, or silently replace text artifacts with CPU-rendered textures.

## `DrawTextRun` Payload

The normalized text command payload includes:

- command ID;
- text layout result ID or glyph run ID;
- immutable list of glyph run descriptors or artifact references;
- transform facts;
- clip facts;
- layer facts;
- material descriptor for text fill/stroke/decoration when applicable;
- blend/color facts from the adapter;
- artifact key hashes;
- atlas generation and invalidation tokens;
- upload dependency facts;
- route diagnostics from text stack;
- source provenance for evidence.

The payload must be free of direct `Sk*` API types.

## Glyph Artifact Planner

`GlyphArtifactPlanner` is font-owned. It receives:

- `TextLayoutResult`;
- target use case;
- style and material facts relevant to glyph representation;
- transform classification;
- GPU capability summary supplied by the adapter or renderer boundary;
- cache budget policy;
- prior atlas generation facts when available.

It produces:

- representation decisions;
- artifact keys;
- CPU-generated A8 or SDF data when selected;
- color/bitmap/SVG plans;
- upload plans;
- diagnostics;
- refusal when no valid route exists.

## Allowed GPU Routes

Text routes must map to GPU renderer route kinds:

| Text output | GPU route |
|---|---|
| Outline glyph plan | `GPUNative` if renderer supports path/text geometry, otherwise typed prepared geometry or refusal. |
| COLR/COLRv1 color plan | `GPUNative` or `CPUPreparedGPU` depending on renderer material and layer support. |
| PNG bitmap glyph plan | `CPUPreparedGPU` upload or existing GPU texture resource. |
| SVG glyph plan | `GPUNative` when converted to renderer-supported vector paths/materials; any prepared fallback must be a named glyph-scoped artifact, never a complete CPU-rendered text texture. |
| A8 glyph atlas | `CPUPreparedGPU` `GlyphAtlasArtifact`. |
| SDF glyph atlas | `CPUPreparedGPU` `SDFGlyphAtlasArtifact`. |

The forbidden route remains forbidden: CPU-rendering a complete unsupported
text draw into a texture and compositing it as GPU support.

## GPU Renderer Target Contract

The complete GPU renderer target for consuming these artifacts is defined in
`.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`.

That spec registers the target renderer concepts for:

- `GlyphAtlasArtifact`;
- `SDFGlyphAtlasArtifact`;
- `GlyphUploadPlan`;
- `ColorGlyphPlan`;
- `BitmapGlyphPlan`;
- `SVGGlyphPlan`;
- `OutlineGlyphPlan`.

Registration in the target spec is not an implementation support claim. Until
a route has implementation evidence, GPU evidence, deterministic dumps, and
stable diagnostics, it remains `DependencyGated` or refuses with
`text.gpu.artifact-unregistered` or a narrower diagnostic.

## Artifact Registry

The GPU renderer artifact registry must recognize text artifact types:

- `GlyphAtlasArtifact`;
- `SDFGlyphAtlasArtifact`;
- `GlyphUploadPlan`;
- `ColorGlyphPlan`;
- `BitmapGlyphPlan`;
- `SVGGlyphPlan`;
- `OutlineGlyphPlan`.

Each registered type defines:

- key preimage;
- compact hash;
- lifetime class;
- invalidation facts;
- memory budget;
- upload budget;
- GPU consumer step;
- refusal codes.

## Ordering And Mutation

Text draws may create ordering dependencies:

- atlas upload must happen before sampling;
- atlas mutation invalidates stale generations;
- destination-read text effects must create barriers through GPU renderer layer
  policy;
- color glyph composites may require offscreen planning;
- SDF sampling may require material-specific uniforms.

The handoff reports these dependencies as ordering facts. The GPU renderer owns
final task graph placement.

## Diagnostics

Text handoff diagnostics include:

- selected text route;
- selected GPU route class;
- artifact type;
- artifact key hash;
- atlas generation;
- cache hit/miss;
- upload byte count;
- transform eligibility;
- fallback font facts;
- color glyph facts;
- refusal reason if any.

Stable reason-code families:

- `text.gpu.artifact-unregistered`;
- `text.gpu.artifact-key-nondeterministic`;
- `text.gpu.artifact-budget-exceeded`;
- `text.gpu.atlas-generation-stale`;
- `text.gpu.upload-plan-missing`;
- `text.gpu.capability-missing`;
- `text.gpu.transform-unsupported`;
- `text.gpu.color-plan-unsupported`;
- `text.gpu.SVG-plan-unsupported`;
- `text.gpu.CPU-rendered-texture-forbidden`.

## Acceptance Criteria

- `DrawTextRun` can be dumped without `Sk*` objects.
- GPU renderer route selection can happen from text artifacts alone.
- Text artifacts are registered typed `CPUPreparedGPU` artifacts when CPU
  preparation is used.
- Upload, cache, generation, and invalidation facts are explicit.
- Unsupported text routes refuse with stable diagnostics instead of CPU-rendered
  texture compatibility.
