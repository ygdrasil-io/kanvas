# M50 Sprint Plan: Toward 80% Post-MVP Readiness

Date: 2026-05-31
Status: Proposed
Target: `archives/target-closeout-2026-05-31/rendering-conformance-performance-target.md`
Backlog: `archives/target-closeout-2026-05-31/post-mvp-conformance-backlog.md`

## Goal

M50 should try to move Post-MVP Big Target readiness from 60% to 80%, but only
if the work turns the current M49 gate candidate and draft front/font specs into
owned, executable evidence.

The 80% target is possible, but it is not automatic. It requires CI ownership,
front validation, broader adapter-backed scene evidence, a first font/text
evidence pack, and performance trend automation. If any lane lands as
documentation only, the readiness score must stay below 80%.

## Starting Point

M49 closed with:

| Signal | Count |
|---|---:|
| Scene rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 21 |
| Static policy rows | 2 |
| Adapter-backed rows | 7 |

Current readiness is 60% after rounding:

| PM area | Weight | M49 progress |
|---|---:|---:|
| Evidence foundation | 25% | 100% |
| Skia integration coverage | 25% | 45% |
| CI and release gates | 20% | 60% |
| Performance readiness | 15% | 35% |
| PM demo and reporting workflow | 15% | 45% |

## Target Score

M50 may claim 80% only if the sprint reaches this target mix:

| PM area | Weight | M50 target | Why this is enough |
|---|---:|---:|---|
| Evidence foundation | 25% | 100% | Preserve generated dashboard semantics, zero `tracked-gap`, zero `fail`, and stable fallback policy. |
| Skia integration coverage | 25% | 65% | Add meaningful adapter-backed and generated breadth, including first text/font evidence, without broad unsupported claims. |
| CI and release gates | 20% | 85% | Make dashboard and PM bundle validation release-owned instead of only local candidate tasks. |
| Performance readiness | 15% | 60% | Turn the non-blocking trend contract into automated, owned warning evidence with baselines and variance policy. |
| PM demo and reporting workflow | 15% | 85% | Add front/browser/accessibility checks, stable PM bundle manifest, image inspection, and route/reference notices. |

Weighted result: `25 + 16.25 + 17 + 9 + 12.75 = 80`.

## Work Lanes

### M50-A Required CI Ownership

Promote the M49 gate candidate into an owned CI/release gate.

Definition of Done:

- CI runs `pipelineSceneDashboardGate` on the selected release path.
- Gate output is archived or linked from the build artifacts.
- The gate fails on duplicate scene ids, missing support artifacts, unsupported
  rows without stable fallback reason, unexpected `tracked-gap`, and unexpected
  `fail`.
- Non-blocking GPU inventory remains visible and has an owner, even when it is
  not a merge blocker.
- The release checklist references the exact CI job, dashboard path, and PM
  bundle path.

Validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```

### M50-B Front Evidence Gate

Implement the first accepted gate from `.upstream/specs/front/`: PM dashboard
experience, image inspection, filters, route/reference notices, and browser
quality checks.

Definition of Done:

- Dashboard image inspection opens in-page and supports reference, CPU, GPU,
  and diff artifacts without navigating away.
- Scene cards remain readable in a two-column layout at desktop widths and a
  single-column layout on narrow screens.
- Artifact lists are collapsed by default.
- Filters cover status, priority, reference source, generated/static maturity,
  adapter-backed evidence, and expected-unsupported reason.
- Dashboard includes clear notices for routes and reference/oracle meaning.
- Browser QA captures desktop and mobile screenshots.
- Accessibility report has no critical issue for the PM dashboard path.
- `pipelinePmBundle` includes the front QA report and screenshot paths.

Validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelinePmBundle
```

### M50-C Adapter-Backed Scene Expansion V2

Increase Skia integration confidence by making more support rows adapter-backed,
not by adding unsupported claims.

Definition of Done:

- Adapter-backed rows increase from 7 to at least 14.
- New adapter-backed rows cover at least four families among paint, blend,
  bitmap, gradient, clip, transform, Path AA promoted subset, image-filter DAG,
  and runtime-effect compatibility.
- Every new pass row has reference, CPU, GPU, diff, stats, route diagnostics,
  adapter metadata, and `fallbackReason=none`.
- No new support claim is made from route diagnostics alone.
- Merged dashboard remains 0 `tracked-gap` and 0 `fail`.

Validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```

### M50-D First Font/Text Evidence Pack

Turn the draft font spec into selected generated evidence without pretending
that broad font, emoji, shaping, or glyph-mask support is complete.

Definition of Done:

- Add at least three generated `pass` scenes from the existing pure Kotlin
  OpenType/simple text path. Suggested candidates:
  - simple Latin outline `drawString`;
  - positioned `SkTextBlob` or equivalent glyph-run fixture;
  - bundled typeface/style or kerning fixture with route diagnostics.
- Add at least two generated `expected-unsupported` scenes for explicit font
  gaps, such as standalone glyph alpha-mask, emoji/color glyph, complex
  shaping, or SDF/LCD text, using stable fallback reasons defined by the font
  spec and implementation.
- Every promoted font scene records font source, text input, shaping mode,
  glyph diagnostics, CPU/GPU/refusal route, diff, stats, and reference/oracle.
- Dependency-gated rows remain dependency-gated until real deliveries land.
- No external font library is introduced as a shortcut.

Validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:test --tests 'org.skia.foundation.opentype.*'
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```

### M50-E Performance Warning Gate

Promote M49's non-blocking trend contract from prose into automated warning
evidence.

Definition of Done:

- Benchmark payloads include host, OS, JDK, backend, adapter, warmup/cold
  classification, sample count, baseline id, and variance policy.
- At least two CPU and two GPU/cache rows are refreshed by the performance
  harness.
- A CI or release task emits warning-only trend output and never silently drops
  missing measured rows.
- Baseline owner, quarantine policy, rollback policy, and allowed variance are
  documented next to the generated report.
- No release-blocking performance threshold is claimed until the owner accepts
  the blocking policy.

Validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Run the owning benchmark task once it is attached to the M50 ticket.

### M50-F Closeout And Score Update

Close M50 only after all lanes publish evidence and the score is recalculated
from generated artifacts.

Definition of Done:

- Sprint review reports dashboard counters, adapter-backed count, front QA
  status, font scene status, CI gate status, performance trend status, and PM
  bundle paths.
- README and target docs move readiness only to the proven score.
- If any lane misses its Definition of Done, the review records the lower
  justified score instead of claiming 80%.
- Linear closeout links tickets, PRs, CI runs, generated reports, screenshots,
  and known limitations.

## Score Rules

M50 may claim 80% only when all of these are true:

- `pipelineSceneDashboardGate` is release-owned or required on the accepted
  release path.
- `pipelinePmBundle` contains current dashboard data, artifacts, manifest,
  limitations, front QA evidence, and local serve instructions.
- Adapter-backed support rows are at least 14.
- First font/text generated evidence pack is present, with pass and explicit
  expected-unsupported rows.
- Performance trend output is automated, owned, warning-only, and visible.
- Merged dashboard remains 0 `tracked-gap` and 0 `fail`.

If only the CI/front lanes land, the likely score is 70-72%.
If CI/front plus adapter-backed expansion land, the likely score is 74-76%.
If font evidence or performance automation remains spec-only, 80% is not
defensible.

## Non-Goals

- Do not claim complete MEP.
- Do not claim broad Skia parity.
- Do not claim broad font, emoji, shaping, SDF, LCD, or glyph-mask support.
- Do not make performance thresholds release-blocking without owner-approved
  baseline and rollback policy.
- Do not hide unsupported rows to improve dashboard counters.
- Do not port Ganesh, Graphite, SkSL compiler, Skia IR, or Skia VM.
