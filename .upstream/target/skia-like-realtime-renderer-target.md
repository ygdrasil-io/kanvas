# Target: Skia-Like Breadth And Real-Time Renderer

Date: 2026-05-31
Status: Proposed
Previous target: `archives/target-closeout-2026-05-31/rendering-conformance-performance-target.md`
Parent architecture: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Spec entry point: `.upstream/specs/skia-like-realtime/README.md`

## Purpose

The MEP evidence platform is complete for the selected target: promoted rows
have generated evidence, stable refusals, PM-visible artifacts, and
release-blocking performance gates where selected. The next target has two
tracks: first, increase Skia-like feature breadth and fidelity; second, build a
measured real-time rendering lane on top of that breadth. Real-time is not
claimed complete until M67/M68 gates provide measured frame evidence.

This is not a Ganesh or Graphite port. Kanvas keeps its WebGPU backend,
Kotlin/WGSL pipeline, explicit fallback diagnostics, generated evidence
discipline, and registered runtime-effect model.

## Big Target

Kanvas should become a Skia-like 2D renderer with a real-time lane:

- high-fidelity CPU/WebGPU rendering for selected Skia-relevant feature
  families;
- expanded support for Path AA, image filters, text/glyphs, blend/color
  filters, gradients, runtime effects, and GM-derived scenes;
- an interactive runtime that can animate, transform, filter, and inspect
  scenes at frame cadence;
- PM demos that show actual rendering behavior, not only static dashboard
  evidence;
- release gates that combine correctness, refusal policy, performance budgets,
  and real-time frame telemetry.

## Starting Point

MEP readiness is 100%, but that readiness covers the evidence and selected
performance target. It does not mean broad Skia parity.

Current strengths:

- generated dashboard evidence and PM bundle are release-visible;
- selected CPU/GPU/reference scene rows are reviewable;
- explicit `expected-unsupported` diagnostics prevent false support claims;
- seven selected performance rows have release-blocking measured lanes;
- WebGPU remains the intended GPU backend;
- WGSL parser/generator integration direction is established, but `wgsl4k`
  remains an active dependency and must not be treated as finished;
- Kadre from `ygdrasil-io/poc-koreos` is the intended incubating windowing
  host for live demos and can be included as a git submodule while it is not
  published.

Current gaps:

- broad Path AA, dash, cap, join, stroke-outline, and complex clip support;
- arbitrary image-filter DAGs and picture prepass;
- broad text, shaping, glyph cache, color font, emoji, and font fallback;
- wide Skia GM parity beyond promoted selected rows;
- real-time frame loop, invalidation, cache telemetry, and interactive demo;
- release-grade performance budgets across feature families;
- hosted/live PM demo flow beyond static artifacts.

## New Readiness Model

The previous 100% score remains historical for the MEP evidence target. The
new breadth-and-real-time target starts at 25% because the scope expands
from proof infrastructure to feature breadth and interactive runtime.

Readiness is calculated from counted denominators, not manually assigned sprint
percentages. A milestone may only move an area when its evidence updates the
corresponding denominator and is linked from the sprint report.

| Area | Weight | Denominator | Initial count | Initial progress | 100% means |
|---|---:|---|---:|---:|---|
| Rendering feature breadth | 30% | 10 target feature families with generated support/refusal contracts. | 2/10 | 20% | Path AA, filters, text, blend/color, bitmap, gradients, runtime effects, clipping, transforms/layers, and fallbacks all have selected generated evidence. |
| Skia-like fidelity | 20% | 100 selected GM/reference rows. | 25/100 | 25% | Selected rows have Skia reference or documented non-Skia oracle, diff stats, and burn-down classification. |
| Real-time runtime | 20% | 10 runtime capabilities. | 1/10 | 10% | Kadre host, frame loop, input, invalidation, resource telemetry, live controls, export, nonblank smoke, frame gate, and demo scene all exist. |
| Performance and cache readiness | 15% | 20 measured performance/cache gates. | 7/20 | 35% | Selected row, family, cache, and frame gates have measured baselines, thresholds, and quarantine policy. |
| PM/demo operability | 15% | 20 PM/release artifacts. | 7/20 | 35% | Dashboard, live demo, reports, limitations, release package, and hosted/export flows are reproducible. |

