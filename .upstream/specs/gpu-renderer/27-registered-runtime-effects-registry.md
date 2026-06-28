# Runtime Effects Registry

Status: Draft
Date: 2026-06-13
Updated: 2026-06-28
Note: Filename `27-registered-runtime-effects-registry.md` is historical; this spec
now covers both registered and custom runtime effects in one document. The file
may be renamed in a future cleanup pass.

## Purpose

Define the target registry for Kanvas runtime effects in the GPU-first
renderer, covering both **registered** (built-in/project) and **custom**
(user-provided WGSL) effects.

Kanvas exposes Skia-like runtime-effect compatibility only through descriptors.
For registered effects, support requires a stable descriptor, Kotlin/CPU oracle
behavior, parser-validated WGSL implementation, reflected uniform and child
layout, route integration, diagnostics, and evidence. For custom effects,
support requires user-provided WGSL validated through `wgsl4k` and security
checks before execution. Arbitrary Skia/SkSL source strings, runtime shader
builders, and shader-source hashes are not executable shader input by
themselves.

This spec owns the registry, descriptor lifecycle, lookup rules, compatibility
facade policy, uniform schema, child/resource binding contract, CPU oracle
contract, WGSL validation contract, security validation, live-parameter
metadata, cache keys, diagnostics, and validation gates for both registered and
custom runtime effects.

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
- `GPURuntimeEffectDiagnostic`;
- `GPUCustomRuntimeEffectID`;
- `GPUCustomRuntimeEffectDescriptor`;
- `GPUCustomRuntimeEffectWGSLPlan`;
- `GPUCustomRuntimeEffectValidationStatus`;
- `GPUCustomRuntimeEffectRegistry`;
- `WGSLSecurityValidator`;
- `WGSLSecurityValidationReport`;
- `WGSLSecurityError`;
- `WGSLDeviceCapabilities`.

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
| `CustomRuntimeEffect` | Custom user-provided WGSL effect validated through `wgsl4k` and security checks, isolated from registered effects. |
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
- `unsupported.runtime_effect.custom_wgsl_syntax_error`
- `unsupported.runtime_effect.custom_wgsl_layout_mismatch`
- `unsupported.runtime_effect.custom_wgsl_unsafe_feature`
- `unsupported.runtime_effect.custom_wgsl_unsafe_atomic`
- `unsupported.runtime_effect.custom_wgsl_unsafe_storage_buffer`
- `unsupported.runtime_effect.custom_wgsl_unsafe_read_write_buffer`
- `unsupported.runtime_effect.custom_wgsl_unsafe_ptr`
- `unsupported.runtime_effect.custom_wgsl_unsafe_recursion`
- `unsupported.runtime_effect.custom_wgsl_unsafe_loop`
- `unsupported.runtime_effect.custom_wgsl_unsafe_dynamic_sampling`
- `unsupported.runtime_effect.custom_wgsl_unsafe_texture_store`
- `unsupported.runtime_effect.custom_wgsl_unsafe_dynamic_binding`
- `unsupported.runtime_effect.custom_wgsl_unsafe_ray_query`
- `unsupported.runtime_effect.custom_wgsl_unsafe_compute`
- `unsupported.runtime_effect.custom_wgsl_resource_limit_exceeded`
- `unsupported.runtime_effect.custom_wgsl_device_unsupported`
- `unsupported.runtime_effect.custom_wgsl_not_registered`

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

## Custom Runtime Effects

Custom runtime effects allow user-provided WGSL shaders while maintaining
Kanvas' architectural constraints: no SkSL compilation, strict `wgsl4k`
validation, isolation from registered effects, and stable diagnostics for
failures.

### Design Principles

- **WGSL-only**: Custom effects are provided as WGSL source strings, not SkSL.
- **Isolation**: Custom effects use a separate registry (`GPUCustomRuntimeEffectRegistry`)
  and do not share caches with registered effects.
- **Explicit refusal**: Validation failures produce stable diagnostics, not
  silent fallbacks or CPU substitution.
- **No promotion**: Custom effects cannot be promoted to `GPUNative` without
  explicit re-validation through the registered descriptor path.
- **Sandboxed**: Custom WGSL is validated and sandboxed against blocked features
  and resource limits.

### Core Objects

#### `GPUCustomRuntimeEffectID`

