# Normalized Draw Commands

Status: Draft
Date: 2026-06-13

## Purpose

Define the command boundary between stateful Skia-like API compatibility and
the new GPU renderer core.

The renderer core receives high-level normalized draw commands with captured
state. It does not receive a stateful stream of `save`, `restore`,
`concatMatrix`, `clipPath`, or `saveLayer` operations.

## Ownership Boundary

The legacy adapter owns:

- interpreting current `SkCanvas`/`SkDevice`-style state;
- resolving save/restore nesting;
- combining current transform state;
- resolving clip state into a normalized clip descriptor;
- attaching layer/surface facts;
- converting Skia-like paint and shader objects into normalized material
  descriptors;
- computing conservative bounds and ordering hints;
- refusing invalid state before it enters the core.

The GPU renderer core owns:

- route selection;
- render-step selection;
- material and pipeline key derivation;
- resource planning;
- task and draw-pass creation;
- GPU diagnostics.

## Command Invariants

Every `NormalizedDrawCommand` must be:

- immutable after creation;
- free of direct `Sk*` API types;
- complete enough to route without replaying a Canvas stack;
- explicit about transform, clip, layer, material, bounds, and ordering facts;
- explicit about unsupported or lossy normalization;
- serializable or dumpable for diagnostics and conformance evidence.

Invalid transforms, empty clips, impossible bounds, unsupported paint
normalization, invalid image source descriptors, and invalid resource
references must be rejected at adapter time with stable diagnostics.

## Required Captured State

Each command carries the following facts:

| Field | Meaning |
|---|---|
| `commandId` | Stable per-recording identifier for diagnostics. |
| `drawKind` | High-level operation family such as fill, stroke, image, text, or layer composite. |
| `geometry` | Shape, image rect, glyph run, or other high-level payload in normalized form. |
| `transform` | Captured local-to-device transform facts. |
| `clip` | Captured clip descriptor, including conservative bounds and support classification. |
| `layer` | Captured layer or target scope facts. |
| `material` | Normalized material descriptor used to derive `MaterialKey`. |
| `bounds` | Conservative device-space bounds used for culling, ordering, and diagnostics. |
| `ordering` | Paint-order and dependency hints required for correct blending and clipping. |
| `source` | Adapter provenance for PM/debug dumps. |

## Command Families

The kernel accepts high-level command families. It does not force early
lowering into coverage, paint, blend, or texture micro-ops.

Initial vocabulary:

- `FillShape`
- `StrokeShape`
- `DrawImageRect`
- `DrawTextRun`
- `DrawVertices`
- `DrawLayer`
- `Clear`
- `Discard`

This list is a command vocabulary, not a support claim. A command family is
supported only when route policy and conformance evidence promote it.

## Geometry Payload

Geometry payloads are normalized value objects. They may represent:

- rectangles;
- rounded rectangles;
- paths;
- strokes;
- image source/destination rectangles;
- glyph-run references and typed text artifact references;
- vertices.

Image payloads use `GPUImageSourceDescriptor` and normalized source/destination
rects. They must not leak `SkImage`, raw `GPU` handles, imported handles,
mutable pixel storage, or object identity into the core.
When the payload represents encoded bytes or already-decoded CPU pixels, decode
and preparation are governed by `22-image-bitmap-codec-pipeline.md`; the
normalized command carries stable source facts and route diagnostics, not raw
codec handles or mutable pixel buffers.

Text payloads use `DrawTextRun` with value-object outputs from
`.upstream/specs/pure-kotlin-text/`: `TextLayoutResult`, `GlyphRunDescriptor`,
`GlyphArtifactPlan`, `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`,
`OutlineGlyphPlan`, `ColorGlyphPlan`, `BitmapGlyphPlan`, `SVGGlyphPlan`,
`GlyphUploadPlan`, artifact key hashes, atlas generation tokens, and text route
diagnostics. `DrawTextRun` route selection, subrun splitting, atlas resource
planning, WGSL binding, and refusal behavior are governed by
`21-text-glyph-pipeline.md`.

The payload must not leak mutable legacy shape objects into the core. If a path
or text payload is too expensive to copy, the adapter must use an explicit
immutable handle with lifetime rules and diagnostic identity.

## Transform Facts

Transform facts must include:

- matrix values or a stable transform handle;
- transform classification such as identity, translate, scale, affine, or
  perspective;
- invertibility and finiteness classification;
- local-to-device and, where required, device-to-local availability;
- pixel-snapping facts when normalization applied snapping.

Invalid or non-finite transforms do not enter the core as draw commands.

## Clip Facts

Clip facts must distinguish:

- wide-open clip;
- empty clip;
- device rect/scissor clip;
- analytic simple shape clip;
- complex clip requiring GPU depth/stencil or mask planning;
- unsupported clip with stable refusal reason.

The clip descriptor is captured state. The core may choose a GPU clipping
technique, but it must not reconstruct the original Canvas clip stack.

## Layer Facts

Layer facts must describe the active target and layer semantics without
requiring the core to replay `saveLayer`/`restore` operations.

At minimum, layer facts must include:

- target identity or logical target scope;
- device size and color format facts;
- alpha and blend semantics that affect the layer composite;
- whether destination reads are required. Detailed destination-read routes are
  resolved later through `GPUDestinationReadPlan` from
  `20-destination-read-strategy.md`;
- whether the command depends on prior target contents.

Complex image-filter layer behavior is outside this kernel. It must enter the
core only through a later accepted command contract.

## Material Descriptor

The command carries a normalized material descriptor, not a compiled shader and
not a final `GPURenderPipelineKey`.

The descriptor must be complete enough to derive:

- `MaterialKey`;
- material uniform layout;
- texture/sampler binding requirements;
- `GPUImageSourceDescriptor` facts when the material samples an image source;
- registered runtime-effect identity when present;
- WGSL fragment requirements;
- stable unsupported reasons.

## Ordering Facts

Ordering facts preserve correctness while allowing the core to sort and batch
where legal.

They must include:

- original paint order;
- dependency on destination color;
- dependency on clip or stencil preparation;
- opacity or blend classification when known;
- barriers required by uploads, atlas mutation, or destination reads.

The core may reorder only when these facts prove the output is unchanged.
Destination-color dependencies become `GPUDestinationReadRequirement` and
`GPUDestinationReadClass` facts during analysis and layer planning.

## Diagnostics

Every normalized command must be dumpable with enough facts to explain route
selection:

- command family;
- bounds;
- transform classification;
- clip classification;
- material classification;
- selected or refused route;
- stable reason code;
- adapter source.

Diagnostic dumps must not depend on object identity addresses or nondeterministic
iteration order.

## Non-Goals

- Do not encode a Canvas state machine inside the core.
- Do not force all draws into low-level coverage/paint ops before route
  selection.
- Do not claim support for every command family listed here.
- Do not normalize arbitrary SkSL runtime shader input.
