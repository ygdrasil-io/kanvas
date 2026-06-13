# Material Source And Paint Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the target paint/material-source pipeline for the GPU-first renderer.

This spec closes the gap between normalized paint facts and the existing
`MaterialKey`, `GPUMaterialDictionary`, `WGSLSnippet`, payload, color,
coordinate, and texture contracts. It is target-complete. It is not an
implementation slice and it does not reduce the renderer to solid and linear
materials only.

The target is Graphite-inspired but Kanvas-owned:

- paint source planning is explicit and typed;
- material source behavior becomes `MaterialKey` identity only after
  normalization and validation;
- per-draw source values, texture bindings, gradient stop payloads, and local
  matrices are payload facts unless they change WGSL code shape or layout;
- WGSL is the only shader implementation target;
- unsupported sources refuse with stable diagnostics instead of silently using
  CPU raster or legacy rendering.

## Source Specs

This spec depends on:

- `00-architecture-kernel.md` for module boundary, naming, GPU-first route
  policy, and Graphite equivalence rules;
- `01-normalized-draw-commands.md` for captured material facts on
  `NormalizedDrawCommand`;
- `03-material-key-wgsl.md` for `MaterialKey`, WGSL render module policy, and
  the key/payload boundary;
- `04-pipeline-key-cache-resources.md` for cache, resource, capability, and
  device-generation policy;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `09-draw-family-support-matrix.md` for draw-family support/refusal
  expectations;
- `11-wgsl-layout-binding-abi.md` for WGSL binding and packing ABI;
- `12-blend-color-target-state.md` for final blend, premul, alpha, and target
  color behavior;
- `13-performance-telemetry-cache-gates.md` for material-source counters,
  caches, budgets, and performance gates;
- `14-first-slice-contract.md` for the first rect/rrect solid and linear
  gradient vertical slice;
- `16-material-dictionary-and-snippet-registry.md` for snippet registration,
  decompression, requirement propagation, and assembly plans;
- `17-payload-gathering-and-slots.md` for material payload values, uniform
  slots, texture bindings, gradient stores, and upload plans;
- `18-texture-image-ownership.md` for image source, texture, view, sampler,
  ownership, and sampled binding contracts;
- `22-image-bitmap-codec-pipeline.md` for encoded image decode, animated
  frame, CPU pixel, color/orientation, mip, and upload artifact preparation;
- `23-filter-effect-pipeline.md` for image-filter DAG placement and color
  filter folding boundaries;
- `26-draw-vertices-mesh-pipeline.md` for primitive color and primitive
  blender material inputs;
- `27-registered-runtime-effects-registry.md` for registered material
  runtime-effect descriptors;
- `29-color-management-pipeline.md` for paint colors, gradient interpolation,
  image color conversion, runtime color uniforms, and store/readback behavior;
- `30-coordinate-transform-bounds-policy.md` for local coordinates, local
  matrices, texture coordinate mapping, inverse transforms, and payload
  transform facts.

## Graphite And Skia Evidence

