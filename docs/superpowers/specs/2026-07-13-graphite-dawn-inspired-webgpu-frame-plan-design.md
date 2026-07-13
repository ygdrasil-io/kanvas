# Graphite/Dawn-inspired WebGPU frame plan design

**Date:** 2026-07-13

**Status:** Approved for implementation planning — independently validated

**Scope:** Replace immediate per-operation GPU composition with one measured,
linear WebGPU frame path for offscreen and window rendering. Prefer exact
fixed-function blending, use bounded GPU-only destination snapshots when
required, preserve MSAA contents across pass breaks, and submit one command
buffer per scene render.

## Goal

Bring Kanvas closer to Graphite/Dawn performance without porting Graphite,
Ganesh, or their multi-backend task graph.

Kanvas has one GPU backend, WebGPU through wgpu4k. The design therefore keeps
the target architecture already defined under `.upstream/specs/gpu-renderer/`
and adopts only the useful performance invariants visible in Graphite:

- classify blend and coverage before native execution;
- keep exactly representable blends in the current render pass;
- read destination pixels only when the exact formula requires them;
- copy only bounded destination pixels that a shader will read;
- keep destination access on the GPU;
- reuse transient textures;
- preflight the complete frame before encoding;
- record one scene in one command encoder and submit once;
- release resources only after real GPU completion;
- derive diagnostics and evidence from the plan and resources actually used.

The target flow is:

```text
DisplayList / DisplayOp sequence
  -> GPUOpMapper
  -> NormalizedDrawCommand sequence
  -> GPUDrawAnalysis + GPUGeometryPlan/GPUClipPlan coverage lowering
  -> canonical GPUBlendPlan/GPUColorPlan/GPUTargetState specialization
  -> GPURecording
  -> GPUTaskList
  -> GPUFramePlanner finalization
       -> render-pass segments
       -> bounded destination-snapshot groups
       -> layer/filter/target transitions
       -> readback or surface-blit output
  -> GPUFramePreflighter
  -> PreparedGPUFrame
  -> GPUFrameExecutor
       -> one command encoder
       -> one command buffer
       -> one queue submission
  -> asynchronous GPU completion
```

## Authority and compatibility boundary

This design follows `.upstream/specs/gpu-renderer/`, especially its target
authority, blend, destination-read, coverage, resource, task, and command
contracts. For new `:gpu-renderer` work, that pack explicitly makes
`NormalizedDrawCommand`, analysis, material, pipeline, resource, task, and
diagnostic contracts authoritative.

`KanvasPipelineIR` remains legacy/migration and CPU-reference context. This
design does not make it the new renderer's semantic center. It also does not
create a third blend authority:

- the existing `GPUBlendPlan` is evolved to cover all 29 current blend modes
  and the exact coverage-dependent route;
- `GPUDestinationReadPlan` remains the owner of snapshot/intermediate/refusal
  strategy after `GPUBlendPlan` declares the need;
- `GPUGeometryPlan`/`GPUGeometryRoute` and `GPUClipPlan` remain the active GPU
  geometry and coverage authorities; legacy `GeometryPlan`/`CoveragePlan`
  values are migration and CPU-oracle inputs translated once at the adapter
  boundary;
- `GPURecording` and `GPUTaskList` remain the immutable recording and ordered
  task authorities; `GPUFramePlan` is their executable projection, not a
  parallel task model;
- `GPUOpMapper` does not reconstruct blend or coverage semantics;
- the duplicate blend-mode enums and boolean routing authorities are removed
  after migration.

The stricter reference, CPU/GPU evidence, WGSL validation, route diagnostic,
and fallback requirements from the older WGSL and geometry-coverage packs
remain applicable.

## Context and current problem

Commit `2d2764415` made blend and geometry-coverage composition correct across
the mapped drawing APIs, but it also made `coverageCompositionRequired` route
many antialiased operations through `destinationReadComposer`. That path can
create a source surface, copy the destination, and execute a fullscreen
three-texture formula pass even for common `SrcOver` draws.

The native runtime compounds the cost:

- `copyOffscreenTexture()` creates, finishes, and submits its own encoder;
- `encodeOffscreenTexture()` creates and submits another encoder;
- `snapshotTargetToOffscreenTexture()` calls `readRgba()`, waits for CPU
  mapping, then uploads the bytes into a texture;
- offscreen and primary-target routes use different snapshot behavior;
- `GPUDestinationReadExecutor` can report copy statistics without executing
  the matching native strategy;
- `GPUQueueManager` currently treats `present()` as queue completion even
  though presentation is not proof that GPU work has finished.

The visible result is a performance cliff for draw-heavy GMs. In the measured
snapshot that motivated this design, `hairmodes` takes about 10.2 seconds,
`aaxfermodes` about 2.4 seconds, and `xfermodes` about 1.35 seconds. The main
cost is the multiplication of intermediate passes, copies, encoders, queue
submissions, and CPU/GPU synchronization, not blend arithmetic alone.

## Graphite/Dawn reference and explicit differences

The reference checkout is Skia commit
`defc3a5a92966c32cb2a6a901e2fa3036a13bb8a` under
`/Users/chaos/workspace/kanvas-forge/skia-main`.

Relevant reference points are:

- `src/gpu/graphite/ContextUtils.cpp`: `CanUseHardwareBlending()` checks
  coverage, dual-source requirements, target clamping, and advanced-blend
  capabilities before selecting hardware blending.
- `src/gpu/BlendFormula.cpp`: coverage-aware Porter-Duff output formulas show
  which cases need dual-source blending and which can use one attachment
  output.
- `src/gpu/graphite/PaintParams.cpp`: `DstUsage` separates painter-order
  dependence from a real shader destination read.
- `src/gpu/graphite/ResourceTypes.h` and `Caps.cpp`: `DstReadStrategy` selects
  framebuffer fetch only when available, otherwise a texture copy.
- `src/gpu/graphite/dawn/DawnCaps.cpp`: Dawn advertises framebuffer fetch,
  partial resolve, and load-resolve behavior only when the device exposes the
  corresponding feature.
- `src/gpu/graphite/Device.cpp`: the texture-copy destination strategy flushes
  before a destination-reading draw.
- `src/gpu/graphite/DrawContext.cpp`: one bounded `DstCopy` is created for the
  pending draw pass.
- `src/gpu/graphite/DrawList.cpp`: draws are collected and made immutable before
  pass execution; reset after a snapped pass uses `LoadOp::kLoad`.
- `src/gpu/graphite/RenderPassDesc.cpp` and
  `src/gpu/graphite/dawn/DawnCommandBuffer.cpp`: Graphite/Dawn handles MSAA
  continuation through load-resolve features or explicit load/resolve
  emulation when a fresh MSAA attachment cannot preserve old pixels.
- `src/gpu/graphite/Image_Graphite.cpp`: non-copyable but texturable sources can
  use GPU copy-as-draw; non-copyable and non-texturable sources fail rather
  than read through the CPU.
