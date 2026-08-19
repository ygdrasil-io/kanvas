# Target Authority, Taxonomy, And Diagnostics

Status: Draft
Date: 2026-06-13

## Purpose

Define the authority rules, status vocabulary, route taxonomy, and diagnostic
registry policy for the GPU renderer spec pack.

This is a consolidation spec. It does not add a new renderer feature. It makes
the already-agreed target language stable enough that later implementation
tickets can derive work without inventing local meanings for "accepted",
"route", "strategy", "fallback", or diagnostic reason codes.

## Target Authority

The `gpu-renderer/` pack is the target authority for new work inside the
`:gpu-renderer` module.

Older target and spec packs remain project context and evidence:

- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/target/skia-like-realtime-renderer-target.md`
- `.upstream/specs/wgsl-pipeline/`
- `.upstream/specs/geometry-coverage/`

When an older document describes `KanvasPipelineIR` as the semantic center, new
`:gpu-renderer` work must treat that as legacy and migration context. The new
semantic center is the normalized command, analysis, material, pipeline,
resource, task, and diagnostic model defined by this pack.

When an older document gives evidence requirements that are stricter than this
pack, the stricter evidence requirement still applies until explicitly
replaced. When an older document conflicts with this pack on new
`:gpu-renderer` architecture, this pack wins.

Implementation tickets that touch GPU renderer routes, shader assembly,
runtime effects, material lowering, resources, pass construction, or
`gpu-raster` integration must cite this pack and the older evidence they are
superseding or preserving.

## Normative Authority Registry

This table is the single ownership registry for the frame-planning amendment.
Other active specs consume these contracts and may explain them, but they must
not declare a second owner or alternate product entry.

| Contract | Sole authority and invariant |
|---|---|
| `GPUBlendPlan` | `passes` owns the canonical 29-mode identity, planner, exact fixed-function state, shader formula identity, coverage encoding, opacity specialization, and semantic `GPUBlendDestinationReadRequirement`; it never imports `destination`. |
| `GPUFramePlan` | `recording` owns the immutable deterministic linear execution schedule projected from the dependency-authoritative `GPUTaskList`; it is not a second task graph. |
| `GPUFrameCoordinator` | `execution` owns the sole product entry across frame finalization, preflight, and execution; it makes no route decision, preserves planning/preflight refusals as terminal frame outcomes, and cannot be bypassed by scene or surface entries. |
| `GPUFramePreflighter` | `execution` owns orchestration of the only materialization boundary after final frame order is known, consuming `recording` plans and `resources` contracts for resource decisions, command streams, scratch budgeting, late surface acquisition, and rollback. |
| `PreparedGPUFrame` | `execution` owns the sealed preflight result consumed by the executor; it pairs one semantic frame plan with one encoder plan, opaque prepared resources, a completion ticket, and rollback/retention facts. |
| `GPUSceneTarget` | `resources` owns the canonical single-sample scene texture, optional persistent/retained MSAA continuation, generation, references to `color`-owned format/interpretation, and resident-memory accounting for both offscreen and window outputs. |
| `GPUQueueCompletionTicket` | `execution` owns the version-scoped real queue-completion proof; presentation never completes or releases a submission, and the unchanged wgpu4k facade API is used only after its corrected revision passes native conformance. |
| `LCDCoverage` | `passes` owns vector RGB coverage: channel-wise interpolation, maximum channel alpha, destination-shader routing for every non-`Dst` mode, and exact MSAA refusal when a single-sample lowering is unproven. |
| `RefusedCompositeCommand` | `recording` owns terminal refusal of one normalized composite scope with child provenance and ordering tokens, preventing unsupported layer, picture, or filter children from leaking into the parent target. |

The registry forbids a second blend-mode enum, any product snapshot sourced
from host readback, release or terminal queue status inferred from the host
output handoff, materialization until after the final
`GPUTaskList`/`GPUFramePlan` order, and any product path that bypasses
`GPUFrameCoordinator`.

## Status Vocabulary

Spec status describes document maturity:

| Status | Meaning |
|---|---|
| `Draft` | Target direction is being designed or consolidated. Implementation may prototype from it, but promoted support cannot claim final acceptance from this status alone. |
| `Accepted` | Target direction, implementation evidence, conformance or explicit refusal diagnostics, and PM evidence have all been reviewed for the touched route. |
| `Superseded` | A newer spec replaces this document as authority. The old document remains historical evidence only. |
| `Archived` | Historical evidence that must not be used as active backlog or acceptance criteria. |

Decision maturity describes route or family intent:

| Maturity | Meaning |
|---|---|
| `AcceptedKernelDecision` | User-approved architectural direction in this draft pack. It is binding for future specs, but it is not a support claim. |
| `TargetRequired` | Required target concept for any promoted GPU route, even when the first slice implements only a subset. |
| `TargetNative` | Intended to become a `GPUNative` product route after evidence. |
| `TargetPrepared` | Intended to use typed `CPUPreparedGPU` artifacts after evidence. |
| `DependencyGated` | Blocked on another accepted spec, external dependency, or delivered subsystem. |
| `PolicyGated` | Blocked by an explicit product or architecture policy decision, not by missing code alone. |
| `ReferenceOnly` | CPU oracle or evidence behavior only. It is not a product rendering route. |
| `RefuseRequired` | Stable refusal is the target behavior unless a future accepted spec changes the route. |
| `FutureResearch` | Recognized future area without accepted target semantics. |
| `ImplementationCandidate` | Coherent target behavior ready to be sliced into implementation tickets. |
| `PromotedSupported` | Product support has evidence, diagnostics, and gates accepted for the selected route. |

`Accepted Kernel Decisions` in the README are
`AcceptedKernelDecision` entries. They do not mean the whole draft pack is
`Accepted`, and they do not mean product support exists.

## Route Taxonomy

Route kinds are the top-level product outcomes:

| Route kind | Meaning |
|---|---|
| `GPUNative` | GPU render, compute, copy, clear, or fixed-function work executes the product route without CPU rasterizing the final draw/layer/filter/scene result. |
| `CPUPreparedGPU` | CPU creates a typed artifact that is consumed by an accepted GPU route. The artifact type, key, lifetime, budget, and GPU consumer are explicit. |
| `CPUReferenceOnly` | CPU computes oracle or report evidence only. It is not a product fallback. |
| `RefuseDiagnostic` | The renderer refuses deterministically with a stable diagnostic because no accepted product route exists. |

Strategies, plans, and routes are not interchangeable terms:

| Term | Scope |
|---|---|
| Route kind | Product-level outcome such as `GPUNative`, `CPUPreparedGPU`, or `RefuseDiagnostic`. |
| Strategy | A domain-specific choice inside a route, such as `GPUDestinationReadStrategy`, atlas strategy, or layer isolation strategy. |
| Plan | A typed decision record that can be dumped, keyed, validated, and consumed by later stages. |
| Diagnostic | Stable explanation of acceptance, refusal, or late materialization failure. |

Every promoted strategy must map back to exactly one route kind for the command
or task it affects. A strategy may split work into multiple GPU tasks, but it
must not hide a broad CPU-rendered compatibility path.

## Capability Taxonomy

`GPUCapabilities` describes the selected implementation of the Kanvas `GPU`
facade. The facade is WebGPU-like, but it is not a browser-only naming or
deployment assumption.

Capability reports must distinguish:

- facade-level limits and features shared with WebGPU vocabulary;
- `wgpu4k` implementation facts;
- future Dawn-backed implementation facts;
- future pure Kotlin implementation facts;
- adapter/device limits that affect validation or route selection;
- known unsupported behavior.

Pipeline, module, route, and materialization keys may include capability facts
only when they affect validity or behavior. They must not include the current
backend object identity, cache residency, or incidental platform strings.

## Diagnostic Registry Policy

Reason codes are lowercase ASCII and dot-separated:

```text
unsupported.<domain>.<reason>
invalid.<domain>.<reason>
stale.<domain>.<reason>
budget.<domain>.<reason>
dependency.<domain>.<reason>
validation.<domain>.<reason>
capability.<domain>.<reason>
```

Segments may use snake_case. Segments must not use hyphens or uppercase
letters. Examples with uppercase acronyms in older draft text must be
canonicalized before promotion.

Canonical domains:

| Domain | Scope |
|---|---|
| `command` | Normalized command shape and invariants. |
| `material` | Paint/material-source lowering and `MaterialKey` creation. |
| `runtime_effect` | Registered runtime-effect descriptor lookup and placement. |
| `wgsl` | WGSL assembly, parser, reflection, and ABI validation. |
| `pipeline` | Render or compute pipeline key and creation. |
| `capability` | Missing facade/device feature or limit. |
| `artifact` | Typed `CPUPreparedGPU` artifact registration, key, lifetime, or budget. |
| `texture` | Texture ownership, views, samplers, imports, surface leases, and active attachment rules. |
| `image` | Codec, decode, animation, pixel prep, orientation, upload, and image color facts. |
| `color` | Color spaces, profiles, transfer functions, HDR, gainmap, and store conversion. |
| `transform` | Coordinate spaces, matrices, inverses, perspective, and precision. |
| `bounds` | Conservative bounds proofs, rounding, overflow, and clip reduction. |
| `destination_read` | Destination/backdrop copy, intermediate reuse, layer isolation, and active attachment sampling. |
| `text` | Glyph, atlas, shaping handoff, subrun, and text render-step routes. |
| `filter` | Filter DAG, node, runtime filter effect, intermediate, and filter resource policy. |
| `geometry` | Shape, path, stroke, vertices, mesh, tessellation, and render-step geometry routes. |
| `clip` | Clip stack, scissor, analytic, stencil, mask, shader clip, and ordering. |
| `atlas` | Path, coverage, glyph, or text atlas capacity, generation, mutation, and eviction. |
| `resource` | Resource provider, allocation, device generation, device loss, and materialization. |
| `layer` | saveLayer, offscreen target, backdrop, initialization, source filter, and composite behavior. |
| `vertices` | DrawVertices and mesh-like buffer, topology, primitive color, and index behavior. |

Promoted diagnostics must define:

- canonical reason code;
- human-readable message template;
- route kind or stage that emitted it;
- facts required in the diagnostic dump;
- whether the diagnostic is terminal or retryable;
- PM evidence field names.

## Diagnostic Aliases

During draft consolidation, older examples may still appear in local specs. A
promoted route must normalize aliases before support is claimed.

Current aliases to normalize:

| Alias | Canonical code |
|---|---|
| `unsupported.filter.CPU_rendered_texture_forbidden` | `unsupported.filter.cpu_rendered_texture_forbidden` |
| `unsupported.color.YUV_conversion` | `unsupported.color.yuv_conversion` |
| `unsupported.texture.active-attachment-sampled` | `unsupported.texture.active_attachment_sampled` |
| `unsupported.runtimeEffect.unregistered` | `unsupported.runtime_effect.unregistered` |
| `unsupported.runtime_effect.unregistered_runtime_effect` | `unsupported.runtime_effect.unregistered` |

New specs must use the canonical form directly.

## Refusal Semantics

`RefuseDiagnostic` is a product outcome. A refused command is visible to the
caller through diagnostics and route reports; it is not silently dropped unless
the command semantics are no-op by definition, such as empty clip or proven
cull.

The renderer may batch accepted commands around a refused command only when
ordering, target state, layer state, and reporting semantics remain explicit.
Refusal must not cause later commands to inherit an invalid state.

If a user or host application can increase a budget, the diagnostic must name
the exact budget policy and requested threshold. Increasing the budget may
allow a future route attempt, but it must not change the meaning of the
original diagnostic.

## Validation Requirements

Before an implementation ticket promotes route support, it must prove:

- the spec status and decision maturity are named;
- route kind, strategy, and plan names are not conflated;
- diagnostics use canonical lowercase reason codes;
- any aliases are normalized in emitted reports;
- capability facts that affect validity are present in dumps;
- refusal remains stable across equivalent inputs;
- no hidden CPU-rendered compatibility path was selected.
