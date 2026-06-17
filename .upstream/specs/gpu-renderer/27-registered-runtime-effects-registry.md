# Registered Runtime Effects Registry

Status: Draft
Date: 2026-06-13

## Purpose

Define the target registry for Kanvas registered runtime effects in the
GPU-first renderer.

Kanvas exposes Skia-like runtime-effect compatibility only through registered
descriptors. A runtime effect is supported when Kanvas has a stable descriptor,
Kotlin/CPU oracle behavior, parser-validated WGSL implementation, reflected
uniform and child layout, route integration, diagnostics, and evidence. Arbitrary
Skia/SkSL source strings, runtime shader builders, and shader-source hashes are
not executable shader input by themselves.

This spec owns the registry, descriptor lifecycle, lookup rules, compatibility
facade policy, uniform schema, child/resource binding contract, CPU oracle
contract, WGSL validation contract, live-parameter metadata, cache keys,
diagnostics, and validation gates for registered runtime effects.

This is a target-complete spec. It is not an implementation slice. The first
rect/rrect plus solid/linear-gradient slice remains governed by
`14-first-slice-contract.md` and does not register runtime effects.

The target is Graphite-inspired but Kanvas-owned:

- Graphite separates runtime-effect snippet IDs, dictionaries, known effects,
  encountered effects, uniform metadata, and pipeline creation tasks;
- Kanvas keeps the registry idea but replaces SkSL compilation with explicit
  Kanvas descriptors and WGSL;
- descriptors are versioned data contracts, not source strings;
- `GPUMaterialDictionary` and `GPUFilterRuntimeEffectPlan` consume registered
  descriptors instead of inventing runtime-effect rules locally;
- unsupported effects refuse with stable diagnostics.

## Source Specs

This spec depends on:

- `00-architecture-kernel.md` for module, naming, and Graphite equivalence
  policy;
- `01-normalized-draw-commands.md` for captured runtime-effect identity in
  normalized material/filter/clip facts;
- `03-material-key-wgsl.md` for `MaterialKey`, render `WGSLModule`, compute
  `WGSLComputeModule`, and material runtime-effect boundaries;
- `31-material-source-paint-pipeline.md` for registered runtime-effect
  placement as a material source, material child-slot source planning, and
  material-source diagnostics;
- `04-pipeline-key-cache-resources.md` for cache, resource, device-generation,
  and typed artifact policy;
- `05-routing-policy.md` for `GPUNative`, `CPUReferenceOnly`, and
  `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `09-draw-family-support-matrix.md` for runtime-effect target maturity;
- `11-wgsl-layout-binding-abi.md` for uniform, texture, sampler, storage, and
  child binding ABI;
- `12-blend-color-target-state.md` for alpha, premul, destination color, and
  color-space behavior;
- `29-color-management-pipeline.md` for color uniforms, `layout(color)`-like
  behavior, color value specs, working-space policy, and runtime-effect color
  diagnostics;
- `30-coordinate-transform-bounds-policy.md` for runtime-effect coordinate
  contracts, child sample radius, local-coordinate helpers, transform
  uniforms, and bounds diagnostics;
- `13-performance-telemetry-cache-gates.md` for registry and runtime-effect
  counters;
- `16-material-dictionary-and-snippet-registry.md` for material snippet
  registration and material assembly;
- `17-payload-gathering-and-slots.md` for runtime-effect uniform/resource
  payload values;
- `18-texture-image-ownership.md` for texture/image child ownership;
- `20-destination-read-strategy.md` for destination-dependent effects;
- `23-filter-effect-pipeline.md` for filter DAG runtime-effect node routes;
- `24-clip-stencil-mask-pipeline.md` for future registered clip shader routes;
- `26-draw-vertices-mesh-pipeline.md` for primitive-color and primitive-blender
  integration when a runtime effect is used as a registered primitive blender.

The older `.upstream/specs/skia-like-realtime/` and
`.upstream/target/high-performance-wgsl-pipeline-target.md` runtime-effect
sections remain product/evidence context. This spec is the GPU-renderer target
contract.

## Graphite And Skia Evidence

Relevant local Graphite and Skia evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/`.