A unique identifier generated from SHA-256 of WGSL source, uniform schema hash,
and child slot hash.

Format: `custom.<hex>` (e.g., `custom.a1b2c3d4e5f6`).

```kotlin
@JvmInline
value class GPUCustomRuntimeEffectID(val value: String) {
    init { require(value.isNotBlank()) }

    companion object {
        fun generate(source: String, schemaHash: String, childSlotHash: String): GPUCustomRuntimeEffectID {
            val input = "custom-runtime-effect-id-v1:$source:$schemaHash:$childSlotHash"
            val digest = MessageDigest.getInstance("SHA-256")
            val hex = digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(12)
            return GPUCustomRuntimeEffectID("custom.$hex")
        }
    }
}
```

#### `GPUCustomRuntimeEffectValidationStatus`

```kotlin
enum class GPUCustomRuntimeEffectValidationStatus {
    PENDING,  // Not yet validated
    VALID,    // Validated and ready for execution
    INVALID,  // Validation failed (syntax, layout, or security)
}
```

#### `GPUCustomRuntimeEffectWGSLPlan`

Contains the custom WGSL source and its validation/reflection hashes.
Uses hash-based identity (consistent with `GPURuntimeEffectWGSLPlan`),
with `source` as the extra field that distinguishes custom effects.

```kotlin
data class GPUCustomRuntimeEffectWGSLPlan(
    val source: String,
    val entryPoint: String,
    val moduleHash: String,
    val reflectionHash: String,
    val validationReportHash: String,
)
```

#### `GPUCustomRuntimeEffectDescriptor`

Standalone descriptor for custom WGSL effects. Not interchangeable with
registered `GPURuntimeEffectDescriptor`. Custom effects do not carry version,
route contract, or live-edit plan — those belong to registered descriptors only.

```kotlin
data class GPUCustomRuntimeEffectDescriptor(
    val id: GPUCustomRuntimeEffectID,
    val uniformSchema: GPURuntimeEffectUniformSchema,
    val childSlots: List<GPURuntimeEffectChildSlotPlan>,
    val resources: GPURuntimeEffectResourcePlan,
    val wgslPlan: GPUCustomRuntimeEffectWGSLPlan,
    val sourceProvenance: String,
    val validationStatus: GPUCustomRuntimeEffectValidationStatus,
)
```

Descriptor invariants for custom effects:

- Descriptor ID plus WGSL source hash is the durable identity.
- Custom descriptors are not valid for registered descriptor lookup.
- A custom descriptor cannot become `VALID` until wgsl4k syntax validation,
  reflection, security checks, and resource limits all pass.
- Custom descriptors are not serialized to persistent caches by default.
- `resources.bindingPlanHash` is derived from wgsl4k reflection at registration
  time and used for WebGPU bind group layout and pipeline key derivation.

#### `GPUCustomRuntimeEffectRegistry`

A separate registry for custom effects, isolated from `GPURuntimeEffectRegistry`.

| Method | Purpose |
|---|---|
| `registerCustomEffect(source, uniformSchema, childSlots, provenance)` | Parse, validate, and register a custom WGSL effect. Returns `Result<GPUCustomRuntimeEffectID>`. |
| `getDescriptor(id)` | Retrieve a registered custom descriptor by ID. |
| `unregisterCustomEffect(id)` | Remove a custom effect from the registry. |
| `isRegistered(id)` | Check whether a custom effect is registered. |

