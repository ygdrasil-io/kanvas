# kanvas

Kanvas is a Kotlin graphics stack that is converging toward a shared
high-performance rendering pipeline for CPU raster and WebGPU. The active
pipeline target is based on a typed Kanvas IR, WGSL parser/generator support,
CPU scalar/vector execution plans, and parser-validated generated WGSL for the
GPU backend.

## Post-MVP Big Target

Last updated: 2026-05-31

Post-MVP Big Target readiness for MEP: 40%.

This percentage is a PM readiness score for the full Post-MVP target, not an
effort estimate and not the completion state of the last sprint. It moves only
when Kanvas gains release-relevant capability with visible report, artifact,
dashboard, CI, or demo evidence.

The MVP is complete. The big target is now the Kanvas Rendering Conformance &
Performance Platform: a generated evidence system that turns CPU/GPU rendering
tests into PM-readable progress and engineering-actionable proof.

Current PM interpretation: M41-M47 built the evidence foundation, and M48
expanded representative Skia integration breadth from the narrow 15% baseline to
35%. The platform is still not MEP-ready: CI gates, performance trends, and a
deployable demo/report workflow remain early, and broader Skia coverage still
needs more families and adapter-backed proof.

| PM area | Weight | Status | Progress | Evidence / remaining work |
|---|---:|---|---:|---|
| Evidence foundation | 25% | Done through M48 | 100% | Generated dashboard, 21 generated rows, 0 tracked-gap, 0 fail |
| Skia integration coverage | 25% | Expanded | 35% | M48 added 10 selected rows across paint, clip, transform, bitmap, gradient, Path AA, and image-filter breadth while keeping unsupported scope explicit |
| CI and release gates | 20% | Early | 10% | Dashboard generation is validated, but promotion/performance gates are not yet release-grade |
| Performance readiness | 15% | Early | 15% | M43 measured payloads exist, but trends remain reporting-only |
| PM demo and reporting workflow | 15% | Prototype | 15% | Static dashboard works locally; deployable and repeatable PM workflow still missing |

Weighted PM readiness: 40% after rounding.

| Track | Status | Progress | Evidence |
|---|---|---:|---|
| Generated scene dashboard | Done | 100% | M41 generated rows and dashboard exporter |
| Adapter-backed P0 GPU capture | Done | 100% | M42 P0 captures and status policy |
| Measured CPU/GPU benchmark payloads | Done | 100% | M43 reporting-only benchmark evidence |
| Narrow Path AA support promotion | Done | 100% | M44 selected Path AA family |
| Bounded image-filter DAG support | Done | 100% | M45 selected DAG subset |
| Static-to-generated evidence expansion | Done | 100% | M46 converted five additional rows |
| Remaining static evidence hardening | Done | 100% | M47 converted remaining static pass rows and validated Path AA policy rows |
| MEP scene coverage expansion | Done | 100% | M48 added 7 generated support rows and 3 expected-unsupported breadth rows |

Evidence-hardening readiness is 100% through M47:
all static pass rows have generated evidence, and the only remaining static rows
are explicit Path AA expected-unsupported policy sentinels.

M48 coverage expansion is complete for the selected scene pack: the dashboard now
has 23 rows, 18 pass, 5 expected-unsupported, 0 tracked-gap, 0 fail, 21 generated
rows, 2 static rows, and 2 adapter-backed rows. This justifies moving Skia
integration coverage from 15% to 35%, but not higher, because CI gates,
performance thresholds, broad adapter-backed captures, text/font/codec coverage,
and a repeatable PM demo workflow remain outside this milestone.

What remains before MEP:

- broaden the Skia integration scene set beyond the current selected dashboard
  rows;
- make generated evidence the normal path for new support claims, not a
  hand-curated closeout exercise;
- define CI gates for required conformance, allowed expected-unsupported rows,
  and non-blocking inventory;
- turn performance payloads into stable trends and approved release thresholds;
- publish a repeatable PM demo/report flow outside a local-only static page;
- define a final MEP acceptance checklist that links Linear, CI, dashboard,
  reports, and known limitations.