Weighted starting readiness: approximately 25%.

Current readiness after the M74 replay-command foundation slice: 67.75%, rounded for PM to approximately 70%.

| Area | Weight | Current count | Current progress | Movement |
|---|---:|---:|---:|---|
| Rendering feature breadth | 30% | 6/10 | 60% | M66 normalizes selected rows across existing families without adding a new counted family denominator. |
| Skia-like fidelity | 20% | 50/100 selected rows, 37/100 Skia-comparable minimum | 50% selected evidence | M66 adds 19 selected support/refusal rows with `referenceKind`; 6 are `skia-upstream`, 6 `test-oracle`, and 7 `cpu-oracle`. CPU-oracle rows do not automatically count as Skia-comparable fidelity. |
| Real-time runtime | 20% | 9/10 | 90% | M65 adds a reporting-only headless/offscreen 120-frame runtime smoke lane; M68 adds Kadre source-build bridge evidence; M69 adds a bounded Kadre/AppKit/Metal WebGPU present loop; M70-A adds a PM-visible demo task; M70-B/C confirm normalized surface-success evidence and produce a real wgpu4k offscreen texture readback artifact; M71 drives that selected route with Kadre/AppKit `ControlFlow.Poll`; M72 renders one selected `solid-rect` replay contract with explicit command counters and source dashboard evidence; M73 adds a bounded typed replay-pack registry and scene-id routing for selected scenes; M74 extracts that registry into closed typed replay commands without broadening the runtime claim. Native input, broad Kanvas display-list replay, arbitrary op streams, dynamic multi-scene live switching, and window-surface screenshot/readback remain incomplete. |
| Performance and cache readiness | 15% | 9/20 | 45% | M67 promotes `frame.headless-webgpu` to a candidate gate from M65 telemetry, adds one measured family budget, and proves deterministic quarantine/rebaseline behavior. M70-A adds reporting-only `frame.kadre-windowed` telemetry with warmup/measured samples; it is not a release-grade FPS gate. |
| PM/demo operability | 15% | 20/20 | 100% | PM bundle includes M65 runtime telemetry/artifacts, M66 family/reference counters, M67 performance tiering, M68 Kadre bridge/demo evidence, M69 host adapter smoke artifacts, M69 native Kadre present evidence, M70-A/B/C live-runtime/readback evidence, M71 autonomous frame-clock evidence, M72 single-scene replay evidence, M73 replay-pack registry evidence, and M74 replay-command foundation evidence. |

Expected milestone deltas are capped until evidence lands:

| Milestone | Primary area movement | Maximum readiness delta |
|---|---|---:|
| M60 | Rendering breadth, fidelity, performance | +5% |
| M61 | Rendering breadth, fidelity, PM diagnostics | +5% |
| M62 | Rendering breadth, fidelity, PM diagnostics | +5% |
| M63 | Rendering breadth, fidelity, performance | +5% |
| M64 | Rendering breadth, runtime-effect operability | +4% |
| M65 | Real-time runtime smoke and telemetry | +6% |
| M66 | Cumulative GM/reference promotion wave | +8% |
| M67 | Performance/cache/frame gates | +8% |
| M68 | Native Kadre demo package | +8% |
| M69 | Kanvas/Kadre host adapter and bounded native present loop | +6% |
| M70-A/B/C | Live Kadre demo, surface semantics, native readback | +1% |
| M71 | Autonomous Kadre frame clock | +2% |
| M72 | Single-scene Kadre replay | +1% |
| M73 | Bounded Kadre replay pack registry | +3% |
| M74 | Replay command foundation | +0% |
| M70 | Release-candidate closure | Remaining counted evidence only |