Relevant local source evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/`.

Useful landmarks:

- Graphite `PaintParams` keeps the shading subset of an `SkPaint`. It excludes
  style and complex effects handled higher in the draw pipeline.
- Graphite `ShadingParams::toKey()` builds separate roots for source color,
  final blender, and optional clip contribution. Kanvas keeps the separation:
  this spec owns material source planning, while final blend and clip remain in
  their dedicated specs.
- Graphite `PaintParamsKey` is a compact paint identity that can decompress to
  `ShaderNode` trees through `ShaderCodeDictionary`.
- Graphite `ShaderSnippet`, `SnippetRequirementFlags`, and `ShaderNode`
  demonstrate that snippets declare children, uniforms, textures, sampler
  facts, local-coordinate needs, prior-stage color, destination color,
  primitive color, and gradient-buffer needs.
- Graphite `KeyHelpers` lowers solid colors, paint color, gradients, local
  matrices, image shaders, YUV images, blend shaders, color filters, and
  runtime effects into key blocks plus payload gathered by
  `PipelineDataGatherer`.
- Graphite gradients choose different snippet/layout classes by gradient type
  and stop-storage class: small inline stop arrays, storage-buffer stops when
  supported, or texture-backed stops otherwise.
- Skia gradient factories validate gradient parameters, normalize implicit
  stops, handle degenerate linear/radial/sweep/conical cases, and apply tile
  mode behavior explicitly.
- Skia `SkImageShader` records image source, subset, sampling, tile modes,
  raw/color-managed behavior, and optional local matrix.
- Skia `SkLocalMatrixShader` wraps another shader and changes the coordinates
  used by the wrapped shader.

Kanvas adopts these invariants. Kanvas does not copy Graphite's C++ key stream,
arena ownership, snippet ID layout, SkSL generation, exact precompile model,
Graphite texture proxy classes, or Skia shader class hierarchy.

## Ownership Boundary

This spec owns:

- normalized paint/source descriptor shape inside `:gpu-renderer`;
- paint evaluation order for the material-facing part of a draw;
- source-family classification;
- source validation and simplification before key derivation;
- solid color, paint color, gradient, image shader, local matrix, shader
  blend, and registered runtime-effect material-source plans;
- material tile and sampling requirements when they affect shader behavior,
  binding layout, payload shape, or refusal;
- material-source cache, budget, telemetry, and diagnostics.

This spec does not own:

- public Skia-like API compatibility or legacy `SkPaint` parsing;
- draw style, stroke geometry, path effects, path fill, rrect normalization, or
  geometry coverage route selection;
- final target blend state or destination-read strategy;
- clip stack interpretation;
- image decoding, animated frame composition, CPU pixel upload artifacts, or
  codec selection;
- concrete texture allocation, import, upload, surface leases, or GPU handle
  lifetime;
- color-profile parsing, color transforms, HDR/gainmap policy, or target store
  conversion;
- arbitrary WGSL string loading or dynamic SkSL support.

Compatibility adapters may read legacy paint/shader objects and produce
`GPUPaintDescriptor` values. The core renderer consumes only Kanvas descriptors
under `org.graphiks.kanvas.gpu.renderer`.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUPaintDescriptor` | Captured paint material facts after legacy state interpretation and before renderer source planning. |
| `GPUPaintPipelinePlan` | Ordered material-facing paint evaluation plan: source, primitive color, paint alpha, folded color filter, optional dither/color stage, and final-blend handoff. |
| `GPUPaintStagePlan` | One accepted, skipped, delegated, or refused stage in a paint pipeline plan. |
| `GPUPaintEvaluationOrder` | Stable order descriptor used by diagnostics and plan dumps. |
| `GPUMaterialSourceDescriptor` | Tree-shaped source descriptor: solid, gradient, image shader, local-matrix wrapper, shader blend, registered runtime effect, or refused source. |
| `GPUMaterialSourceKind` | Stable source-family enum used by diagnostics, `MaterialKey` preimages, cache reporting, and support matrices. |
| `GPUMaterialSourcePlan` | Accepted or refused source planning product consumed by `MaterialKey`, dictionary, payload, color, coordinate, and texture planning. |
| `GPUSolidColorPlan` | Solid source plan with color value spec, alpha/premul state, color conversion requirements, payload shape, and snippet identity. |
| `GPUGradientPlan` | Gradient source plan with kind, geometry, tile mode, interpolation, stops, local-coordinate requirements, payload-store strategy, and snippet identity. |
| `GPUGradientKind` | Linear, radial, sweep, two-point conical, or dependency-gated future gradient class. |
| `GPUGradientGeometryPlan` | Finite geometric facts for gradient evaluation: points, centers, radii, angles, normalized unit-space mapping, degeneracy, and inverse needs. |
| `GPUGradientStopPlan` | Canonical stop list, implicit stop insertion, stop ordering, color value specs, interpolation policy, converted payload values, and refusal reasons. |
| `GPUGradientStopStorePlan` | Inline uniform, storage-buffer, texture-backed, or refused stop storage route. |
| `GPUMaterialTileMode` | Material tile mode: clamp, repeat, mirror, decal, or refused/registered extension. |
| `GPUMaterialSamplingPlan` | Material-side sampling facts that affect image/gradient WGSL behavior, sampler descriptors, and payload/binding layout. |
| `GPUImageShaderPlan` | Image material source plan with `GPUImageSourceDescriptor`, subset, tile/sampling policy, color-management requirements, local-coordinate requirements, and texture ownership dependencies. |
| `GPULocalMatrixShaderPlan` | Wrapper plan that composes a local matrix with child source coordinates and records inverse/precision/payload requirements. |
| `GPUShaderBlendSourcePlan` | Shader-side source composition plan for accepted blend-shader style trees before final target blending. |
| `GPUPaintColorPlan` | Paint color contribution plan used by solid sources, alpha modulation, image colorization, primitive-color interaction, and color filters. |
| `GPUMaterialSourcePayloadPlan` | Material-source payload facts: uniforms, gradient stores, texture/sampler bindings, coordinate payloads, and runtime-effect child payloads. |
| `GPUMaterialSourceCachePlan` | Cache identity and invalidation policy for descriptor normalization, source plans, source snippets, and material preimage dumps. |
| `GPUMaterialSourceBudgetPolicy` | Stop-count, payload-byte, texture-binding, child-depth, source-tree, and generated-WGSL budget policy. |
| `GPUMaterialSourceDiagnostic` | Structured accepted/refused diagnostic product for source planning. |
| `GPUPaintPipelineDiagnostic` | Structured accepted/refused diagnostic product for paint-stage ordering and handoffs. |

