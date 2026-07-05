# 01 Rendering Feature Expansion

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

This spec defines how Kanvas expands rendering feature breadth after M59. It
covers the M60-M64 feature families and establishes the evidence expected
before a family is called supported.

## Shared Rules

Every feature family must define:

- supported subset;
- explicit non-goals;
- CPU reference route;
- WebGPU route or stable refusal;
- fallback reason taxonomy;
- PipelineIR operations and normalization rules;
- PipelineKey axes;
- generated WGSL validation path;
- performance counters;
- GM/source scene selection;
- PM demo scene.

WGSL is the shader implementation target for GPU work. SkSL may appear in this
spec only when describing Skia API compatibility or explicit non-goals. A
runtime effect is supportable only through a registered Kanvas descriptor with
Kotlin CPU behavior and generated or registered WGSL that passes parser
validation.

## MEP-NEXT Feature Breadth Slice

The post-RC-MEP `FOR-189` through `FOR-192` slice is PM breadth evidence for
bounded visual families. It may aggregate already generated rows when the rows
have reference, CPU, GPU, diff/stat, route, and refusal artifacts. It must not
turn aggregation into a new broad support claim.

The slice uses `reports/wgsl-pipeline/m89-feature-breadth/evidence.json` as
the headless contract. The contract is valid only while:

- dashboard expectations remain `0 fail` and `0 tracked-gap`;
- image-filter support is limited to selected crop/non-null prepass and
  bounded compose/color-filter/matrix-transform rows;
- clip and Path AA support is limited to selected clip difference, AA clip,
  nested/rrect, and stroke primitive rows already carrying row artifacts;
- bitmap support is limited to selected nearest/local-matrix/subset/repeat and
  fixture-backed replay rows;
- runtime-effect support is limited to registered descriptors with Kotlin CPU
  behavior, parser-validated WGSL, reflected layout, and stable live-edit
  metadata;
- SkSL wording remains compatibility/refusal context only.

Stable non-goals for this slice:

- arbitrary recursive image-filter DAGs and broad picture prepass;
- broad Path AA, broad clip stacks, path boolean parity, and global AA budget
  increases;
- broad image, texture, codec, mipmap, perspective, or color-managed decode
  support;
- arbitrary Skia/SkSL runtime shader input, SkSL compiler, SkSL IR, or SkSL VM.

## M60 Coverage & Path AA Expansion

### Initial Numeric Budgets

These are starting budgets for M60 planning, not global Skia parity claims.
They may move only through generated evidence and sprint review.

| Budget | Initial value | Gate meaning |
|---|---:|---|
| Path verb count | <= 96 verbs | Refuse larger paths with `coverage.verb-budget-exceeded`. |
| Coverage edge count | <= 256 edges | Preserve the existing broad edge-budget guard; promote smaller scoped rows first. |
| Cubic subdivision segments | <= 16 per cubic | Refuse or flatten through CPU-only diagnostics above the limit. |
| Stroke width range | 0.5 px to 64 px | Excludes hairline and very large stroke-outline stress cases. |
| Dash interval count | <= 8 intervals | Refuse larger dash arrays with `coverage.dash-budget-exceeded`. |
| Clip stack depth | <= 4 nested clips | Refuse deeper stacks with `coverage.clip-depth-exceeded`. |
| Device-space bounds | <= 2048 x 2048 px | Avoid promoting giant intermediate coverage rows first. |

### Target Subsets

- quadratic and cubic paths under a bounded segment budget;
- stroked lines, rectangles, rrects, circles, and simple paths;
- common stroke caps: butt, round, square;
- common joins: miter with limit, round, bevel;
- bounded dash patterns;
- nested rectangular/rrect/convex clips;
- AA coverage for simple fill and stroke families.

### Non-Goals

- arbitrary path boolean operations;
- unbounded clip stacks;
- global edge budget increase without family evidence;
- broad hairline/stroke-outline/dash parity;
- perspective path clips unless explicitly scoped.

### Architecture

Coverage lowering produces `CoveragePlan` records:

```text
ShapeVerbStream
  -> PathApproximationPlan
  -> CoveragePlan
  -> CPU span oracle
  -> WebGPU coverage strategy
```

Recommended WebGPU strategies:

- analytic rect/rrect where possible;
- bounded stroke analytic coverage;
- stencil-cover for bounded path subsets;
- explicit refusal for edge-count and unsupported join/cap combinations.

### Acceptance

- at least one GM-derived scene per promoted subset;
- CPU/GPU/reference/diff/stats artifacts;
- edge-count and clip-depth diagnostics in route JSON;
- no broad Path AA support claim from one bounded subset;
- performance payload for at least one heavy promoted row.

## M61 Image Filter DAG V2

### Initial Numeric Budgets

| Budget | Initial value | Gate meaning |
|---|---:|---|
| DAG node count | 2 to 4 nodes | Larger graphs remain `image-filter.dag-node-budget-exceeded`. |
| Blur sigma | <= 12 px | Larger blur remains unsupported until measured. |
| Intermediate textures | <= 4 per row | More intermediates remain refused or CPU-only. |
| Intermediate texture size | <= 2048 x 2048 px | Larger layers require a separate memory budget. |
| Filter children per node | <= 2 | Avoid arbitrary fan-in until graph planning lands. |

### Target Subsets

- offset;
- blur with bounded radius;
- crop;
- color matrix;
- blend/composite of two filter children;
- matrix transform affine;
- two to four node DAGs with explicit intermediate texture ownership.

### Non-Goals

- arbitrary recursive DAGs;
- picture prepass support unless separately implemented;
- unbounded intermediate texture chains;
- implicit readback compatibility;
- filter graph support from copied single-node artifacts.

### Architecture