Useful source landmarks:

- Graphite `ShaderCodeDictionary` owns built-in snippets, known runtime-effect
  snippets, user runtime-effect snippets, and IDs for equivalent paint keys.
- Graphite `RuntimeEffectDictionary` keeps per-recording references to
  encountered runtime effects by code snippet ID so pipeline creation can later
  retrieve the effect.
- Graphite uses a runtime-effect key based on effect hash and uniform size to
  de-duplicate equivalent runtime effects across object lifetimes.
- `KeyHelpers` gathers runtime-effect uniforms, validates child slots, routes
  shader/color-filter/blender children, and handles runtime-effect intrinsics.
- Skia `SkRuntimeEffectPriv` exposes stable-key, hash, child sample usage,
  color-transform usage, opacity, alpha, uniform transformation, and capability
  checks.
- `SkImageFilters::RuntimeShader` shows that filter runtime effects need child
  inputs and sample-radius/bounds behavior, not only material snippets.

Kanvas adopts these invariants:

- runtime-effect support is dictionary/registry driven;
- stable IDs, versions, uniform schema, child slots, and code identity are
  visible in diagnostics;
- material, filter, blender, and future clip uses share the same descriptor
  source of truth;
- per-recording usage can hold descriptor references without changing registry
  identity;
- uniform values are payload values, not pipeline-key facts;
- child sample usage and destination/color requirements affect route planning.

Kanvas intentionally does not copy:

- SkSL parsing, SkSL IR, SkSL VM, or SkSL-to-WGSL translation;
- Graphite's runtime-effect source retention model for arbitrary user effects;
- Graphite class names, snippet ID layout, arena ownership, spinlock strategy,
  or exact runtime-effect key shape;
- accepting unknown runtime effects by hash alone;
- CPU fallback rendering for unsupported runtime effects.

## Ownership Boundary

The compatibility adapter owns:

- recognizing Skia-like runtime-effect facade calls;
- computing a canonical compatibility lookup key when an API supplies a source
  string or stable effect name;
- mapping accepted compatibility inputs to registered Kanvas descriptor IDs;
- rejecting arbitrary or unknown Skia/SkSL runtime input with stable
  diagnostics before it enters product GPU routing;
- preserving source provenance for PM/debug dumps without treating it as
  executable shader input.

`31-material-source-paint-pipeline.md` owns only the material-source placement
decision for registered runtime effects. It consumes registry descriptors and
route contracts from this spec; it must not create descriptors, accept
arbitrary source strings, or treat WGSL text outside a registered descriptor as
product support.

This spec owns:

- `GPURuntimeEffectRegistry`;
- `GPURuntimeEffectRegistrySnapshot`;
- `GPURuntimeEffectID`;
- `GPURuntimeEffectKind`;
- `GPURuntimeEffectDescriptor`;
- `GPURuntimeEffectDescriptorVersion`;
- `GPURuntimeEffectCompatibilityKey`;
- `GPURuntimeEffectLookupPlan`;
- `GPURuntimeEffectRegistrationPlan`;
- `GPURuntimeEffectUniformSchema`;
- `GPURuntimeEffectUniform`;
- `GPURuntimeEffectUniformBlockPlan`;
- `GPURuntimeEffectChildSlotPlan`;
- `GPURuntimeEffectResourcePlan`;
- `GPURuntimeEffectWGSLPlan`;
- `GPURuntimeEffectCPUOracle`;
- `GPURuntimeEffectParameterPlan`;
- `GPURuntimeEffectLiveEditPlan`;
- `GPURuntimeEffectRouteContract`;
- `GPURuntimeEffectCachePlan`;
- `GPURuntimeEffectBudgetPolicy`;
- `GPURuntimeEffectDiagnostic`.

Owned by other specs:

- material key construction and material WGSL snippet tree expansion:
  `03-material-key-wgsl.md` and
  `16-material-dictionary-and-snippet-registry.md`;
- filter DAG node execution and bounds: `23-filter-effect-pipeline.md`;
- WGSL layout, reflection, and Kotlin packing: `11-wgsl-layout-binding-abi.md`;
- texture/image child ownership: `18-texture-image-ownership.md`;
- destination reads: `20-destination-read-strategy.md`;
- payload gathering: `17-payload-gathering-and-slots.md`;
- color management details: future color-management spec, with current alpha
  and target behavior constrained by `12-blend-color-target-state.md`.

`MaterialKey`, `GPUFilterRuntimeEffectPlan`, `GPUPrimitiveBlendPlan`, and
future `GPUClipShaderPlan` may include descriptor identity, descriptor version,
route kind, child-slot shape, uniform layout identity, and WGSL fragment/module
identity. They must not include uniform values, mutable source object identity,
arbitrary source text, concrete child resource handles, uploaded texture
artifacts, or CPU oracle output pixels.

## Core Objects

| Object | Purpose |
|---|---|
| `GPURuntimeEffectRegistry` | Versioned owner of registered runtime-effect descriptors for one `GPUSharedScope` or renderer configuration. |
| `GPURuntimeEffectRegistrySnapshot` | Immutable registry generation used by a recording, pipeline cache, or PM evidence run. |
| `GPURuntimeEffectID` | Stable Kanvas effect identity, for example `runtime.simple_rt`. |
| `GPURuntimeEffectKind` | Effect use kind: material shader, color filter, blender, filter node, compute filter, primitive blender, clip shader future, or refused. |
| `GPURuntimeEffectDescriptor` | Complete support contract for one registered effect and kind. |
| `GPURuntimeEffectDescriptorVersion` | Version salt for descriptor, WGSL, CPU oracle, uniform schema, child slots, and route contract changes. |
| `GPURuntimeEffectCompatibilityKey` | Optional compatibility lookup key for known Skia-like source/stable-key inputs. |
| `GPURuntimeEffectLookupPlan` | Accepted/refused lookup result from facade input or normalized command to descriptor. |
| `GPURuntimeEffectRegistrationPlan` | Descriptor installation, validation, versioning, collision, and registry-generation policy. |
| `GPURuntimeEffectUniformSchema` | Ordered uniform schema with names, types, roles, offsets, packing, defaults, and diagnostics. |
| `GPURuntimeEffectUniform` | One uniform declaration and semantic role. |
| `GPURuntimeEffectUniformBlockPlan` | WGSL/Kotlin uniform buffer or payload packing plan. |
| `GPURuntimeEffectChildSlotPlan` | Child shader/color-filter/blender/image/filter input slot contract and sample usage. |
| `GPURuntimeEffectResourcePlan` | Texture, sampler, storage, child, and external resource binding contract. |
| `GPURuntimeEffectWGSLPlan` | WGSL fragment or compute module identity, entry points, features, validation, and reflection facts. |
| `GPURuntimeEffectCPUOracle` | Kotlin/CPU behavior descriptor used for tests, diffs, and reference evidence. |
| `GPURuntimeEffectParameterPlan` | Parameter metadata for user-facing/live-editable uniform values. |
| `GPURuntimeEffectLiveEditPlan` | Stable runtime parameter update contract that changes payload values without changing pipeline keys. |
| `GPURuntimeEffectRouteContract` | Accepted placements: material, color filter, blender, filter node, compute, primitive blender, clip shader future, or refusal. |
| `GPURuntimeEffectCachePlan` | Descriptor, WGSL, CPU oracle, lookup, reflection, payload, and pipeline cache policy. |
| `GPURuntimeEffectBudgetPolicy` | Limits for uniforms, child count, sample radius, WGSL size, bindings, and live-edit updates. |
| `GPURuntimeEffectDiagnostic` | Structured accepted/refused diagnostic product. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Descriptor Semantics

