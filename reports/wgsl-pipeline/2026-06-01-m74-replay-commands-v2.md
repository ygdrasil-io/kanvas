# M74 Replay Commands V2

Status: `implementation-foundation`

M74 keeps the M73 replay-pack claim deliberately narrow while making the
implementation ready for M75-M88 feature scenes. The replay registry, CPU
oracle, generated WGSL path, and pack JSON now live behind a closed typed
command model instead of being embedded directly in the native smoke entrypoint.

## PM Outcome

- Readiness remains `67.75%`, rounded for PM to approximately `70%`.
- M74 does not add a new rendering-family support claim.
- M74 preserves the M73 pack contract: `5` scenes, `4` renderable, `1`
  expected-unsupported.
- The default PM scene remains `m73-linear-gradient-rect-replay-v1`.
- The explicit unsupported sentinel remains
  `m73-nested-rrect-clip-refusal-v1`.
- Broad display-list replay, arbitrary SkCanvas op streams, multi-scene live
  switching, native input, and release-grade FPS remain non-claims.

## Engineering Outcome

- Replay ownership moved to
  `kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/ReplaySceneRegistry.kt`.
- The model uses the closed `ReplayCommand` hierarchy:
  `Clear`, `FillRect`, and `ExpectedUnsupported`.
- JSON compatibility fields are preserved for existing PM/report consumers:
  `sceneReplay`, `replayPack`, `sourceSceneId`, `commandCounters`,
  `sourceEvidence`, `unsupportedCommands`, `renderedByKadre`, and `status`.
- `M69KadreNativeSmoke.kt` keeps runtime ownership: Kadre/AppKit window loop,
  WebGPU surface/pipeline, capture, telemetry, CLI parsing, and result writing.

## Linear Scope

- Epic: `FOR-90`.
- Extraction/model tickets: `FOR-105`, `FOR-106`.
- Evidence preservation ticket: `FOR-107`.
- PM/readiness docs: `FOR-108`.
- Review/PR/CI/merge closeout: `FOR-109`.

## Validation

Completed locally on 2026-06-01:

- `:kadre-runtime:compileKotlin`: pass.
- `:kadre-runtime:test`: pass, including JSON contract and registry invariant
  tests for `ReplaySceneRegistry`.
- `m73-linear-gradient-rect-replay-v1`: `native-runnable`.
- `m73-bitmap-rect-nearest-replay-v1`: `native-runnable`.
- `m73-nested-rrect-clip-refusal-v1`: `blocked` with stable
  expected-unsupported reason `m73.kadre-replay-scene-expected-unsupported`.
- `m73-unknown-scene`: `blocked` with stable reason
  `m73.kadre-replay-scene-unknown`.

```bash
rtk ./gradlew --no-daemon :kadre-runtime:compileKotlin
rtk ./gradlew --no-daemon :kadre-runtime:test
rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-linear-gradient-rect-replay-v1 -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=0 :kadre-runtime:runM70KadreNativeDemo
rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-bitmap-rect-nearest-replay-v1 -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=0 :kadre-runtime:runM70KadreNativeDemo
rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-nested-rrect-clip-refusal-v1 -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=0 :kadre-runtime:runM70KadreNativeDemo
rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-unknown-scene -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=0 :kadre-runtime:runM70KadreNativeDemo
rtk ./gradlew --no-daemon -PkadreReplaySceneId=m73-linear-gradient-rect-replay-v1 -PkadreDemoFrames=180 -PkadreDemoWarmupFrames=30 :kadre-runtime:runM70KadreNativeDemo pipelineM70KadreLiveRuntimeEvidence pipelinePmBundle
```
