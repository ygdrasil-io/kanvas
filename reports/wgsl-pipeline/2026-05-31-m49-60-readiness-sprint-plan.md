# M49 Sprint Plan: MEP Readiness Gate Toward 60%

Date: 2026-05-31
Status: Proposed
Parent target: `.upstream/target/rendering-conformance-performance-target.md`
Backlog: `.upstream/target/post-mvp-conformance-backlog.md`
Starting point: M48 sprint review

## Goal

M49 should turn the M48 dashboard from a strong local evidence artifact into a
release-oriented MEP readiness gate.

The stretch PM target is to move Post-MVP Big Target readiness from 40% to about
60%, but only if M49 lands evidence on four axes at once:

- CI and release gates for the generated scene dashboard;
- portable PM artifact bundle and repeatable demo/report workflow;
- first non-blocking performance trend gate design with measured payloads;
- broader adapter-backed proof for selected high-value scenes.

If M49 lands only the CI validation lane, the readiness score should move less.

## Starting Baseline

M48 closed with:

| Signal | Count |
|---|---:|
| Scene rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| `maturity.generated-evidence` | 21 |
| `maturity.static-evidence` | 2 |
| `maturity.adapter-backed` | 2 |

Post-MVP PM readiness after M48:

| Area | Weight | M48 progress |
|---|---:|---:|
| Evidence foundation | 25% | 100% |
| Skia integration coverage | 25% | 35% |
| CI and release gates | 20% | 10% |
| Performance readiness | 15% | 15% |
| PM demo and reporting workflow | 15% | 15% |

Weighted readiness: about 40%.

## M49 Target Scoring

M49 may claim about 60% readiness only if the closeout proves these movements:

| Area | Weight | Required M49 exit state | Target progress |
|---|---:|---|---:|
| Evidence foundation | 25% | Preserve generated dashboard discipline: 0 tracked-gap, 0 fail, stable ids/tags/fallbacks. | 100% |
| Skia integration coverage | 25% | Add adapter-backed proof for selected high-value pass rows and, if needed, a small number of generated scene rows without broad new family claims. | 45% |
| CI and release gates | 20% | Add a CI-friendly validation task/gate that fails on support-claim regressions and validates allowed expected-unsupported rows. | 60% |
| Performance readiness | 15% | Convert M43 measured payloads into a non-blocking trend gate contract with baseline metadata, variance policy, and report output. | 35% |
| PM demo and reporting workflow | 15% | Produce a portable artifact bundle with dashboard, counters, references, command evidence, and a repeatable local serve command. | 45% |

Weighted target: `25% * 100% + 25% * 45% + 20% * 60% + 15% * 35% + 15% * 45% = 60.25%`.

Do not round this above 60% unless all lanes land with merged evidence.

## Workstreams

### M49-A Gate Invariants

Define the dashboard invariants that CI can enforce:

- no duplicate scene ids;
- 0 `tracked-gap`;
- 0 `fail`;
- every `pass` row has reference, CPU evidence, route diagnostics, stats, and
  GPU evidence or a documented CPU-only policy;
- every `expected-unsupported` row has a stable fallback reason;
- tags remain parseable and useful for PM filters;
- artifact paths referenced by rows exist in the generated bundle.

Output: `reports/wgsl-pipeline/<date>-m49-dashboard-gate-invariants.md`.

### M49-B CI Validation Task

Add a CI-friendly validation task for the generated scene dashboard.

The task must fail on:

- support-claim regressions;
- missing artifacts for generated support rows;
- duplicate ids;
- unsupported rows without fallback reasons;
- accidental `tracked-gap` or `fail` rows in the promoted dashboard.

The task may allow:

- explicitly listed expected-unsupported rows;
- static Path AA policy sentinels if their tags and fallback reasons remain
  stable;
- reporting-only performance trends until thresholds are promoted.

Output: Gradle task, validation report, and command evidence.

### M49-C Portable PM Artifact Bundle

Generate a portable bundle suitable for PM review.

The bundle must include:

- dashboard HTML;
- scene JSON;
- generated result JSON;
- referenced image, diff, route, and stats artifacts;
- a manifest with commit, generation command, timestamp, row counters, and known
  limitations;
- a documented local serve command.

Output: bundle directory or archive under `build/reports/`, plus report notes.

### M49-D Adapter-Backed Expansion

Increase adapter-backed proof from 2 rows to at least 6 rows.

Recommended candidate rows:

- `bitmap-rect-nearest`;
- `linear-gradient-rect`;
- `src-over-stack`;
- `bitmap-shader-local-matrix`;
- `clip-rect-difference`;
- one M48 paint/transform/gradient pass row if stable on the available adapter.

Every promoted adapter-backed row must name backend, adapter, command, similarity
threshold, fallback reason, and artifact links.

Do not promote broad Path AA, arbitrary image-filter DAG, text/font, codec, or
arbitrary runtime-effect coverage as part of this workstream.

### M49-E Performance Trend Gate Contract

Turn M43 measured payloads into a non-blocking trend gate.

The gate design must define:

- eligible host/JDK/backend/adapter combinations;
- cold/warm measurement policy;
- variance and sample rules;
- baseline owner and update process;
- fail, warn, and quarantine behavior;
- how trend output is shown in the PM bundle.

No blocking performance threshold may be enabled until the contract names a
stable baseline and rollback policy.

### M49-F Release Readiness Checklist

Create a single MEP readiness checklist that links:

- Linear epic and child tickets;
- dashboard gate command;
- PM artifact bundle path;
- current counters;
- allowed expected-unsupported rows;
- adapter-backed rows;
- performance trend status;
- non-claims and dependency-gated gaps.

Output: `reports/wgsl-pipeline/<date>-m49-mep-release-readiness-checklist.md`.

### M49-G Sprint Review And Score Update

Close M49 with a sprint review.

The review must include:

- final dashboard counters;
- before/after PM readiness table;
- validation commands;
- artifact bundle path;
- adapter-backed row list;
- performance trend gate status;
- Linear and PR references;
- whether the 60% target was earned, partially earned, or rejected.

## Demo

The PM demo should show:

1. the dashboard still has 0 `tracked-gap` and 0 `fail`;
2. the CI validation command and its generated report;
3. the portable bundle manifest;
4. adapter-backed rows and their route/stats evidence;
5. the performance trend status as non-blocking;
6. the readiness score calculation.

## Validation

All M49 tickets that touch dashboard generation or evidence must run:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

M49-B must also run the new validation task. M49-C must prove the bundle can be
served locally from the generated output. M49-D must run the owning adapter-backed
capture command on the available GPU adapter.
