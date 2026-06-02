# RC-MEP Closeout

Date: 2026-06-02

Linear issues: FOR-179, FOR-180, FOR-181, FOR-182, FOR-183, FOR-184, FOR-185, FOR-186, FOR-187

## Summary

RC-MEP is a stabilization and release-readiness sprint. It does not increase
the 67.75% target readiness score because it does not add a new counted
rendering, fidelity, runtime, performance, or PM-operability denominator.

The sprint makes the current RC safer to present and delegate:

- Kadre native PM demo execution is explicit and opt-in;
- `frame.kadre-windowed` remains candidate/reporting-only;
- `pipelinePmBundle` remains the headless PM package gate;
- WGSL is the shader implementation target;
- SkSL appears only as Skia API compatibility/refusal wording;
- expected-unsupported, dependency-gated, implementation-gap, and
  reporting-only rows have PM-facing explanations.

## FOR-187 Sprint Review, PR, Merge, And Closeout

FOR-187 is closed by the integration PR and merge evidence for the whole
RC-MEP slice, not by a separate renderer feature.

Definition of done:

| Requirement | Evidence |
|---|---|
| FOR-180/181/182 runtime evidence is generated | `reports/wgsl-pipeline/2026-06-02-rc-mep-kadre-runtime-slice.md` |
| FOR-183/184/185/186 docs/package/CI evidence is generated | `reports/wgsl-pipeline/2026-06-02-rc-mep-docs-package-ci-wording.md` |
| PM bundle source does not reintroduce old SkSL or Kadre-source-generation wording | `build.gradle.kts` manifest/overview strings now use WGSL target wording and `rtk ./gradlew --no-daemon pipelinePmBundle` |
| Checked-in RC validator covers the strengthened M88 package contract | `scripts/validate_m88_rc2.py` |
| Portable PM package gate remains runnable | `./gradlew --no-daemon pipelinePmBundle` |
| Kadre runtime slice remains runnable when the submodule is available | `./gradlew --no-daemon :kadre-runtime:pipelineRcMepKadreRuntimeSlice` |
| PR review is addressed before merge | review findings are tracked in the PR and this closeout |
| Linear closeout is auditable | FOR-179..FOR-187 receive merge evidence before moving to Done |

## Validation

Integration validation run:

```bash
python3 scripts/validate_m88_rc2.py .
./gradlew --no-daemon :kadre-runtime:pipelineRcMepKadreRuntimeSlice
./gradlew --no-daemon pipelinePmBundle
git diff --check
```

The native window demo remains manual because it opens a local desktop window:

```bash
git submodule update --init --recursive external/poc-koreos
./gradlew --no-daemon :kadre-runtime:runRcMepKadreNativePmDemo
```

## Non-Claims

- This sprint does not promote `frame.kadre-windowed` to a release-blocking
  performance gate.
- This sprint does not claim broad Skia parity.
- This sprint does not claim dynamic SkSL compilation. WGSL remains the shader
  implementation target.
- This sprint does not claim broad runtime-effect support, broad display-list
  replay, real OS event injection, window-surface screenshot/readback, or
  observed broad WebGPU runtime cache telemetry.

## Next Milestone

After FOR-179..FOR-187 are merged and closed, MEP-NEXT can be unblocked for
feature breadth and live runtime work. The blocked child tickets should start
from the current support/refusal matrix instead of reopening archived MEP
evidence rows.
