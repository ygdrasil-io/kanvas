# GPU Renderer Offscreen Renderable Scene Evidence

Date: 2026-06-24

This checkpoint refreshes the WebGPU offscreen evidence for every executable
scene in `GPURendererSceneRegistry.scenes`.

Scope:

- Catalogued executable scenes attempted: 77
- Fresh `rendered` reports: 22
- Fresh `not-yet-rendered` reports: 55
- Render backend: `webgpu-offscreen`

Rendered scenes:

- `activation-candidate-boundary-board`
- `blend-mode-strip`
- `cache-frame-budget-strip`
- `cache-pressure-deck`
- `cache-source-ledger-board`
- `dash-pattern-ladder`
- `filter-dag-refusal-board`
- `filtered-photo-chip`
- `first-route-rollback-panel`
- `frame-gate-blocker-board`
- `frame-gate-m23-baseline`
- `gradient-tile-mode-boundary`
- `layer-filter-chain-board`
- `legacy-route-comparison`
- `path-aa-stroke-join-board`
- `performance-budget-review`
- `performance-gates-product-flag`
- `pipeline-cache-telemetry-review`
- `pm-evidence-m23-bundle`
- `product-route-smoke-lanes`
- `solid-card-stack`
- `stroke-cap-join`
- `translucent-card-overlap`

Non-claims:

- The 55 `not-yet-rendered` reports are not product support claims.
- They preserve current runner limits for command families outside the faithful
  offscreen subset, such as rrect/gradient, bitmap, saveLayer/filter,
  runtime-effect, text, and mesh/vertices scenes.
- New M16-M23 scenes with FillRect-only commands are now rendered.