These names follow the uppercase `GPU`, `CPU`, and `WGSL` acronym policy. Kotlin
import aliases may be used later if a lower-level facade type collides with a
renderer concept name.

## Paint Descriptor Model

`GPUPaintDescriptor` is the renderer-core paint material input. It records:

- paint source descriptor;
- paint color and alpha facts when the paint color participates in source
  evaluation;
- color filter identity or folded color-filter plan reference when accepted;
- primitive color/blender requirements for draw families such as vertices;
- final blend identity reference only as a link to `GPUBlendPlan`, not as
  material-source ownership;
- dither request only when a later accepted color/target plan uses it;
- diagnostic source labels and adapter provenance.

It must not include:

- draw style, stroke width, joins, caps, miter, path effects, dash, or fill
  rule;
- mutable canvas matrix or clip stack state;
- concrete texture handles;
- raw image pixels;
- arbitrary Skia shader objects;
- arbitrary WGSL or SkSL source strings.

Draw style and shape facts are normalized by geometry specs. Final blend facts
are resolved by `12-blend-color-target-state.md` and destination-read specs.
This spec may describe shader-side blend sources, but it does not decide final
target attachment blending.

## Paint Pipeline Order

`GPUPaintPipelinePlan` records the material-facing order in which paint stages
are evaluated. It is inspired by Graphite `ShadingParams::toKey()` but does not
copy its root encoding or SkSL pipeline.

The canonical order is:

1. material source selection: explicit shader/source, image override, or paint
   color source;
2. primitive color composition when the draw family provides primitive color;
3. paint alpha modulation when the paint color alpha affects the source;
4. folded color-filter stage when the filter is accepted as material WGSL;
5. optional dither or target-color helper when an accepted color/target plan
   requires it;
6. final blend handoff to `GPUBlendPlan`;
7. clip handoff to `GPUClipPlan` outside the material source tree.

Only stages 1 through 5 may contribute to `GPUMaterialSourcePlan` and
`MaterialKey`. Stage 6 is a link to blend planning. Stage 7 is clip planning.
The plan must record skipped stages explicitly so diagnostics can distinguish
"not present" from "present but unsupported".

Rules:

- stage ordering must be deterministic for equivalent descriptors;
- a color filter may fold into material only with evidence from
  `23-filter-effect-pipeline.md` and `29-color-management-pipeline.md`;
- primitive color may enter the plan only through the owning draw-family
  descriptor;
- final blend identity may be referenced for handoff diagnostics, but final
  blend state is owned by `GPUBlendPlan`;
- unsupported or ambiguous stage ordering refuses with
  `unsupported.paint_pipeline.stage_order`;
- no stage may ask the CPU to render the full draw into a texture.

## Source Planning Flow

Material source planning runs before `MaterialKey` derivation:

```text
GPUPaintDescriptor
  -> GPUPaintPipelinePlan
  -> GPUMaterialSourceDescriptor
  -> GPUMaterialSourcePlan
  -> MaterialKey source preimage
  -> GPUMaterialDictionary + WGSLSnippet tree
  -> GPUMaterialSourcePayloadPlan
  -> GPUPayloadGatherer
```

The planner must:

- classify the source tree deterministically;
- normalize equivalent descriptors into equivalent plans;
- validate non-finite values, unsupported child trees, unsupported color facts,
  unsupported tile/sampling facts, and budget limits;
- decide which facts are key identity and which facts are payload values;
- emit stable accepted/refused diagnostics;
- never replace unsupported source behavior with a hidden CPU-rendered texture.

If planning refuses, route selection returns `RefuseDiagnostic`. The rejected
draw may still be covered by CPU reference tests, but that is
`CPUReferenceOnly`, not a product fallback.

## `MaterialKey` Boundary

