# FOR-316 Pont de preuve runtime cache counter

Linear: `FOR-316`

Source memory: `global/kanvas/ticket-drafts/draft-for-next-runtime-cache-counter-evidence-bridge-ticket`

Decision: `RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_APPLIED`

## Résultat

FOR-316 relie la cartographie FOR-314 à la preuve FOR-315. La source candidate
`SkWebGpuDevice.cacheTelemetrySnapshot()` reste décrite dans FOR-314, et le
nouveau champ `observedEvidenceBridge` pointe maintenant vers l'artefact observé
`reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json`.

Ce pont établit une preuve Kanvas headless WebGPU observée pour ce scénario:
source class `kanvas-headless-webgpu-observed`, source API `SkWebGpuDevice.cacheTelemetrySnapshot()`,
adapter `Apple M2 Max`, cold `pipelineCacheMisses=1`,
cold `pipelineCacheHits=0`, warm
`pipelineCacheHits=1`.

## Frontière de revendication

Cette preuve n'est pas un callback cache Kadre/wgpu4k natif large. Elle provient
d'une exécution headless WebGPU Kanvas et ne change ni le comportement natif
Kadre, ni les callbacks exposés par wgpu4k, ni les blockers M92.

## Cartographie FOR-314

- Source candidate FOR-314: `kanvas-headless-webgpu-observed-candidate`.
- Pont observé FOR-315: `kanvas-headless-webgpu-observed`.
- Artefact observé nommé: `reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json`.
- API snapshot: `SkWebGpuDevice.cacheTelemetrySnapshot()`.

## M90/M92 conservés

- M90 cache hits/misses: `derived`.
- M90 allocations route native: `observed-partial`.
- M90 live route counters: `observed-partial`.
- M92 broad cache callback rows: conservent les classifications `not-observable`
  ou `derived` protégées.
- Blocker natif conservé: `kadre-runtime.native-cache-counter-unavailable`.

## Non-changements

- Readiness: unchanged.
- Release gate status: unchanged.
- Score: unchanged.
- Renderer behavior: unchanged.
- Shaders: unchanged.
- Thresholds: unchanged.
- Fallbacks: unchanged.
- Kadre native behavior: unchanged.

## Validation

- `rtk python3 scripts/validate_for316_runtime_cache_counter_evidence_bridge.py`
- `rtk python3 scripts/validate_for315_headless_webgpu_cache_counters.py`
- `rtk python3 scripts/validate_for314_runtime_cache_counter_source_map.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json >/dev/null`
- `rtk python3 -m json.tool reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json >/dev/null`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check`