- `src/gpu/graphite/ShaderInfo.cpp`: destination sampling and coverage-aware
  blend math are fused into the fragment shader.
- `src/gpu/graphite/dawn/DawnCommandBuffer.cpp`: copies and render passes are
  recorded on a command encoder rather than submitted by blend code.

The following are deliberate Kanvas decisions, not claims about Graphite:

- one command buffer and one `queue.submit()` per scene render;
- a linear frame plan instead of Graphite's task DAG;
- safe sharing of a destination snapshot across proven-disjoint draws;
- a persistent frame-local MSAA attachment instead of Dawn's load-resolve
  extensions or emulation;
- exact partial-coverage `Plus` instead of Graphite's allowed approximation on
  clamped formats;
- one WebGPU capability lane instead of Graphite's backend hierarchy.

Graphite's texture-copy route commonly flushes before each destination-reading
draw. Snapshot sharing is therefore a Kanvas optimization inspired by deferred
recording, not observed Graphite behavior.

## Decisions

### One canonical GPU scene texture

Every target renders toward an internal single-sample WebGPU texture with:

```text
RenderAttachment | TextureBinding | CopySrc | CopyDst
```

This texture is the canonical resolved destination for both offscreen GMs and
window rendering. Layer and filter targets use the same descriptor model.

- A GM appends a texture-to-staging copy to the frame encoder, submits once,
  waits for that submission, then maps and depads the staging buffer.
- A window appends a render pass that samples the canonical texture and writes
  the acquired surface texture.
- The surface texture is never a destination-read source.
- `snapshotTargetToOffscreenTexture()` and its CPU readback/upload loop are
  removed.
- Primary and offscreen rendering use the same blend, clip, layer, filter,
  destination-read, and frame execution path.

The target is recreated on resize, format, color-space, sample-plan, or device
generation change. Superseded resources remain retained through the last real
GPU completion that references them.

### Explicit MSAA continuation

A fresh transient MSAA attachment cannot load pixels from a single-sample
resolve texture through portable WebGPU. Resolving a partial draw from such an
attachment would overwrite unchanged scene pixels. The frame planner therefore
produces one `MSAAContinuationPlan` per target:

```text
SingleSampleFrame
PersistentFrameAttachment(sampleCount, colorLease, depthStencilLease)
RetainedTargetAttachment(sampleCount, targetGeneration, colorLease, depthStencilLease)
Refuse(reason)
```

Rules:

1. A target uses one sample plan for its whole active frame interval. The
   planner never alternates direct single-sample writes and unrelated MSAA
   writes to the same canonical target.
2. `PersistentFrameAttachment` is valid when the target begins with clear or
   discard semantics. The first pass clears or discards the MSAA attachment;
   later pass segments use `LoadOp.Load` and `StoreOp.Store` on the same
   attachment. Every producing pass resolves to the canonical texture.
3. The MSAA color attachment and required depth/stencil attachment survive
   destination-copy pass breaks, filter/layer scheduling gaps, and pipeline
   changes until the target's final pass.
4. A preserve-load target may use `RetainedTargetAttachment` only when the
   matching MSAA attachment has the same target/device generation and remained
   authoritative alongside its resolve texture. Otherwise it must choose a
   proven exact single-sample coverage lowering or refuse.
5. A target that combines shader destination reads with sample-based AA uses
   `SingleSampleFrame` only when every affected GPU geometry/clip coverage plan
   has a proven equivalent analytic, stencil-1x, or sampled-mask lowering. If not, the
   affected atomic scope refuses with
   `unsupported.blend.msaa_destination_read_exactness`.
6. No route silently drops AA, samples a multisample attachment as a normal
   texture, or assumes Dawn's `ExpandResolveTexture` extension exists.

This simpler mono-backend policy preserves unchanged pixels without porting
Graphite's load-from-resolve machinery.

### No executable framebuffer-fetch branch

The wgpu4k/WebGPU facade used by Kanvas does not expose framebuffer fetch. The
live strategy model therefore has no inactive executable `FramebufferFetch`
case.

Kanvas retains:

- the Graphite/Dawn explanation of why the feature can be faster;
- stable refusal
  `unsupported.destination_read.framebuffer_fetch_unavailable` for an explicit
  request;
- a negative capability test proving the feature is not claimed.

A future real, testable facade feature enters as a new typed plan with native
pixel and performance evidence. No placeholder route is added now.

### No CPU destination fallback

This design explicitly amends the generic ordering in
`.upstream/specs/gpu-renderer/20-destination-read-strategy.md` for the mono-
backend frame path. Destination access follows this GPU-only order:

```text
NoDestinationRead
  -> FixedFunctionAttachmentBlend
  -> LayerCompositeIsolation when layer/filter semantics require isolation
  -> SampleExistingIntermediate
  -> TargetCopySnapshot(NativeTextureCopy | CopyAsDrawMaterialization)
  -> RefuseDiagnostic
```

The amendment moves an exact existing intermediate before a new copy because
it contains the required target generation without allocating or copying. It
also evaluates mandatory layer/filter isolation before the ordinary snapshot
routes. `CopyAsDrawMaterialization` is not a seventh destination-read strategy;
it is a materialization of `TargetCopySnapshot` for a real non-`CopySrc` but
texturable native source.

The initial canonical scene and layer targets are Kanvas-owned with `CopySrc`,
so copy-as-draw materialization is modeled and tested as a capability path but
need not be implemented until a real native target requires it.

No product destination route may use `readRgba()`, `mapAsync`, uploaded
CPU-produced snapshot pixels, or a hidden CPU renderer as input to later GPU
rendering.

### One canonical blend-and-coverage specialization

The current independent booleans `requiresDestinationRead` and
`coverageCompositionRequired` stop being routing authorities. The existing
`GPUBlendPlan` is expanded and becomes the single blend decision consumed by
pipeline and frame planning.

The specializer consumes:

- `NormalizedDrawCommand` and `GPUDrawAnalysis` paint facts;
- the established `GPUGeometryPlan`/`GPUGeometryRoute` and `GPUClipPlan`
  coverage result, including any once-translated legacy coverage evidence;
- `GPUColorPlan`, source alpha classification, and target format;
- `GPUTargetState`, sample plan, and WebGPU capabilities.

It produces one of:

```text
FixedFunctionBlend(stateId, sourceCoverageEncoding)
ShaderBlendNoDstRead(formulaId)
ShaderBlendWithDstRead(formulaId, GPUBlendDestinationReadRequirement)
LayerCompositeBlend(childBlendPlan, layerOrderingToken)
NoOp(reason)
UnsupportedBlend(diagnostic, refusalScope)
```

`LayerCompositeBlend` is a composite-command wrapper around one of the same
exhaustive fixed-function, shader, no-op, or refusal decisions. It cannot
introduce a private layer-only formula.

