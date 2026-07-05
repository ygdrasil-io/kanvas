# Implementation Roadmap And Parallel Proposal

Status: Draft
Date: 2026-06-13

## Purpose

Define a target implementation roadmap for the new `:gpu-renderer` module.

This document is not a ticket backlog and not an implementation slice list.
It gives the order in which implementation work should be proposed, reviewed,
and proven after the full target contracts in this spec pack are accepted.
Concrete tickets may be cut from this roadmap later, but tickets must preserve
the stage boundaries, refusal policy, and validation gates described here.

The roadmap is vertical-first: implement one narrow rendering path end to end,
then widen coverage by adding more draw families, material sources, geometry
routes, clips, layers, filters, images, and text. Package ownership remains the
source of truth from `35-package-class-layout.md`, but implementation should
not proceed package-by-package.

## Current Starting Point

The repository currently has a pure `:gpu-renderer` module rooted at
`org.graphiks.kanvas.gpu.renderer`. It is a typed contract surface, not yet an
operational renderer.

The current usable starting facts are:

- `NormalizedDrawCommand.FillRect` exists as the first command shape;
- first-route contracts exist for analysis, routing, materials, WGSL, payloads,
  pipeline keys, resources, passes, recording, execution, validation, and
  telemetry;
- several public contracts still use explicit `TODO(...)` behavior, especially
  around payload gathering, resource materialization, command execution,
  recording, render steps, and promotion checks;
- package-boundary tests already protect the module against Skia-like API,
  Ganesh, Graphite, validation, and browser-only leakage.

The first implementation work must therefore make the contracts executable
inside `:gpu-renderer` before any product route is enabled through
`:gpu-renderer`. Shadow integration can be prepared in parallel, but the default
legacy route remains unchanged until isolated evidence is green.

## Roadmap Shape

The primary implementation chain is:

```text
legacy adapter / test fixture
  -> NormalizedDrawCommand
  -> GPUDrawAnalysisDecision
  -> GPURouteDecision
  -> GPUPaintPipelinePlan / GPUMaterialSourcePlan
  -> MaterialKey
  -> GPUMaterialDictionary / WGSLSnippet graph
  -> WGSLModule / WGSLReflectionResult
  -> WGSLPackingPlan
  -> GPUPayloadGatherPlan / GPUDrawPayloadRef
  -> GPURenderPipelineKey / GPUPipelineCreationPlan
  -> GPUDrawInvocation / GPUDrawPass
  -> GPUDrawPacketStream / GPUPassCommandStream
  -> GPUResourceMaterializationDecision
  -> GPUCommandScope / GPUCommandSubmission
  -> GPUValidationReport / GPUTelemetryLedger
```

Each arrow must have tests or evidence before the route is promoted. A later
stage may refuse an earlier accepted candidate only with a stable diagnostic
and dumpable evidence, as defined by
`34-analysis-materialization-recording.md`.

## Critical Path

### R0: Keep The Structure Guarded

Goal: make the newly typed skeleton hard to regress.

Implement and preserve:

- contract layout tests for all primary concepts;
- tests rejecting empty public placeholders;
- tests rejecting public methods with `Nothing` return types;
- package-boundary checks for Skia-like API, Ganesh, Graphite, browser-only
  `webgpu` package ownership, validation imports, dependency matrix, and
  package cycles;
- deterministic fixture dumps for first-slice ownership and aliases.

Promotion evidence:

- `:gpu-renderer:check` passes;
- no `:gpu-renderer` production source imports Skia-like, Ganesh, or Graphite
  classes;
- fake behavior remains explicit through `TODO(...)`, refusal diagnostics, or
  test-only fixtures.

Parallel-safe work:

- improve dump readability;
- add more negative package-boundary fixtures;
- split oversized contract files only when the split preserves package
  ownership and public type names.

### R1: First Native Draw Route

Goal: prove the full renderer chain for a minimal product route.

