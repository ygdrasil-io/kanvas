# GPU Renderer Offscreen Renderable Scene Evidence

Date: 2026-06-24

This checkpoint refreshes the WebGPU offscreen evidence for every executable
scene in `GPURendererSceneRegistry.scenes`.

Scope:

- Catalogued executable scenes attempted: 77
- Fresh `rendered` reports: 31
- Fresh `not-yet-rendered` reports: 46
- Render backend: `webgpu-offscreen`

Rendered scenes:

- `activation-candidate-boundary-board`
- `bitmap-sampler-matrix`
- `blend-mode-strip`
- `blur-radius-ladder`
- `cache-frame-budget-strip`
- `cache-pressure-deck`
- `cache-source-ledger-board`
- `color-matrix-filter`
- `dash-pattern-ladder`
- `dst-read-strategy`
- `filter-dag-refusal-board`
- `filtered-photo-chip`
- `first-route-rollback-panel`
- `frame-gate-blocker-board`
- `frame-gate-m23-baseline`
- `glyph-atlas-strip`
- `gradient-tile-mode-boundary`
- `layer-filter-chain-board`
- `legacy-route-comparison`
- `path-aa-stroke-join-board`
- `performance-budget-review`
- `performance-gates-product-flag`
- `pipeline-cache-telemetry-review`
- `pm-evidence-m23-bundle`
- `product-route-smoke-lanes`
- `runtime-effect-child`
- `runtime-effect-uniform`
- `savelayer-isolated`
- `sdf-glyph-scale`
- `solid-card-stack`
- `stroke-cap-join`
- `tile-mode-strip`
- `translucent-card-overlap`

Non-claims:

- The 46 `not-yet-rendered` reports are not product support claims.
- M24 expands the faithful offscreen subset to include bitmap-rect, blur-rect,
  color-matrix-rect, stroke-rect, text-run, runtime-effect, and save-layer
  command families with procedural WGSL rendering.