`GPUDestinationReadPlan` then selects snapshot, existing intermediate, layer
isolation, or refusal. It does not re-decide blend semantics.

Coverage consumption forms are:

```text
FullOrScissor
ScalarCoverage                 // analytic or sampled single-channel F
StencilCoverage1x              // binary attachment test at sampleCount=1
MultisampleAttachmentCoverage  // MSAA/alpha-to-coverage
LCDCoverage                    // RGB/subpixel coverage, never scalarized
```

The scalar reference formula is:

```text
F = geometryCoverage * clipCoverage
result = destination + F * (Blend(source, destination) - destination)
```

Geometry and clip coverage are multiplied in the final shader when both are
sampled. A separate coverage texture remains allowed when geometry or clip
lowering inherently produces one, but the common path does not add a separate
fullscreen `COMBINE_COVERAGE_WGSL` pass.

`LCDCoverage` is a distinct vector-coverage contract, including accepted
`GPUSubpixelLCDPlan` text routes. Portable WebGPU has no dual-source blending,
so its normative route is:

```text
Dst                         -> NoOp
every other one of 29 modes -> ShaderBlendWithDstRead(lcd.<mode>@v1)
```

For each RGB channel `c`, the shader computes
`R.c = D.c + F.c * (Blend(S,D).c - D.c)`. Its premultiplied alpha follows the
Graphite LCD rule: compute the three channel-wise interpolated alpha values
`mix(D.a, Blend(S,D).a, F.rgb)` and store their maximum. The route uses a
single-sample destination snapshot. If an LCD draw cannot use a proven exact
single-sample target plan, it refuses with
`unsupported.blend.lcd_msaa_exactness`. LCD coverage is never passed to a
scalar formula or silently reduced to `max(F.rgb)` for color.

This is an explicit proposed amendment to
`.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`, whose current main
route text still refuses LCD/subpixel product rendering while a later appendix
describes an accepted `GPUSubpixelLCDPlan`. After user approval, that spec must
be made internally consistent: the vector formula and gates above become the
target route, while the old unconditional product refusal is superseded. Until
that authority update and the native evidence pass, LCD remains unpromoted and
must keep its existing refusal rather than activating from this draft alone.

#### Normative 29-mode route matrix

The table below is normative for a non-opaque source on an accepted premul
target. `FF(id)` is exact fixed-function attachment blending, `SD(id)` is exact
shader blending with a destination snapshot, `N` is `NoOp`, and `R` is a
stable refusal. `StencilCoverage1x` is binary, so it uses the same route as
full/scissor coverage. Multisample advanced blends refuse unless the whole
target is proven equivalent under `SingleSampleFrame`, in which case the
scalar column applies.

| Blend mode | Full/scissor and stencil-1x | Scalar coverage | MSAA attachment coverage |
|---|---|---|---|
| `Clear` | `FF(zero_zero)` | `FF(cov_reverse_subtract)` | `FF(zero_zero)` |
| `Src` | `FF(one_zero)` | `SD(src@v1)` | `FF(one_zero)` |
| `Dst` | `N` | `N` | `N` |
| `SrcOver` | `FF(one_isa)` | `FF(modulate_one_isa)` | `FF(one_isa)` |
| `DstOver` | `FF(ida_one)` | `FF(modulate_ida_one)` | `FF(ida_one)` |
| `SrcIn` | `FF(da_zero)` | `SD(src_in@v1)` | `FF(da_zero)` |
| `DstIn` | `FF(zero_sa)` | `FF(cov_reverse_subtract_isa)` | `FF(zero_sa)` |
| `SrcOut` | `FF(ida_zero)` | `SD(src_out@v1)` | `FF(ida_zero)` |
| `DstOut` | `FF(zero_isa)` | `FF(modulate_zero_isa)` | `FF(zero_isa)` |
| `SrcATop` | `FF(da_isa)` | `FF(modulate_da_isa)` | `FF(da_isa)` |
| `DstATop` | `FF(ida_sa)` | `SD(dst_atop@v1)` | `FF(ida_sa)` |
| `Xor` | `FF(ida_isa)` | `FF(modulate_ida_isa)` | `FF(ida_isa)` |
| `Plus` | `FF(one_one_clamped)` | `SD(plus_exact@v1)` | `FF(one_one_clamped)` |
| `Modulate` | `FF(zero_sc)` | `FF(cov_reverse_subtract_isc)` | `FF(zero_sc)` |
| `Screen` | `FF(one_isc)` | `FF(modulate_one_isc)` | `FF(one_isc)` |
| `Overlay` | `SD(overlay@v1)` | `SD(overlay@v1)` | `R(msaa_destination_read_exactness)` |
| `Darken` | `SD(darken@v1)` | `SD(darken@v1)` | `R(msaa_destination_read_exactness)` |
| `Lighten` | `SD(lighten@v1)` | `SD(lighten@v1)` | `R(msaa_destination_read_exactness)` |
| `ColorDodge` | `SD(color_dodge@v1)` | `SD(color_dodge@v1)` | `R(msaa_destination_read_exactness)` |
| `ColorBurn` | `SD(color_burn@v1)` | `SD(color_burn@v1)` | `R(msaa_destination_read_exactness)` |
| `HardLight` | `SD(hard_light@v1)` | `SD(hard_light@v1)` | `R(msaa_destination_read_exactness)` |
| `SoftLight` | `SD(soft_light@v1)` | `SD(soft_light@v1)` | `R(msaa_destination_read_exactness)` |
| `Difference` | `SD(difference@v1)` | `SD(difference@v1)` | `R(msaa_destination_read_exactness)` |
| `Exclusion` | `SD(exclusion@v1)` | `SD(exclusion@v1)` | `R(msaa_destination_read_exactness)` |
| `Multiply` | `SD(multiply@v1)` | `SD(multiply@v1)` | `R(msaa_destination_read_exactness)` |
| `Hue` | `SD(hue@v1)` | `SD(hue@v1)` | `R(msaa_destination_read_exactness)` |
| `Saturation` | `SD(saturation@v1)` | `SD(saturation@v1)` | `R(msaa_destination_read_exactness)` |
| `Color` | `SD(color@v1)` | `SD(color@v1)` | `R(msaa_destination_read_exactness)` |
| `Luminosity` | `SD(luminosity@v1)` | `SD(luminosity@v1)` | `R(msaa_destination_read_exactness)` |

The fixed-function IDs expand as follows. `P` is the primary fragment output.
`Add` and `RevSub` apply independently to color and alpha. `Dst` in a color
factor means component-wise destination color; its alpha counterpart is
`DstAlpha`. Likewise `Src` becomes `SrcAlpha` for alpha. A row is accepted only
if the selected WebGPU format supports its operation and factors exactly.

