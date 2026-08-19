# Agentic Skia Fidelity Convergence Design

Date: 2026-08-14

Status: Accepted for implementation planning

## Purpose

Define a safe agentic operating model for improving Kanvas rendering fidelity
against Skia. The model first restores trustworthy test evidence, then reduces
Skia GM gaps by independently verifiable root-cause cohorts.

This design responds to the FP-13 test report at `f2e68b895`:

- `ModeColorFilterGm` and `ModeColorFiltersGm` now fail deterministically with
  `unsupported.frame_memory.aggregate_budget_exceeded`.
- `GPUAllApiBlendSurfaceTest` has an observed full-suite-only
  `failed.surface.prepared.session-close` failure.
- SVG `texture-3` can differ in a full run but pass when isolated.
- FP-13 evidence claims an unchanged dashboard based on a snapshot taken
  before the FP-13 landing, so it is no longer a sufficient current baseline.

The current JUnit XML confirms the two `modecolorfilters` failures. The only
working-tree change to `test-similarity-scores.properties` is its timestamp;
this design must not overwrite it incidentally.

## Goals

- Restore a reproducible, truthful headless rendering signal before claiming
  any fidelity improvement.
- Use CPU as the Skia-like reference path and WebGPU/WGSL as the GPU backend.
- Improve true Skia-comparable GM fidelity, not only CPU-oracle breadth rows.
- Make every support or refusal transition evidence-backed and auditable.
- Allow parallel work without concurrent edits to the same source, generated
  artifacts, score file, or report.

## Non-Goals

- Porting Ganesh or Graphite.
- Implementing a SkSL compiler, IR, VM, or arbitrary dynamic shader support.
- Treating an old FP plan, old phase checklist, or recovered Git-history
  document as active backlog.
- Making expected refusals disappear by weakening thresholds, skipping tests,
  or silently falling back.
- Requiring Kadre or its unpublished submodule for headless CI evidence.

## Architectural Constraints

- `KanvasPipelineIR` remains the shared semantic contract; geometry produces
  coverage and paint consumes it.
- Registered runtime effects require Kotlin/CPU behavior and parser-validated
  WGSL implementations. SkSL is API-compatibility wording only.
- A surprising WGSL parser, IR, or generator result stops the Kanvas-side
  assumption and becomes a minimized `wgsl4k` ticket.
- Font and codec gaps remain dependency-gated until a real dependency or real
  implementation is available.
- A CPU-oracle row may demonstrate rendering breadth but does not count as
  Skia-comparable fidelity.

The source target documents are:

- `.upstream/target/skia-like-realtime-renderer-target.md`
- `.upstream/specs/skia-like-realtime/README.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/wgsl-pipeline/README.md`

## Agent Topology And Isolation

One orchestrator owns sequencing, integration decisions, and the final wave
report. It does not implement all fixes.

| Role | Agent type | Scope | Write ownership |
| --- | --- | --- | --- |
| Orchestrator | primary session | Defines one wave, resolves conflicts, integrates results serially | Wave report and integration decisions only |
| Discovery | `explore` | Read-only source, JUnit, dashboard, and route triage | None |
| Fix | `general` | One root-cause cohort and its focused tests | Its isolated worktree only |
| Evidence scanner | `general` or primary session | Produces the fresh JUnit/dashboard inventory | Generated evidence and scores, exclusively |
| Independent reviewer | `general` | Checks a completed cohort against its acceptance contract | Review note only |

`general-pro` is deprecated and must not be used for routine discovery,
implementation, or verification. It is reserved only for an exceptional design
or review escalation where the smaller agent types cannot answer the question.

Fix agents use separate Git worktrees. They receive an exclusive source-file
boundary before editing. If two candidate fixes need the same production file,
they become one sequential task rather than competing parallel edits. The
orchestrator serializes integration and report generation.

Only the evidence scanner may update generated renders, dashboard output, or
`integration-tests/skia/test-similarity-scores.properties`. No fix agent edits
these artifacts directly.

## Wave 0: Restore Signal Integrity

Wave 0 is a release-blocking prerequisite for fidelity work. Its three tasks
are independent enough to investigate and implement in parallel after their
file boundaries are confirmed.

### A. Core-Primitive Destination Snapshot Accounting And Native Materialization

Owner boundary:

- `gpu-renderer/.../GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
- `gpu-renderer/.../GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- focused builder, frame-planner, and materializer tests