```kotlin
class GPUCustomRuntimeEffectRegistry(
    private val validator: WGSLValidator,
    private val reflectionProvider: WGSLReflectionProvider,
    private val securityValidator: WGSLSecurityValidator,
    private val deviceCapabilities: WGSLDeviceCapabilities,
) {
    private val descriptors: MutableMap<GPUCustomRuntimeEffectID, GPUCustomRuntimeEffectDescriptor> = mutableMapOf()

    fun registerCustomEffect(
        source: String,
        uniformSchema: GPURuntimeEffectUniformSchema,
        childSlots: List<GPURuntimeEffectChildSlotPlan>,
        sourceProvenance: String,
    ): Result<GPUCustomRuntimeEffectID> {
        val childSlotHash = childSlots.joinToString(",") { "${it.slotName}:${it.acceptedSourceKinds.sorted().joinToString("+")}" }
        val id = GPUCustomRuntimeEffectID.generate(source, uniformSchema.schemaHash, sha256(childSlotHash))

        val module = validator.parse(source)
        if (module.syntaxErrors.isNotEmpty()) {
            return Result.failure(GPUCustomRuntimeEffectValidationError(
                code = "custom-wgsl.syntax-error",
                message = "WGSL syntax error: ${module.syntaxErrors.joinToString()}"))
        }

        val securityReport = securityValidator.validateSecurity(module)
        if (!securityReport.isSecure) {
            return Result.failure(GPUCustomRuntimeEffectValidationError(
                code = securityReport.errors.first().code,
                message = "Security validation failed: ${securityReport.errors.joinToString()}"))
        }

        val reflection = reflectionProvider.reflect(module)
        val validationReportHash = sha256(securityReport.errors.joinToString("|") { "${it.code}:${it.severity}" })
        val wgslPlan = GPUCustomRuntimeEffectWGSLPlan(
            source = source,
            entryPoint = reflection.entryPoint,
            moduleHash = reflection.moduleHash,
            reflectionHash = reflection.reflectionHash,
            validationReportHash = validationReportHash)

        val resources = GPURuntimeEffectResourcePlan(
            resourceLabels = listOf("group0.binding0.uniformBuffer"),
            bindingPlanHash = "binding:custom:${id.value}")

        val descriptor = GPUCustomRuntimeEffectDescriptor(
            id = id,
            uniformSchema = uniformSchema,
            childSlots = childSlots,
            resources = resources,
            wgslPlan = wgslPlan,
            sourceProvenance = sourceProvenance,
            validationStatus = GPUCustomRuntimeEffectValidationStatus.VALID)

        descriptors[id] = descriptor
        return Result.success(id)
    }

    fun getDescriptor(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor? = descriptors[id]
    fun unregisterCustomEffect(id: GPUCustomRuntimeEffectID) { descriptors.remove(id) }
    fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean = descriptors.containsKey(id)
}
```

### Custom Effect Execution Pipeline

Custom effects follow a dedicated execution path:

1. **Lookup**: The renderer checks `GPUCustomRuntimeEffectRegistry` for the ID.
2. **Validation**: If not registered or invalid, refuse explicitly.
3. **Pipeline Creation**: If valid, compile WGSL into a WebGPU module.
4. **Uniform Packing**: Pack uniforms according to the declared schema.
5. **Execution**: Dispatch the shader with the packed uniforms.

Custom effects can be used as material sources, color filters, or blenders
in `GPUMaterialDictionary`. They cannot be folded into shared caches.

### Validation Rules

#### Mandatory Checks

| Check | Description | Failure Diagnostic |
|---|---|---|
| `wgsl4k` Syntax Validation | WGSL is syntactically correct. | `custom-wgsl.syntax-error` |
| Layout Reflection | Uniforms/bindings match the declared schema. | `custom-wgsl.layout-mismatch` |
| Resource Limits | Shader does not exceed budgets. | `custom-wgsl.resource-limit-exceeded` |
| Security Checks | No blocked features. | `custom-wgsl.unsafe-*` |
| Device Compatibility | WGSL is supported by the target device. | `custom-wgsl.device-unsupported` |

### Security Constraints

Custom WGSL is untrusted. Kanvas enforces strict validation before execution.

#### Threat Model

| Threat | Mitigation |
|---|---|
| Resource Exhaustion | Strict resource limits. |
| Infinite Loops | WebGPU timeouts + Kanvas loop validation. |
| Memory Corruption | Validate buffer/texture access bounds. The current `checkBufferTextureAccessBounds` is a boolean-flag shell; full AST-level bounds analysis is future work. |
| Denial of Service | Isolate custom effects in a separate pipeline. |
| Information Leakage | Restrict texture/buffer access to declared inputs. |
| Unsupported Features | Validate against device capabilities. |

#### Blocked WGSL Features