| State ID | Fragment output `P` | Color operation `(op, src, dst)` | Alpha operation `(op, src, dst)` |
|---|---|---|---|
| `zero_zero` | `vec4(0)` (value ignored) | `(Add, Zero, Zero)` | `(Add, Zero, Zero)` |
| `one_zero` | `S` | `(Add, One, Zero)` | `(Add, One, Zero)` |
| `one_isa` | `S` | `(Add, One, OneMinusSrcAlpha)` | `(Add, One, OneMinusSrcAlpha)` |
| `ida_one` | `S` | `(Add, OneMinusDstAlpha, One)` | `(Add, OneMinusDstAlpha, One)` |
| `da_zero` | `S` | `(Add, DstAlpha, Zero)` | `(Add, DstAlpha, Zero)` |
| `zero_sa` | `S` | `(Add, Zero, SrcAlpha)` | `(Add, Zero, SrcAlpha)` |
| `ida_zero` | `S` | `(Add, OneMinusDstAlpha, Zero)` | `(Add, OneMinusDstAlpha, Zero)` |
| `zero_isa` | `S` | `(Add, Zero, OneMinusSrcAlpha)` | `(Add, Zero, OneMinusSrcAlpha)` |
| `da_isa` | `S` | `(Add, DstAlpha, OneMinusSrcAlpha)` | `(Add, DstAlpha, OneMinusSrcAlpha)` |
| `ida_sa` | `S` | `(Add, OneMinusDstAlpha, SrcAlpha)` | `(Add, OneMinusDstAlpha, SrcAlpha)` |
| `ida_isa` | `S` | `(Add, OneMinusDstAlpha, OneMinusSrcAlpha)` | `(Add, OneMinusDstAlpha, OneMinusSrcAlpha)` |
| `one_one_clamped` | `S` | `(Add, One, One)` | `(Add, One, One)` |
| `zero_sc` | `S` | `(Add, Zero, Src)` | `(Add, Zero, SrcAlpha)` |
| `one_isc` | `S` | `(Add, One, OneMinusSrc)` | `(Add, One, OneMinusSrcAlpha)` |
| `cov_reverse_subtract` | `vec4(F)` | `(RevSub, Dst, One)` | `(RevSub, DstAlpha, One)` |
| `modulate_one_isa` | `F * S` | `(Add, One, OneMinusSrcAlpha)` | `(Add, One, OneMinusSrcAlpha)` |
| `modulate_ida_one` | `F * S` | `(Add, OneMinusDstAlpha, One)` | `(Add, OneMinusDstAlpha, One)` |
| `cov_reverse_subtract_isa` | `vec4(F * (1 - S.a))` | `(RevSub, Dst, One)` | `(RevSub, DstAlpha, One)` |
| `modulate_zero_isa` | `F * S` | `(Add, Zero, OneMinusSrcAlpha)` | `(Add, Zero, OneMinusSrcAlpha)` |
| `modulate_da_isa` | `F * S` | `(Add, DstAlpha, OneMinusSrcAlpha)` | `(Add, DstAlpha, OneMinusSrcAlpha)` |
| `modulate_ida_isa` | `F * S` | `(Add, OneMinusDstAlpha, OneMinusSrcAlpha)` | `(Add, OneMinusDstAlpha, OneMinusSrcAlpha)` |
| `cov_reverse_subtract_isc` | `F * (vec4(1) - S)` | `(RevSub, Dst, One)` | `(RevSub, DstAlpha, One)` |
| `modulate_one_isc` | `F * S` | `(Add, One, OneMinusSrc)` | `(Add, One, OneMinusSrcAlpha)` |

These states are not aliases for the current enum factors. In particular:

- `zero_zero` still emits fragment location 0 and performs an attachment
  write; both blend factors being zero makes the source value irrelevant but
  does not authorize omitting the write;
- `Screen` is a coefficient mode and moves to fixed function;
- advanced modes start at `Overlay`, with `Multiply` also advanced;
- partial-coverage `Src`, `SrcIn`, `SrcOut`, and `DstATop` require dual-source
  blending for a general translucent source, so portable WebGPU uses the exact
  destination shader;
- partial-coverage `Plus` uses `plus_exact@v1`; the clamped fixed-function
  approximation used by Graphite on some targets is not accepted;
- `one_one_clamped` is accepted only for normalized clamping target formats;
  other formats use the exact shader or refuse.

Opaque source specialization may upgrade only these scalar cases after the
alpha proof is part of the plan and pipeline key:

| Mode | Opaque scalar specialization |
|---|---|
| `Src` | `FF(modulate_one_isa)` |
| `SrcIn` | `FF(modulate_da_isa)` |
| `DstIn` | `N` |
| `SrcOut` | `FF(modulate_ida_isa)` |
| `DstATop` | `FF(modulate_ida_one)` |

All other opaque routes remain as in the normative matrix. Coverage zero is a
proven `NoOp`; coverage one collapses to the full/scissor column when no
sampled coverage binding remains.

Pipeline keys contain formula/code identity, binding topology, exact
attachment state, sample count, target format/color class, source opacity
specialization, and coverage topology. They exclude concrete texture identity,
snapshot origin, logical bounds, and other uniform-only values.

### A linear immutable frame plan

`GPUFramePlanner` finalizes one or more compatible `GPURecording.taskList`
values into an immutable ordered `GPUFramePlan` without native WebGPU handles.
It preserves every source task ID, dependency token, refusal, recording
compatibility key, and target insertion order. It may linearize already-proven
dependencies; it may not invent a second route decision or erase a refused
task. A cycle, incompatible replay key, or unisolatable dependency is an atomic
planning failure.

Representative executable steps are:

```text
RenderPassStep(target, loadStore, samplePlan, draws)
ComputePassStep(target, resources, dispatches)
CopyDestinationStep(source, snapshot, logicalBounds, copyLayout)
CopyAsDrawMaterializationStep(source, snapshot, logicalBounds)
TargetTransitionStep(parent, child, transitionKind)
ReadbackCopyStep(source, staging, ReadbackLayout)
AcquireSurfaceOutput(outputDescriptor)
SurfaceBlitRenderPassStep(scene, surfaceOutput)
PostSubmitPresentAction(surfaceOutput)
RefusedLeafDrawStep(command, diagnostic)
RefusedCompositeCommandStep(commandId, provenanceTokens, diagnostic)
```

The plan preserves painter's order and the command-scope provenance already
resolved by the legacy adapter. `GPUTaskList` remains the dependency authority;
the frame plan is its deterministic linear execution schedule, not a second
stateful command stream or general dependency DAG.

Consecutive draws remain in one render pass whenever target, attachment/sample
state, and resource hazards permit pipeline, binding, scissor, and stencil
changes inside that pass. A destination snapshot closes the active pass,
records a copy, then starts a later pass on the same command encoder.

### Target-scoped destination snapshot grouping

Snapshot reuse is allowed only inside a group with this exact key:

```text
target identity
target generation
device generation
format and color interpretation
sample/MSAA continuation state
source-intermediate identity, if any
```

