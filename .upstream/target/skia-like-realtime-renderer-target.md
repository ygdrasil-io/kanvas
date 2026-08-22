# Target: Skia-Like Breadth And Real-Time Renderer

Date: 2026-08-22
Status: Active target
Parent architecture: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Spec entry point: `.upstream/specs/skia-like-realtime/README.md`

## Purpose

This document defines the active product target for Kanvas: a Skia-like 2D
renderer with broad CPU/WebGPU fidelity and a measured real-time lane. It is a
target contract, not an implementation schedule or a progress report.

The supported runtime evidence is headless/offscreen. Native interactive
windowing is outside the current target and requires a separate architecture
decision before it can become a supported route.

This target is not a Ganesh or Graphite port. Kanvas keeps its WebGPU backend,
Kotlin/WGSL pipeline, explicit fallback diagnostics, generated evidence
discipline, and registered runtime-effect model.

WGSL is the shader implementation target. SkSL is referenced only where Kanvas
exposes or emulates Skia compatibility APIs such as `SkRuntimeEffect`; dynamic
SkSL compilation is not part of the target. A supported runtime effect requires
a registered Kanvas descriptor, Kotlin/CPU behavior, and a parser-validated
WGSL GPU module.

## Target outcome

Kanvas should provide:

- high-fidelity CPU/WebGPU rendering for selected Skia-relevant feature
  families;
- bounded support for path coverage, image filters, text/glyphs,
  blend/color filters, gradients, runtime effects, and GM-derived scenes;
- a headless/offscreen frame loop that can animate, transform, filter, and
  inspect selected scenes;
- PM evidence that shows rendering behavior, diagnostics, and frame telemetry;
- release gates combining correctness, refusal policy, performance budgets, and
  real-time measurements.

## Capability boundaries

Support is evaluated per capability and per route. The following boundaries are
part of the target:

| Capability | Required boundary |
|---|---|
| Rendering breadth | Each selected feature family has a support contract, an explicit refusal contract, and generated evidence. |
| Skia-like fidelity | Selected GM/reference rows have a reference or documented oracle, CPU output, GPU output when applicable, diff statistics, and a root-cause classification. |
| Real-time runtime | The headless/offscreen route exposes deterministic frame progression, invalidation, resource generation, cache counters, and stable unsupported diagnostics. |
| Performance and cache readiness | Measurements include warmup, steady-state samples, thresholds, quarantine/rebaseline policy, and cache/resource counters where relevant. |
| PM and release operability | Reports and demo artifacts are reproducible from checked-in commands or artifacts and state non-claims explicitly. |

Readiness must be derived from current evidence artifacts. This document does
not assign a fixed percentage or preserve a snapshot of an earlier score.

## Current support boundary

- CPU remains the behavioral reference for Skia-like semantics.
- WebGPU is the GPU backend; GPU claims require CPU comparison and route
  diagnostics.
- The runtime lane is headless/offscreen and must not imply a supported native
  windowing route.
- Broad Skia parity is not implied by a selected family or a diagnostic-only
  route.
- Missing support remains visible as an `expected-unsupported` result with a
  stable reason code.
- Font and codec capabilities are dependency-gated until their real
  implementations and evidence exist.

## Evidence contract

Every support claim must identify the owning capability and provide the
evidence appropriate to that route:

- reference pixels or a documented non-Skia oracle;
- CPU output or an explicit CPU refusal;
- GPU output or an explicit GPU refusal;
- diff/stat artifacts and the threshold policy used;
- route diagnostics and fallback reasons;
- measured performance data or an explicit non-gating rationale;
- reproducible PM/report links.

Refusals remain in inventories and dashboards. A route diagnostic alone never
promotes a capability.

## Architecture rules

- Preserve one semantic pipeline across CPU and WebGPU.
- Keep CPU as the reference path for Skia-like behavior.
- Keep WebGPU as the GPU backend; do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep `SkRuntimeEffect` as a compatibility facade backed by registered
  Kotlin/WGSL implementations.
- Generate deterministic WGSL and validate it through the evolving `wgsl4k`
  dependency.
- If `wgsl4k` parser, IR, or generator behavior is ambiguous, stop the Kanvas
  assumption and record a minimized upstream issue rather than adding a hidden
  workaround.
- Pipeline keys represent layout, shader code, resource topology, or pipeline
  state; concrete resource identity and arbitrary uniform values are not key
  axes.
- Missing support produces stable diagnostics, never a silent fallback.
- Font and codec work uses real dependencies or real implementations; no
  short-lived substitutes are added to improve an evidence count.

## WGSL dependency contract

The current `wgsl4k` integration is expected to provide, for the supported
generated shader subset:

- deterministic parsing and printing of WGSL modules;
- reflection of entry points, resource bindings, structs, scalar/vector/matrix
  types, and uniform layouts;
- round-trip behavior without semantic edits when syntax is supported;
- source-span diagnostics for parse and validation failures.

The following remain dependency-gated until evidence proves them:

- complex expression normalization in generated effect code;
- nested uniform payload alignment and array layout edge cases;
- parser recovery diagnostics after invalid syntax;
- stable preservation of comments or non-semantic formatting;
- new WGSL features required by additional runtime-effect families.

Any parser or generator behavior that changes shader meaning, loses reflection
data, accepts or rejects the wrong WGSL, or makes generated output
nondeterministic must be recorded as an upstream issue with minimized evidence.

## Real-time runtime contract

The supported real-time lane is headless/offscreen. It must provide:

- deterministic frame ticks and scene inputs;
- explicit invalidation and resize/resource-generation behavior;
- frame timing, cache, and resource telemetry;
- selected scene replay with typed commands and bounded refusal semantics;
- CPU oracle facts shared by tests, native smoke where available, and reports;
- nonblank/readback evidence where the route supports it;
- a clear distinction between offscreen readback and window-surface capture.

Interactive native windowing, arbitrary display-list replay, arbitrary event
injection, broad blend/clip/image support, and dynamic arbitrary runtime-effect
controls are outside this target until a separate decision and evidence package
accept them.

## PM and release evidence

Each supported capability should expose a PM-visible artifact containing:

- the selected scene or fixture;
- CPU/GPU/reference results and diff statistics where applicable;
- route diagnostics and explicit unsupported reasons;
- frame/cache/performance telemetry for runtime-sensitive work;
- reproducible commands and artifact paths;
- concise non-claims that distinguish support from scope.

Correctness and performance gates are maintained separately. A performance
payload with estimated or missing measurements is not a measured release gate.

## Open decisions

The following decisions remain intentionally outside the target contract:

- flagship PM scene selection;
- frame target and warning threshold for curated headless scenes;
- first text language/script scope beyond the supported fixtures;
- any future native host architecture and its evidence requirements.
