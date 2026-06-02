# 04 Performance Tiering And Release Gates

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

M59 proves selected measured performance lanes can be release-blocking. The
real-time target needs a broader model: feature-family budgets, frame budgets,
cache budgets, and quarantine/rebaseline rules.

## M67 Performance Tiering

### Budget Classes

| Tier | Meaning | Example gate |
|---|---|---|
| P0 frame | Core live demo frame budget | 60 FPS target, 30 FPS warning for curated scene. |
| P0 pipeline | Selected feature rows | median/p95 lane thresholds, release-blocking. |
| P1 family | Important but broader feature family | warning or quarantine before release-blocking. |
| Inventory | Planning rows | never performance-gating. |

## Measurement Types

- CPU scalar row benchmark;
- CPU vector row benchmark where available;
- GPU/cache row benchmark;
- live frame benchmark;
- warmup/pipeline cache benchmark;
- glyph atlas upload benchmark;
- image-filter intermediate texture benchmark.

## Frame Gate Execution

Frame/FPS gates must be executable without relying on an interactive desktop
session for every CI run.

| Lane | Execution mode | Gate phase |
|---|---|---|
| `frame.headless-webgpu` | Offscreen WebGPU surface or command-submission proxy with pixel readback/nonblank proof. | Required for M65 smoke, candidate in M67, release-blocking only after variance is accepted. |
| `frame.kadre-windowed` | Kadre-hosted window on an eligible adapter with present timing telemetry. | Reporting-only before M68; candidate/release-blocking only on owned hardware. |

Curated P0 scenes:

- `p0-live-basic-transform`;
- `p0-live-filter-small`;
- `p0-live-text-atlas`;
- `p1-live-runtime-effect` when M64 is complete.

Initial frame budget:

- 120 warmup frames;
- 300 measured frames;
- p50 <= 16.7 ms target;
- p95 <= 33.3 ms warning threshold;
- release-blocking threshold is not enabled until M67 records baseline,
  variance, negative fixture, adapter eligibility, and quarantine owner.

## Required Metadata

Measured payloads must include:

- scene id;
- lane;
- status `measured`;
- command;
- host;
- OS/architecture;
- JDK;
- backend;
- adapter for GPU/cache lanes;
- sample count;
- median and p95;
- counters;
- baseline name and commit;
- owner;
- regression label;
- raw samples or stable summary.

Estimated or missing payloads are not measured evidence.

## Runtime Frame Counters

Frame-level gates should track:

- frame count;
- median frame time;
- p95 frame time;
- worst frame time;
- warmup frame count;
- pipeline cache misses;
- bind group churn;
- texture upload bytes;
- intermediate texture bytes;
- glyph atlas misses;
- fallback/refusal count.

## Gate Progression

1. Reporting-only: metric is visible but not blocking.
2. Warning: metric appears in PM report and sprint review.
3. Candidate: threshold is defined and negative fixture exists.
4. Release-blocking: CI fails on threshold or metadata violation.

No metric may skip directly from estimated to release-blocking.

## Quarantine And Rebaseline

Use quarantine when:

- adapter does not match eligibility;
- host/JDK differs from baseline;
- variance exceeds policy but correctness is stable;
- external load makes result suspect.

Use rebaseline when:

- improvement/regression is understood;
- owner approves;
- raw evidence is preserved;
- PM report explains score impact.

Rollback is required when:

- correctness regresses;
- a support row loses required artifacts;
- a release-blocking P0 metric fails repeatedly without accepted rebaseline.

## Negative Fixtures

Every release-blocking gate must have at least one negative fixture that proves
the failure path. The fixture must not mutate checked-in baseline data.

## M84 Native Frame Timing Candidate

M84 is the first `frame.kadre-windowed` timing candidate slice after the native
Kadre display-list replay evidence. It may serialize measured native samples,
but it must remain candidate/reporting-only until the owning release gate accepts
variance, adapter eligibility, resource telemetry, and an end-to-end frame-time
claim.

Required M84 payload fields:

- lane `frame.kadre-windowed`;
- gate phase `candidate-reporting-only`;
- `releaseBlocking=false`;
- `countedAsMeasuredGate=false`;
- warmup frame count;
- measured sample count;
- p50, p95, and worst observed native timing;
- host OS/architecture;
- JDK version;
- adapter information;
- estimated and missing metric counters;
- cache-counter fields, even when their source is a placeholder;
- quarantine reasons;
- negative fixture status and reason.

M84 may report `measuredPayload.status=measured` only when the serialized
samples are present and positive. That does not mean the metric counts as a
release gate. The counted release-gate flag must stay false until a later
milestone promotes the lane.

The M84 negative fixture must use a too-low p95 threshold, report
`expected-fail`, keep a stable reason, and never rewrite checked-in baselines.

## M85 Runtime Resource/Cache Evidence

M85 replaces the M84 cache-counter placeholder for the selected realtime route
with an auditable deterministic selected-scene resource/cache ledger. It must
not move a performance/cache readiness denominator until observed runtime cache
telemetry exists. The ledger is acceptable only when all of these are true:

- per-frame resource ledger is serialized for the selected route;
- cache hits/misses, shader modules, pipelines, bind groups, textures, texture
  upload bytes, intermediate texture bytes, bind group churn, resource
  generations, and invalid-resource reuse count are present;
- cache key spaces are bounded or explicitly finite for the selected scene;
- `PipelineKey` policy excludes uniform values and remains limited to layout,
  code, resource, and pipeline-state axes;
- resize/scale-factor reconfiguration evidence proves resource generation
  invalidation and zero invalid-resource reuse;
- device/surface loss is either supported with real recreate evidence or
  refused with stable reason
  `m85.device-loss-recreate-observation-unsupported`.

M85 does not make arbitrary scene caches release-ready. It proves the selected
route's cache/resource ledger and the failure taxonomy required before broader
cache gates can become release-blocking.

## MEP-NEXT Runtime Telemetry Classification

FOR-196 may add live resource/cache telemetry only when each counter declares
its source class:

| Source class | Meaning | Gate treatment |
|---|---|---|
| `observed` | Collected from a native or headless runtime route during execution, such as frame samples or surface statuses. | Eligible for reporting and later candidate gates when variance and host eligibility are documented. |
| `observed-partial` | Collected from the selected route, but not a complete cache API signal, such as per-frame shader/pipeline creation counts. | Reporting-only; must name missing counter families. |
| `derived` | Computed from deterministic selected-scene ledgers or fixture contracts. | PM-readable diagnostics only; cannot count as observed cache readiness. |
| `unavailable` | Runtime does not expose the counter yet. | Must include a stable missing-counter reason and a non-claim. |

MEP-NEXT native windowed demo and benchmark tasks remain opt-in local evidence.
The headless CI evidence task may validate schema, bounded scene switching,
input-state telemetry, and observed-vs-derived classification, but it must not
promote `frame.kadre-windowed` to release-blocking and must not count derived
cache hits/misses as observed WebGPU cache telemetry.

## Acceptance

- P0 frame demo has measured telemetry.
- Feature-family budgets exist before broad support claims.
- PM bundle exposes performance status by family.
- CI distinguishes correctness failures from performance quarantine.
- No estimated metric is counted as measured.