The group closes on:

- layer, filter, target, or generation transition;
- incompatible sample/resolve state;
- composite-command refusal boundary;
- an intervening write that intersects a later read;
- source-intermediate identity change;
- scratch budget or deterministic grouping-cost rejection.

The planner tracks:

```text
candidateSnapshotBounds = union(pixelAlignedReadBounds)
writtenSinceSnapshot = union(conservativeWriteBounds)
```

A draw may join only if its read bounds do not intersect
`writtenSinceSnapshot`. Every accepted draw then contributes its conservative
write bounds.

Read bounds are transformed into target space, expanded for AA/filter outsets,
rounded with `floor(left/top)` and `ceil(right/bottom)`, then intersected with
clip and target bounds. Empty bounds are a no-op or diagnostic according to the
command semantics.

Sharing also passes `SnapshotGroupingCostModel`. For a fixed capability/config
record, the decision is deterministic and dumped. It compares:

- aligned union copy bytes versus aligned individual copy bytes;
- union-area inflation versus sum of member areas;
- one group pass-break cost versus individual pass breaks;
- peak scratch bytes and available reusable size classes.

Calibration constants come from a checked-in microbenchmark report, not an
undocumented guess. Until calibration is available, the conservative policy
is one snapshot per destination-reading draw. The calibrated policy must keep
`unionArea / sum(memberArea) <= 2.0` and the frame scratch budget. Two small,
distant rectangles therefore cannot create a nearly full-target union copy.

### Logical snapshot size versus backing size

`GPUScratchTexturePool` rounds physical allocations into deterministic size
classes, but every lease separately exposes:

```text
logicalBounds
logicalWidth/Height
copyOrigin
backingWidth/Height
format/usage/sampleCount/deviceGeneration
```

Destination shaders use integer `textureLoad` coordinates when the format and
shader route allow it. Otherwise the payload provides copy origin, logical
extent, and backing extent separately. Sampling never normalizes by logical
size while binding a larger backing texture.

### Transactional preflight and prepared frame

`GPUFramePreflighter` is the only materialization boundary. It consumes a
validated `GPUFramePlan` and returns either a terminal diagnostic or one
`PreparedGPUFrame` containing:

- the immutable semantic frame plan;
- source recording IDs, compatibility keys, task IDs, and dependency seals;
- the one-to-one `GPUCommandEncoderPlan` used for evidence;
- an opaque `GPUPreparedResourceSet` whose pipeline, bind-group, buffer,
  texture, and view handles remain owned by `GPUResourceProvider`;
- target, device, and resource generations;
- scratch and surface leases with lifetime intervals;
- destination bindings and snapshot layouts;
- optional `ReadbackLayout`;
- a pre-reserved `GPUQueueCompletionTicket` from an accepted, version-scoped
  completion adapter;
- optional acquired surface output and post-submit present action;
- rollback actions for every newly acquired resource.

The surface output is acquired as the final ephemeral preflight operation,
after reusable pipelines and scene resources are ready but before encoder
creation. `lost` and `outdated` close the lease and request reconfiguration;
a genuine `timeout` retries a later frame without submission;
`outOfMemory` and `deviceLost` are terminal. The version-scoped wgpu4k native
status-normalization adapter remains separate from frame planning and must
retain its existing regression evidence.

If any preflight step fails, all new leases are rolled back and no encoder or
submission is created. `GPUFrameExecutor` accepts only `PreparedGPUFrame`; it
cannot allocate an unplanned intermediate, reclassify blend, widen bounds,
change generations, or choose fallback behavior.

Prepared native references are scoped to that execution and never become
durable identity or appear as raw handles/addresses in dumps and keys.

### Present, submission, and real completion

Presentation is separated into:

```text
AcquireSurfaceOutput                // host/preflight action
SurfaceBlitRenderPassStep           // encoded GPU work
queue.submit(commandBuffer)         // one submission
PostSubmitPresentAction             // host action
```

`present()` is not a command-buffer operation and is not GPU completion.

wgpu4k `0.2.0-SNAPSHOT` exposes `GPUQueue.onSubmittedWorkDone()`, but method
presence is not capability proof. The inspected native implementation creates
its callback/upcall and callback-info inside a short-lived `memoryScope`, does
not explicitly select a wgpu-native callback mode, and exposes no facade event
pump or device poll. This design therefore does not accept that method directly
as safe completion evidence.

`GPUQueueCompletionAdapter` is a version-scoped resource/execution boundary.
Before surface acquisition, preflight reserves a `GPUQueueCompletionTicket`
that proves:

- callback, callback-info, userdata, and native upcall lifetime through the
  terminal callback;
- an explicit compatible callback mode;
- event pumping, instance waiting, or device polling on a dedicated execution
  context, never a per-frame wait on the render thread;
- exactly-once completion and ordered “all work submitted before registration”
  semantics;
- device-loss, adapter-close, timeout, and cancellation behavior;
- compatibility with the exact wgpu4k/wgpu-native revision.

The required fix is an upstream wgpu4k facade implementation with those
guarantees. This design does not authorize a temporary Kanvas native callback
implementation. If the facade revision fails conformance, product activation
stays closed and Kanvas reports minimized evidence to wgpu4k instead of
bypassing its public API.

That upstream correction is in progress and is expected to preserve the
existing `GPUQueue.onSubmittedWorkDone()` API. The Kanvas implementation plan
therefore assumes no private native workaround: its adapter remains a thin
lifecycle and evidence boundary around that API. Product activation still
waits for the corrected wgpu4k revision and the native conformance evidence
listed below.

Without an accepted adapter, product preflight refuses before submission with
`dependency.resource.queue_completion_unavailable`. A post-submit ticket-arm
failure is `FailedAfterSubmit`; all resources are quarantined until device
teardown. Readback mapping remains an output operation after accepted queue
completion, not a substitute for the general window-resource completion
contract.

The lifecycle records two related states:

```text
execution: Planned -> Prepared -> Encoded -> Submitted
           -> GPUCompleted | FailedPreSubmit | FailedAfterSubmit
output:    NotApplicable | Acquired | Presented | PresentFailed
```

Rules:

- immediately after `queue.submit()`, retain the submission resources and arm
  the prepared completion ticket before invoking any fallible present action;
- `Presented` may occur before `GPUCompleted`; neither implies the other.
- only `GPUCompleted` releases, reuses, or evicts resources referenced by the
  submission;
- window rendering pumps completion away from the render thread;
- readback awaits the same submission, then maps its staging buffer;
- a failed completion callback or device loss moves to `FailedAfterSubmit` and
  conservatively quarantines resources until explicit device teardown;
- target close is not fabricated as successful GPU completion.

### Readback layout

Preflight creates an immutable `ReadbackLayout`:

```text
unpaddedBytesPerRow = logicalWidth * bytesPerPixel
bytesPerRow = alignUp(unpaddedBytesPerRow, copyBytesPerRowAlignment)
rowsPerImage = logicalHeight
bufferOffset = aligned offset
totalBytes = bufferOffset + bytesPerRow * logicalHeight
```

The selected facade capability currently reports the WebGPU alignment of 256
bytes, but the plan consumes the capability value rather than hard-coding it.
After mapping, rows are depadded into the logical output. Overflow, invalid
alignment, excessive buffer size, or generation mismatch fails preflight.

## Component ownership

### `GPUOpMapper`

At the legacy-adapter boundary, `GPUOpMapper` maps every `DisplayOp` into the
existing `NormalizedDrawCommand` model. The adapter consumes mutable transform,
clip, save/restore, picture, and begin/end-layer state and emits captured
commands such as `NormalizedDrawCommand.DrawLayer` with provenance and ordering
tokens. The renderer core never replays a Canvas state stack. The mapper
preserves paint/material, target/layer identity, conservative bounds, and
explicit source/filter facts on the normalized command.

It does not:

- select blend or destination-read strategy;
- derive coverage again after GPU geometry/clip planning;
- hold WebGPU resources;
- expose unresolved `SetTransform`, `SetClip`, `BeginLayer`, `EndLayer`,
  `save`, or `restore` operations to the renderer core.

### `GPUBlendPlan` specialization

The current `GPUBlendPlan` and `GPUBlendPlanKind` are evolved rather than
wrapped in a competing `BlendCoveragePlan`. The current small
`state.GPUBlendMode`, the 29-mode `passes.GPUBlendMode`, and any route-driving
booleans converge into one canonical mode identity and one exhaustive planner.
The final mode, planner, and semantic `GPUBlendDestinationReadRequirement` are
owned by `passes`; `passes` never imports `destination`.

The planner produces exact fixed-function state or stable WGSL formula IDs.
The `destination` package depends on those pass products.
`GPUDestinationReadStrategyPlanner` consumes the semantic destination-read
requirement and maps it to materialization actions; it does not reinterpret the
mode.

### Existing batching and command contracts

`GPUPassBatcher` remains responsible for compatible draw grouping inside a
render-pass segment. `GPUFramePlanner` owns frame-level copies, target
transitions, output, and lifetime order.

`GPURecording` and `GPUTaskList` remain upstream of that finalizer. Existing
`GPUTask.Render`, `PrepareResources`, `DrawPass`, `Compute`, `Copy`, `Upload`,
`Barrier`, and `Refused` variants are evolved with the typed facts needed for
the frame. `GPUFramePlan` keeps their identities and produces the single legal
linear order consumed by preflight; it does not replace recording evidence.

`GPUPassCommandStream` remains the pass-local lowering format.
`GPUCommandEncoderPlan` becomes the one-to-one preflight description of the
real mixed render/copy/readback/surface-blit encoder. Evidence-only encoder
plans, declarative copy statistics without native execution, and a second
post-blend strategy decision are removed.

### `GPUSceneTarget` and scratch pool

`GPUSceneTarget` owns the canonical resolved texture, optional retained MSAA
attachments, dimensions, format/color facts, sample plan, generation, and
usage facts. MSAA color and depth/stencil bytes are explicit allocation facts,
not hidden target overhead.

`GPUScratchTexturePool` keys textures by format, usage, sample count, size
class, and device generation. It reuses only completed, non-overlapping leases.
The runtime enforces device limits and a configurable peak live-scratch budget.
It first reuses compatible free textures, then evicts completed resources,
then refuses with a detailed budget diagnostic.

An aggregate `GPUFrameMemoryBudgetPlan` also accounts for canonical target
bytes, frame-local persistent MSAA color/depth-stencil attachments, retained
target MSAA attachments, layer/filter targets, destination snapshots, readback
staging, and pool scratch. It reports both `peakFrameTransientBytes` and
`targetResidentBytes`. A 4K/4x MSAA attachment set therefore cannot bypass the
budget merely because it is target-owned rather than pool-owned.

The former fixed 16 MiB per-copy ceiling no longer defines feature support.

### `GPUFrameExecutor`

The executor:

- validates the prepared-frame generation seal;
- creates one command encoder;
- records planned render/compute/copy/readback/surface-blit scopes in order;
- finishes one command buffer;
- calls `queue.submit()` once;
- registers all referenced leases under that submission;
- immediately arms the prepared asynchronous `GPUQueueCompletionTicket` for
  that submission;
- invokes the planned post-submit present action, if any;
- records presentation success/failure independently from completion.

Completion-ticket arming is in the mandatory post-submit path before
`PostSubmitPresentAction`. Ticket arming and presentation execute as independent
guarded branches: an arm failure quarantines resources but does not skip the
required surface present/discard handling, and a throwing `present()` cannot
cancel an already-armed completion. Presentation and GPU completion/failure
update independent lifecycle states.

It never submits an intermediate encoder.

## Drawing API inventory

Every visual `DisplayOp` must map to the common normalized, analysis, blend,
coverage, and frame path. The exhaustive formula matrix is tested once, while
each family proves its facts and composite-command behavior:

| DisplayOp family | Required mapping behavior |
|---|---|
| `DrawColor`, `Clear` | full-target bounds, explicit blend/clear semantics; `Clear` is not inferred from a generic draw |
| `DrawPoint`, `DrawPoints`, lines | point/stroke geometry plus analytic, mask, stencil, or MSAA coverage |
| rect, rrect, DRRect, path | `GPUGeometryPlan`/route and `GPUClipPlan` coverage lowering, conservative target-space bounds |
| image, image-nine, lattice | normalized image rectangles/patches, sampling/material facts, per-patch bounds |
| atlas | per-entry transform/texture/color and primitive blend before final target `GPUBlendPlan` |
| vertices and mesh | geometry/index/primitive-color plan before final target blend |
| text/glyph | atlas or prepared glyph coverage, final target blend unchanged |
| picture | adapter produces a child recording/composite command with provenance; refusal cannot leak child draws to the parent target |
| begin/end layer and restore | adapter resolves the stack into one `DrawLayer` composite command with child target, initialization, filter, and parent-composite tokens |
| filters/backdrop | explicit source/intermediate/target transitions and expanded read/write bounds |
| `FlushAndSnapshot` | explicit ordering/output boundary; never a hidden CPU continuation snapshot |
| transforms, clips, annotations | state/metadata only; no fabricated visual draw |

No family receives a private blend implementation.

## Refusal scope and error handling

### Semantic refusal scopes

A refusal is typed as:

```text
RefusedLeafDrawStep
RefusedCompositeCommand
AtomicFrameFailure
```

- `RefusedLeafDrawStep` is legal only when omitting that leaf cannot corrupt
  target, clip, layer, or later command state.