## Milestones

| Milestone | Name | Target outcome |
|---|---|---|
| M60 | Coverage & Path AA Expansion | Promote bounded curves, strokes, joins/caps, and nested AA clips without weakening broad refusals. |
| M61 | Image Filter DAG V2 | Render bounded multi-node image-filter graphs with explicit intermediate texture ownership. |
| M62 | Text & Glyph Rendering V1 | Render simple text through the font spec pack, bundled Liberation references, glyph masks, and a WebGPU glyph atlas. |
| M63 | Color, Blend & ColorFilter Parity | Promote bounded SrcOver, linear-gradient color-filter kPlus, and sweep-gradient clamp rows; refuse wide-gamut color-space and advanced blend chains with stable reasons. |
| M64 | Registered Runtime Effects | Promote SimpleRT through a registered Kotlin/WGSL descriptor with parser-reflected uniforms; refuse SpiralRT without WGSL descriptor and arbitrary SkSL with stable reasons. |
| M65 | Real-Time Scene Runtime | Add a Kadre-hosted frame loop, display-list replay boundary, invalidation diagnostics, cache telemetry, live controls, and reporting-only frame metrics. |
| M66 | Skia GM Promotion Wave | Aggregate M60-M64 promotions and add only the missing rows needed to reach the selected 50-100 GM/reference set. |
| M67 | Performance Tiering | Promote M65 frame metrics into a `frame.headless-webgpu` candidate gate, family budgets, and deterministic quarantine/rebaseline evidence. |
| M68 | Native Real-Time Demo | Package Kadre source-build bridge evidence and flagship scene inputs; native windowed launch remains blocked until a Kanvas/Kadre host adapter exists. |
| M69 | Kanvas/Kadre Host Adapter V1 | Generate the host adapter contract, route smoke, first scene route, PM bundle counters, and a bounded standalone native Kadre/WebGPU present loop; keep input-driven interaction and broad Kanvas replay as the next explicit blockers. |
| M70-A | Kadre Live Runtime V1 | Add a PM-visible Kadre demo command, one selected Kanvas-owned scene contract, reporting-only `frame.kadre-windowed` telemetry, and PM bundle evidence. |
| M70-B | Kadre Surface Success | Audit Kadre/wgpu4k surface status semantics and keep raw API status separate from normalized native presentation evidence. |
| M70-C | Kadre Native Readback | Produce or precisely block a real native readback artifact for the selected Kadre scene contract; the current implementation uses wgpu4k offscreen texture readback, not a window screenshot. |
| M71 | Kadre Autonomous Frame Clock | Make the selected live Kadre demo advance from an explicit Kadre/AppKit frame clock rather than pointer/input wakeups, and expose the clock source in PM telemetry. |
| M72 | Kadre Scene Replay V1 | Render one selected `solid-rect` replay contract in the Kadre live route with command counters, CPU/GPU evidence, and explicit broad-replay non-claims. |
| M73 | Kadre Replay Pack V1 | Add a bounded typed replay-pack registry with scene-id selection, selected renderable scenes, expected-unsupported sentinel evidence, and explicit broad-replay non-claims. |
| M74 | Replay Commands V2 | Extract the replay model/registry into closed typed commands while preserving M73 pack behavior, JSON compatibility fields, expected-unsupported diagnostics, and broad display-list non-claims. |
| M70 | Release Candidate Renderer | Freeze API, runtime, PM demo, CI gates, and known limitations for a renderer release candidate. |

## Dependency DAG