Accepted scope:

- `FillRect`;
- `FillRRect` after `FillRect` is complete;
- solid color material;
- identity and translation-like transforms;
- wide-open clip and simple scissor;
- root layer only;
- `SrcOver` fixed-function blend;
- target format class equivalent to `rgba8unorm`.

Primary chain:

```text
NormalizedDrawCommand.FillRect
  -> GPUDrawAnalysisRecord
  -> GPURouteDecision.Native
  -> GPUSolidColorPlan
  -> MaterialKey
  -> WGSLSnippet
  -> WGSLModule
  -> GPUPayloadGatherPlan
  -> GPURenderPipelineKey
  -> GPUDrawPass
  -> GPUDrawPacketStream
  -> GPUPassCommandStream
  -> GPUCommandSubmission
```

Required refusals:

- perspective or singular transform;
- unsupported target format;
- unsupported blend;
- non-simple clip stack;
- layer/filter/destination-read requirement;
- missing capability fact;
- WGSL validation or ABI mismatch.

Promotion evidence:

- accepted-route dump;
- refused-route dump for each unsupported branch above;
- module source hash and reflection dump;
- payload packing dump;
- pipeline key preimage dump;
- CPU/reference or readback evidence for at least one accepted scene;
- no hidden CPU-rendered texture route.

Parallel-safe work:

- refine command fixtures;
- write refusal fixtures;
- build route and diagnostic dump format;
- prepare GPU facade test doubles that cannot submit fake success.

### R2: WGSL Module Assembly And ABI

Goal: make shader support parser-validated and ABI-backed, not handwritten by
convention.

Implement:

- material-owned `WGSLSnippet` entries for solid source first;
- complete `WGSLModule` assembly with stable module salt;
- `wgsl4k` parse and reflection handoff;
- `WGSLBindingLayout`, `WGSLUniformLayout`, and `WGSLPackingPlan` generation;
- Kotlin payload packing checks against reflection;
- `WGSLValidationDiagnostic` for parser, reflection, layout, and packing
  mismatches.

Required refusals:

- snippet graph cycle;
- missing entry point;
- binding collision;
- Kotlin/WGSL layout mismatch;
- unsupported facade limit or feature.

Promotion evidence:

- accepted module reflection dump;
- rejected module fixtures;
- payload byte-size and alignment fixture;
- no route promotion from fragment-only validation.

Parallel-safe work:

- solid-source snippet registry;
- layout dump schema;
- minimized `wgsl4k` failure fixtures;
- diagnostic-code normalization.

### R3: Material Keys, Dictionary, Payloads, And Pipeline Keys

Goal: separate material identity, per-draw payload values, and executable
pipeline identity.

Implement:

- `MaterialKey` derivation for solid color;
- `GPUMaterialDictionary` expansion into `GPUMaterialRootSet` and
  `GPUMaterialProgramID`;
- `GPUPayloadGatherer` for solid payloads;
- `GPUUniformPayloadBlock`, `GPUUniformPayloadSlot`, and
  `GPUDrawPayloadRef`;
- `GPUPipelineKeyPreimage.Render`;
- `GPURenderPipelineKey` and `GPUPipelineCreationPlan`;
- cache telemetry for key/module/pipeline hit/miss facts.

Validation requirements:

- payload value changes do not change `MaterialKey` or pipeline keys unless
  layout or code shape changes;
- concrete textures, surface leases, bind groups, and resource handles never
  enter material or pipeline keys;
- key preimage dumps are deterministic.

Parallel-safe work:

- key equivalence tests;
- payload mutation tests;
- cache ledger fixture schema;
- preimage dump golden data.

### R4: Resources, Target Preparation, And Execution

Goal: materialize the accepted first route without backend leakage into
planning packages.

Implement:

- `GPUTargetPreparationContext`;
- `GPUTextureDescriptor`, `GPUTextureViewDescriptor`, and `GPUSamplerDescriptor`
  for the first target class;