```text
SkImageFilter graph
  -> ImageFilterDAG
  -> LayerPlan
  -> IntermediateTexturePlan
  -> CPU oracle / WebGPU passes
  -> compose into parent pipeline
```

Each node must declare:

- input bounds;
- output bounds;
- required color type;
- sampling policy;
- texture lifetime;
- fallback reason if refused.

### Acceptance

- DAG graph dump is attached to dashboard row;
- intermediate texture count and bytes are reported;
- GPU route shows pass order;
- unsupported graph shapes remain visible;
- PM demo shows graph diagram plus final image.

## M62 Text & Glyph Rendering V1

Ownership comes from `.upstream/specs/font/`. M62 must not invent a separate
font stack. The deterministic reference font family is the bundled Liberation
set under `reports/font/fixtures/fonts/liberation/`, exposed through the
pure-Kotlin font catalog; host system fonts are not deterministic reference
evidence unless captured as fixtures.

### Target Subsets

- Latin text with simple positioning;
- font loading through bundled Liberation font files;
- glyph masks grayscale coverage;
- CPU glyph raster oracle;
- WebGPU glyph atlas;
- basic transform and scale;
- fallback for missing glyphs.

### Non-Goals

- complex shaping as a first slice;
- full font fallback stack;
- emoji ZWJ sequences;
- color fonts beyond separately selected fixtures;
- LCD/subpixel text unless scoped later.

### Architecture

```text
TextBlob / glyph run
  -> FontResolvePlan
  -> GlyphRunPlan
  -> GlyphMaskPlan
  -> CPU mask composition
  -> WebGPU atlas upload and draw
```

Glyph cache telemetry must include:

- glyph count;
- atlas bytes;
- atlas misses/hits;
- upload count;
- eviction count;
- frame impact.

### Acceptance

- one simple paragraph scene;
- one transformed text scene;
- one missing-glyph/fallback scene;
- all text rows cite `.upstream/specs/font/README.md` and the exact bundled
  Liberation face used as the reference font;
- glyph atlas visualization artifact;
- stable refusal for shaping-heavy text;
- no fake dependency substitute.

## M63 Color, Blend & ColorFilter Parity

### Target Subsets

- common Porter-Duff modes;
- selected advanced blend modes by explicit allowlist;
- color matrix filter;
- blend color filter;
- linear, radial, sweep gradient variants;
- premul/unpremul and clamp behavior;
- destination color-space policy.

### Non-Goals

- all Skia blend modes in one sprint;
- color management beyond declared destination policy;
- silent approximation for unsupported modes;
- shader destination reads without explicit layer/readback plan.

### Architecture

Paint lowering must preserve order:

```text
shader/color
  -> local matrix
  -> color filter
  -> alpha modulation
  -> blender
  -> color-space/store
```

`PipelineKey` may include blend mode, color filter class, shader family,
texture/sampler presence, and attachment format. Uniform values stay out of
the key unless measurement proves specialization is worth it.

### Acceptance

- matrix of blend/color-filter scenes;
- CPU and GPU routes name the same semantic operations;
- generated WGSL parser validation;
- no threshold weakening for color mismatches;
- PM diff grid.

## M64 Registered Runtime Effects

### Target Subsets

- registered effects with known descriptor schema;
- reflected uniform layout;
- CPU implementation for the same descriptor;
- generated or registered WGSL module;
- live parameter editing for PM demo.

### Non-Goals

- dynamic SkSL compilation;
- SkSL IR or VM implementation;
- arbitrary Skia/SkSL runtime shader input as a supported MEP feature;
- treating missing WGSL descriptors as implicit CPU/GPU compatibility.
- unregistered effects silently mapped to approximations;
- GPU-only effect without CPU/reference behavior.

### Architecture

```text
SkRuntimeEffect facade
  -> RuntimeEffectRegistry
  -> EffectDescriptor
  -> CPU evaluator
  -> WGSL module builder
  -> parser reflection
  -> uniform packer validation
```

### Acceptance

- at least three registered effects;
- reflection dump and packer test;
- `wgsl4k` parser/IR/generator behavior is treated as a dependency contract:
  ambiguous or surprising behavior stops the Kanvas assumption and is reported
  as a `ygdrasil-io/wgsl4k` ticket with a minimized input;
- CPU/GPU/reference artifacts;
- unknown effect refusal diagnostic;
- live PM controls for uniforms.

## M87 Registered Runtime Effect Live Editing V2

M87 narrows the live-editing promise to one registered effect before broad
runtime-effect controls are claimed. The selected effect is `runtime.simple_rt`
with WGSL implementation `wgsl/runtime_simple_rt`.

### Target Subset

- `runtime.simple_rt` only;
- `gColor.b` as the first live-editable parameter;
- bounded float range `[0.0, 1.0]` with clamp diagnostics;
- reflected `gColor` uniform layout from the registered WGSL module;
- at least two edited parameter states with CPU/GPU/diff artifacts;
- stable refusals for arbitrary Skia/SkSL runtime shader input and registered
  effects without WGSL descriptors.

### Non-Goals

- dynamic SkSL parsing, compilation, IR, or VM;
- promoting SpiralRT or LinearGradientRT to WGSL-backed GPU support;
- GPU-only runtime-effect support without CPU/reference behavior;
- adding uniform values to `PipelineKey`;
- generating a new shader module per parameter value.

### Acceptance

- parameter metadata names the uniform, component, type, range, default, UI
  constraint, and invalid-value diagnostic;
- the reflected WGSL layout verifies `gColor` offset `0`;
- telemetry records at least two parameter updates and keeps the pipeline key
  stable across them;
- edited-state artifacts include CPU, GPU, diff, and route JSON paths;
- PM wording states that this is selected `SimpleRT` live editing, not broad
  runtime-effect compatibility.