| Feature | Reason | Diagnostic Code |
|---|---|---|
| `storageBuffer` without bounds | Memory corruption risk. | `custom-wgsl.unsafe-storage-buffer` |
| `read_write` storage buffers | Arbitrary memory writes. | `custom-wgsl.unsafe-read-write-buffer` |
| `atomic` operations | Race conditions, undefined behavior. | `custom-wgsl.unsafe-atomic` |
| `ptr` operations | Low-level memory manipulation. | `custom-wgsl.unsafe-ptr` |
| Recursive functions | Stack overflow risk. | `custom-wgsl.unsafe-recursion` |
| Unbounded loops | Infinite execution risk. | `custom-wgsl.unsafe-loop` |
| `textureSample` with dynamic coords | Arbitrary texture access. | `custom-wgsl.unsafe-dynamic-sampling` |
| `textureStore` | Arbitrary texture modification. | `custom-wgsl.unsafe-texture-store` |
| `bindGroup` with dynamic indices | Unbound resource access. | `custom-wgsl.unsafe-dynamic-binding` |
| `rayQuery` | Ray tracing not supported. | `custom-wgsl.unsafe-ray-query` |
| `compute` shaders | Additional validation required. | `custom-wgsl.unsafe-compute` |
| `workgroup` builtins | Undefined behavior risk. | `custom-wgsl.unsafe-workgroup` |

#### Resource Limits

| Resource | Limit | Diagnostic Code |
|---|---|---|
| Uniforms | 16 | `custom-wgsl.uniform-count-exceeded` |
| Bind groups | 4 | `custom-wgsl.bind-group-count-exceeded` |
| Bindings per group | 8 | `custom-wgsl.binding-count-exceeded` |
| Uniform buffer size | 16 KB | `custom-wgsl.uniform-buffer-size-exceeded` |
| Storage buffer size | 64 KB | `custom-wgsl.storage-buffer-size-exceeded` |
| Textures | 8 | `custom-wgsl.texture-count-exceeded` |
| Samplers | 4 | `custom-wgsl.sampler-count-exceeded` |
| Texture dimensions | 4096x4096 | `custom-wgsl.texture-dimension-exceeded` |
| Loop iterations | 1024 | `custom-wgsl.loop-iteration-exceeded` |
| Function depth | 8 | `custom-wgsl.function-depth-exceeded` |

### `WGSLSecurityValidator`