`GPURuntimeEffectDescriptor` records:

- descriptor ID;
- descriptor version;
- human-readable label;
- effect kind or accepted kind set;
- compatibility keys, if any;
- route contract;
- uniform schema;
- child slot plan;
- resource binding plan;
- WGSL plan;
- CPU oracle descriptor;
- color, alpha, premul, opacity, and color-space behavior facts;
- local-coordinate and destination-read requirements;
- sample radius and child sample usage facts;
- live-parameter metadata;
- capability requirements;
- supported conformance fixtures;
- stable refusal codes for unsupported modes.

Descriptor invariants:

- Descriptor ID plus descriptor version is the durable support identity.
- WGSL source hash alone is not a support identity.
- Skia/SkSL source hash may be a compatibility lookup key only when the target
  descriptor is already registered.
- Uniform names, types, offsets, roles, and default/range metadata are part of
  the descriptor contract.
- Child slot count, child kinds, null-child policy, coordinate transform,
  sample radius, and sample usage are part of the descriptor contract.
- A descriptor cannot become `supported` until CPU oracle, WGSL validation,
  reflection, packing, routing, and evidence gates are all present for at least
  one route.

Descriptor changes that affect output, layout, bindings, route requirements,
or diagnostics require a new descriptor version. Descriptor registration must
not silently mutate behavior for existing recordings or pipeline cache entries.

## Registry And Registration Lifecycle

`GPURuntimeEffectRegistry` records:

- registry version;
- registry generation;
- descriptor list in deterministic order;
- descriptor ID index;
- compatibility-key index;
- descriptor-kind index;
- WGSL module/fragment index;
- CPU oracle index;
- capability filter;
- registration diagnostics;
- registry build provenance.

Registration rules:

- Built-in descriptors are immutable after registry creation.
- Project/application descriptors may be added only through
  `GPURuntimeEffectRegistrationPlan` before a recording starts, unless a later
  spec accepts dynamic registry replacement.
- Dynamic registration publishes a new `GPURuntimeEffectRegistrySnapshot`
  generation instead of mutating an active snapshot.
- Descriptor ID collisions refuse unless ID, version, WGSL hash, CPU oracle
  ID, uniform schema, and route contract are identical.
- Compatibility-key collisions refuse unless they resolve to the same
  descriptor ID and version.
- Removing a descriptor is a registry-generation change and invalidates
  affected material/filter/pipeline caches.
- A recording pins the registry snapshot it used.

`GPURuntimeEffectRegistrySnapshot` is the immutable object carried by
`GPURecorder`, `GPUMaterialDictionary`, filter planning, pipeline-key
construction, validation dumps, and PM evidence.

## Lookup And Compatibility Facade

Runtime-effect lookup can start from:

- explicit `GPURuntimeEffectID`;
- normalized material/filter descriptor carrying a descriptor ID;
- compatibility facade input such as a known `SkRuntimeEffect` stable key or
  canonical source hash;
- PM/replay scene metadata naming a supported runtime effect;
- live-edit parameter metadata referencing an existing descriptor ID.

`GPURuntimeEffectLookupPlan` records:

- lookup input kind;
- registry snapshot generation;
- compatibility key when present;
- matched descriptor ID and version;
- effect kind requested by the call site;
- accepted route or refusal;
- diagnostic provenance.

Rules:

- Arbitrary Skia/SkSL source text is never compiled.
- Unknown source hashes refuse even if the WGSL text happens to look similar to
  a registered descriptor.
- A compatibility source hash may map to a descriptor only when it is in the
  registry's compatibility-key index.
- A descriptor registered for one kind is not automatically valid for another
  kind. A material shader descriptor cannot become a filter node or blender
  unless its route contract declares that placement.
- `CPUReferenceOnly` is allowed for oracle evidence and unsupported reporting,
  not product GPU rendering.

## Uniform Schema And Parameter Metadata

`GPURuntimeEffectUniformSchema` records:

- schema version;
- uniform order;
- name;
- WGSL type;
- Kotlin packing type;
- offset, alignment, and size;
- array length when accepted;
- role: raw, color, matrix, scalar parameter, enum-like integer, bounds,
  sample radius, or descriptor-specific;
- default value;
- valid range and clamp/refusal policy;
- live-edit eligibility;
- color-space interpretation when the role is color;
- `GPUColorUniformPlan` reference when the uniform participates in
  `layout(color)`-like transforms, raw color behavior, or compatibility color
  semantics;
- diagnostic label.

`GPURuntimeEffectUniformBlockPlan` records:

- binding group and slot contribution;
- uniform buffer layout hash;
- `WGSLPackingPlan` hash;
- Kotlin packer version;
- reflection evidence;
- payload slot shape;
- dynamic-offset policy when accepted;
- refusal reason when packing cannot be proven.

Uniform values are payload facts gathered by `GPUPayloadGatherer`. They must not
enter `MaterialKey`, `GPURenderPipelineKey`, `GPUComputePipelineKey`, or
registry identity.

`GPURuntimeEffectParameterPlan` records user-facing parameter metadata:

- descriptor ID and version;
- uniform name and optional component;
- value type;
- range, step, default, and clamp/refusal policy;
- UI group/label for PM tooling when needed;
- stable invalid-value diagnostic;
- whether parameter edits keep pipeline keys stable.

`GPURuntimeEffectLiveEditPlan` records runtime updates:

- descriptor ID and version;
- registry snapshot generation;
- edited uniform path;
- old value hash and new value hash;
- payload update route;
- pipeline-key stability proof;
- CPU/GPU evidence requirement for promoted live editing;
- diagnostic when an edit is out of range, type-invalid, or route-invalid.

M87-style `runtime.simple_rt` editing is one selected live-editing lane. It
does not imply broad runtime-effect live controls.

## Child Slots And Resource Bindings

`GPURuntimeEffectChildSlotPlan` records:

- child slot name and index;
- child kind: shader/material, color filter, blender, image/filter input,
  texture, sampler, storage resource, or future registered kind;
- required or optional policy;
- null-child behavior;
- coordinate-space behavior;
- child sample usage: same pixel, local coords, explicit child coords,
  outside-main sample radius, destination sample, or refused;
- child color/alpha contract;
- child route requirements;
- diagnostic labels.

Child rules:

- Child shader/material slots are lowered through `MaterialKey` and
  `GPUMaterialDictionary`.
- Child image/texture slots use `GPUImageSourceDescriptor`,
  `GPUTextureOwnershipPlan`, and sampled binding rules.
- Filter child slots use `GPUFilterInputPlan` and `GPUFilterRuntimeEffectPlan`.
- Blender child slots must be registered descriptor-based routes; arbitrary
  Skia blenders or SkSL source refuse.
- A descriptor must state whether a missing child is refused, transparent
  black, identity, or another explicit behavior.
- Sample radius and outside-main sampling must feed bounds planning for filter
  routes.

`GPURuntimeEffectResourcePlan` records:

- uniform buffers;
- sampled textures;
- samplers;
- storage buffers/textures when accepted;
- child bindings;
- bind group roles;
- capability requirements;
- resource lifetime and ownership references;
- reflection evidence.

Concrete resource handles, imported handles, child texture contents, atlas
entry refs, and upload generations are not descriptor identity.

## WGSL And CPU Oracle Contract

`GPURuntimeEffectWGSLPlan` records:

- WGSL fragment or module ID;
- WGSL version;
- render fragment, render helper, compute module, or filter node category;
- entry point names;
- declared uniform/resource ABI;
- child invocation ABI;
- required features;
- reflection output;
- parser diagnostics;
- module or fragment hash;
- generated module integration status;
- route-specific validation fixtures.

WGSL rules:

- Complete render or compute modules must validate through `wgsl4k` before
  product support is claimed.
