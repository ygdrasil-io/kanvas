# Text Glyph Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the complete target contract for rendering text and glyph artifacts in
the new GPU renderer.

The pure Kotlin text stack owns font loading, shaping, paragraph layout, glyph
representation selection, CPU glyph generation, color glyph planning, and
artifact production. The GPU renderer owns normalized text commands, route
selection from typed glyph artifacts, GPU resource materialization, render
steps, WGSL binding layout, pass ordering, batching, diagnostics, telemetry,
and submission.

This is a target-complete spec. It is not an implementation slice. It covers
A8 mask text, SDF text, outline glyph plans, color glyph plans, bitmap glyph
plans, SVG glyph plans, atlas resources, and stable refusal behavior.

## Source Specs

Text production source of truth:

- `.upstream/specs/pure-kotlin-text/README.md`;
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`;
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`;
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`;
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`;
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`.

GPU renderer specs consumed by this target:

- `01-normalized-draw-commands.md`;
- `02-gpu-recording-task-graph.md`;
- `03-material-key-wgsl.md`;
- `04-pipeline-key-cache-resources.md`;
- `05-routing-policy.md`;
- `07-validation-conformance.md`;
- `10-gpu-execution-context-submission.md`;
- `11-wgsl-layout-binding-abi.md`;
- `12-blend-color-target-state.md`;
- `15-draw-layer-planner-and-sort-policy.md`;
- `17-payload-gathering-and-slots.md`;
- `18-texture-image-ownership.md`;
- `19-path-coverage-atlas-strategy.md`;
- `20-destination-read-strategy.md`;
- `24-clip-stencil-mask-pipeline.md`;
- `29-color-management-pipeline.md` for text paint colors, glyph palette
  colors, bitmap glyph profile facts, SVG glyph color facts, SDF/glyph
  coverage output value specs, and color glyph composite conversions;
- `30-coordinate-transform-bounds-policy.md` for glyph, text-local, atlas,
  mask, SDF, bitmap, SVG, layer, and target coordinate spaces; transform
  classification; conservative subrun bounds; and pixel/texel rounding.

## Graphite Evidence

Graphite's useful model is conceptual:

- `GlyphRunList` groups shaped glyph runs with bounds and origin.
- `TextBlobRedrawCoordinator`, `SubRunContainer`, and `Slug` cache reusable
  subrun decisions separately from a single draw call.
- `SubRunControl` classifies text into path, direct mask, transformed mask,
  and SDF text based on size, transform, paint, and capabilities.
- `AtlasSubRun` is the atlas-backed draw unit used to generate vertices and
  reference glyph atlas locations.
- `SubRunData` carries the subrun span, mask bounds, mask-to-device transform,
  glyph range, SDF/LCD facts, and recorder/atlas association into geometry.
- `TextAtlasManager` owns atlas lifetime, atlas generation, glyph insertion,
  use tokens, uploads, compaction, and eviction for text atlases.
- Graphite has distinct render steps for bitmap/direct mask text and SDF text.
- Atlas generation changes when glyph residency is evicted, and draws must not
  sample stale atlas coordinates.
- When the atlas cannot hold all glyphs, Graphite splits/flushing work to
  preserve atlas residency before drawing the remaining glyphs.

Kanvas adopts these invariants:

- text is split into renderer-consumable subruns before pipeline creation;
- glyph atlas residency is explicit and generation-checked;
- uploads happen before sampling;
- text render steps are separate from generic path, image, and coverage steps;
- material identity is separate from pass-local glyph resource bindings;
- text color value specs are explicit for paint, palette, bitmap/SVG glyph,
  SDF, mask modulation, and color-glyph composite outputs;
- text draws can split by atlas page, atlas generation, representation,
  transform, material, clip, and destination-read requirements;
- failure to materialize a valid text route returns stable diagnostics.

Kanvas intentionally does not copy:

- Graphite classes, C++ ownership, packed bit layouts, or Skia cache identity;
- SkSL text shader code;
- LCD subpixel text as a complete Kanvas target;
- Graphite's silent early return when atlas allocation fails;
- Ganesh/Graphite text op or glyph op machinery;
- native font engine behavior as a correctness oracle.

## Ownership Boundary

Owned by this spec:

- `GPUTextRunPlan`;
- `GPUTextSubRunPlan`;
- `GPUTextRepresentation`;
- `GPUTextRoute`;
- `GPUTextRenderStep`;
- `GPUTextAtlasPlan`;
- `GPUTextAtlasDescriptor`;
- `GPUTextAtlasPageDescriptor`;
- `GPUTextAtlasEntryRef`;
- `GPUTextUploadPlan`;
- `GPUTextResourcePlan`;
- `GPUTextInstanceLayout`;
- `GPUTextInstanceBufferPlan`;
- `GPUTextBinding`;
- `GPUTextSDFParams`;
- `GPUColorGlyphCompositePlan`;
- `GPUTextBatchKey`;
- `GPUTextOrderingToken`;
- `GPUTextBudgetPolicy`;
- `GPUTextDiagnostic`.

Owned by pure Kotlin text specs and consumed here:

- `TextLayoutResult`;
- `GlyphRunDescriptor`;
- `GlyphArtifactPlan`;
- `GlyphAtlasArtifact`;
- `SDFGlyphAtlasArtifact`;
- `GlyphUploadPlan`;
- `OutlineGlyphPlan`;
- `ColorGlyphPlan`;
- `BitmapGlyphPlan`;
- `SVGGlyphPlan`;
- `TextRouteDiagnostics`.

The GPU renderer must not parse fonts, shape text, run paragraph layout,
choose fallback fonts, decode embedded PNG glyph payloads, evaluate COLR/SVG
paint graphs from font tables, or generate glyph masks/SDFs. It consumes typed
artifacts and plans emitted by the text stack.

The text stack must not allocate `GPU` facade resources, create render
pipelines, schedule render passes, or decide GPU task ordering. It may emit
upload plans, artifact keys, generation tokens, bounds, and route diagnostics.

## Core Objects

### `GPUTextRunPlan`

Planning product for one normalized `DrawTextRun` command:

- command ID and provenance;
- text layout result ID or glyph run ID;
- ordered glyph run descriptors;
- accepted or refused glyph artifact plans;
- transform, clip, layer, blend, destination-read, and color facts;
- `GPUColorValueSpec` / `GPUColorPlan` references for paint, glyph, palette,
  bitmap/SVG glyph, or composite color domains;
- selected text routes and subruns;
- resource/upload dependencies;
- render step requirements;
- batching constraints;
- diagnostics.

`GPUTextRunPlan` is created during draw analysis. It is not a material key and
does not hold live `GPU` handles.

### `GPUTextSubRunPlan`

Renderer-owned draw unit derived from one or more glyph artifacts with
compatible representation, atlas/page residency, transform class, color plan,
blend plan, clip requirements, destination-read plan, and render step.

Fields:

- parent text run ID;
- subrun ID;
- source glyph run descriptor references;
- glyph range or explicit glyph IDs;
- representation;
- route;
- bounds in text-local, layer, and target space;
- mask/source coordinate space;
- text-local to target transform;
- atlas entry references or non-atlas source descriptors;
- instance layout and buffer plan;
- material and blend/color plan references;
- ordering token;
- diagnostic outcome.

Subruns are value objects. They may reference immutable artifact IDs and
recording-local resource references, but not mutable text-stack objects or
raw backend handles.

### `GPUTextRepresentation`

Representation consumed by the renderer:

| Representation | Source |
|---|---|
| `A8MaskAtlas` | `GlyphAtlasArtifact` with A8 coverage masks. |
| `SDFMaskAtlas` | `SDFGlyphAtlasArtifact` with R8 normalized signed distance values. |
| `OutlineGlyph` | `OutlineGlyphPlan` from the text stack. |
| `COLRColorGlyph` | `ColorGlyphPlan` with COLR/CPAL paint graph facts. |
| `BitmapGlyph` | `BitmapGlyphPlan` with decoded PNG-backed glyph image facts. |
| `SVGGlyph` | `SVGGlyphPlan` with pure Kotlin glyph-scoped SVG plan facts. |
| `RefusedText` | Stable refusal from text stack or GPU route selection. |

LCD subpixel masks are not a target representation. Requests for LCD text
diagnose through `text.gpu.LCD-future-research` or a narrower text-stack
reason.

### `GPUTextRoute`

Accepted or refused GPU route:

| Route | Meaning |
|---|---|
| `AtlasMaskSample` | Draw quads/instances that sample A8 atlas coverage. |
| `AtlasSDFSample` | Draw quads/instances that sample SDF atlas coverage and reconstruct coverage in WGSL. |
| `OutlinePathRoute` | Convert or reference text-stack outline plans through the renderer's path/coverage strategy. |
| `ColorGlyphCompositeRoute` | Render a color glyph plan through renderer-supported fills, gradients, clips, composites, and layers. |
| `BitmapGlyphTextureRoute` | Sample PNG bitmap glyph texture/artifact through texture ownership. |
| `SVGGlyphVectorRoute` | Render a pure Kotlin SVG glyph plan through renderer-supported vector/material routes. |
| `DependencyGated` | Text artifact is valid, but required renderer route is not registered or proven. |
| `RefuseDiagnostic` | No accepted route preserves semantics. |

Routes may lower to `GPUNative` or `CPUPreparedGPU` as defined by
`05-routing-policy.md`. They must never lower to a CPU-rendered complete text
texture.

### `GPUTextRenderStep`

Renderer step used by a text subrun:

| Step | Input | Output |
|---|---|---|
| `A8TextMaskStep` | Atlas A8 texture, sampler, per-glyph instances, material/color uniforms. | Coverage-masked text color in render pass. |
| `SDFTextMaskStep` | SDF atlas texture, sampler, per-glyph instances, SDF params, material/color uniforms. | Reconstructed coverage text in render pass. |
| `OutlineGlyphPathStep` | Outline glyph plans lowered through path/coverage specs. | Path/coverage draw or refusal. |
| `ColorGlyphCompositeStep` | Color glyph composite plan lowered to supported renderer primitives. | One or more draws/passes, possibly layers. |
| `BitmapGlyphTextureStep` | Bitmap glyph texture artifact/source and per-glyph instances. | Textured glyph draw. |
| `SVGGlyphVectorStep` | SVG glyph vector plan lowered to supported renderer primitives. | One or more vector draws/passes. |

Every render step must declare:

- required vertex/instance buffers;
- required uniform/resource bindings;
- text/glyph source and output color value specs from
  `29-color-management-pipeline.md`;
- color target state;
- blend requirements;
- clip/stencil/depth requirements;
- destination-read requirements;
- batching key;
- WGSL modules/snippets;
- diagnostics and validation fixtures.

### `GPUTextAtlasPlan`

Renderer-side plan for consuming glyph atlas artifacts:

- artifact type: A8 or SDF;
- source artifact key and generation;
- atlas descriptor;
- page descriptors;
- entry refs used by the draw;
- upload plan dependencies;
- texture ownership plan;
- view and sampler descriptors;
- residency/generation validation;
- eviction and rebuild/refusal behavior;
- budget class and telemetry.

This is the GPU consumption contract for text atlases. The text stack owns mask
generation and packing artifacts; the GPU renderer owns texture resources,
upload execution, view/sampler binding, and pass ordering.

### `GPUTextAtlasDescriptor`

Dumpable descriptor for one logical text atlas resource:

- artifact family: `GlyphAtlasArtifact` or `SDFGlyphAtlasArtifact`;
- mask/storage format: `A8` or `R8Unorm`;
- atlas dimensions;
- page dimensions;
- padding policy;
- coordinate normalization policy;
- texture usage requirements;
- lifetime class;
- maximum active pages;
- sampling policy;
- generation token;
- diagnostic label.

Text atlas descriptors use `GPUTextureOwnershipPlan` with provenance
`AtlasTexture` from `18-texture-image-ownership.md`.

### `GPUTextAtlasPageDescriptor`

Descriptor for one physical or logical atlas page:

- parent atlas descriptor hash;
- page index;
- texture descriptor hash;
- page generation;
- plot/region generation when exposed by the artifact;
- upload byte ranges;
- live entry count;
- budget and eviction facts.

Multi-page atlases are accepted. A subrun that spans pages either binds all
required pages within resource limits or splits into page-compatible subruns.

### `GPUTextAtlasEntryRef`

Pass-local reference to one glyph entry:

- artifact key hash;
- glyph strike key hash;
- atlas descriptor hash;
- page descriptor hash;
- atlas generation;
- page generation;
- glyph rectangle in texels;
- source bounds and origin;
- padding/inset;
- normalized UV rectangle;
- mask format;
- source mask hash.

`GPUTextAtlasEntryRef` is not a material identity. It validates residency for
the current recording/pass and becomes stale when the atlas generation changes.

### `GPUTextUploadPlan`

GPU upload plan for atlas pages or bitmap glyph resources:

- source artifact key;
- destination texture allocation or reuse plan;
- upload byte ranges;
- row stride and alignment;
- target page/region;
- upload-before-sample dependency;
- staging buffer requirements;
- budget decision;
- failure behavior.

Uploads are encoded through `GPUResourceProvider` and `GPUTaskList`. They are
not performed by `GPUPayloadGatherer`.

### `GPUTextResourcePlan`

Resource plan for a text subrun:

- texture ownership plans;
- atlas page resource refs;
- sampled texture bindings;
- sampler descriptors;
- instance buffer allocation;
- uniform block references;
- upload dependencies;
- lifetime owner scope;
- stale generation checks.

### `GPUTextInstanceLayout`

The target supports a stable dumpable instance layout for text mask and bitmap
steps. Required fields:

- glyph target rectangle or origin and size;
- atlas UV rectangle or bitmap source rectangle;
- glyph origin and bearing;
- local-to-mask or mask-to-target transform facts;
- color/material index or inline color when accepted;
- atlas page index;
- representation flags;
- subpixel bucket;
- SDF spread/scale index for SDF routes when not uniform per subrun;
- source glyph index for diagnostics.

The exact packed binary layout is backend-owned and may differ by platform,
but the preimage must be serialized and validated. `GPURenderPipelineKey`
contains only layout/stride/access facts required for executable validity.

### `GPUTextInstanceBufferPlan`

Records how instance data reaches the GPU:

- subrun IDs;
- instance count;
- layout hash;
- buffer usage flags;
- upload/update byte count;
- owner scope;
- ring-buffer or dedicated-buffer policy;
- alignment and stride;
- allocation failure behavior.

Large text draws may split by instance buffer budget. Splits must preserve
visual order and atlas generation validity.

### `GPUTextBinding`

Payload/resource binding for one text subrun:

- text subrun ID;
- render step;
- artifact type;
- atlas texture ownership plan IDs;
- texture view descriptor hashes;
- sampler descriptor hashes;
- binding layout hash;
- uniform/resource slot IDs;
- instance buffer resource ref;
- atlas/page generation facts;
- SDF params ID when relevant;
- material and color plan IDs.

`GPUTextBinding` stays out of `MaterialKey`. It is consumed by
`GPUPayloadGatherer` after route and resource plans are accepted.

### `GPUTextSDFParams`

Uniform facts for SDF sampling:

- spread in source pixels;
- atlas texel-to-distance scale;
- edge value;
- coverage reconstruction mode;
- antialias width policy;
- transform scale facts;
- gamma/color-space adjustment policy when accepted;
- diagnostics for unsupported transform or coverage policy.

The default SDF contract follows the pure Kotlin text SDF spec. WGSL coverage
must use the normalized distance convention from the artifact, not Skia's
private distance table.

### `GPUColorGlyphCompositePlan`

Renderer-owned lowering plan for `ColorGlyphPlan`, `BitmapGlyphPlan`, and
`SVGGlyphPlan` when they require multiple primitives or passes:

- source glyph plan ID;
- ordered primitive list;
- required fills, gradients, images, clips, transforms, composites, and layers;
- required destination-read plans for composite operations;
- intermediate targets when needed;
- unsupported primitive diagnostics;
- route outcome.

This plan is separate from general SVG or image rendering. It is glyph-scoped
and bounded by the text stack's glyph plan.

### `GPUTextBatchKey`

Planner-local key for batching compatible text subruns:

- render step;
- pipeline layout facts;
- material key or compatible material block;
- blend/color plan;
- clip/stencil/depth state;
- atlas descriptor/page binding compatibility;
- sampler policy;
- SDF parameter compatibility;
- destination-read class;
- target format;
- instance layout.

It is not durable and must not include glyph IDs, live handles, raw pointers,
or mutable atlas generation unless generation affects binding compatibility.

### `GPUTextOrderingToken`

Ordering token linking:

- text artifact generation;
- atlas upload task;
- atlas page/resource generation;
- instance buffer upload;
- draw that samples the atlas or bitmap texture;
- atlas eviction or compaction event;
- target/layer/destination-read barriers.

The token prevents stale atlas sampling and unsafe movement across upload,
eviction, clip, layer, destination-read, or target changes.

### `GPUTextBudgetPolicy`

Budget policy for text:

- maximum glyph instances per draw/subrun;
- maximum text instance bytes per frame/recording;
- maximum atlas upload bytes per frame/recording;
- maximum active atlas pages;
- maximum live atlas bytes;
- maximum SDF atlas bytes;
- maximum bitmap glyph texture bytes;
- maximum color/SVG glyph primitive count;
- maximum text-induced pass splits;
- maximum text color transform, palette conversion, bitmap glyph profile, and
  color glyph composite color-management cost inherited from
  `GPUColorBudgetPolicy`;
- maximum route diagnostics retained per text run.

Diagnostics must distinguish hard capability refusals from configurable budget
refusals.

## Normalized Command Contract

`NormalizedDrawCommand.DrawTextRun` carries only value objects:

- command ID;
- text layout result ID or glyph run ID;
- immutable glyph run descriptor references;
- glyph artifact plan references;
- transform facts;
- clip facts;
- layer facts;
- material descriptor for fill, stroke, decoration, or glyph color policy;
- blend/color facts;
- artifact key hashes;
- atlas generation and invalidation tokens;
- upload dependency facts;
- text-stack diagnostics;
- provenance for evidence.

It must not carry:

- `SkFont`, `SkTypeface`, `SkTextBlob`, `SkPaint`, `SkPath`, or other Skia-like
  mutable API types;
- font bytes;
- platform font handles;
- raw `GPU` handles;
- CPU-rendered complete text textures.

Decoration geometry may enter as separate normalized shape commands when the
paragraph/text stack emits decoration facts. This spec covers glyph drawing,
not every underline or selection rectangle command.

## Route Selection

Route selection is deterministic:

1. Reject any command with nondumpable or mutable text payloads.
2. Consume text-stack diagnostics and artifact plans.
3. For each artifact, select an accepted `GPUTextRepresentation`.
4. For each representation, select a `GPUTextRoute`.
5. Split into `GPUTextSubRunPlan` values by route, render step, atlas page,
   transform class, material, clip, layer, blend, destination-read, and budget.
6. Build resource, upload, instance, and binding plans.
7. Return accepted subruns or stable refusals.

Route mapping:

| Text artifact | Preferred GPU route | Refusal if unavailable |
|---|---|---|
| `GlyphAtlasArtifact` A8 | `AtlasMaskSample` | `text.gpu.A8-atlas-route-unavailable` |
| `SDFGlyphAtlasArtifact` | `AtlasSDFSample` | `text.gpu.SDF-route-unavailable` |
| `OutlineGlyphPlan` | `OutlinePathRoute` through path/coverage specs | `text.gpu.outline-route-unavailable` |
| `ColorGlyphPlan` | `ColorGlyphCompositeRoute` | `text.gpu.color-plan-unsupported` |
| `BitmapGlyphPlan` | `BitmapGlyphTextureRoute` | `text.gpu.bitmap-route-unsupported` |
| `SVGGlyphPlan` | `SVGGlyphVectorRoute` | `text.gpu.SVG-plan-unsupported` |

When multiple artifacts are provided for the same glyph, the text stack's
selection policy and diagnostics decide semantic priority. The GPU renderer
must not silently pick a different representation unless the text-stack plan
declares that fallback as valid.

## Atlas Mask Route

`AtlasMaskSample` draws glyph quads or instances sampling an A8 atlas.

Rules:

- source artifact must be `GlyphAtlasArtifact`;
- texture format is `R8Unorm` or an explicitly accepted A8-equivalent format;
- sampler is clamp-to-edge, non-mipmapped;
- nearest sampling is default for direct pixel-aligned masks;
- linear sampling is allowed only when artifact policy, transform facts, and
  validation accept it;
- per-glyph instances carry target rect and atlas UV facts;
- coverage multiplies the selected text material/color in WGSL;
- atlas upload must complete before draw;
- atlas generation must match every entry ref;
- stale or missing atlas entries refuse or trigger an accepted rebuild route
  within budget.

A8 mask text may batch across glyph runs only when material, blend, clip,
atlas page bindings, target state, and ordering tokens are compatible.

## SDF Route

`AtlasSDFSample` draws glyph quads or instances sampling an SDF atlas.

Rules:

- source artifact must be `SDFGlyphAtlasArtifact`;
- texture format is `R8Unorm` unless a later text SDF format is accepted;
- WGSL reconstructs coverage from normalized signed distance using
  `GPUTextSDFParams`;
- SDF spread and source resolution must match the artifact dump;
- transform eligibility comes from the text stack and renderer capability
  facts;
- perspective, hairline stroke, non-closed glyphs, unsupported color glyphs,
  and unsupported filter interactions refuse unless
  `23-filter-effect-pipeline.md` accepts the interaction;
- SDF params are uniform per compatible subrun or indexed per instance;
- cache reuse is not correctness evidence without generation and key checks.

LCD SDF is not part of this target. Any request that requires LCD-specific
subpixel reconstruction refuses.

## Outline Glyph Route

`OutlinePathRoute` renders outline glyph plans through GPU path/coverage
contracts.

Rules:

- `OutlineGlyphPlan` supplies immutable glyph path facts, bounds, transform,
  fill rule, and source glyph key;
- the GPU renderer lowers outlines through `19-path-coverage-atlas-strategy.md`
  or accepted path render steps;
- text-specific glyph identity and strike facts remain in text diagnostics;
- path/coverage artifacts created from outlines use typed path/coverage
  artifacts, not glyph atlas entries, unless a later spec accepts a shared
  ownership model;
- route selection must preserve glyph order and material/blend semantics;
- malformed or missing outlines refuse unless text-stack fallback explicitly
  supplied another accepted representation.

Outline glyphs are useful for large vector text, path effects, and glyphs that
are not appropriate for A8/SDF atlas sampling.

## Color Glyph Route

`ColorGlyphCompositeRoute` renders COLR/CPAL plans through renderer primitives.

Rules:

- `ColorGlyphPlan` owns COLR graph evaluation, palette selection, variable
  color facts, bounds, and glyph-scoped diagnostics;
- GPU renderer owns lowering the plan into fills, gradients, clips,
  composites, layers, and destination-read plans;
- every paint primitive must map to an accepted renderer material or refuse;
- composite operations that need prior destination color use
  `GPUDestinationReadPlan`;
- intermediate targets use `GPUTargetTextureDescriptor`;
- unsupported COLRv1 paint operations refuse with stable diagnostics;
- monochrome fallback is allowed only when the text-stack plan declares it.

COLR route support cannot be claimed by merely parsing COLR tables. It needs
GPU route evidence for every promoted paint primitive.

## Bitmap Glyph Route

`BitmapGlyphTextureRoute` draws decoded PNG bitmap glyph artifacts.

Rules:

- `BitmapGlyphPlan` owns PNG decode, strike selection, premul/alpha policy,
  bounds, origin placement, source hash, and diagnostics;
- GPU renderer consumes a typed bitmap glyph texture artifact or upload plan;
- texture ownership follows `18-texture-image-ownership.md`;
- bitmap glyph uploads use `UploadedTextureArtifact` only when the artifact is
  glyph-scoped and typed by the text handoff;
- general image/bitmap codec behavior from
  `22-image-bitmap-codec-pipeline.md` is not counted as bitmap glyph support
  unless a future text spec update explicitly routes embedded glyph images
  through `GPUImageCodecRegistry`;
- sampler, scaling, color conversion, and premul facts must be explicit;
- unsupported non-PNG payloads refuse before GPU route selection.

The renderer must not combine a full text run into one CPU-rendered texture.

## SVG Glyph Route

`SVGGlyphVectorRoute` renders pure Kotlin glyph-scoped SVG plans.

Rules:

- `SVGGlyphPlan` owns SVG parsing, static subset validation, bounds, path
  conversion, gradients, clips, and refusal for external/dynamic features;
- GPU renderer lowers the plan into accepted vector/path/material routes;
- general SVG document rendering remains out of scope;
- unsupported vector primitives, gradients, clips, composites, or filters
  refuse with glyph-scoped diagnostics unless `23-filter-effect-pipeline.md`
  accepts the filter interaction;
- prepared fallback artifacts must be glyph-scoped and typed, never a complete
  CPU-rendered text texture.

## WGSL And Binding ABI

Text render steps use the shared binding model from
`11-wgsl-layout-binding-abi.md`.

Target binding groups:

- material uniforms and color data use the material group;
- transform, clip, and target uniforms use the draw/pass groups;
- text atlas textures, bitmap glyph textures, and samplers use the
  artifact/resource group;
- instance buffers use the vertex/storage binding lane selected by the render
  step;
- destination read bindings, when needed, use `GPUDestinationReadBinding`.

`A8TextMaskStep` WGSL requirements:

- sample A8 coverage;
- apply coverage to text material/color;
- respect premul/alpha policy from `GPUColorPlan`;
- support clip coverage composition when clip route supplies mask facts.

`SDFTextMaskStep` WGSL requirements:

- sample normalized SDF;
- reconstruct coverage from `GPUTextSDFParams`;
- apply antialias width policy;
- preserve material/color/blend semantics;
- refuse if required SDF params are missing.

WGSL modules must be parser-validated through `wgsl4k`. Text shader code must
be generated from registered snippets or explicit modules; no SkSL path is
accepted.

## Material, Blend, And Color

Text color and materials flow through existing material contracts:

- ordinary solid text uses `MaterialKey` color/material facts;
- gradient or shader text uses registered material/snippet routes;
- color glyph primitives may ignore or combine with paint color only when the
  text-stack plan and color glyph plan declare the rule;
- A8/SDF coverage modifies alpha/coverage, not material identity;
- blend state comes from `GPUBlendPlan`;
- destination-dependent blend routes use `GPUDestinationReadPlan`;
- color-space and premul facts come from `GPUColorPlan`.

Text material keys must not include atlas coordinates, glyph IDs, atlas
generation, live texture handles, or upload tokens. Those are payload/resource
facts.

## Clip, Layer, And Destination Reads

Text draws participate in the same planning rules as other draws:

- clip facts are captured in `NormalizedDrawCommand`;
- complex clips may use stencil, coverage masks, path atlas routes, or refusal;
- text subruns can split around clip/layer boundaries;
- layer isolation and saveLayer restore behavior use `GPULayerPlan`;
- color glyph composites may create layer/intermediate work;
- shader text blends and color glyph composites that observe destination color
  use `GPUDestinationReadPlan`;
- text route selection must not sample the active target unless
  `20-destination-read-strategy.md` accepts the route.

Direct-to-parent layer elision is illegal when a text/color glyph route needs
stable backdrop or destination contents and the destination-read plan cannot
prove equivalence.

## Planner, Sorting, And Batching

Text ordering facts:

- glyph upload precedes atlas sampling;
- instance buffer upload precedes draw;
- atlas generation validation precedes draw;
- atlas eviction/compaction cannot move before draws that depend on old
  residency;
- destination-read, clip, layer, and target barriers are honored;
- fallback/refusal diagnostics remain associated with the source text range.

Sorting may reorder text subruns only when:

- visual order is not semantically observable;
- blend, clip, layer, destination-read, and target state allow it;
- atlas pages and resource bindings are compatible or split safely;
- material and pipeline layout match the `GPUTextBatchKey`;
- no upload/eviction/generation token is crossed unsafely.

The planner may split a text run by representation, atlas page, resource
budget, instance buffer budget, clip, layer, destination-read class, or
pipeline compatibility. Splitting must not drop glyphs or change the order of
overlapping glyph coverage when order affects output.

## Resource Lifetime

Text resources are explicit:

- CPU glyph artifacts are keyed by text-stack artifact keys;
- atlas GPU textures are owned by `GPUResourceProvider`;
- atlas textures use `GPUTextureOwnershipPlan` provenance `AtlasTexture`;
- bitmap glyph textures use accepted texture/upload ownership;
- instance buffers are recording/pass resources;
- uniform and binding blocks follow normal payload rules;
- stale atlas or artifact generations refuse or rebuild within budget.

Device loss invalidates GPU resources, not text-stack artifact correctness.
Rebuild uses artifact keys and upload plans; if rebuild cannot happen within
budget, route selection returns stable refusal.

## Artifact Registry

The GPU renderer artifact registry must know these text artifacts:

- `GlyphAtlasArtifact`;
- `SDFGlyphAtlasArtifact`;
- `GlyphUploadPlan`;
- `OutlineGlyphPlan`;
- `ColorGlyphPlan`;
- `BitmapGlyphPlan`;
- `SVGGlyphPlan`.

For each artifact type, registry metadata includes:

- artifact descriptor version;
- key preimage fields;
- compact hash;
- owner subsystem;
- supported GPU routes;
- resource plan builder;
- upload plan builder when relevant;
- diagnostics for missing, stale, unsupported, or budget-exceeded artifacts.

Unregistered artifacts refuse with `text.gpu.artifact-unregistered`.

## Budgets And Telemetry

Text telemetry:

- `DrawTextRun` count;
- glyph run count;
- text subrun count by representation and route;
- glyph instance count;
- atlas page count and bytes;
- atlas upload bytes and upload count;
- instance buffer bytes and allocation count;
- atlas cache hit/miss/eviction;
- stale generation refusal count;
- SDF route count and refusal count;
- outline/color/bitmap/SVG route count and refusal count;
- color glyph composite primitive count;
- text-induced pass split count;
- text route budget refusal count;
- text GPU time and submission time where measurable.

Text budgets are advisory until promoted by a later acceptance update, but
route selection must still enforce hard capability and memory limits.

## Diagnostics

Every accepted or refused text route emits `GPUTextDiagnostic`.

Fields:

- command ID;
- text layout result ID or glyph run ID;
- glyph range or text range;
- representation;
- route;
- render step;
- artifact key hash;
- artifact generation;
- atlas descriptor/page hashes;
- atlas entry refs;
- upload plan ID and byte count;
- instance layout hash and instance count;
- binding layout hash;
- material, blend, color, clip, layer, and destination-read plan IDs;
- ordering token;
- budget decision;
- route outcome or stable refusal reason.

Stable reason-code examples:

- `text.gpu.payload-nondumpable`
- `text.gpu.Sk-type-leaked`
- `text.gpu.artifact-unregistered`
- `text.gpu.artifact-key-nondeterministic`
- `text.gpu.artifact-generation-stale`
- `text.gpu.artifact-budget-exceeded`
- `text.gpu.upload-plan-missing`
- `text.gpu.upload-budget-exceeded`
- `text.gpu.upload-failed`
- `text.gpu.atlas-descriptor-unaccepted`
- `text.gpu.atlas-page-unavailable`
- `text.gpu.atlas-entry-missing`
- `text.gpu.atlas-generation-stale`
- `text.gpu.A8-atlas-route-unavailable`
- `text.gpu.SDF-route-unavailable`
- `text.gpu.SDF-params-missing`
- `text.gpu.SDF-transform-unsupported`
- `text.gpu.outline-route-unavailable`
- `text.gpu.color-plan-unsupported`
- `text.gpu.color-composite-unsupported`
- `text.gpu.bitmap-route-unsupported`
- `text.gpu.SVG-plan-unsupported`
- `text.gpu.LCD-future-research`
- `text.gpu.instance-buffer-budget-exceeded`
- `text.gpu.binding-layout-unavailable`
- `text.gpu.destination-read-unaccepted`
- `text.gpu.clip-route-unaccepted`
- `text.gpu.CPU-rendered-texture-forbidden`

Diagnostics from the text stack remain attached. GPU diagnostics do not replace
font/shaping/glyph diagnostics; they explain renderer route decisions.

## Validation Requirements

Promoted text GPU behavior requires:

- canonical dumps for every owned object in this spec;
- `DrawTextRun` payload tests proving no `Sk*` object leakage;
- artifact registry tests for every text artifact type;
- A8 atlas positive draw fixture;
- SDF atlas positive draw fixture before SDF support is claimed;
- outline glyph route fixture before outline support is claimed;
- color glyph composite fixtures for every promoted COLR primitive;
- bitmap glyph fixture for PNG-backed glyph texture sampling;
- SVG glyph vector fixture for every promoted SVG primitive class;
- unregistered artifact refusal fixture;
- stale atlas generation refusal fixture;
- upload-before-sample ordering fixture;
- atlas eviction/compaction ordering fixture;
- instance buffer layout dump and packing tests;
- WGSL parser/reflection tests for text render steps;
- payload tests proving `GPUTextBinding` stays out of `MaterialKey`;
- material key tests proving atlas coordinates and glyph IDs are not material
  identity;
- planner tests for batching, split, and ordering barriers;
- clip/layer/destination-read interaction tests;
- CPU oracle or text-stack artifact comparison for claimed semantics;
- GPU evidence for every promoted GPU route;
- PM evidence exposing route counts, atlas bytes, upload bytes, instance
  counts, pass splits, and refusals.

External HarfBuzz, FreeType, platform text, and Skia native output may appear
only in non-normative drift reports from the text stack. GPU renderer pass/fail
uses Kanvas-owned fixtures and artifacts.

## Complete Target Support Matrix

| Feature | Target route |
|---|---|
| Shaped A8 mask text | `AtlasMaskSample` through `A8TextMaskStep`. |
| Shaped SDF text | `AtlasSDFSample` through `SDFTextMaskStep`. |
| Direct glyph ID runs | Same routes as shaped runs, using supplied glyph descriptors. |
| Paragraph text | Consumes paragraph-produced glyph descriptors and artifacts. |
| Outline glyphs | `OutlinePathRoute` through path/coverage contracts. |
| COLRv0/COLRv1 glyphs | `ColorGlyphCompositeRoute` for accepted paint primitives. |
| PNG bitmap glyphs | `BitmapGlyphTextureRoute` through texture ownership. |
| SVG-in-OpenType glyphs | `SVGGlyphVectorRoute` through accepted vector/material routes. |
| Emoji sequences | Supported only when text stack emits accepted color/bitmap/SVG/outline artifacts. |
| LCD subpixel text | Future research; stable refusal. |
| CPU-rendered full text texture | Forbidden. |

## Non-Goals

- Do not parse fonts or shape text in the GPU renderer.
- Do not make native font engines normative dependencies.
- Do not port Graphite text classes, `SubRunContainer`, `TextAtlasManager`, or
  render-step code.
- Do not use SkSL.
- Do not support LCD subpixel text as part of this target.
- Do not CPU-render complete unsupported text runs into textures.
- Do not hide atlas uploads or pass splits inside payload gathering.
- Do not put atlas coordinates, glyph IDs, live handles, or upload tokens in
  `MaterialKey`.
- Do not merge glyph atlas, path atlas, coverage atlas, image upload, and
  filter intermediate lifetimes into one untyped atlas.
