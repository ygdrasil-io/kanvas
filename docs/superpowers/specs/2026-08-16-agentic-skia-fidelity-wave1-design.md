# Agentic Skia Fidelity Wave 1 Design

Date: 2026-08-16
Status: Accepted for implementation planning
Scope: Fresh Skia Fidelity Classification, one causal cohort, and one reviewed fix

## Purpose

Wave 1 establishes a fresh, auditable classification of Skia fidelity gaps after
Wave 0. It must select at most one causal cohort, prove that the cohort is
actionable, and either close it with a reviewed renderer fix or remain blocked.
The classification is based on current evidence, not on historical FP-13 or
Wave 0 counts.

The current precondition is satisfied:

- PR #2064 is merged into `codex/graphite-dawn-frame-plan-design`.
- The active worktree was clean before this design document was added and is
  the `codex/skia-fidelity-wave1` worktree at the merged Wave 0 commit.
- Wave 0 artifacts remain historical evidence and are not regenerated in place.
- The `wgsl4k` and Kadre submodules are not required for the headless
  classification lane and remain explicitly dependency-gated.

## Goals

- Seal one fresh Skia GM baseline at the current source commit.
- Run SVG and relevant CPU/GPU suites separately so their failure populations
  cannot be conflated.
- Record reference provenance, CPU/GPU pixels, route diagnostics, diffs, stats,
  refusals, and non-claims in one dated manifest.
- Group current rows by shared causal subsystem rather than GM name.
- Compare candidate cohorts using recomputed current denominators.
- Select one cohort only when a shared cause and isolated ownership boundary are
  demonstrated.
- Implement a focused fix with a red regression test and independent review.
- Produce before/after evidence that proves pixel improvement, not only route
  execution.

## Non-Goals

- Porting Ganesh or Graphite.
- Rebuilding Skia's SkSL compiler, IR, or VM.
- Treating dynamic SkSL compilation as a Kanvas implementation target.
- Raising the 1 GiB frame-memory budget.
- Lowering a global or family similarity threshold.
- Modifying JUnit assertions or reference PNGs to obtain a pass.
- Reusing old FP-13 inventory numbers as an active denominator.
- Converting a terminal refusal or lifecycle failure into a success or skip.
- Requiring an initialized Kadre or `wgsl4k` submodule for headless evidence.
- Implementing more than one causal cohort in this wave.

## Architectural Constraints

- CPU remains the Skia-like reference path.
- WebGPU/WGSL remains the GPU backend.
- `KanvasPipelineIR` remains the shared semantic pipeline contract.
- Geometry produces coverage and paint consumes coverage.
- `SkRuntimeEffect` remains a compatibility facade backed by registered
  Kotlin/WGSL descriptors.
- Registered runtime effects require Kotlin/CPU behavior and parser-validated
  WGSL implementations.
- A surprising `wgsl4k` parser, IR, reflection, or generator result stops the
  Kanvas change and becomes minimized `wgsl4k` evidence instead of a hidden
  Kanvas workaround.
- Unsupported behavior retains a stable diagnostic and an explicit fallback or
  refusal.
- Memory and performance impact must be measured or explicitly documented as
  not gating the selected cohort.

## Agent Topology And Ownership

| Role | Agent | Scope | Write ownership |
| --- | --- | --- | --- |
| Orchestrator | Primary session | Preconditions, ordering, selection, integration, final report | Plans, decision, final report, integration commits |
| Evidence scanner | `general` or primary session | Fresh JUnit inventory, renders, dashboard, scores, manifests | All Wave 1 generated evidence, scores, dashboards, PNGs, manifests |
| Discovery | Multiple `explore` agents | Disjoint current causal cohorts, read-only | None |
| Fix | `general` agent | One selected cohort in an isolated worktree | Exclusive production/test boundary assigned by plan |
| Reviewer | Independent `general` agent | Plan, diff, focused tests, and before/after proof | Review result only |

The evidence scanner is the only writer of generated scores, dashboards, PNGs,
JUnit-derived inventories, and manifests. Discovery and fix agents may inspect
existing files and use disposable output outside the repository, but they must
not edit or stage generated evidence. If two candidate causes need the same
production file, they are one sequential ownership boundary, not parallel fixes.

## Fresh Baseline Flow