The evidence indicates that FP-13 correctly makes scalar-coverage AA `SRC`
draws read destination pixels, but the core-primitive builder creates a
full-target destination snapshot resource for every packet. In
`modecolorfilters`, many ordered copies therefore exceed the 1 GiB aggregate
budget. The current native shader deliberately samples device coordinates from
an exact full-target copy, so this wave shares one full-target snapshot resource
across non-overlapping copy lifetimes rather than changing the shader/uniform
ABI. After the accounting fix, the native materializer must also accept and
execute the resulting ordered copy sequence; otherwise the GM merely advances
from an aggregate-budget refusal to
`unsupported.native-core-primitive.destination-copy-shape`.

The task must preserve the destination-read blend semantics. It must
deduplicate the full-target snapshot resource across non-overlapping lifetimes,
reuse the existing destination-snapshot aliasing/grouping contract, and extend
native materialization to execute the resulting ordered copy sequence. A future
footprint-bounded optimization would require a separately reviewed shader and
uniform-origin ABI change and is not part of this wave. This task must not
solve the failure by raising the budget or reverting the scalar-coverage
projection.

Required proof:

- A focused failing test for many small destination-read packets.
- A unit assertion that all non-overlapping copies share one full-target
  preparation/allocation and that the frame budget counts it once.
- A frame-planner assertion that non-overlapping aliases remain valid and
  overlapping aliases still refuse atomically.
- A native materializer test proving multiple non-overlapping ordered copy
  steps are accepted and retain consumer order.
- An isolated `modecolorfilters` GM run that no longer emits
  `unsupported.frame_memory.aggregate_budget_exceeded` or the old
  single-copy shape refusal.
- CPU/GPU/reference/diff evidence for the affected GM family.

### B. Prepared-Session Lifecycle And SVG Error Truthfulness

Owner boundary:

- `kanvas/.../GPUPreparedSurfaceFrameExecution.kt`
- backend factory/session lifecycle code as required
- focused lifetime tests and `integration-tests/svg/SvgIntegrationTest.kt`

The prepared executor caches a session across a process-wide native device
lifecycle. Test fixtures can dispose the device, leaving the executor to close
an already closed or poisoned cached session during the next generation. This
causes the observed `failed.surface.prepared.session-close` contamination.

The task must tie cache invalidation to device disposal or make the generation
transition explicit while retaining failure provenance. It must not catch and
discard close failures. The SVG integration test must only abort known
unsupported-feature diagnostics; it must surface lifecycle and terminal GPU
errors as failures.

Required proof:

- Repeated dispose/recreate and ordered blend-suite probes pass in consecutive
  reruns.
- The final relevant JUnit XML has no `failed.surface.prepared.session-close`.
- `texture-3` is checked alone and after its SVG siblings; terminal lifecycle
  errors are failures rather than aborts.

### C. Evidence Reconciliation And Gate Manifest

Owner boundary:

- A new date-stamped report below `reports/upstream-rebaseline/`
- Fresh JUnit-derived machine-readable inventory and dashboard artifacts

This task does not change rendering. It records the pre-fix evidence, the
post-fix evidence, classifications, environment, commands, and a precise delta
from the FP-13 snapshot. It must distinguish:

- terminal expected refusals;
- unexpected renderer or lifecycle failures;
- missing references and size mismatches;
- similarity failures;
- test aborts;
- CPU-oracle rows versus Skia-comparable rows.

The report is the proposed written issue/closure record for the two
`modecolorfilters` regressions. It must state that the previous FP-13 dashboard
claim was based on pre-landing evidence rather than silently revise history.

## Wave 0 Integration Gate

The orchestrator merges Wave 0 results only when all conditions hold:

- The two `modecolorfilters` cases are no longer failures caused by aggregate
  memory accounting or the old one-copy materializer limitation.
- No targeted JUnit XML contains `failed.surface.prepared.session-close`.
- SVG does not convert a terminal GPU/lifecycle error to a skip.
- No similarity threshold, test assertion, expected-unsupported category, or
  reference artifact was weakened to obtain the result.
- The fresh manifest makes every delta from the prior baseline explicit.
- Focused tests, `:kanvas:test`, `:gpu-renderer:test`,
  `:integration-tests:skia:test`, and `:integration-tests:svg:test` are run as
  evidence. Existing known failures remain separately identified, never hidden
  by a blanket success claim.

The full suite has expected failures, so its process exit status is not the
only gate. The machine-readable manifest must classify each failure and reject
any new unclassified or unexpected result.

## Wave 1: Fresh Skia Fidelity Classification

Wave 1 starts only after Wave 0 is integrated. The evidence scanner runs the
Skia GM suite and generates one sealed baseline for the wave. It is the only
writer of generated scores and dashboard files.

