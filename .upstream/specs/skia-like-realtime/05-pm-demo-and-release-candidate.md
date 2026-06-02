# 05 PM Demo And Release Candidate

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

The new target needs PM demos that show real rendering progress. Static
dashboard evidence remains necessary, but it is no longer sufficient once the
goal includes real-time rendering.

## Demo Levels

| Level | Purpose | Required by |
|---|---|---|
| Static evidence dashboard | Review support/refusal claims and diffs. | Every milestone. |
| Interactive web/local demo | Inspect live scene behavior and telemetry. | M65 onward. |
| Native/demo app | Product-style runtime proof. | M68. |
| Release candidate package | Final PM and engineering sign-off. | M70. |

## M68 Native Real-Time Demo

### Required Scene Content

The demo must include:

- animated transform;
- Path AA/stroke/clip content;
- image or bitmap sampling;
- at least one image-filter DAG if M61 is complete;
- simple text/glyph rendering if M62 is complete;
- blend/color filter grid if M63 is complete;
- runtime effect controls if M64 is complete;
- telemetry overlay.

### Controls

- play/pause;
- reset;
- zoom/pan or transform slider;
- feature toggles;
- route/debug overlay toggle;
- export current frame evidence.

### Evidence

The demo must emit:

- screenshot or frame PNG;
- telemetry JSON;
- route summary JSON;
- PM report Markdown;
- known limitations.

## M70 Release Candidate Renderer

### Release Candidate Criteria

- API surface for the demo/runtime is documented.
- CI correctness gates pass.
- Performance gates pass or quarantine with owner-approved rationale.
- PM demo is reproducible.
- Dashboard and runtime evidence agree on support/refusal status.
- Known limitations are explicit.
- No archived backlog row is treated as active work.

### PM Scorecard

The release candidate should report:

| Area | Evidence |
|---|---|
| Feature breadth | Supported/refused family counters. |
| Fidelity | Diff burn-down and representative comparisons. |
| Real-time | Frame time/FPS and resource telemetry. |
| Performance | P0 gate status and quarantine list. |
| Operability | How to run demo, regenerate dashboard, inspect artifacts. |

## M83 Display-List PM Evidence

For M83 and later display-list replay work, the PM demo package must distinguish
three facts:

- selected display-list scene contract;
- native Kadre/WebGPU execution evidence;
- unsupported display-list nodes and their stable refusal reasons.

Minimum PM artifacts:

- `reports/wgsl-pipeline/m83-display-list-replay/native-demo.json`;
- `reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png`;
- `reports/wgsl-pipeline/m83-display-list-replay/evidence.json`;
- `reports/wgsl-pipeline/m83-display-list-replay/evidence.md`;
- PM bundle manifest entry `m83DisplayListReplay`.

The native JSON must name `m83-display-list-pm-scene-v1` as the selected
`sceneContract.id`, and the evidence JSON must report
`nativePixelsProducedFromDisplayListByThisTask=true`. If either condition is
missing, the sprint is not done; it is only route-prep evidence.

## M84 Native Timing PM Evidence

For M84 and later timing work, the PM demo package must distinguish three
separate facts:

- native timing samples were serialized;
- the lane remains candidate/reporting-only;
- the timing is not counted as a release-blocking measured gate.

Minimum PM artifacts:

- `reports/wgsl-pipeline/m84-native-frame-timing/evidence.json`;
- `reports/wgsl-pipeline/m84-native-frame-timing/evidence.md`;
- `reports/wgsl-pipeline/m84-native-frame-timing/negative-fixture.json`;
- PM bundle manifest entry `m84NativeFrameTiming`.

The evidence JSON must report `lane=frame.kadre-windowed`,
`gatePhase=candidate-reporting-only`, `releaseBlocking=false`, and
`countedAsMeasuredGate=false`. The PM wording must not describe the lane as
release-grade FPS until a later milestone promotes it with accepted variance and
resource telemetry.

## M85 Resource/Cache PM Evidence

For M85 and later resource work, the PM demo package must show whether the
selected realtime route has auditable resource lifetime and cache pressure.
M85 evidence is a deterministic selected-scene ledger, not observed WebGPU
runtime cache telemetry.

Minimum PM artifacts:

- `reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json`;
- `reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.md`;
- `reports/wgsl-pipeline/m85-resource-lifetime-cache/cache-pressure.json`;
- PM bundle manifest entry `m85ResourceLifetimeCache`.

The evidence JSON must report:

- per-frame resource ledger counters;
- bounded cache key spaces;
- resize/surface invalidation with zero invalid-resource reuse;
- cache pressure before/after;
- stable device-loss diagnostic
  `m85.device-loss-recreate-observation-unsupported` when real recovery is not
  observable.

PM wording must not imply arbitrary scene cache coverage or real device-lost
recovery until those are backed by route evidence. PM wording must also avoid
counting M85 as a cache-readiness gate until observed runtime cache telemetry
lands.