```kotlin
class WGSLSecurityValidator(
    private val deviceCapabilities: WGSLDeviceCapabilities,
) {
    fun validateSecurity(module: WGSLParsedModule): WGSLSecurityValidationReport {
        val errors = mutableListOf<WGSLSecurityError>()
        errors.addAll(checkBlockedFeatures(module))
        errors.addAll(checkResourceLimits(module))
        errors.addAll(checkDeviceCapabilities(module, deviceCapabilities))
        errors.addAll(checkBufferTextureAccessBounds(module))
        return WGSLSecurityValidationReport(isSecure = errors.isEmpty(), errors = errors)
    }

    private fun checkBlockedFeatures(module: WGSLParsedModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.usesAtomics)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-atomic", "Atomic operations not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesUnboundedStorageBuffers)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-storage-buffer", "Storage buffers must have explicit size", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesReadWriteBuffers)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-read-write-buffer", "Read-write storage buffers not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesPtrOperations)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-ptr", "Pointer operations not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.hasRecursiveFunctions)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-recursion", "Recursive functions not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.hasUnboundedLoops)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-loop", "Unbounded loops not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesDynamicSampling)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-dynamic-sampling", "Dynamic texture sampling not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesTextureStore)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-texture-store", "Texture storage not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesDynamicBinding)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-dynamic-binding", "Dynamic bind groups not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesComputeShader)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-compute", "Compute shaders not allowed", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesWorkgroupBuiltins)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-workgroup", "Workgroup builtins not allowed", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }

    private fun checkResourceLimits(module: WGSLParsedModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.uniforms.size > MAX_CUSTOM_UNIFORMS)
            errors.add(WGSLSecurityError("custom-wgsl.uniform-count-exceeded", "${module.uniforms.size} > $MAX_CUSTOM_UNIFORMS", WGSLSecurityErrorSeverity.ERROR))
        if (module.textures.size > MAX_CUSTOM_TEXTURES)
            errors.add(WGSLSecurityError("custom-wgsl.texture-count-exceeded", "${module.textures.size} > $MAX_CUSTOM_TEXTURES", WGSLSecurityErrorSeverity.ERROR))
        if (module.bindGroups.size > MAX_CUSTOM_BIND_GROUPS)
            errors.add(WGSLSecurityError("custom-wgsl.bind-group-count-exceeded", "${module.bindGroups.size} > $MAX_CUSTOM_BIND_GROUPS", WGSLSecurityErrorSeverity.ERROR))
        if (module.loopIterationCount > MAX_CUSTOM_LOOP_ITERATIONS)
            errors.add(WGSLSecurityError("custom-wgsl.loop-iteration-exceeded", "${module.loopIterationCount} > $MAX_CUSTOM_LOOP_ITERATIONS", WGSLSecurityErrorSeverity.ERROR))
        if (module.functionDepth > MAX_CUSTOM_FUNCTION_DEPTH)
            errors.add(WGSLSecurityError("custom-wgsl.function-depth-exceeded", "${module.functionDepth} > $MAX_CUSTOM_FUNCTION_DEPTH", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }

    private fun checkDeviceCapabilities(module: WGSLParsedModule, capabilities: WGSLDeviceCapabilities): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.usesRayQuery && !capabilities.supportsRayQuery)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-ray-query", "Device does not support ray query", WGSLSecurityErrorSeverity.ERROR))
        if (module.usesAtomics && !capabilities.supportsAtomics)
            errors.add(WGSLSecurityError("custom-wgsl.device-unsupported", "Device does not support atomic operations", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }

    private fun checkBufferTextureAccessBounds(module: WGSLParsedModule): List<WGSLSecurityError> {
        val errors = mutableListOf<WGSLSecurityError>()
        if (module.storageBuffers.isNotEmpty() && module.usesUnboundedStorageBuffers)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-storage-buffer", "Unbounded storage buffers risk out-of-bounds memory access", WGSLSecurityErrorSeverity.ERROR))
        if (module.textures.isNotEmpty() && module.usesDynamicSampling && module.usesTextureStore)
            errors.add(WGSLSecurityError("custom-wgsl.unsafe-texture-store", "Dynamic texture sampling with storage risks out-of-bounds access", WGSLSecurityErrorSeverity.ERROR))
        return errors
    }
}

data class WGSLSecurityValidationReport(val isSecure: Boolean, val errors: List<WGSLSecurityError>)
data class WGSLSecurityError(val code: String, val message: String, val severity: WGSLSecurityErrorSeverity)

enum class WGSLSecurityErrorSeverity { ERROR, WARNING }

data class WGSLDeviceCapabilities(
    val supportsRayQuery: Boolean = false,
    val supportsStorageBuffers: Boolean = true,
    val supportsAtomics: Boolean = false,
    val maxTextureDimensions: Int = 4096,
    val maxUniformBufferSize: Int = 16 * 1024,
    val maxStorageBufferSize: Int = 64 * 1024,
)

const val MAX_CUSTOM_UNIFORMS = 16
const val MAX_CUSTOM_TEXTURES = 8
const val MAX_CUSTOM_BIND_GROUPS = 4
const val MAX_CUSTOM_BINDINGS_PER_GROUP = 8
const val MAX_CUSTOM_UNIFORM_BUFFER_SIZE = 16 * 1024
const val MAX_CUSTOM_STORAGE_BUFFER_SIZE = 64 * 1024
const val MAX_CUSTOM_LOOP_ITERATIONS = 1024
const val MAX_CUSTOM_FUNCTION_DEPTH = 8
```

### Custom Effect Diagnostics

Custom effects emit `GPURuntimeEffectDiagnostic` with the following additional
fields:

- `sourceProvenance`: origin tag for the custom WGSL.
- `wgslSourceHash`: hash of the source text.
- `securityReport`: validation report when security checks fail.
- `resourceLimits`: budget used vs budget available.

Additional stable reason codes:

| Code | Meaning |
|---|---|
| `unsupported.runtime_effect.custom_wgsl_syntax_error` | wgsl4k parse failure. |
| `unsupported.runtime_effect.custom_wgsl_layout_mismatch` | Layout incompatible with declared schema. |
| `unsupported.runtime_effect.custom_wgsl_unsafe_feature` | Blocked WGSL feature detected. |
| `unsupported.runtime_effect.custom_wgsl_resource_limit_exceeded` | Budget exceeded (uniforms, textures, etc.). |
| `unsupported.runtime_effect.custom_wgsl_device_unsupported` | WGSL not supported by device. |
| `unsupported.runtime_effect.custom_wgsl_not_registered` | Effect ID not found in custom registry. |

### Telemetry