Read-only discovery agents then examine disjoint refusal or low-similarity
cohorts. Each returns a root-cause hypothesis, working comparison, affected
files, a proposed ownership boundary, and required evidence. The classifier
groups rows by causal subsystem rather than GM name.

Historical FP-13 inventory suggests these likely independent first candidates:

| Candidate cohort | Historical scale | Expected investigation focus |
| --- | ---: | --- |
| Non-deterministic path keys | 103 GM refusals | Geometry key canonicalization and stable lowering identity |
| Image alpha interpretation | 56 GM refusals | Premul/unpremul and image sampling contracts |
| Gradient material capability | 59 GM refusals | Shared pipeline lowering and generated WGSL capability |
| Stencil edge-fan budget | 39 GM refusals | Geometry/coverage execution bounds |

These numbers are prioritization hints, not a current backlog. The new scanner
must recompute counts and references before an implementation agent is
assigned.

The classifier prioritizes a cohort using:

1. Number of Skia-comparable rows unlocked by one root cause.
2. Confidence in a shared root cause rather than a per-GM workaround.
3. Product value and reference quality.
4. Risk to CPU/GPU semantic parity, memory, and frame time.
5. Whether the source-file boundary permits isolated parallel implementation.

## Fidelity Fix Loop

For each selected cohort:

1. A discovery agent captures the failing GM's route diagnostics, CPU output,
   GPU output, Skia reference, diff/stat artifact, and minimal operation trace.
2. The classifier assigns exactly one owner and defines the source/test file
   boundary.
3. The fix agent writes a focused failing test before changing implementation.
4. The agent changes one semantic layer at a time. CPU and GPU behavior cannot
   drift together without an explicit CPU-oracle justification.
5. The evidence scanner reruns the focused cohort and creates before/after
   artifacts.
6. An independent reviewer rejects assertion weakening, unsupported-to-pass
   relabeling without pixels, untracked thresholds, and silent fallbacks.
7. The orchestrator serially integrates accepted cohorts and refreshes the
   wave manifest.

A feature is not marked supported from a route diagnostic alone. Its evidence
must include reference, CPU, GPU, diff/stat, route diagnostics, stable fallback
behavior, and relevant memory/performance facts.

## Escalation Rules

- WGSL parser/reflection/generator ambiguity: stop the change and prepare a
  minimized `wgsl4k` ticket. Do not install a hidden Kanvas workaround.
- A legitimate unsupported dependency or unregistered runtime effect: retain a
  stable refusal with a clear explanation.
- Threshold pressure: require a family-specific, reference-justified policy
  decision and independent review. Never lower a global threshold to close a
  cohort.
- A shared-file conflict: merge the work into one sequential agent task.
- Three failed targeted fix hypotheses: stop and re-evaluate the affected
  architecture with the user rather than stacking further fixes.
- Native Kadre evidence: keep it opt-in and separate from headless CI; document
  submodule requirements where used.

## CI And Evidence Tiers

| Tier | Trigger | Evidence | Purpose |
| --- | --- | --- | --- |
| T0 | Per-agent loop | Focused unit test and one GM/family repro | Fast hypothesis validation |
| T1 | Every integration | Affected module tests, targeted Skia/SVG probes, JUnit delta parser | Prevent local regressions and error masking |
| T2 | Wave close | Full headless Skia, SVG, Kanvas, and GPU renderer suites plus fresh dashboard/manifest | Validate the full classified baseline |
| T3 | Opt-in native | Kadre/WebGPU evidence on documented hardware | Demonstrate live runtime only; not a hidden headless dependency |

All evidence records the adapter, driver, JDK, Gradle command, display/runtime
configuration, repeat count where flakiness is evaluated, and generated
artifact paths. Native performance evidence remains reporting-only unless an
explicit accepted gate says otherwise.

## Definition Of Done

A Wave 0 regression is closed only with a root-cause test, focused reruns,
full relevant-suite evidence, native execution evidence where applicable, and
an explicit report delta.

A Wave 1 cohort is closed only when:

- Its reference provenance is declared.
- It has CPU, GPU, reference, diff/stat, and route evidence.
- It introduces no new unclassified failures or regressions.
- It preserves explicit refusal behavior for unsupported adjacent cases.
- It does not weaken thresholds or assertions.
- It has an independent review result and a wave-manifest entry.

## Next Decision

The implementation plan begins with Wave 0. It names concrete tests, commands,
file boundaries, and the order in which agents are dispatched. Native
materialization is part of Wave 0A rather than a follow-up.
