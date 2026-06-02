# RC PM Demo Script

Date: 2026-06-02

Scope: `FOR-217`, `FOR-220`, with closeout coverage for `FOR-204`..`FOR-213`
and `FOR-219`.

## Pre-Demo Checklist

1. Run headless validators:

```bash
rtk ./gradlew --no-daemon pipelinePmBundle
python3 scripts/validate_mep_rc_runtime.py .
```

1. Confirm source artifacts exist:

```bash
python3 -m json.tool reports/wgsl-pipeline/m90-runtime-interactive/evidence.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m92-kadre-runtime-rc/evidence.json >/dev/null
```

1. Provision Kadre only on the demo machine that will open the native window:

```bash
git submodule update --init --recursive external/poc-koreos
```

Optional Kadre-provisioned evidence refresh:

```bash
rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive
```

This task resolves the Kadre runtime module and is intentionally not required
for the headless RC gate when the source-build submodule is unavailable.

## Demo Order

1. Dashboard/evidence framing:

Open the PM bundle produced by
`rtk ./gradlew --no-daemon pipelinePmBundle`, or show the checked-in report
artifacts if the bundle is unavailable. State that the RC is at the same
readiness percentage because this package improves runtime evidence and
telemetry provenance, not counted release-gated support.

2. Native Kadre RC command:

```bash
rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive
```

Show the native Kadre window only if local native setup is available. The
expected PM story is scene switching across selected bounded scenes, input
changes to visible parameters, and live telemetry/export semantics.

3. Scene sequence:

| Step | Scene/status | Point to show |
|---|---|---|
| 1 | M83 display-list PM scene | Promoted bounded PM scene; not broad SkCanvas replay. |
| 2 | M73 bitmap rect nearest | Image sampling route remains selectable. |
| 3 | M73 gradient color-filter kPlus | Generated WGSL shader route remains the target. |
| 4 | M73 linear gradient rect | Simple generated route remains stable. |
| 5 | Nested rrect clip refusal | Expected unsupported stays explicit instead of silently degrading. |

4. Input controls:

Use ArrowRight/ArrowLeft for scene selection, Space for play/pause state, and
pointer movement for the pointer-controlled parameter. Do not describe this as
real OS event injection coverage in CI; the checked-in evidence uses
deterministic fixtures.

5. Telemetry:

Show `reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json`.
Use these exact categories:

| Category | Say |
|---|---|
| observed | Captured directly by existing evidence for the selected route. |
| observed-partial | Real selected-route churn, not broad cache hit/miss telemetry. |
| derived | Deterministic selected-scene ledger, useful but not a native counter. |
| expected-unsupported | Deliberate refusal with a stable reason. |
| not-observable | Blocked on Kadre/wgpu4k API visibility; not invented. |

## Native-Unavailable Fallback

If the native window cannot open on the PM machine, do not improvise a web demo
as a substitute for Kadre native. Use the headless fallback:

```bash
rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive
python3 scripts/validate_mep_rc_runtime.py .
```

Then show:

- `reports/wgsl-pipeline/m90-runtime-interactive/pm-report.md`
- `reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json`
- `reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json`
- `reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png`
- `reports/wgsl-pipeline/2026-06-02-rc-kadre-runtime-closeout.md`

## Do Not Over-Sell

- Do not claim release-grade `frame.kadre-windowed` FPS.
- Do not claim broad observed WebGPU cache hit/miss telemetry.
- Do not claim broad SkCanvas/display-list replay.
- Do not claim dynamic SkSL compilation; WGSL remains the shader target.
- Do not claim native window execution for this closeout if only the headless
  validator was run.