The evidence scanner first records `git status --short --branch`, the source
commit, repository metadata, Java/Gradle/runtime details, and the merged Wave 0
precondition. It then runs these lanes separately with `DISPLAY=:99`, `-F off`,
`--no-daemon`, `--no-parallel`, and plain console output:

```text
DISPLAY=:99 ./gradlew -F off :integration-tests:skia:test \
  --tests "org.graphiks.kanvas.skia.SkiaGmRunner" \
  -Dkanvas.gm.includeBlocking=true \
  --no-daemon --no-parallel --console=plain

DISPLAY=:99 ./gradlew -F off :integration-tests:svg:test \
  --no-daemon --no-parallel --console=plain

DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --no-daemon --no-parallel --console=plain

DISPLAY=:99 ./gradlew -F off :gpu-renderer:test \
  --no-daemon --no-parallel --console=plain
```

The Skia and SVG commands run with `DISPLAY=:99` prefixed in the shell. Any
additional focused CPU/GPU command must be recorded in the manifest rather than
silently folded into a suite total.

The full Skia inventory is not replaced by a focused GM run. Focused runs are
allowed only as supplemental probes after the inventory has been sealed.

## Wave 1 Manifest

The scanner writes new date-stamped artifacts without modifying Wave 0 files:

```text
reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.json
reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-manifest.md
reports/upstream-rebaseline/2026-08-16-skia-fidelity-wave1-inputs/
```

The JSON manifest contains at least:

- `schemaVersion`, `kind`, `generatedBy`, `generatedAt`;
- `sourceCommit` and repository/worktree identity;
- exact commands and environment values;
- SHA-256 hashes for every input and generated evidence file;
- JUnit totals split by suite, outcome, terminal failure, expected refusal,
  abort, and unclassified error;
- dashboard and score counters with their provenance;
- rows grouped by `referenceKind`;
- reference paths, dimensions, and size-mismatch classifications;
- CPU, GPU, diff, statistic, and route-artifact paths when present;
- refusal codes, missing references, invalid sizes, similarity failures, and
  explicit non-claims;
- `globalThresholdWeakened`, `assertionsWeakened`,
  `referencesModified`, `memoryBudgetChanged`, and `readinessDelta` policy
  fields, all expected to remain false or zero;
- an evidence status of `classification`, `approved`, or `blocked`.

The Markdown manifest is a human-readable projection of the same data. It must
state that Wave 0 and FP-13 counts are context only and must never present them
as the Wave 1 denominator.

## Row And Reference Classification

Each current row is classified independently of its GM display name.

`skia-comparable` requires a valid Skia reference with declared provenance,
matching dimensions, a captured reference hash, and a valid comparison shape.
`test-oracle` and `cpu-oracle` remain useful evidence classes but do not count
as Skia-comparable fidelity. Missing references, invalid dimensions, and size
mismatches remain visible rows with non-claim status; they are excluded from
the comparable denominator.

For each row, the scanner preserves:

- GM/class identity and source registration;
- reference kind, path, dimensions, and hash;
- CPU and GPU render paths, dimensions, and hashes;
- similarity and diff statistics without changing thresholds;
- route diagnostics and stable refusal codes;
- JUnit outcome, failure/error message, abort status, and classification;
- the candidate causal bucket and confidence supplied by discovery;
- adjacent unsupported behavior that must remain refused.

The scanner distinguishes a route that executed from a pixel result that
improved. Route success alone never promotes a row.

## Causal Cohort Discovery

After the baseline is sealed, discovery agents receive disjoint row sets based
on current route/failure/trace signatures. The historical candidates are only
starting hypotheses:

- path-key canonicalization;
- image alpha interpretation and sampling;
- gradient material capability;
- stencil edge-fan or coverage budget;
- any other shared cause revealed by the fresh rows.

Every candidate cohort must return, without repository edits:

- representative GM rows and exact current comparable-row counts;
- route diagnostics and refusal/error codes;
- a minimal operation trace for each representative;
- reference provenance and quality;
- CPU/GPU/reference/diff/stat availability;
- a shared-cause explanation and confidence level;
- affected production/test files;
- an exclusive ownership boundary proposal;
- memory/performance risk;
- stable fallback/refusal behavior for adjacent unsupported cases;
- explicit gaps that prevent a support claim.

The evidence scanner then captures or verifies the required artifacts for each
candidate cohort. A discovery note cannot claim a cohort is actionable merely
because several GM names share a label or a refusal string.