- `RefusedCompositeCommand` consumes and reports one already-normalized
  composite command plus its child-recording provenance and ordering tokens.
  An unsupported `DrawLayer`, picture composite, or filter cannot leak its
  child tasks into the parent target.
- `AtomicFrameFailure` is required when the planner cannot isolate the invalid
  scope or preserve painter order and state for the remainder.

Representative canonical codes are:

```text
unsupported.blend.coverage_formula
unsupported.blend.msaa_destination_read_exactness
unsupported.blend.lcd_msaa_exactness
unsupported.destination_read.copy_unavailable
unsupported.destination_read.texture_not_sampleable
unsupported.destination_read.framebuffer_fetch_unavailable
unsupported.destination_read.cpu_readback_forbidden
dependency.resource.queue_completion_unavailable
invalid.command.composite_scope
```

No refusal coerces a blend to `SrcOver`, silently drops a composite-command
boundary, or disappears from evidence.

### Runtime failure semantics

Known planning, allocation, generation, pipeline, binding, layout, or
synchronous encoding failures produce `FailedPreSubmit`; rollback runs and no
command buffer is submitted.

Once `queue.submit()` returns, the frame is `Submitted` and cannot be retracted.
WebGPU error scopes, uncaptured validation errors, completion callback failure,
and device loss can be asynchronous; those become `FailedAfterSubmit`. The
spec therefore does not promise “no submit” for errors discovered after
submission.

One command buffer prevents Kanvas from intentionally submitting only a prefix
of a scene. It does not turn an asynchronous device failure into a pre-submit
failure. Window rendering keeps the last successfully presented output when
possible; GM/readback reports the terminal state and produces no accepted
artifact.

### Scratch budget diagnostics

A budget refusal reports:

- logical and backing dimensions;
- requested aligned bytes;
- current live, reusable, and evictable bytes;
- canonical, MSAA color, MSAA depth/stencil, layer/filter, snapshot, readback,
  and other scratch byte classes;
- configured peak budget and grouping-cost inputs;
- target/device generation;
- device texture and buffer limits.

Budget policy controls resource lifetime and safety, not semantic fallback.

## Validation

### Exhaustive blend-and-coverage unit tests

Every one of the 29 modes is tested across:

- coverage `0`, `0.25`, `0.5`, and `1`;
- opaque and translucent sources;
- transparent, translucent, and opaque destinations;
- full, scissor, sampled clip, analytic AA, stencil-1x, MSAA, and RGB/LCD
  coverage;
- clamped normalized and non-clamping target format classes.

Tests assert the normative route, exact attachment state or stable formula ID,
source coverage encoding, destination-read requirement, refusal scope, and CPU
reference result. The oracle uses premultiplied colors and
`D + F * (Blend(S,D) - D)`.

The five opaque scalar upgrades have dedicated positive and negative opacity
proof tests. `Plus` proves exact partial coverage and never selects the
fixed-function approximation. LCD tests cover unequal RGB coverage values for
all 29 modes, the channel-wise alpha maximum, `Dst` no-op, destination-read
routing, and the MSAA exactness refusal. A vector coverage value must never
select `ScalarCoverage`.

### Drawing API routing tests

Every inventory row proves that its visual commands provide the correct
normalized facts to the same planner. Geometry families prove that consumed
coverage is the result of active GPU geometry/clip planning, including stable
`geometry.*` and `clip.*` refusals. While the legacy adapter remains, tests also
prove its one-time `CoveragePlan` translation and canonical diagnostic mapping.
Layer, picture, and filter tests prove composite-command refusal rather than
child-task leakage.

### Frame planner tests

Deterministic sequences cover:

- consecutive direct draws in one pass and no copy;
- disjoint destination reads sharing a bounded snapshot when the cost model
  accepts it;
- distant rectangles rejected from a full-surface union;
- overlapping reads receiving a fresh snapshot;
- direct writes between destination reads;
- group closure on target, layer, filter, format, generation, sample state,
  and source-intermediate change;
- AA/filter bound expansion, floor/ceil rounding, clip and target intersection;
- logical snapshot smaller than its pooled backing texture;
- snapshot-origin and `textureLoad` coordinate math;
- local leaf refusal, composite-command refusal, and atomic escalation;
- resource intervals, scratch reuse only after completion, and rollback.

### MSAA continuation tests

Tests explicitly prove:

- old scene pixels plus a partial MSAA draw plus a pass break leave pixels
  outside the draw unchanged;
- several pass breaks reuse the same stored MSAA attachment;
- MSAA plus destination read selects a proven single-sample frame lowering or
  the stable exactness refusal;
- preserve-load requires a matching retained MSAA target generation;
- a fresh transient attachment is never accepted as continuation;
- layers with different sample plans do not alias attachments.

### Preflight and native executor tests

Instrumented execution asserts:

- one prepared semantic plan and one matching encoder plan;
- one native command encoder, one command buffer, and one `queue.submit()`;
- planned pass/copy/pass and surface-blit order;
- bounded copy extents and no CPU destination snapshot;
- complete rollback and no submit on preflight/synchronous encode failure;
- missing completion-adapter proof refuses before surface acquisition and
  submission;
- exact retained resource set registered on submission;
- `presented` does not release resources;
- a throwing post-submit `present()` still leaves completion armed and all
  leases retained until completion or quarantine;
- accepted completion-ticket success marks `GPUCompleted` and releases;
- completion failure quarantines resources;
- target close does not fabricate successful completion.

The native completion adapter has a separate conformance suite that delays
callbacks beyond the registering call and allocation scope, churns memory/GC,
submits many ordered tickets, drives the configured event pump/poll path, and
proves exactly-once success, failure, device loss, close, and cancellation. It
records the callback mode and exact wgpu4k/wgpu-native revisions. A facade API
method without this evidence remains `dependency.resource.queue_completion_unavailable`.

### Readback and surface tests

Readback tests cover widths both aligned and unaligned to 256-byte rows,
non-zero buffer offsets, rows-per-image, overflow, total padded size, and row
depadding. Adapter-backed window tests cover success, lost, outdated, genuine
timeout, out-of-memory, device loss, present failure, and resize/generation
change.

### Pixel and GM tests

Adapter-backed and native tests compare all RGBA channels against the CPU
reference at interiors, exteriors, AA edges, and clip edges. They cover all
blend families and representative drawing APIs. `aaxfermodes`, `hairmodes`,
and `xfermodes` remain the primary visual and performance regression GMs.

## Performance measurement protocol

Structural completion and performance promotion are separate gates.

The reproducible benchmark protocol is:

- same host, power mode, adapter, device, driver, JDK, dimensions, target
  format, color space, sample plan, and scene implementation;
- separate offscreen/readback and window/present results; never compare one to
  the other;
- same GPU-completion boundary for timed samples;
- VSync/present mode explicitly controlled and recorded;
- warm-cache gate run: 120 untimed warmup frames followed immediately by 300
  measured frames per case;