- `GPUTextureAllocationPlan.LeaseSurfaceTexture` and necessary offscreen
  target descriptors only if the first route needs them;
- `GPUResourceProvider.materialize`;
- `GPUCommandScope.Render`;
- `GPUDrawPacketStream` for accepted packets;
- `GPUPassCommandStream` for render-pass commands;
- `GPUCommandEncoderPlan` for the selected `GPU` facade implementation;
- `GPUExecutionContext.submit`;
- `GPUReadbackRequest` and `GPUReadbackResult` for evidence.

Required refusals:

- expired surface lease;
- missing usage flag;
- wrong device generation;
- upload or staging budget exceeded;
- active attachment sampled;
- pipeline creation failure.

Promotion evidence:

- materialization decision dump;
- target preparation dump;
- packet-stream and pass-command-stream dumps;
- command submission dump;
- readback evidence or explicitly skipped evidence with stable reason.

Parallel-safe work:

- resource lifetime token tests;
- surface target descriptor fixtures;
- readback bounds fixtures;
- execution context test double that refuses by default.

### R5: Recording, Task Graph, And Replay Policy

Goal: move from one-off first-route tests to immutable recordings and task
lists without changing route semantics.

Implement:

- `GPURecorder` capture for the first accepted command family;
- immutable `GPURecording`;
- `GPURecordingCompatibilityKey`;
- `GPUTaskList` with render, upload/copy, barrier, and refused task variants
  as needed by the first route;
- one-shot recording refusal when replay is not legal;
- ordered recording insertion facts for multiple first-route recordings.

Promotion evidence:

- analysis decision dump before materialization;
- task-list dump after analysis;
- replay compatibility key dump;
- one-shot replay refusal fixture;
- dependency-token fixture for upload-before-use or render ordering.

Parallel-safe work:

- task-list dump schema;
- recording compatibility fixtures;
- ordered recording negative tests.

### R6: Evidence And Promotion Gate

Goal: make "supported" a result of evidence, not an implementation claim.

Implement:

- `GPUValidationReport`;
- `GPUPromotionGateCheck`;
- PM evidence bundle for the first route;
- telemetry counters for accepted/refused routes, module validation, pipeline
  cache, resource materialization, and submission;
- negative CPU-fallback fixtures.

Promotion requirements:

- accepted GPU route evidence;
- refusal evidence for unsupported route families;
- WGSL reflection evidence;
- payload and key evidence;
- resource and execution evidence;
- no hidden CPU fallback evidence.

Parallel-safe work:

- PM report formatting;
- telemetry snapshot tests;
- evidence artifact naming.

## Parallel Implementation Lanes

The critical path above should stay sequential. The following lanes may run in
parallel when their outputs are reviewed through the same validation gates.

### Lane A: Contract And Fixture Hardening

```text
layout checks
  -> source-boundary checks
  -> diagnostic registry fixtures
  -> deterministic dump schemas
  -> negative fallback fixtures
```

Safe deliverables:

- tests only;
- validation helpers;
- PM dump schemas;
- diagnostic-code fixtures.

Must not:

- introduce route support;
- add fake GPU success;
- weaken package-boundary checks.

### Lane B: Shader And ABI Infrastructure

```text
snippet metadata
  -> module assembly
  -> wgsl4k validation
  -> reflection
  -> Kotlin packing
```

Safe deliverables:

- `wgsl` fixtures;
- material snippet registry fixtures;
- reflection and packing tests.

Must not:

- accept arbitrary WGSL strings as product shader support;
- claim support from fragment-only validation;
- bypass `wgsl4k` ambiguity with hidden workarounds.

### Lane C: Route And Diagnostic Infrastructure

```text
route preimage
  -> route decision
  -> refusal diagnostics
  -> late failure diagnostics
  -> telemetry facts
```

Safe deliverables:

- route decision dumps;
- canonical refusal tests;
- telemetry ledger fixtures.