```mermaid
flowchart LR
    M60["M60 Path AA slices"] --> M66["M66 cumulative GM wave"]
    M61["M61 filter DAG V2"] --> M66
    M62["M62 text/glyph V1"] --> M66
    M63["M63 color/blend parity"] --> M66
    M64["M64 registered effects"] --> M66
    M60 --> M65["M65 Kadre runtime smoke"]
    M61 --> M65
    M62 --> M65
    M64 --> M65
    M65 --> M67["M67 frame/perf gates"]
    M68 --> M69["M69 host adapter route"]
    M67 --> M68["M68 native Kadre demo"]
    M68 --> M70["M70 release candidate"]
    M69 --> M70
    M69 --> M70A["M70-A live runtime"]
    M70A --> M70
```

M66 is cumulative, not a separate support universe. Rows promoted by M60-M64
count toward the 50-100 target when they satisfy the M66 reference and evidence
rules.

## Architecture Rules

- Preserve one semantic pipeline across CPU and WebGPU.
- CPU remains the reference path for Skia-like behavior.
- WebGPU is the GPU backend; do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- `SkRuntimeEffect` remains a compatibility facade backed by registered
  Kotlin/WGSL implementations.
- Generated WGSL must be deterministic and parser-validated.
- WGSL parser/generator work depends on `ygdrasil-io/wgsl4k`; if an agent
  finds ambiguous, invalid, or surprising parser/IR/generator behavior, it
  must stop that assumption, keep the Kanvas side explicit, and open a
  dedicated `wgsl4k` ticket instead of hiding the issue in Kanvas.
- Live windowing uses Kadre from `ygdrasil-io/poc-koreos`, currently incubating
  and unpublished. Kanvas may include it as a git submodule for M65/M68 work;
  do not replace it with an unrelated shell just to make the demo easier.
- `PipelineKey` axes must represent layout, shader code, resources, or
  pipeline state, not arbitrary uniform values.
- Missing support must produce stable diagnostics, not silent fallback.
- New `pass` claims require reference, CPU/GPU evidence, route diagnostics,
  diff/stat artifacts, and performance impact assessment when relevant.
- Font and codec work must use real dependencies or real implementations; do
  not add substitutes just to clear old backlog rows.

## wgsl4k Capability Baseline

Assumed usable for M60-M64 planning:

- parse and print deterministic WGSL modules used by the current generated
  shader subset;
- inspect entry points, resource bindings, structs, scalar/vector/matrix
  types, and uniform layouts needed by registered descriptors;
- round-trip modules without semantic edits when no unsupported syntax is
  involved;
- report parse/validation failures with enough source span context to attach
  minimized evidence to a ticket.

At-risk until proven by a Kanvas milestone:

- complex expression normalization used by generated effect code;
- edge cases in struct layout, alignment, arrays, and nested uniform payloads;
- diagnostics for parser recovery after invalid syntax;
- preserving comments or non-semantic formatting as stable golden output;
- any WGSL feature added for a new runtime-effect family.

Ticket trigger:

- any parser/IR/generator result that changes shader meaning, loses reflection
  data, rejects WGSL expected to be valid, accepts WGSL expected to be invalid,
  or makes generated output nondeterministic.

## PM Demos

Every milestone must include one PM-visible demo artifact:

- visual side-by-side reference/CPU/GPU/diff when the feature is correctness
  oriented;
- live frame telemetry when the feature affects real-time runtime;
- route diagnostics and fallback notices for unsupported subsets;
- concise non-claims so PM can distinguish support from planned scope.

## Open Decisions

The plan assumes Kadre-hosted desktop-first WebGPU real-time demo, Apple
M-series as the first measured adapter, and Latin text before complex shaping.
These are not blockers for planning; they should be confirmed before
M62/M65/M68 execution.

Closed choices recommended:

- first live demo platform: Kadre desktop windowing via
  `ygdrasil-io/poc-koreos`, included as a git submodule while unpublished;
- first real-time frame target: 60 FPS for curated scenes, with 30 FPS as a
  warning threshold for heavier scenes;
- first text scope: Latin/simple glyph masks before shaping-heavy scripts.

Open question:

- which PM demo scene should become the flagship release-candidate scene:
  dashboard-like technical grid, document/text scene, design-tool canvas, or
  animated creative scene?