- Fragment-only validation is not a product support claim.
- Reflection must match descriptor-declared uniforms, child resources, and
  binding layouts.
- If `wgsl4k` rejects valid expected WGSL, accepts invalid WGSL, loses
  reflection data, or produces nondeterministic output, the Kanvas route stays
  unpromoted and a `wgsl4k` issue is opened with minimized evidence.
- WGSL code generation must be deterministic and independent of uniform values.

`GPURuntimeEffectCPUOracle` records:

- CPU oracle ID and version;
- supported effect kind;
- scalar/vector or test-oracle implementation route;
- uniform interpretation;
- child evaluation rules;
- color/alpha/premul behavior;
- unsupported modes;
- fixture list;
- determinism and tolerance policy.

CPU oracle behavior is required evidence for supported runtime effects. It is
not a product fallback route for GPU rendering.

## Route Contracts

`GPURuntimeEffectRouteContract` declares where a descriptor may be used:

| Route | Meaning |
|---|---|
| `MaterialSource` | Effect produces source color in `MaterialKey` through `GPUMaterialDictionary`. |
| `MaterialColorFilter` | Effect is a color-filter snippet folded into material evaluation. |
| `MaterialBlender` | Effect is a shader-side blender accepted by material/blend planning. |
| `FilterRenderNode` | Effect executes as a render filter node with bounds/intermediate planning. |
| `FilterComputeNode` | Effect executes as a compute filter node with storage resources. |
| `PrimitiveBlender` | Effect combines per-vertex primitive color and material output. |
| `ClipShader` | Future registered clip shader route consumed by `GPUClipShaderPlan`. |
| `CPUReferenceOnly` | Effect is available only for oracle/refusal evidence. |
| `RefuseDiagnostic` | Descriptor is known but unsupported for the requested placement. |

Each accepted route must declare:

- required descriptor kind;
- WGSL plan;
- CPU oracle;
- uniform schema;
- child slots;
- resource bindings;
- color/alpha behavior;
- destination-read requirement;
- bounds/sample-radius behavior when relevant;
- capability requirements;
- required evidence.

A descriptor may support multiple routes only when each route has its own
validation facts. Support in material context does not imply support in filter,
blend, primitive, or clip context.

## Integration Points

### Material Dictionary

`GPUMaterialDictionary` registers material runtime-effect snippets by consuming
`GPURuntimeEffectDescriptor` values from a registry snapshot.

Rules:

- the dictionary version includes the registry snapshot generation and the
  descriptor versions it consumes;
- snippet IDs are dictionary-local optimization handles;
- material keys store descriptor ID/version and route facts, not raw source;
- child material slots use descriptor child-slot rules;
- unknown descriptor IDs refuse before module assembly.

### Filter Pipeline

`GPUFilterRuntimeEffectPlan` references `GPURuntimeEffectDescriptor` for
runtime shader/filter nodes.

Rules:

- the filter node must use a route declared by the descriptor;
- sample radius and child sample usage feed `GPUFilterBoundsPlan`;
- intermediate resources and child inputs remain filter-plan facts;
- descriptor support is insufficient without filter bounds/resource evidence.

### Blend, Primitive Color, And Destination Reads

Runtime-effect blenders must declare whether they need source, destination,
prior stage color, primitive color, or child blender inputs.

Rules:

- destination-dependent runtime effects use `GPUDestinationReadPlan`;
- framebuffer fetch is not assumed;
- primitive-color effects are governed by
  `26-draw-vertices-mesh-pipeline.md`;
- final target blend remains `GPUBlendPlan`.

### Clip Shader Future

`GPUClipShaderPlan` may consume registered runtime-effect descriptors only when
the descriptor route contract accepts `ClipShader`.

Until then, arbitrary clip shaders and unregistered runtime effects refuse.

## Cache, Versioning, And Budgets

`GPURuntimeEffectCachePlan` may cache:

