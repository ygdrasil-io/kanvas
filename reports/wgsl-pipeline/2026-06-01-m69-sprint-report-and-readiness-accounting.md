# M69 Sprint Report And Readiness Accounting

Status: completed after native Kadre continuation.

## Scope

- Linear epic: `FOR-55` M69 Kanvas/Kadre Host Adapter V1.
- Linear issues: `FOR-56`, `FOR-57`, `FOR-58`, `FOR-59`.

## Delivered

M69 turns the M68 generic host-adapter blocker into an executable route
contract and a bounded native present loop:

- `pipelineM69KadreHostAdapterSmoke`
- `:kadre-runtime:runM69KadreNativeSmoke`
- `scripts/m69_kadre_host_adapter_smoke.py`
- `kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/M69KadreNativeSmoke.kt`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/`
- `reports/wgsl-pipeline/m69-kadre-native/native-smoke.json`
- `reports/wgsl-pipeline/2026-06-01-m69-kadre-host-adapter-smoke.md`

The smoke report audits Kadre source APIs from `external/poc-koreos`, maps them
to the Kanvas host contract, reuses M65 nonblank headless pixel proof for the
bridge lane, and emits a first scene route covering animated transform,
gradient/bitmap, and simple shape/path source evidence.

The route status is now `native-runnable` with
`m69.kadre-native-presented-frames` when host-local native smoke evidence is
available. The native smoke opens a Kadre AppKit/Metal window, creates a WebGPU
surface/device, renders a bounded standalone WGSL scene, calls `present()`, and
records 3 presented frames plus adapter/surface diagnostics. This does not yet
claim broad Kanvas display-list replay through Kadre.

## PM Interpretation

What the PM can claim:

- the Kanvas/Kadre host adapter contract is now explicit and generated;
- surface creation, resize, present, input, frame clock, diagnostics, export,
  and native presentation responsibilities are tracked as route capabilities;
- the first Kadre adapter scene route has auditable source evidence and
  headless pixel proof;
- a bounded standalone native Kadre/WebGPU lane presents frames on an eligible macOS host;
- the PM bundle exposes M69 counters under `m69KadreHostAdapter`.

What the PM must not claim yet:

- no native input loop is connected to a live Kanvas scene;
- no native screenshot/frame PNG is captured yet;
- native timing is present-call duration only, not release-grade FPS;
- broad Kanvas display-list replay through Kadre is not implemented yet;
- M69 does not add new Skia GM fidelity rows or rendering-family coverage.

## Readiness

Readiness moves from approximately 58% to approximately 62%.

| Area | Previous | Current | Reason |
|---|---:|---:|---|
| Rendering feature breadth | 60% | 60% | No new rendering-family support/refusal denominator changed. |
| Skia-like fidelity | 50% | 50% | No new selected GM/reference rows landed. |
| Real-time runtime | 50% | 65% | The Kadre host adapter now has an executable contract, route smoke, and bounded standalone native AppKit/Metal WebGPU present loop. |
| Performance and cache readiness | 40% | 40% | No new measured performance/cache gate landed. |
| PM/demo operability | 90% | 100% | PM bundle now includes M69 host adapter route status, contract, telemetry, first scene evidence, and native present smoke evidence. |

Weighted readiness: about 62%.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:runM69KadreNativeSmoke
rtk ./gradlew --no-daemon pipelineM69KadreHostAdapterSmoke
rtk ./gradlew --no-daemon -PkanvasRunNativeKadreSmoke=true pipelinePmBundle
python3 -m json.tool reports/wgsl-pipeline/m69-kadre-host-adapter/contract.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m69-kadre-host-adapter/route-status.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m69-kadre-host-adapter/scene-route.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m69-kadre-host-adapter/telemetry.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m69-kadre-host-adapter/bridge-smoke.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m69-kadre-native/native-smoke.json >/dev/null
python3 -m json.tool build/reports/wgsl-pipeline-pm-bundle/manifest.json >/dev/null
rtk git diff --check
```

## Next Blockers

- Add native screenshot or readback capture so PM can see the frame artifact, not only JSON counters.
- Connect resize/input events to a live Kanvas scene update loop.
- Replace the bounded inline WGSL scene with broader Kanvas display-list replay through the runtime pipeline.
- Promote native timing from present-call duration to an owner-approved FPS/frame gate.