`GPUTelemetryLedger` records custom runtime-effect counters:

- `custom_runtime_effect.registry_descriptor_count`
- `custom_runtime_effect.registration_count`
- `custom_runtime_effect.lookup_count`, `hit_count`, `refusal_count`
- `custom_runtime_effect.validation_success_count`, `validation_failure_count`
- `custom_runtime_effect.security_refusal_count`
- `custom_runtime_effect.resource_limit_refusal_count`
- `custom_runtime_effect.execution_count`
- `custom_runtime_effect.validation_duration_ms`

### Example: Sinusoidal Wave Effect

```wgsl
@group(0) @binding(0) var<uniform> time: f32;
@group(0) @binding(1) var<uniform> resolution: vec2<f32>;
@group(0) @binding(2) var<uniform> amplitude: f32;
@group(0) @binding(3) var<uniform> frequency: f32;

@fragment
fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
    let normalizedUv = uv * 2.0 - 1.0;
    let waveOffset = sin(normalizedUv.y * frequency + time) * amplitude;
    let distortedUv = normalizedUv + vec2<f32>(waveOffset, 0.0);
    let hue = atan2(distortedUv.y, distortedUv.x) * 0.5 + 0.5;
    return vec4<f32>(hue, 0.8, 0.9, 1.0);
}
```

Registration:

```kotlin
val customRegistry = GPUCustomRuntimeEffectRegistry(
    validator = WGSLValidator(),
    reflectionProvider = WGSLReflectionProvider(),
    securityValidator = WGSLSecurityValidator(WGSLDeviceCapabilities()),
    deviceCapabilities = WGSLDeviceCapabilities(),
)

val result = customRegistry.registerCustomEffect(
    source = sinusoidalWaveWGSL,
    uniformSchema = GPURuntimeEffectUniformSchema(
        schemaHash = "schema:sinusoidal_wave:v1",
        fields = listOf(
            "time:f32@0:4",
            "resolution:vec2<f32>@4:8",
            "amplitude:f32@12:4",
            "frequency:f32@16:4",
        ),
        packingPolicy = "std140",
    ),
    childSlots = emptyList(),
    sourceProvenance = "example.sinusoidal_wave",
)

if (result.isSuccess) {
    val effectId = result.getOrThrow()
    // Use effectId in a material or filter
}
```

### Custom Effect Budget Policy

`GPURuntimeEffectBudgetPolicy` records custom effect limits:

- maximum custom descriptor count per session;
- maximum WGSL source byte size;
- maximum validation cache bytes;
- maximum security validation duration.

Budget exhaustion refuses with stable diagnostics. The renderer must not silently
truncate WGSL, skip validation, or weaken security checks.

### Integration Points

Custom effects integrate with the same material, filter, and blend pipelines as
registered effects, using the `CustomRuntimeEffect` route. Key differences:

- Custom effects are never folded into material dictionary snippet caches.
- Custom effect pipeline keys include the WGSL source hash.
- Filter runtime-effect nodes using custom effects must declare sample radius
  and child sample usage explicitly.
- Live-edit support is not provided for custom effects.

### Open Questions

1. Should custom effects support **live editing**?
   - Proposal: No, to avoid complexity. Custom effects are static after registration.
2. Should custom effects be **serializable**?
   - Proposal: Yes, but only if the WGSL source is preserved (not just the compiled module).
3. Should custom effects support **child shaders**?
   - Proposal: Yes, but with stricter validation (no recursive child chains).
4. Should we allow **compute shaders** for custom effects?
   - Proposal: No initially. Only fragment/vertex shaders.

---

## Non-Goals

- Do not compile SkSL.
- Do not translate SkSL to WGSL.
- Do not accept arbitrary WGSL strings as registered runtime-effect descriptors
  without registry metadata and validation.
- Do not support unknown runtime effects by source hash alone.
- Do not make GPU-only runtime-effect support claims without CPU oracle
  behavior (registered effects) or security validation (custom effects).
- Do not put uniform values into material, render, or compute pipeline keys.
- Do not hide runtime-effect failures behind CPU fallback.
- Do not promote custom effects to `GPUNative` without explicit re-validation
  through the registered descriptor path.
- Do not share caches between custom and registered runtime effects.
- Do not imply that selected `runtime.simple_rt` support means broad
  runtime-effect compatibility.