- registry snapshots;
- descriptor lookup results;
- compatibility-key lookup results;
- WGSL validation and reflection products;
- CPU oracle descriptor facts;
- uniform packing plans;
- material snippet registrations;
- filter runtime-effect node lowering;
- live-parameter metadata;
- pipeline/module keys that reference descriptor versions.

Cache keys include:

- registry version and generation;
- descriptor ID and version;
- effect kind and route;
- WGSL plan ID and hash;
- uniform schema hash;
- child slot hash;
- resource binding hash;
- capability facts that affect validity;
- device generation when GPU resources or pipeline objects are involved.

Cache keys must not include uniform values, child texture handles, source object
addresses, or arbitrary source strings.

`GPURuntimeEffectBudgetPolicy` records:

- maximum registered descriptor count;
- maximum compatibility keys per descriptor;
- maximum uniform count and uniform block bytes;
- maximum child slot count;
- maximum texture/sampler/storage binding count;
- maximum WGSL source/module byte size;
- maximum child sample radius;
- maximum filter runtime-effect graph depth contribution;
- maximum live parameter update rate for telemetry;
- maximum validation/reflection cache bytes.

Budget exhaustion refuses with stable diagnostics. The renderer must not drop
uniforms, ignore children, shrink sample radius, or substitute another effect.

## Diagnostics

Every accepted, looked-up, live-edited, or refused runtime-effect route emits
`GPURuntimeEffectDiagnostic`.

Fields:

- lookup input kind;
- registry version and generation;
- descriptor ID and version;
- compatibility key hash when present;
- requested effect kind and placement;
- selected route or refusal;
- uniform schema hash and packing plan hash;
- child slot summary;
- resource binding summary;
- WGSL plan ID, module/fragment hash, validation, and reflection status;
- CPU oracle ID and version;
- color/alpha/premul/local-coordinate/destination-read facts;
- sample radius and child sample usage;
- material/filter/blend/clip integration point;
- cache hits/misses and version facts;
- budget policy ID, budget used, budget remaining, and hard/policy flag;
- live-edit parameter path and key-stability proof when relevant;
- stable reason code.

Stable reason-code examples:

- `unsupported.runtime_effect.unregistered_descriptor`
- `unsupported.runtime_effect.compatibility_key_unknown`
- `unsupported.runtime_effect.kind_mismatch`
- `unsupported.runtime_effect.descriptor_version`
- `unsupported.runtime_effect.descriptor_collision`
- `unsupported.runtime_effect.compatibility_key_collision`
- `unsupported.runtime_effect.uniform_schema_invalid`
- `unsupported.runtime_effect.uniform_packing`
- `unsupported.runtime_effect.uniform_value_invalid`
- `unsupported.runtime_effect.layout_color_unvalidated`
- `unsupported.runtime_effect.child_count`
- `unsupported.runtime_effect.child_kind`
- `unsupported.runtime_effect.child_missing`
- `unsupported.runtime_effect.child_sample_radius`
- `unsupported.runtime_effect.resource_binding`
- `unsupported.runtime_effect.wgsl_missing`
- `unsupported.runtime_effect.wgsl_validation`
- `unsupported.runtime_effect.wgsl_reflection`
- `unsupported.runtime_effect.cpu_oracle_missing`
- `unsupported.runtime_effect.route_unaccepted`
- `unsupported.runtime_effect.destination_read`
- `unsupported.runtime_effect.live_parameter`
- `unsupported.runtime_effect.live_update_out_of_range`
- `unsupported.runtime_effect.budget_exceeded`
- `unsupported.runtime_effect.dynamic_sksl_forbidden`
- `unsupported.runtime_effect.dynamic_wgsl_forbidden`

Existing migration/evidence reason codes such as
`runtime-effect.arbitrary-sksl-unsupported` and
`runtime-effect.wgsl-descriptor-missing` may remain in older reports, but new
GPU renderer diagnostics should prefer the `unsupported.runtime_effect.*`
codes above.

## Telemetry

`GPUTelemetryLedger` records runtime-effect counters:

- registry descriptor count;
- registry generation count;
- lookup count, hit count, and refusal count;
- compatibility-key lookup count;
- descriptor kind histogram;
- material runtime-effect count;
- filter runtime-effect count;
- primitive/blender runtime-effect count;
- clip-shader runtime-effect refusal count;
- WGSL validation/reflection success and failure count;
- CPU oracle availability count;
- uniform byte histogram;
- child slot count histogram;
- live-parameter update count;
- live-parameter refusal count;
- cache hit/miss count;
- budget pressure count;
- dynamic SkSL refusal count.

Performance reports must distinguish:

- runtime-effect descriptor support;
- material route support;
- filter route support;
- live-edit support;
- WGSL validation/reflection cost;
- uniform payload update cost;
- pipeline cache stability across parameter changes;
- refused unsupported input.

## Validation Requirements

Promoted runtime-effect behavior requires:

- canonical dumps for `GPURuntimeEffectRegistry`,
  `GPURuntimeEffectRegistrySnapshot`, `GPURuntimeEffectDescriptor`,
  `GPURuntimeEffectLookupPlan`, `GPURuntimeEffectUniformSchema`,
  `GPURuntimeEffectUniformBlockPlan`, `GPURuntimeEffectChildSlotPlan`,
  `GPURuntimeEffectResourcePlan`, `GPURuntimeEffectWGSLPlan`,
  `GPURuntimeEffectCPUOracle`, `GPURuntimeEffectParameterPlan`,
  `GPURuntimeEffectRouteContract`, `GPURuntimeEffectBudgetPolicy`, and
  `GPURuntimeEffectDiagnostic`;
- descriptor determinism tests;
- descriptor collision and compatibility-key collision tests;
- lookup tests for descriptor ID, known compatibility key, unknown source, and
  wrong effect kind;
- stable refusal tests for arbitrary Skia/SkSL source input;
- uniform schema, default, range, layout, and packing tests;
- `layout(color)` or equivalent color-role tests when a descriptor declares
  color uniforms;
- child-slot tests for shader, color-filter, blender, image/filter input,
  optional child, missing child, and wrong child kind;
- WGSL validation and reflection tests through `wgsl4k`;
- CPU oracle tests for every promoted descriptor;
- material dictionary integration tests for material routes;
- filter pipeline integration tests for filter routes, including bounds and
  sample-radius behavior;
- destination-read negative tests for effects that require destination input;
- live-edit tests proving payload updates do not change pipeline keys;
- cache invalidation tests for registry-generation and descriptor-version
  changes;
- budget pressure tests;
- GPU evidence before product support claims;
- PM evidence showing descriptor ID, route, uniforms, child slots, WGSL
  reflection, CPU/GPU/diff artifacts, live edits when applicable, and stable
  refusals.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice may validate refusal and
boundary behavior only.

It may validate:

- unknown runtime-effect descriptor lookup refusal;
- arbitrary Skia/SkSL source refusal;
- material-key exclusion of uniform values;
- registry snapshot generation included in diagnostics;
- `GPUMaterialDictionary` refuses unregistered runtime-effect snippets with
  stable codes.

It must not claim support for:

- `runtime.simple_rt`;
- live runtime-effect editing;
- arbitrary `SkRuntimeEffect`;
- material runtime-effect snippets;
- runtime color filters;
- runtime blenders;
- filter runtime shader nodes;
- clip shader runtime effects.

Those routes require later evidence against this spec.

## Non-Goals

- Do not compile SkSL.
- Do not translate SkSL to WGSL.
- Do not accept arbitrary WGSL strings as runtime-effect descriptors without
  registry metadata and validation.
- Do not support unknown runtime effects by source hash alone.
- Do not make GPU-only runtime-effect support claims without CPU oracle
  behavior.
- Do not put uniform values into material, render, or compute pipeline keys.
- Do not hide runtime-effect failures behind CPU fallback.
- Do not imply that selected `runtime.simple_rt` support means broad
  runtime-effect compatibility.