## Selection Rule

The classifier records three separate quantities:

1. `observedComparableRows`: current rows with valid Skia references and valid
   comparison dimensions, regardless of current pass/fail outcome.
2. `candidateUnlockedRows`: comparable rows whose route, operation trace, and
   source ownership all support the same causal hypothesis.
3. `supportedRowsAfter`: rows that actually improve after the fix with complete
   reference/CPU/GPU/diff/stat/route evidence.

Only the first two quantities may influence selection; the third is produced
only after implementation. Historical counts are not inputs to these values.

Selection is lexicographic:

1. highest `candidateUnlockedRows`;
2. strongest demonstrated shared-cause confidence;
3. best reference completeness and provenance;
4. lowest CPU/GPU, memory, and performance risk;
5. cleanest isolated file boundary.

The decision record must include rejected cohorts and the reason each was not
selected. If no candidate has a demonstrated shared cause, or if required
reference/evidence quality is insufficient, the wave is `blocked` and no
production correction is dispatched.

## Focused Implementation Contract

Before production changes, the orchestrator writes a plan under
`docs/superpowers/plans/` naming the selected cohort, exact red test, source
boundary, test boundary, evidence paths, and non-goals.

The fix agent must:

- add and run a focused red regression test before the implementation change;
- modify only the assigned source/test files;
- preserve CPU reference semantics and GPU/WGSL backend ownership;
- keep all thresholds, assertions, references, and the 1 GiB budget unchanged;
- preserve adjacent stable refusals and fallback diagnostics;
- stop and report a minimized `wgsl4k` issue if parser/reflection/generator
  behavior is ambiguous;
- leave generated score/dashboard/PNG/manifest files untouched.

The fix and focused-test changes are committed separately from evidence. The
orchestrator integrates the reviewed fix serially into `codex/skia-fidelity-wave1`.

## Review Contract

The independent reviewer checks the selected plan, source diff, red-to-green
test transition, focused suite results, route diagnostics, and complete
before/after evidence. Approval requires proof of pixel improvement or an
explicit conclusion that the hypothesis failed. A route-only improvement is
not sufficient.

The reviewer must explicitly reject:

- weakened assertions, thresholds, references, or budgets;
- unsupported-to-pass relabeling without reference/CPU/GPU pixel evidence;
- hidden fallback or newly unclassified failure;
- unbounded memory or performance regression;
- shared-file edits outside the assigned boundary;
- unsupported `wgsl4k` assumptions.

## Post-Fix Evidence And Gate

The evidence scanner reruns the selected cohort and all relevant focused lanes,
then writes a before/after report with separate source commits and hashes. The
cohort is `approved` only when all of the following are true:

- the root cause is demonstrated by the focused regression test;
- comparable rows are explicitly counted before and after;
- reference, CPU, GPU, diff, statistic, and route artifacts are present;
- pixel similarity improves under the unchanged policy, or the scanner records
  a truthful failure and the wave is blocked;
- adjacent refusals remain explicit and stable;
- no new unclassified error or regression is introduced;
- no threshold, assertion, reference, memory budget, or fallback rule changed;
- memory and performance impact is documented;
- the manifest contains source and artifact hashes;
- independent review is approved.

If any predicate fails, the final status is `blocked`. The report must retain
the failed hypothesis, the evidence gap, and every adjacent refusal rather than
masking or skipping it.

## Expected Deliverables

- dated Wave 1 manifest and human-readable projection;
- fresh causal cohort classification;
- written selected/rejected cohort decision;
- targeted implementation plan;
- focused test and implementation commits;
- reference/CPU/GPU/diff/stat/route artifacts;
- independent review result;
- before/after report with hashes and commit provenance;
- final `approved` or `blocked` status.

## Traceability

This design follows:

- `.upstream/target/skia-like-realtime-renderer-target.md`;
- `.upstream/specs/skia-like-realtime/README.md`;
- `.upstream/target/high-performance-wgsl-pipeline-target.md`;
- `.upstream/specs/wgsl-pipeline/README.md`;
- `docs/superpowers/specs/2026-08-14-agentic-skia-fidelity-design.md`;
- `docs/superpowers/plans/2026-08-14-agentic-skia-fidelity-wave0.md`;
- `reports/upstream-rebaseline/2026-08-14-skia-fidelity-wave-0-review.md`.