Must not:

- convert refusal into CPU-rendered texture compatibility;
- change `GPUNative` to `CPUPreparedGPU` late unless the analysis decision
  named that alternate route.

### Lane D: Resource And Execution Test Doubles

```text
target descriptor
  -> resource provider test double
  -> execution context test double
  -> readback evidence fixture
  -> backend integration seam
```

Safe deliverables:

- refusing-by-default test doubles;
- resource lifetime tests;
- target/surface lease fixtures.

Must not:

- expose backend handles outside `resources` or `execution`;
- store surface leases in durable keys;
- submit commands without capability and target validation.

### Lane E: Adapter And Integration Boundary

```text
legacy canvas state
  -> captured command facts
  -> normalized command fixture
  -> :gpu-renderer route
  -> gpu-raster integration
```

Safe deliverables:

- command fixtures;
- adapter cleanup tests;
- integration shims behind explicit refusal.

Must not:

- move Skia-like API objects into `:gpu-renderer`;
- replay Canvas-style mutable state inside the core;
- route unsupported commands through legacy CPU fallback.

## First Parallel Implementation Batch

The first implementation batch should be parallelized by disjoint ownership,
but merged through a single vertical promotion path. Each lane may write code
and tests in its owned packages, but no lane may claim product support until
the merged route produces the evidence listed in R6.

### Batch P0-A: Contract And Fixture Lockdown

Owned scope:

- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/**`;
- test-only fixture and dump helpers;
- no production route support unless needed to expose stable diagnostics.

Deliverables:

- `FirstRouteCommandTest`;
- `FirstRoutePromotionGateTest`;
- package-boundary extensions for newly touched packages;
- deterministic dumps for command, analysis, route, material, WGSL, payload,
  pipeline key, resource decision, submission, and telemetry.

Merge dependency:

- can merge first;
- subsequent lanes must update these fixtures instead of inventing local dump
  formats.

### Batch P0-B: Command, Analysis, Route, And Pass Builder

Owned scope:

- `commands`;
- `geometry`;
- `analysis`;
- `routing`;
- `passes`.

Deliverables:

- executable builder for `NormalizedDrawCommand.FillRect`;
- `FillRRect` command contract only after `FillRect` is fully accepted;
- analysis decision for identity and translate-like transforms;
- route decision for `GPUNative` solid rect candidates;
- canonical refusal diagnostics for transform, clip, blend, layer, filter,
  target, and capability failures;
- first `GPUDrawPass` and `GPUDrawInvocation` construction without real backend
  submission.

Merge dependency:

- depends on P0-A dump schema;
- unblocks material, payload, and pipeline tests with real route inputs.

### Batch P0-C: Solid Material, WGSL Module, And ABI

Owned scope:

- `materials`;
- `wgsl`;
- material-owned shader fixture data.

Deliverables:

- `GPUPaintPipelinePlan` and `GPUMaterialSourcePlan.Accepted` for solid color;
- `MaterialKey` derivation that excludes RGBA payload values;
- `GPUMaterialDictionary` to `GPUMaterialRootSet` expansion;
- `WGSLSnippet` graph for solid color;
- complete `WGSLModule` assembly and `wgsl4k` validation;
- `WGSLBindingLayout`, `WGSLUniformLayout`, `WGSLPackingPlan`, and reflection
  diagnostics.

First ABI target:

```text
group 0: frame, render-step, and intrinsic draw data
group 1: SolidMaterialBlock(color: vec4<f32>)
```

Merge dependency:

- depends on P0-B only for the final accepted route fixture;
- can develop WGSL and ABI fixtures in parallel with P0-B.

### Batch P0-D: Payloads, Pipeline Keys, And Cache Telemetry

Owned scope:

- `payloads`;
- `pipelines`;
- cache and telemetry fixtures directly related to key construction.

Deliverables:

- solid payload gatherer;
- rect intrinsic payload slots;
- padding and zero-fill checks;
- `GPUPipelineKeyPreimage.Render`;
- `GPURenderPipelineKey`;
- `GPUPipelineCreationPlan`;
- cache ledger facts for material, module, and pipeline hit/miss events.

Key rule:

- rect bounds, radii, and RGBA values are payload data, not key data;
- material layout, snippet identity, dictionary version, module hash, target
  format, blend/sample state, bind-group layout, vertex layout, and capability
  class are key data.

Merge dependency:

- depends on P0-C for material and WGSL layout facts;
- can build negative key-equivalence tests before P0-C is complete.

### Batch P0-E: Resources, Execution Doubles, And Readback Evidence

Owned scope:

- `resources`;
- `execution`;
- target preparation fixtures;
- test doubles for the `GPU` facade.

Deliverables:

- refusing-by-default `GPUResourceProvider` and `GPUExecutionContext` test
  doubles;
- target descriptor and surface lease validation;
- resource materialization decisions for first-route descriptors;
- packet-stream and pass-command-stream construction for the first route;
- command submission object construction;
- readback request/result evidence or a stable skipped-readback diagnostic.

Merge dependency:

- depends on P0-D for pipeline and payload references;
- must not return fake GPU success;
- real facade submission can be added only after the refusal-first test double
  behavior is stable.

### Batch P0-F: Adapter Shadow Integration

Owned scope:

- `gpu-raster` integration shims;
- adapter tests;
- explicit feature flag or shadow route only.

Deliverables:

- capture of legacy canvas state into first-route command facts;
- `FillRect` shadow handoff into `:gpu-renderer`;
- rollbackable integration point;
- default legacy behavior unchanged.

Merge dependency:

- starts after P0-A fixtures exist;
- product route activation waits for P0-A through P0-E promotion evidence.

### Batch Merge Order

The recommended merge order is:

```text
P0-A fixture lockdown
  -> P0-B command/route/pass
  -> P0-C material/WGSL/ABI
  -> P0-D payload/pipeline/cache
  -> P0-E resource/execution evidence
  -> P0-F shadow integration
  -> R6 promotion gate
```

P0-C and P0-D can be developed in parallel with P0-B, and P0-E can prepare
refusing test doubles in parallel with all earlier lanes. The merge order stays
vertical because each merged step must tighten the same end-to-end evidence
chain instead of creating isolated package success.

### First Batch Test Matrix

The first batch should introduce or complete these tests:

- `FirstRouteCommandTest`;
- `SolidMaterialKeyTest`;
- `WGSLModuleAbiTest`;
- `PayloadGathererTest`;
- `PipelineKeyContractTest`;
- `ResourceExecutionContractTest`;
- `FirstRoutePromotionGateTest`;
- package-boundary and dependency-cycle tests for every newly touched package.

The target validation command for the isolated batch is:

```shell
rtk ./gradlew --no-daemon :gpu-renderer:test
```

Shadow integration later adds targeted `:gpu-renderer` tests behind an explicit
flag or shadow mode, without changing the default route.

## Expansion Order After First Route

After R1-R6 are promoted, expansion should follow the same vertical pattern.

Recommended order:

```text
solid rect/rrect
  -> linear gradient rect/rrect
  -> simple scissor clip
  -> basic path fill
  -> stencil-cover path
  -> simple stroke
  -> image shader with already-decoded pixels
  -> saveLayer isolated target
  -> destination read via copy/intermediate
  -> simple filter render node
  -> A8 text atlas
  -> SDF text atlas
  -> runtime effects registered by descriptor
```

Each expansion must add:

- accepted-route fixtures;
- refusal fixtures for nearby unsupported variants;
- key and payload dumps;
- WGSL reflection dumps if shader code changes;
- resource/materialization dumps if resources change;
- telemetry and promotion evidence.

## Expansion Lanes After Architecture Specs

After R6 promotion and the new architecture specs (38-40), expansion continues
through these parallel lanes:

### Lane 1: Geometry Hardening
```
compute tessellation (spec 25)
  -> advanced stroke / path effects (spec 25)
  -> perspective acceptance for first-slice (spec 30)
```

### Lane 2: Text Breadth
```
subpixel LCD (spec 21)
  -> color fonts / emoji (spec 21)
  -> variable fonts + fallback chain (spec 21)
  -> complex shaping integration (spec 21)
```

### Lane 3: Color Fidelity
```
HDR transfer functions (spec 29)
  -> wide-gamut working spaces (spec 29)
  -> gain map pipeline (spec 29)
  -> ICC profile parsing (spec 29)
```

### Lane 4: Image Pipeline Extension
```
HEIF/AVIF gate promotion (spec 22)
  -> YUV multi-plan route (spec 22)
  -> mipmap auto-generation (spec 22)
  -> hardware codec descriptor (spec 22)
```

### Lane 5: Filter Breadth
```
blur multi-pass (spec 23)
  -> morphology (spec 23)
  -> drop shadow (spec 23)
  -> lighting + displacement (spec 23)
  -> tile-based filter eval (spec 23)
```

### Lane 6: Runtime Effect Extension
```
live editing V2 (spec 27)
  -> blender / clip-shader / compute kinds (spec 27)
  -> dynamic shader graph (spec 27)
```

### Lane 7: Rendering Architecture
```
MSAA resolve (spec 12)
  -> instanced batching (spec 37)
  -> subpass merging (spec 02)
  -> deferred display list (spec 02)
```

### Lane 8: New Architecture Capabilities
```
tile-deferred rendering (spec 38)
  -> multi-threaded recording (spec 39)
  -> hi-z occlusion culling (spec 40)
```

### Merge Dependency

Lanes 1-7 are independent and may execute in parallel. Lane 8 depends on R0-R6
completion and on tile infrastructure from spec 38 for hi-z tile interaction.

Each lane expansion must add:
- accepted-route fixtures;
- refusal fixtures for unsupported variants;
- key, payload, WGSL, resource, and telemetry dumps;
- promotion evidence matching `07-validation-conformance.md` criteria.

## Commit And Review Proposal

Implementation should be committed in small vertical increments:

1. test or fixture change proving the next contract requirement;
2. minimal implementation that makes the new test pass;
3. validation and boundary checks;
4. review for spec compliance;
5. commit.

Recommended commit groups:

- `test: lock gpu renderer first route refusal fixtures`;
- `feat: add gpu renderer solid rect analysis route`;
- `feat: assemble gpu renderer solid WGSL module`;
- `feat: add gpu renderer payload packing for solid rect`;
- `feat: add gpu renderer render pipeline preimage`;
- `feat: materialize gpu renderer first route resources`;
- `feat: submit gpu renderer first route command scope`;
- `test: add gpu renderer first route promotion evidence`.

Do not batch unrelated domains into one commit unless they are needed to make
one vertical route pass.

## Stop Conditions

Stop implementation and update specs before continuing when:

- a package needs to import against a forbidden dependency rule;
- a route needs broad CPU-rendered texture compatibility;
- `wgsl4k` behavior is ambiguous or surprising;
- a key needs concrete resource identity;
- an execution test double would need to return fake success;
- a late materialization decision would silently change route kind;
- a support claim lacks GPU evidence or explicit refusal evidence.

## Open Planning Questions

These questions should be answered when cutting concrete implementation
tickets, not by weakening this roadmap:

- which exact `GPU` facade test double should be used for first-route evidence;
- whether first-route execution starts with a real native `wgpu4k` target or a
  command-recording facade test double plus readback later;
- the minimum accepted `wgsl4k` API surface for reflection and layout checks;
- how `gpu-raster` will hand off target/surface ownership after isolated
  `:gpu-renderer` evidence is green;
- which PM evidence artifact format becomes the durable promotion bundle.
