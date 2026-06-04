# MEP-NEXT Closeout

Date: 2026-06-02

Linear issues: FOR-188, FOR-189, FOR-190, FOR-191, FOR-192, FOR-193, FOR-194, FOR-195, FOR-196, FOR-197

## Summary

MEP-NEXT packages the first post-RC-MEP feature breadth and interactive runtime
evidence. It is intentionally conservative: the PM can see more visual families
and a clearer Kadre runtime path, while unsupported areas stay explicit.

Readiness remains `67.75%`. M89 and M90 improve PM evidence and runtime
operability, but they do not add a new counted renderer denominator, promote a
release-blocking timing gate, or claim broad Skia parity.

## PM Outcome

| Slice | Linear | PM-visible result |
|---|---|---|
| M89 feature breadth | FOR-189..192 | Bounded evidence for image filters, clips/Path AA, bitmap sampling, and registered WGSL runtime effects, with stable refusals. |
| M90 runtime interactive | FOR-193..196 | Bounded Kadre runtime evidence for autonomous loop semantics, scene switching, input telemetry, and observed-partial/derived resource counters. |
| Closeout | FOR-197 | PM package, validations, PR/merge evidence, and next-roadmap boundary. |

## Commands

Headless gates:

```bash
./gradlew --no-daemon validateMepNextRuntimeInteractive
python3 scripts/validate_mep_next_feature_breadth.py .
python3 scripts/validate_mep_next_runtime_interactive.py .
./gradlew --no-daemon pipelinePmBundle
```

Optional/provisioned Kadre runtime refresh:

```bash
git submodule update --init --recursive external/poc-koreos
./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive
```

The direct Kadre refresh may resolve `org.graphiks.kadre:*` and is not a
required headless gate when Kadre source substitution or local artifacts are
unavailable.

Opt-in native PM demo commands:

```bash
./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive
./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeBenchmark -PkadreMepNextFrames=300 -PkadreMepNextWarmupFrames=120
```

## Evidence

- `reports/wgsl-pipeline/m89-feature-breadth/evidence.json`
- `reports/wgsl-pipeline/m89-feature-breadth/evidence.md`
- `reports/wgsl-pipeline/2026-06-02-mep-next-feature-breadth-pm-report.md`
- `reports/wgsl-pipeline/m90-runtime-interactive/evidence.json`
- `reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json`
- `reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json`
- `reports/wgsl-pipeline/m90-runtime-interactive/pm-report.md`
- `reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md`

After `pipelinePmBundle`, PM evidence is available in:

- `manifest.json#m89FeatureBreadth`
- `manifest.json#m90RuntimeInteractive`
- `release/m89-feature-breadth/`
- `runtime/m90-runtime-interactive/`

## Non-Claims

- No arbitrary image-filter DAG support.
- No broad Path AA, broad clip-stack, broad bitmap/texture/image/codec support.
- No dynamic SkSL compilation; WGSL remains the shader implementation target.
- No broad SkCanvas/display-list replay.
- No real OS/window-manager event injection in CI.
- No release-grade `frame.kadre-windowed` FPS gate.
- No broad observed WebGPU cache telemetry.

## Next Roadmap Boundary

The next plan should move from evidence aggregation into implementation depth:

- promote actual renderer fixes with before/after artifacts;
- add real observed runtime cache counters where WebGPU/Kadre exposes them;
- decide whether `frame.kadre-windowed` can become a measured gate on owned
  hardware;
- expand the native PM demo from bounded replay contracts toward product-like
  scene composition.