`GPUMaterialSourcePlan` contributes to `MaterialKey` only through
behavior-affecting identity:

- source kind;
- source-tree shape and child-slot structure;
- snippet IDs or registered descriptor IDs;
- gradient kind;
- gradient stop-count class and storage-layout class;
- interpolation mode when it changes WGSL behavior;
- tile mode class when it changes WGSL behavior or sampler layout;
- image source descriptor class and sampled layout class;
- sampler descriptor facts only when they affect WGSL code, binding shape, or
  pipeline validity;
- local-coordinate and local-matrix helper class when it changes WGSL code,
  layout, or precision requirements;
- color-space and alpha-domain requirements that affect WGSL code shape;
- runtime-effect descriptor ID/version and route contract;
- source plan version and feature flags.

It must not include:

- concrete color values;
- concrete gradient stop colors or offsets unless a future accepted route proves
  they are compile-time constants;
- concrete matrix values unless they change WGSL code shape or layout class;
- texture object handles, resource refs, imports, leases, upload artifact keys,
  pixels, cache pointers, or generation IDs;
- payload slot IDs;
- pass-local resource binding slots;
- CPU-rendered fallback artifacts.

Concrete values are gathered through `GPUMaterialSourcePayloadPlan` and
`GPUPayloadGatherer`.

## Solid And Paint Color Sources

Solid color is a target-required material source.

`GPUSolidColorPlan` records:

- `GPUColorValueSpec` for the input color;
- alpha domain: unpremul, premul, opaque, or refused;
- paint-alpha modulation requirements;
- color-space conversion plan references from `29-color-management-pipeline.md`;
- payload layout and `WGSLSnippet` identity;
- opacity proof when available.

Acceptance rules:

- color channels and alpha must be finite;
- color value spec must be known;
- untagged colors must follow the accepted untagged policy from color
  management;
- premul/unpremul conversion must be explicit;
- target conversion, clamp, tone-map, or refusal behavior must be explicit.

A solid source may be optimized into a constant snippet only when this does not
erase color-management, alpha, or diagnostics requirements. A clear blend may
normalize to transparent source behavior only through an explicit adapter or
blend-plan rule, not by this source planner guessing final blend semantics.

`GPUPaintColorPlan` is the reusable plan for paint color as an input to image
colorization, primitive-color composition, alpha modulation, and color-filter
folding. It uses the same color value and payload rules as solid sources.

## Gradient Sources

Gradients are target material sources. The target surface includes:

| Gradient kind | Target status | Required behavior |
|---|---|---|
| Linear | `TargetRequired` | Two finite endpoints, local-coordinate evaluation, stop normalization, tile mode, interpolation, color conversion, and payload route. |
| Radial | `TargetRequired` | Finite center and radius, degenerate-radius policy, stop/tile/interpolation/color behavior, and local-coordinate evaluation. |
| Sweep | `TargetRequired` | Finite center and angle/t-range facts, wrap/clamp policy, degenerate-angle policy, and local-coordinate evaluation. |
| Two-point conical | `TargetRequired` | Finite centers/radii, normalized evaluation form, radial-degenerate cases, tile/decal behavior, and refusal for unrepresented focal cases. |
| Registered/custom gradient | `RefuseDiagnostic` until descriptor-backed | No ad hoc shader generation; a registered descriptor and evidence are required. |

An implementation slice may promote a smaller subset, but diagnostics must
distinguish unsupported kind, invalid geometry, unsupported storage, unsupported
interpolation, unsupported tile mode, and budget refusal.

### Gradient Geometry

`GPUGradientGeometryPlan` records:

- gradient kind;
- points, center(s), radius/radii, angle range, bias/scale, or equivalent
  normalized evaluation parameters;
- local-to-gradient and gradient-to-local mapping facts;
- whether an inverse transform is required and accepted;
- finite proof;
- degeneracy classification;
- WGSL helper class;
- coordinate payload requirements.

Rules:

- non-finite points, radii, angles, scales, or matrices refuse;
- negative radii refuse unless an accepted normalization proves equivalence;
- singular local matrices refuse unless the source degenerates to a supported
  solid behavior before matrix inversion is needed;
- degenerate gradients must be normalized or refused explicitly;
- perspective local-coordinate evaluation is dependency-gated until the WGSL
  helper, payload precision, and evidence are accepted.

Degenerate behavior follows a deterministic descriptor policy inspired by Skia
gradient factories:

- degenerate clamp linear/radial defaults to the stable terminal-color behavior
  defined by the accepted `GPUGradientDegeneratePolicy`;
