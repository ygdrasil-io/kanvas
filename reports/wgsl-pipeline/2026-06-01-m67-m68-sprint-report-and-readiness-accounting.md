# M67/M68 Sprint Report And Readiness Accounting

Status: completed with one explicit native-demo blocker.

## Scope

- Linear epics: `FOR-43` M67 Performance Tiering, `FOR-44` M68 Native Kadre Real-Time Demo.
- M67 issues: `FOR-45`, `FOR-46`, `FOR-47`, `FOR-48`, `FOR-49`.
- M68 issues: `FOR-50`, `FOR-51`, `FOR-52`, `FOR-53`, `FOR-54`.

## Delivered

M67 adds a reproducible performance tiering lane:

- `pipelineM67PerformanceTiering`
- `pipelineM67PerformanceTieringNegative`
- `reports/wgsl-pipeline/performance/m67-performance-tiering/`
- `reports/wgsl-pipeline/performance/m67-performance-tiering-negative/`

The normal gate emits three `frame.headless-webgpu` candidate rows from M65
headless/offscreen telemetry: 1 `pass`, 2 `warn`, 0 `fail`, 0 `quarantine`.
Family budgets expose seven families: 1 measured candidate
(`core paint/blend`) and 6 reporting-only families. The negative fixture emits
a deterministic `quarantine` result without mutating baseline telemetry.

M68 adds a Kadre bridge/demo evidence pack:

- `pipelineM68KadreDemoEvidence`
- `reports/wgsl-pipeline/m68-kadre-demo/`
- `reports/wgsl-pipeline/2026-06-01-m68-kadre-demo-evidence.md`

Kadre is audited from the checked-in `external/poc-koreos` submodule at
`b5e3ebd89e3e04b208d8d0308b6c61d1a31b316c`. The source-build bridge smoke
passes, flagship scene inputs are present, and the native route is intentionally
blocked with `m68.kadre-host-adapter-not-implemented`.

## PM Interpretation

Kanvas now has a first measurable real-time performance gate candidate and a
clear map from Kadre source APIs to the Kanvas host contract.

What the PM can claim:

- headless/offscreen frame telemetry is generated and gated as candidate data;
- performance families are visible with measured vs reporting-only status;
- quarantine/rebaseline behavior has a deterministic fixture;
- Kadre is available through the source submodule and its host capabilities are
  audited against Kanvas needs;
- the future flagship native scene has source evidence and route metadata.

What the PM must not claim yet:

- no native Kadre-presented Kanvas frame exists;
- no native FPS/present timing exists;
- Path/clip, image/bitmap, image-filter, text/glyph, runtime-effect, and native
  frame-loop performance are not measured as M67 gates;
- the M68 demo is not a runnable product demo until the Kanvas/Kadre host
  adapter lands.

## Readiness

Readiness moves from 53% to approximately 58%.

| Area | Previous | Current | Reason |
|---|---:|---:|---|
| Rendering feature breadth | 60% | 60% | No new rendering-family support/refusal denominator changed. |
| Skia-like fidelity | 50% | 50% | No new selected GM/reference rows landed. |
| Real-time runtime | 40% | 50% | Kadre source-build bridge and host-contract audit now exist, but native presentation remains blocked. |
| Performance and cache readiness | 35% | 40% | M67 adds one measured frame/family candidate plus quarantine fixture. |
| PM/demo operability | 80% | 90% | PM bundle now includes M67 performance tiering and M68 Kadre bridge/demo evidence. |

Weighted readiness: about 58%.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineM67PerformanceTiering pipelineM67PerformanceTieringNegative
rtk ./gradlew --no-daemon pipelineM68KadreDemoEvidence
rtk ./gradlew --no-daemon pipelinePmBundle
rtk git diff --check
python3 -m json.tool reports/wgsl-pipeline/performance/m67-performance-tiering/m67-frame-gate-candidate.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m68-kadre-demo/bridge-smoke.json >/dev/null
```

## Next Blocker

The next feature sprint should implement the Kanvas/Kadre host adapter. That is
the missing link between audited Kadre APIs and a real native PM demo with
presented Kanvas pixels, input, resize, frame clock, telemetry, and export.