Active Post-MVP evidence:

- target doc: [.upstream/target/rendering-conformance-performance-target.md](.upstream/target/rendering-conformance-performance-target.md)
- backlog: [.upstream/target/post-mvp-conformance-backlog.md](.upstream/target/post-mvp-conformance-backlog.md)
- dashboard source: [reports/wgsl-pipeline/scenes/](reports/wgsl-pipeline/scenes/)
- generated demo: `rtk ./gradlew --no-daemon pipelineSceneDashboard`
- M46 review: [reports/wgsl-pipeline/2026-05-31-m46-sprint-review.md](reports/wgsl-pipeline/2026-05-31-m46-sprint-review.md)
- M47 review: [reports/wgsl-pipeline/2026-05-31-m47-sprint-review.md](reports/wgsl-pipeline/2026-05-31-m47-sprint-review.md)
- M47 inventory: [reports/wgsl-pipeline/2026-05-31-m47-remaining-static-evidence-inventory.md](reports/wgsl-pipeline/2026-05-31-m47-remaining-static-evidence-inventory.md)
- M47 Path AA policy validation: [reports/wgsl-pipeline/2026-05-31-m47-path-aa-expected-unsupported-policy-validation.md](reports/wgsl-pipeline/2026-05-31-m47-path-aa-expected-unsupported-policy-validation.md)
- M48 taxonomy: [reports/wgsl-pipeline/2026-05-31-m48-mep-skia-scene-taxonomy.md](reports/wgsl-pipeline/2026-05-31-m48-mep-skia-scene-taxonomy.md)
- M48 scene pack: [reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md](reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md)
- M48 support evidence: [reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md](reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md), [reports/wgsl-pipeline/2026-05-31-m48-bitmap-gradient-generated-evidence.md](reports/wgsl-pipeline/2026-05-31-m48-bitmap-gradient-generated-evidence.md)
- M48 unsupported breadth: [reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md](reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md)

## MVP Roadmap

Last updated: 2026-05-28

MVP readiness: 100%.

The percentage is a readiness score, not an effort estimate. A block only moves
when its milestone Definition of Done has CI, Linear, report, or artifact
evidence. Archived migration plans are historical evidence only and must not be
used as active backlog.

Active execution source:

- Linear project: [Kanvas - WGSL Pipeline Target](https://linear.app/forge-yg/project/kanvas-wgsl-pipeline-target-ef9e97757caa)
- Sprint closeout: [reports/wgsl-pipeline/2026-05-28-m33-m35-sprint-report.md](reports/wgsl-pipeline/2026-05-28-m33-m35-sprint-report.md)
- Architecture target: [.upstream/target/high-performance-wgsl-pipeline-target.md](.upstream/target/high-performance-wgsl-pipeline-target.md)
- Post-MVP big target: [.upstream/target/rendering-conformance-performance-target.md](.upstream/target/rendering-conformance-performance-target.md)
- Post-MVP conformance backlog: [.upstream/target/post-mvp-conformance-backlog.md](.upstream/target/post-mvp-conformance-backlog.md)
- Linear/agent methodology: [.upstream/target/linear-agent-methodology.md](.upstream/target/linear-agent-methodology.md)

| Block | Scope | Status | Weight | Progress | MVP evidence gate |
| --- | --- | --- | ---: | ---: | --- |
| Foundation pipeline | M0-M11: parser deps, PipelineIR, CPU scalar pilot, generated WGSL pilot, runtime effect pilot, Java 25 Vector pilot | Done | 15% | 100% | Parser/generator smoke, stable IR dumps, generated WGSL pilot evidence |
| Geometry/Coverage convergence | M12-M20: GeometryPlan/CoveragePlan contracts, shadow harness, CPU/GPU routing | Done | 20% | 100% | Descriptor-driven geometry coverage baseline and migration evidence |
| Conformance hardening | M21-M30: PipelineKey, parser validation, runtime matrix, CPU vector gate, evidence gates, residual scope | Done | 20% | 100% | Conformance report, release-readiness gates, residual work made explicit |
| GPU CI stabilization | M31: required GPU smoke gate separated from full non-blocking inventory | Done | 15% | 100% | Adapter-backed smoke gate and inventory classification policy |
| Bitmap/ImageRect remediation | M32: fix or evidence-classify `DrawBitmapRect3` and `DrawBitmapRectSkbug4734` GPU similarity deltas | Done | 10% | 100% | `GRA-93` through `GRA-100`; image-rect similarity regressions are zero and `DrawBitmapRectSkbug4734` is required smoke |
| Path AA inventory boundary | M33: classify edge-budget refusals and promote only stable AA coverage | Done | 10% | 100% | `GRA-105` through `GRA-108`; `coverage.edge-count-exceeded` remains inventory-only and `AnalyticAntialiasConvexWebGpuTest` is required smoke |
| Image-filter MVP lane | M34/M38: gate unsupported `Crop(input = nonNull)` graphs and promote the selected SimpleOffset child pre-pass | Done | 5% | 100% | `GRA-109` through `GRA-113` and `GRA-174` through `GRA-184`; selected `SimpleOffsetImageFilterWebGpuTest` is required smoke with dashboard evidence, while `image-filter.crop-input-nonnull-prepass-required` is retained only for out-of-scope Crop(input nonNull) graph shapes |
| MVP release candidate | M35: final smoke, inventory, PM demo, limitations, and release notes | Done | 5% | 100% | Required CI, conformance, smoke, full inventory, PM evidence package, and closeout evidence are complete |

Sprint verification on 2026-05-28 confirmed that Linear epics `GRA-101`,
`GRA-102`, and `GRA-103`, their M33-M35 child tasks, and the M33-M35
milestones are all `Done` / 100%.

```mermaid
flowchart LR
    A["Foundation M0-M11"] --> B["Geometry/Coverage M12-M20"]
    B --> C["Conformance hardening M21-M30"]
    C --> D["GPU CI stabilization M31"]
    D --> E["Bitmap/ImageRect M32"]
    E --> F["Path AA boundary M33"]
    F --> G["Image-filter MVP lane M34"]
    G --> H["MVP release candidate M35"]
```

### MVP Definition

The MVP is reached when:

- the required CPU and GPU smoke gates are green on CI;
- remaining GPU inventory failures are classified as expected unsupported,
  dependency-gated, or tracked follow-up work;
- generated/validated WGSL is the accepted path for promoted pipeline slices;
- CPU reference behavior and GPU similarity policy are visible in tests or
  reports;
- PM-facing evidence links Linear milestones, PRs, CI runs, and known
  limitations.

Non-goals for the MVP:

- porting Ganesh or Graphite;
- rebuilding Skia's SkSL compiler, IR, or VM;
- hiding GPU inventory failures by lowering floors in bulk;
- adding short-lived font or codec substitutes for dependency-gated gaps.

## Post-MVP Evidence Details

The Post-MVP target is tracked through the generated scene dashboard and the
reports below.

Current scene dashboard:

- source: [reports/wgsl-pipeline/scenes/](reports/wgsl-pipeline/scenes/)
- export task: `rtk ./gradlew --no-daemon pipelineSceneDashboard`
- generated output: `build/reports/wgsl-pipeline-scenes/index.html`
- target doc: [.upstream/target/rendering-conformance-performance-target.md](.upstream/target/rendering-conformance-performance-target.md)

Current dashboard evidence after M48 scene coverage expansion:

| Signal | Count | Meaning |
|---|---:|---|
| Scene rows | 23 | Static and generated rows merged by `pipelineSceneDashboard`. |
| `pass` | 18 | Reference, CPU, GPU, diff, stats, and route evidence exist for the selected support scene. |
| `tracked-gap` | 0 | P0 adapter-backed capture gaps were closed by M42 and GRA-222. |
| `expected-unsupported` | 5 | GPU intentionally refuses the scene with a stable fallback reason. |
| `fail` | 0 | No dashboard row is currently a failing support claim. |
| `maturity.generated-evidence` | 21 | M41, M46, M47, and M48 generated rows, including P0 captures, Path AA stroke, image-filter DAG, SrcOver stack, runtime-effect, clip, bitmap local-matrix, and M48 scene-pack evidence. |
| `maturity.static-evidence` | 2 | Remaining rows are explicit Path AA expected-unsupported policy evidence. |
| `maturity.adapter-backed` | 2 | P0 GPU captures on named adapter. |
| CPU/GPU perf `measured` | 2 each | M43 benchmark payloads, reporting-only until CI gate policy is approved. |

Closed post-MVP milestones:

- M41: generated dashboard rows from test outputs;
- M42: closed adapter-backed P0 GPU capture gaps;
- M43: replaced selected estimated metrics with measured CPU/GPU benchmarks;
- M44: promoted one narrow Path AA family to rendered GPU support;
- M45: extended image-filter support to a bounded DAG subset;
- M46: converted five additional static rows to generated evidence;
- M47/GRA-273: locked the remaining static evidence inventory and selected the
  three rows eligible for generated conversion;
- M47/GRA-274: converted `runtime-effect-simple` to generated evidence while
  preserving the registered Kotlin/WGSL descriptor boundary;
- M47/GRA-275: converted `clip-rect-difference` to generated evidence;
- M47/GRA-276: converted `bitmap-shader-local-matrix` to generated evidence;
- M47/GRA-277: kept the two Path AA expected-unsupported rows as static policy
  evidence with stable fallback reasons;
- M47/GRA-278: closed the sprint with 11 generated rows and 2 static policy rows;
- GRA-221: added scene tags, exact-tag filtering, tag search, and
  feature/maturity/risk aggregates.
- M48/GRA-280: defined the MEP scene taxonomy and readiness rule for moving
  Skia integration coverage beyond 15%;
- M48/GRA-281: selected 10 P0/P1 rows for the M48 scene pack;
- M48/GRA-282: added four generated paint, blend, clip, and transform support
  rows;
- M48/GRA-283: added three generated bitmap and gradient support rows;
- M48/GRA-284: added three explicit expected-unsupported Path AA/image-filter
  breadth rows;
- M48/GRA-285: synced PM dashboard counters and readiness after the M48 scene
  pack landed.
- M48/GRA-286: closed the sprint with 23 rows, 21 generated rows, 0 tracked-gap,
  0 fail, and an M49 recommendation for CI/release gates.

Sprint reviews:
[reports/wgsl-pipeline/2026-05-28-m41-m45-sprint-review.md](reports/wgsl-pipeline/2026-05-28-m41-m45-sprint-review.md)
[reports/wgsl-pipeline/2026-05-31-m46-sprint-review.md](reports/wgsl-pipeline/2026-05-31-m46-sprint-review.md)
[reports/wgsl-pipeline/2026-05-31-m47-sprint-review.md](reports/wgsl-pipeline/2026-05-31-m47-sprint-review.md)
[reports/wgsl-pipeline/2026-05-31-m48-sprint-review.md](reports/wgsl-pipeline/2026-05-31-m48-sprint-review.md)

M46 closeout:
[reports/wgsl-pipeline/2026-05-30-m46-generated-evidence-expansion-closeout.md](reports/wgsl-pipeline/2026-05-30-m46-generated-evidence-expansion-closeout.md)

Support claims after the MVP require visible evidence: reference, CPU/GPU
render or explicit refusal, diffs, stats, route diagnostics, and stable fallback
policy. Static or estimated evidence must be labelled as such.

## Development Commands

Use the Gradle wrapper from the repository root:

```bash
./gradlew build
./gradlew check
./gradlew clean
```

For project workflow commands, prefer the repository `rtk` wrapper when it is
available, for example:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon check
```