- cold-cache diagnostic: 30 independent process launches, each recreating the
  device and all Kanvas/Graphite caches and recording the first completed
  frame; cold results are reported but are not promotion gates;
- raw frame samples checked in as report artifacts;
- gate p50 and p95 computed over all 300 raw warm samples with no exclusion;
  median absolute deviation and outlier labels are diagnostic only;
- benchmark script version, metric definitions, and diagnostic outlier labels
  fixed and committed before baseline or candidate runs;
- Graphite nanobench uses one checked-in, SHA-256-recorded driver-only patch
  against the pinned source commit: it removes the assignment that forces
  `FLAGS_samples = 1` for explicit loops, and changes no renderer, Graphite,
  Dawn, GM, or timing code. This permits `--loops 1 --samples 420` without
  auto-calibration or multi-draw samples;
- Graphite+Dawn, pre-change Kanvas, and post-change Kanvas runs include exact
  commits and commands.

The approximate 10.2 s/2.4 s/1.35 s motivating measurements are context only.
The gate baseline is a fresh run of the exact pre-change Kanvas commit on the
same protocol, host, and benchmark script as the candidate. A checked-in
benchmark manifest records the baseline/candidate/Graphite commits and raw
sample artifact hashes.

If a platform cannot run the exact protocol, the report names the deviation
and the result is informational, not a promotion gate.

### Structural completion gates

- one encoder, one command buffer, and one submission per scene render;
- zero CPU destination snapshots;
- zero destination copies caused only by ordinary AA `SrcOver` in
  `hairmodes`;
- no full-target copy when bounded read bounds are smaller;
- snapshot sharing only after dependency and cost proofs;
- persistent MSAA continuation preserves untouched pixels;
- real completion, not `present()`, releases resources;
- no visual-similarity regression in the selected GMs;
- telemetry counts direct draws, shader draws, refusals, pass breaks, snapshot
  groups, copied pixels/bytes, scratch allocation/reuse/eviction, encoders,
  command buffers, submissions, presentation, completion, waits,
  `peakFrameTransientBytes`, and `targetResidentBytes` including MSAA.

### Performance promotion gates

On the exact protocol:

- `hairmodes`, `aaxfermodes`, and `xfermodes` p50 must each be at least 2x
  faster than the exact pre-change Kanvas rerun;
- their p95 must be lower than that same pre-change rerun;
- direct-draw GMs outside those targets must regress by no more than 10% p50
  and 15% p95;
- publish the Kanvas/Graphite+Dawn p50 and p95 ratios;
- direct-draw-dominated scenes target no more than 2x Graphite+Dawn p50 on the
  same host.

Failing a performance gate does not make structural completion true by
explanation. It blocks performance promotion and requires a profile naming the
next dominant cost. State-change elision, upload consolidation, and specialized
instanced batches remain measurement-driven follow-ups, not automatic scope.

## Migration and removal

The implementation remains one chantier delivered through reviewable slices:

1. After user approval, update the authoritative GPU renderer specs for the
   accepted deltas in blend/LCD coverage, destination-read ordering and
   copy-as-draw materialization, frame finalization, MSAA continuation, and
   submission completion. Land or dependency-gate the wgpu4k completion fix and
   its native conformance evidence. No product route activates while those
   authorities still conflict.
2. Converge the duplicate blend-mode/plan authorities into the canonical
   29-mode `GPUBlendPlan`; add the normative matrix tests.
3. Add explicit MSAA continuation and exact single-sample/refusal planning.
4. Evolve `GPUTaskList`, then add its immutable `GPUFramePlan` execution
   projection and extend pass/encoder contracts for mixed render, copy, output,
   and refusal steps.
5. Add `GPUFramePreflighter`, `PreparedGPUFrame`, scratch pooling, readback
   layout, rollback, and lifecycle states.
6. Route offscreen rendering through one native frame executor and remove
   immediate per-operation submissions.
7. Route window rendering through the canonical scene target, late surface
   acquisition, surface blit, post-submit present, and real asynchronous queue
   completion.
8. Integrate clips, layers, filters, pictures, text, images, vertices, meshes,
   and composite-command refusal through the same plan.
9. Remove the CPU snapshot API, superseded destination composer/executor paths,
   route-driving booleans, and evidence-only duplicate decisions.
10. Run exhaustive unit/native/pixel validation, regenerate renders and scores,
   then produce the reproducible Graphite/Kanvas performance report.

Temporary adapters are allowed only between consecutive slices and are removed
before structural completion. The final state has one live blend authority,
one frame planning path, one preflight boundary, and one native execution path.

## Non-goals

- No port of Ganesh or Graphite.
- No multi-backend capability hierarchy.
- No SkSL compiler, IR, or VM.
- No reintroduction of `KanvasPipelineIR` as the new renderer core.
- No placeholder framebuffer-fetch implementation.
- No CPU destination-read fallback.
- No Dawn load-resolve extension assumption.
- No general render-task DAG.
- No global painter-order reordering beyond proven pass-local grouping.
- No support claim from diagnostic-only or evidence-only execution.
- No mandatory instancing system unless measurements identify draw-call
  overhead as the next dominant cost.

## Acceptance criteria

The design is ready for implementation planning only after independent review
finds no critical or important correctness gap and the user approves this
corrected specification.

Structural implementation is complete only when:

- every visual drawing API uses the canonical normalized analysis,
  `GPUBlendPlan`, coverage lowering, and frame path;
- the 29-mode matrix, opaque upgrades, target format rules, and all coverage
  classes have exhaustive tests;
- ordinary AA `SrcOver` does not allocate destination/source intermediates or
  leave the pass solely because of scalar coverage;
- every real destination read uses a bounded GPU snapshot, validated existing
  intermediate, required layer isolation, or stable refusal;
- snapshot reuse proves target/generation/sample/source identity, dependencies,
  and deterministic cost;
- MSAA pass breaks preserve old pixels and never rely on a fresh transient
  attachment;
- offscreen and window rendering use the same canonical scene-target path;
- `snapshotTargetToOffscreenTexture()` and CPU snapshot upload are removed;
- one prepared scene creates one encoder, one command buffer, and one submit;
- surface acquisition, encoded blit, submit, present, and GPU completion are
  distinct and tested;
- an accepted, revision-scoped completion adapter has native callback-lifetime,
  mode, event-pump, device-loss, and exactly-once evidence;
- readback uses a preflighted padded layout and maps only after completion;
- resources are reused or evicted only after real GPU completion;
- leaf, composite-command, and atomic refusals preserve state and remain visible;
- synchronous pre-submit and asynchronous post-submit failure claims match the
  actual lifecycle;
- selected GMs preserve or improve visual similarity;
- final evidence cites exact Kanvas and Graphite/Dawn revisions and commands.

Performance promotion additionally requires every performance gate above. A
profile or bottleneck report is useful evidence after a miss, but it is not a
substitute for passing the stated gate.