- repeat and mirror degeneracy may normalize to an average-color solid only
  when the averaging policy and color space are validated;
- decal degeneracy may normalize to transparent or empty only when coverage and
  alpha behavior are explicit;
- special sweep and conical clamp cases may normalize to a new gradient with
  inserted hard stops only when the transformation is recorded in
  `GPUGradientStopPlan`.

The policy must be dumpable. Hidden best-effort simplification is not allowed.

### Gradient Stops

`GPUGradientStopPlan` records:

- original stop count;
- canonical stop count;
- implicit start/end stop insertion;
- position source: explicit or evenly distributed;
- monotonic normalization and clamping policy;
- duplicate and hard-stop handling;
- color value specs for every stop;
- interpolation space, hue method, and premul interpolation flag;
- converted payload values and color-management plan references;
- precision and tolerance facts for CPU/WGSL comparison.

Rules:

- at least one color is required before normalization;
- stop positions must be finite when present;
- positions outside `[0, 1]` are either clamped according to the accepted
  policy or refused;
- decreasing positions are either canonicalized by a documented monotonic rule
  or refused;
- color-space conversion must occur through `GPUGradientColorPlan`;
- unsupported interpolation spaces refuse with a specific reason.

The source planner may expand a one-color gradient into a two-stop solid-like
gradient only when diagnostics preserve the original descriptor and the target
color-management policy accepts the equivalence.

### Gradient Stop Storage

`GPUGradientStopStorePlan` chooses how stop data reaches WGSL:

| Store class | Policy |
|---|---|
| `InlineUniformStops` | Preferred for small bounded stop counts when ABI packing and reflection agree. |
| `StorageBufferStops` | Accepted when `GPUCapabilities` exposes the required storage-buffer lane and payload budgets allow it. |
| `TextureBackedStops` | Accepted only when texture format, precision, sampler, upload/artifact policy, and color-management evidence are available. |
| `Refuse` | Required when no store route can prove precision, budget, or binding behavior. |

Graphite uses small inline stops and larger buffer/texture routes. Kanvas keeps
the strategy but owns the thresholds, ABI, and diagnostics. The first promoted
thresholds must be written in `GPUMaterialSourceBudgetPolicy`; they must not be
implicit constants hidden in shader code.

## Image Shader Sources

Image shader sources are target material sources, but concrete image
preparation and texture ownership are owned by image and texture specs.

`GPUImageShaderPlan` records:

- `GPUImageSourceDescriptor`;
- source kind: GPU-resident texture, uploaded CPU image artifact, imported
  texture, surface/target lease, atlas source, filter intermediate, or refused;
- subset and domain;
- tile modes for X and Y;
- sampling class: nearest, linear, mipmapped, cubic, anisotropic when accepted,
  or refused;
- raw vs color-managed sampling behavior;
- alpha-only colorization behavior when accepted;
- color-management plan references;
- local-coordinate and texture-coordinate mapping requirements;
- texture/view/sampler binding requirements;
- `GPUTextureOwnershipPlan` dependency;
- payload and snippet identity.

Acceptance rules:

- source dimensions and subset must be finite and non-empty after accepted
  normalization;
- active render attachment sampling refuses unless an accepted
  destination/layer copy route provides a separate sampled texture;
- CPU pixel and encoded image sources must become `UploadedTextureArtifact`
  through `22-image-bitmap-codec-pipeline.md`;
- texture ownership, usage, view, sampler, generation, and lifetime must be
  accepted before the source route is promoted;
- swizzle, origin, premul/unpremul, profile, CICP, YUV/YUVA, HDR, gainmap, and
  orientation behavior must be explicit through image/color/texture specs;
- unsupported sampling or tile modes refuse with source-specific diagnostics.

Image material keys may include image source class, tile/sampling class, sample
type, binding layout, raw/color-managed behavior, and required helper class.
They must not include concrete texture refs, imported handles, surface leases,
upload artifact keys, or pixels.

## Tile Modes And Sampling

`GPUMaterialTileMode` supports the Graphite/Skia tile surface:

| Tile mode | Target behavior |
|---|---|
| `Clamp` | Clamp to the source domain or edge color according to the source family. |
| `Repeat` | Repeat the source domain periodically. |
| `Mirror` | Repeat with alternating mirrored periods. |
| `Decal` | Outside-domain samples evaluate as transparent black before later color/blend steps. |

Tile mode support is source-specific:

- gradients evaluate tile modes in WGSL;
- image shaders may use hardware sampler address modes only when the selected
  route proves equivalence for subset, decal, origin, and filtering behavior;
- if hardware sampler behavior is insufficient, the source must use a WGSL
  helper with clamp/decal/repeat/mirror semantics or refuse;
- unsupported decal behavior must not silently downgrade to clamp.

`GPUMaterialSamplingPlan` records material-side sampling facts that influence
WGSL helper selection, binding layout, payload, or diagnostics. Resource-level
sampler descriptors remain owned by `18-texture-image-ownership.md`.

Sampling support must distinguish:

- nearest;
- linear;
- mipmapped;
- cubic/resampler;
- anisotropic;
- source-specific filtering optimizations;
- unsupported or capability-gated classes.

Pixel-aligned sampling optimizations are allowed only when
`30-coordinate-transform-bounds-policy.md` proves the transform and bounds
conditions and the optimization is visible in diagnostics. They must not change
material key identity unless they change code shape or binding layout.

## Local Matrix Wrappers

`GPULocalMatrixShaderPlan` wraps another `GPUMaterialSourcePlan` and changes
the source coordinates used by that child.

It records:

- child source plan ID or refused child diagnostic;
- local matrix descriptor;
- inverse transform plan;
- composition with image origin, gradient unit-space mapping, atlas mapping, or
  filter/layer origin when relevant;
- affine vs perspective helper class;
- payload matrix layout;
- precision and finite proof.

Rules:

- a non-invertible local matrix refuses unless an accepted source-specific
  simplification removes the need for inverse sampling;
- affine local matrices may use compact payload layouts when WGSL reflection and
  CPU packing agree;
- perspective local matrices are dependency-gated until helper, precision,
  varying/payload, and conformance evidence are accepted;
- local matrix values are payload facts unless they change helper class,
  required ABI, or pipeline validity.

Nested local matrices may be composed during planning only when the composition
is deterministic, finite, and preserves diagnostic provenance.

## Shader Blend And Source Composition

Shader-side source composition is distinct from final target blending.

`GPUShaderBlendSourcePlan` covers material source trees equivalent to
blend-shader style composition: a blender combines two child source colors
inside the material tree. It records:

- blend operator or registered blender descriptor;
- source child plan;
- destination child plan;
- prior-stage color and destination-color requirements;
- color-space and alpha-domain requirements;
- whether the composition can stay inside material WGSL without a
  destination-read route;
- diagnostics for unsupported child, blender, or color behavior.

Fixed source composition may be represented as `WGSLSnippet` nodes when it does
not require reading the target attachment. Destination-dependent blending that
needs the current target color is owned by `GPUBlendPlan` and
`GPUDestinationReadPlan`. A material source may request such a requirement, but
it cannot create framebuffer-fetch behavior on its own.

Unregistered arbitrary blenders refuse. Registered runtime-effect blenders must
use `GPURuntimeEffectRegistry` and route contracts from
`27-registered-runtime-effects-registry.md`.

## Color Filters And Runtime Effects

Color-filter interaction has two valid placements:

- folded into `MaterialKey` as a material color-filter snippet when the filter
  chain is accepted, bounded, and does not require a filter intermediate;
- represented in `GPUFilterPlan` when it belongs to an image-filter DAG or
  layer/filter route.

`GPUMaterialSourcePlan` may reference an accepted folded color-filter plan, but
the detailed filter DAG, intermediate allocation, and filter-node routing remain
owned by `23-filter-effect-pipeline.md`.

Registered runtime-effect material sources are accepted only when:

- compatibility lookup resolves to a registered descriptor;
- descriptor kind allows material-source placement;
- WGSL plan is parser-validated and reflected;
- uniform schema and child slots match the payload and source tree;
- CPU oracle exists for evidence;
- route contract and budgets accept the use.

Unknown Skia/SkSL runtime source, arbitrary WGSL strings, missing descriptors,
kind mismatches, or missing CPU oracle refuse with stable diagnostics.

## Primitive Color Inputs

Some draw families provide primitive color, vertex color, atlas color, or text
color as part of geometry or instance data. This spec does not own those
attributes, but it owns their material-source interaction.

`GPUPaintColorPlan` and `GPUShaderBlendSourcePlan` may consume primitive color
only through descriptor facts produced by the owning draw-family spec:

- `26-draw-vertices-mesh-pipeline.md` owns vertex colors and primitive
  blending;