## M86 Fidelity Burn-Down PM Evidence

M86 packages the fidelity backlog into PM-readable evidence. It should help PM
answer "what visual gaps are next?" without implying that a renderer fix has
already landed.

Minimum PM artifacts:

- `reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json`;
- `reports/wgsl-pipeline/m86-fidelity-burndown/evidence.md`;
- `reports/wgsl-pipeline/2026-06-02-m86-sprint-report-and-readiness-accounting.md`;
- PM bundle manifest entry `m86FidelityBurndown`.

The evidence JSON must report:

- ranked candidate count;
- support and expected-unsupported row counts;
- `referenceKind` counters;
- root-cause counters;
- high-value remediation targets;
- `globalThresholdWeakened=false`;
- unchanged readiness percentage unless a renderer fix with before/after
  artifacts is included.

PM wording must distinguish:

- classified visual debt;
- stable expected unsupported rows;
- actual renderer fixes with before/after proof.

## M87 Runtime-Effect Live-Editing PM Evidence

M87 packages one selected registered runtime effect as PM-visible live-editing
evidence. It should answer "can a registered effect parameter be edited safely?"
without implying arbitrary SkSL compatibility.

Minimum PM artifacts:

- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json`;
- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.md`;
- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/edited-states.json`;
- CPU/GPU/diff PNGs and route JSON for at least two edited states;
- PM bundle manifest entry `m87RuntimeEffectLiveEditing`.

The evidence JSON must report:

- effect stable id `runtime.simple_rt`;
- editable parameter `gColor.b`;
- reflected `gColor` uniform layout;
- at least two parameter updates;
- `uniformValuesInPipelineKey=false`;
- stable refusal reasons `runtime-effect.arbitrary-sksl-unsupported` and
  `runtime-effect.wgsl-descriptor-missing`.

PM wording must say that M87 proves selected SimpleRT live editing. It must not
claim broad runtime-effect live controls, arbitrary SkSL compilation, or new
WGSL support for dispatch-only runtime effects.

## M88 Realtime Renderer RC2 PM Evidence

M88 freezes the next RC2 package. It is a packaging, gate-freeze, and PM handoff
milestone, not a new rendering breadth milestone.

Minimum PM artifacts:

- `reports/wgsl-pipeline/m88-realtime-rc2/rc2-evidence.json`;
- `reports/wgsl-pipeline/m88-realtime-rc2/rc2-evidence.md`;
- `reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json`;
- `reports/wgsl-pipeline/m88-realtime-rc2/gate-freeze.json`;
- `reports/wgsl-pipeline/m88-realtime-rc2/api-surface.json`;
- `reports/wgsl-pipeline/m88-realtime-rc2/pm-demo-script.md`;
- `reports/wgsl-pipeline/m88-realtime-rc2/release-notes.md`;
- PM bundle manifest entry `m88ReleaseCandidate2`.

The evidence JSON must report:

- pack id `m88-realtime-renderer-rc2-v1`;
- status `pass`;
- claim level `realtime-renderer-rc2-freeze-package`;
- readiness before and after `67.75`;
- readiness delta `0.0`;
- API/demo surface frozen for RC2;
- gate freeze that keeps `m84 frame.kadre-windowed` reporting-only;
- gate freeze that keeps the M85 resource/cache ledger reporting-only;
- support/refusal matrix categories for supported, expected-unsupported,
  dependency-gated, implementation-gap, and reporting-only scope.

PM wording must say that M88 produces a reproducible RC2 handoff. It must not
claim full Skia parity, arbitrary SkSL, release-grade windowed FPS, broad
observed runtime cache telemetry, or window-surface screenshot/readback.

## PM Language

Use this framing:

> Kanvas is expanding from a verified evidence platform into a real-time
> Skia-like renderer. Each milestone adds real rendering capability, keeps
> unsupported areas visible, and shows progress through both generated evidence
> and interactive demos.

Avoid:

- "full Skia parity";
- "all Path AA";
- "all image filters";
- "all text";
- "runtime effects compatible with arbitrary SkSL";
- "GPU faster than CPU" without measured evidence.

## Open PM Questions

Closed decisions to confirm:

1. First live demo platform:
   - selected: Kadre desktop windowing from `ygdrasil-io/poc-koreos`;
   - integration mode while unpublished: git submodule;
   - optional later evidence: browser-hosted WebGPU, if a milestone asks for
     it explicitly.
2. Frame target:
   - recommended: 60 FPS target, 30 FPS warning;
   - alternative: 60 FPS release-blocking for curated scenes;
   - alternative: reporting-only for the first runtime milestone.
3. First text scope:
   - recommended: Latin/simple glyph masks;
   - alternative: add shaping early;
   - alternative: defer text until after filters/path work.

Open question:

- What flagship PM scene best represents Kanvas: a technical conformance grid,
  a document/editor canvas, a creative animated scene, or another product-like
  example?
