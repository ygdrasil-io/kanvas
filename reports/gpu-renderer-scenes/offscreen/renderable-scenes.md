# GPU Renderer Offscreen Renderable Scene Evidence

Date: 2026-06-17

This checkpoint refreshes the WebGPU offscreen evidence for every executable
scene listed in `reports/gpu-renderer-scenes/catalog/catalog.json`.

Scope:

- Catalogued executable scenes attempted: 52
- Fresh `rendered` reports: 15
- Fresh `not-yet-rendered` reports: 37
- Render backend: `webgpu-offscreen`

Rendered scenes:

- `activation-candidate-boundary-board`
- `blend-mode-strip`
- `cache-frame-budget-strip`
- `cache-pressure-deck`
- `cache-source-ledger-board`
- `filter-dag-refusal-board`
- `first-route-rollback-panel`
- `frame-gate-blocker-board`
- `gradient-tile-mode-boundary`
- `layer-filter-chain-board`
- `legacy-route-comparison`
- `path-aa-stroke-join-board`
- `product-route-smoke-lanes`
- `solid-card-stack`
- `translucent-card-overlap`

Non-claims:

- The 37 `not-yet-rendered` reports are not product support claims.
- They preserve current runner limits for command families outside the faithful
  offscreen subset, such as rrect/gradient, bitmap, saveLayer/filter,
  runtime-effect, text, and mesh/vertices scenes.
- Stale historical `rendered` report payloads were replaced where the current
  runner now reports `not-yet-rendered`.

Generation command:

```bash
rtk zsh -lc 'set -euo pipefail
scene_ids=(${(f)"$(jq -r '\''.scenes[].sceneId'\'' reports/gpu-renderer-scenes/catalog/catalog.json)"})
for scene_id in "${scene_ids[@]}"; do
  ./gradlew :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId="$scene_id" --daemon --quiet
done'
```