- `21-text-glyph-pipeline.md` owns text/glyph color payload facts;
- atlas, image, and layer specs own their resource-origin colors.

Primitive color facts become `MaterialKey` identity only when they change
source tree shape, snippet requirements, varying layout, or binding ABI.
Concrete primitive color values remain vertex/instance/payload facts.

## Payload Contract

`GPUMaterialSourcePayloadPlan` describes the concrete values needed by an
accepted material source:

- solid and paint color uniforms;
- alpha modulation uniforms;
- gradient geometry uniforms;
- gradient stop inline arrays, storage-buffer entries, or texture-backed stop
  bindings;
- image source subset, inverse dimensions, tile/sampling data, swizzle/origin
  helpers, and texture/sampler bindings;
- local matrix and inverse matrix payloads;
- runtime-effect uniform blocks and child bindings;
- color-transform uniforms or LUT bindings from color management;
- coordinate payloads from transform/bounds planning.

Rules:

- payload layout must be declared by `WGSLSnippet` metadata and validated by
  `WGSLBindingLayout` and `WGSLPackingPlan`;
- payload values must be independent of durable key identity unless a source
  plan explicitly marks them as compile-time layout facts;
- payload gathering must consume only accepted plans from this spec and related
  color/coordinate/texture/runtime-effect specs;
- payload slots are pass-local and must not leak into `MaterialKey`.

## Dictionary And WGSL Integration

Every accepted material source maps to one or more `WGSLSnippet` nodes.

Required source snippet categories:

- solid color source;
- paint color source;
- linear gradient source;
- radial gradient source;
- sweep gradient source;
- conical gradient source;
- gradient stop sampling helper;
- image shader source;
- image tile/sampling helper;
- local matrix coordinate helper;
- shader blend/source composition helper;
- folded color-filter helper;
- registered runtime-effect material contribution.

The dictionary may provide multiple snippet variants for one logical source
family when layout or requirements differ. Examples include gradient stop
count/storage classes, image sampling classes, local-matrix affine vs
perspective classes, and color-management helper classes.

WGSL assembly rules:

- snippets declare child count and child invocation semantics;
- source roots must produce a color in the accepted working color domain;
- requirements propagate deterministically through child nodes;
- unsupported aggregate requirements refuse before module assembly;
- complete assembled modules must validate through `wgsl4k`;
- fragment-only validation is not support evidence.

## Cache, Budget, And Telemetry

`GPUMaterialSourceCachePlan` defines cache keys for:

- source descriptor normalization;
- source plan lookup;
- gradient stop canonicalization;
- gradient stop store allocation;
- image shader descriptor planning;
- local matrix helper classification;
- material-source diagnostics;
- material source to `MaterialKey` preimage dumps.

Cache keys must include descriptor versions and capability facts that affect
accepted behavior. They must not include concrete resource handles, object
addresses, payload slot IDs, or raw pixels.

`GPUMaterialSourceBudgetPolicy` declares:

- maximum source tree depth;
- maximum child count;
- maximum gradient stop count per store class;
- maximum inline uniform bytes;
- maximum material-owned storage-buffer bytes;
- maximum texture-backed gradient stop dimensions;
- maximum sampled texture bindings per material source;
- maximum runtime-effect uniform and child payload sizes;
- maximum WGSL snippet expansion size;
- refusal behavior when a budget would be exceeded.

`GPUTelemetryLedger` must record, when touched:

- material source descriptor count by kind;
- accepted/refused source plan counts by kind;
- solid, gradient, image shader, local matrix, blend source, color-filter, and
  runtime-effect source counts;
- gradient kind, stop-count class, stop-store class, tile mode, interpolation,
  and refusal counters;
- image shader source kind, sampling class, tile mode, ownership dependency,
  and refusal counters;
- material source cache hits/misses;
- material source payload bytes;
- material-source-induced WGSL module variants;
- material source budget refusals.

## Stable Diagnostics

`GPUMaterialSourceDiagnostic` records:

- command ID and source label;
- source kind and source path within the tree;
- paint stage and evaluation-order facts when relevant;
- accepted/refused route;
- reason code;
- relevant source descriptor preimage;
- related color, coordinate, texture, runtime-effect, dictionary, payload, or
  capability diagnostics;
- whether the outcome is product route, `CPUReferenceOnly`, or refusal.

Stable reason code examples:

- `unsupported.material_source.unknown`
- `unsupported.material_source.tree_depth`
- `unsupported.material_source.child_count`
- `unsupported.material_source.payload_budget`
- `unsupported.material_source.wgsl_validation`
- `unsupported.material_source.snippet_missing`
- `unsupported.paint_pipeline.stage_order`
- `unsupported.paint_pipeline.color_filter_chain`
- `unsupported.paint_pipeline.filter_fold_unproven`
- `unsupported.paint_pipeline.requirement_unaccepted`
- `unsupported.paint_pipeline.cpu_rendered_texture_forbidden`
- `unsupported.solid.non_finite`
- `unsupported.solid.color_value_spec`
- `unsupported.gradient.kind`
- `unsupported.gradient.geometry_non_finite`
- `unsupported.gradient.negative_radius`
- `unsupported.gradient.degenerate`
- `unsupported.gradient.stop_count`
- `unsupported.gradient.stop_position_non_finite`
- `unsupported.gradient.stop_order`
- `unsupported.gradient.interpolation_space`
- `unsupported.gradient.tile_mode`
- `unsupported.gradient.stop_store`
- `unsupported.gradient.payload_budget`
- `unsupported.image_shader.source`
- `unsupported.image_shader.subset`
- `unsupported.image_shader.texture_ownership`
- `unsupported.image_shader.active_attachment`
- `unsupported.image_shader.sampling`
- `unsupported.image_shader.tile_mode`
- `unsupported.image_shader.color_management`
- `unsupported.local_matrix.non_invertible`
- `unsupported.local_matrix.perspective`
- `unsupported.shader_blend.unregistered`
- `unsupported.shader_blend.destination_read`
- `unsupported.color_filter.material_fold`
- `unsupported.runtime_effect.unregistered`
- `unsupported.runtime_effect.kind`
- `unsupported.runtime_effect.child_slot`
- `unsupported.runtime_effect.cpu_oracle_missing`

Refusal codes must be specific enough for conformance fixtures and PM reports.
Generic `unsupported.material_source.unknown` is allowed only when the adapter
cannot classify the source at all.

## Validation And Evidence

Promotion evidence for a material source requires:

- normalized descriptor dump;
- `GPUMaterialSourcePlan` dump;
- `MaterialKey` preimage dump;
- `GPUMaterialDictionary` and `WGSLSnippetNode` dump;
- `GPUMaterialAssemblyPlan` dump;
- complete WGSL module validation and reflection through `wgsl4k`;
- `WGSLBindingLayout` and `WGSLPackingPlan` dump;
- `GPUMaterialSourcePayloadPlan` and `GPUPayloadGatherer` dump;
- color, coordinate, texture, runtime-effect, or destination-read companion
  diagnostics when touched;
- CPU oracle or reference evidence where required;
- GPU evidence or explicit refusal;
- budget and telemetry evidence for promoted realtime paths.

Correctness promotion and performance promotion are separate. A material source
may be correct but not realtime-promoted until cache, upload, payload, and
module-variant gates are measured.

## First Slice Contract

The first rect/rrect vertical slice promotes only:

- finite solid color sources with accepted color value specs;
- finite linear gradients;
- accepted stop normalization for the chosen fixture set;
- accepted interpolation lane documented by `GPUGradientColorPlan`;
- accepted tile mode subset, initially clamp unless a broader tile-mode route
  has conformance evidence;
- affine or identity local-coordinate requirements accepted by
  `30-coordinate-transform-bounds-policy.md`;
- inline uniform stop storage or a documented first-slice
  `GPUGradientStopStorePlan`;
- `GPUMaterialDictionary` snippets for solid and linear gradient sources;
- payload gathering and WGSL ABI validation for those sources.

The first slice refuses, with stable diagnostics:

- radial, sweep, conical, image shader, shader blend, folded color-filter, and
  runtime-effect material sources unless explicitly promoted by the slice;
- unsupported tile or interpolation modes;
- non-finite colors, stops, endpoints, local matrices, or bounds;
- non-invertible local matrices;
- stop counts or payload sizes outside the accepted first-slice budget.

This sequencing does not narrow the target surface above.

## Non-Goals

This spec does not:

- port Graphite or Ganesh;
- reimplement SkSL, SkSL IR, or SkSL VM;
- define a new graphics backend abstraction below the existing `GPU` facade;
- allow hidden full CPU fallback;
- make image decoding or texture upload part of material identity;
- make style/stroke/path geometry part of paint source planning;
- accept arbitrary runtime shader source strings;
- use source hashes alone as support identity;
- claim support without diagnostics, WGSL validation, payload ABI evidence, and
  route evidence.
