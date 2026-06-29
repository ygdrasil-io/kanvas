# GPU Renderer Scene Catalog

| Scene ID | Title | Tags | KGPU | Tickets | R Stages | Expectation |
|---|---|---|---|---|---|---|
| `solid-card-stack` | Solid Card Stack | Rect | KGPU M0,M1 |  | R0,R1,R2,R3,R4,R5,R6 | `ShouldRender` |
| `activation-candidate-boundary-board` | Activation Candidate Boundary Board | Rect,Cache,LegacyComparison | KGPU M0,M1 | `KGPU-M0-007`,`KGPU-M1-001`,`KGPU-M1-002` |  | `ShouldRender` |
| `first-route-rollback-panel` | First Route Rollback Panel | Rect,LegacyComparison | KGPU M1 | `KGPU-M1-003`,`KGPU-M1-004` |  | `ShouldRender` |
| `product-route-smoke-lanes` | Product Route Smoke Lanes | Rect,LegacyComparison | KGPU M0,M1 |  |  | `ShouldRender` |
| `rounded-panel-gradient` | Rounded Panel Gradient | RRect,Gradient,Clip | KGPU M2 |  | R1,R2,R3 | `ShouldRender` |
| `rrect-gradient-route-board` | RRect Gradient Route Board | Rect,RRect,Gradient,Clip | KGPU M2 | `KGPU-M2-001`,`KGPU-M2-002` |  | `ShouldRender` |
| `release-gate-progress-board` | Release Gate Progress Board | Rect,RRect,Gradient,Clip | KGPU M2 | `KGPU-M2-003`,`KGPU-M2-004` |  | `ShouldRender` |
| `gradient-tile-mode-boundary` | Gradient Tile Mode Boundary | Rect,Gradient,Clip | KGPU M2 |  |  | `ShouldRender` |
| `path-badge-and-stroke` | Path Badge And Stroke | RRect,Rect | KGPU M3 |  |  | `ShouldRender` |
| `path-coverage-review-board` | Path Coverage Review Board | Rect,RRect,Clip,Path,Stroke | KGPU M3 | `KGPU-M3-001`,`KGPU-M3-003`,`KGPU-M3-004`,`KGPU-M3-005` |  | `ShouldRender` |
| `path-stencil-cover-gate-board` | Path Stencil Cover Gate Board | Rect,RRect,Clip,Path,Stroke | KGPU M3 | `KGPU-M3-002` |  | `ShouldRender` |
| `path-aa-stroke-join-board` | Path AA Stroke Join Board | Rect,Clip,Path,Stroke | KGPU M3 |  |  | `ShouldRender` |
| `clipped-avatar-grid` | Clipped Avatar Grid | Clip,Image | KGPU M3,M5 |  |  | `ShouldRender` |
| `texture-swatch-board` | Texture Swatch Board | Image | KGPU M4 |  |  | `ShouldRender` |
| `asset-intake-thumbnail-grid` | Asset Intake Thumbnail Grid | Image,Clip,RRect | KGPU M4 | `KGPU-M4-001`,`KGPU-M4-002` |  | `ShouldRender` |
| `photo-contact-sheet` | Photo Contact Sheet | Image,Clip,RRect | KGPU M4 |  |  | `ShouldRender` |
| `codec-provenance-gate-board` | Codec Provenance Gate Board | Rect,RRect,Clip,Image | KGPU M4 | `KGPU-M4-003` |  | `ShouldRender` |
| `sampler-boundary-gate-board` | Sampler Boundary Gate Board | Rect,RRect,Clip,Image | KGPU M4 | `KGPU-M4-004` |  | `ShouldRender` |
| `bitmap-sampler-matrix` | Bitmap Sampler Matrix | Image,Clip,RRect | KGPU M4 |  |  | `ShouldRender` |
| `savelayer-isolation-gate-board` | SaveLayer Isolation Gate Board | Rect,RRect,Clip,Layer | KGPU M5 | `KGPU-M5-001` |  | `ShouldRender` |
| `destination-read-strategy-gate-board` | Destination Read Strategy Gate Board | Rect,RRect,Clip,Blend | KGPU M5 | `KGPU-M5-002` |  | `ShouldRender` |
| `layer-filter-chain-board` | Layer Filter Chain Board | Rect,Clip,Layer,Filter | KGPU M5 |  |  | `ShouldRender` |
| `layered-shadow-card` | Layered Shadow Card | Layer,Filter | KGPU M5 |  |  | `ShouldRender` |
| `notification-shadow-stack` | Notification Shadow Stack | Layer,Filter | KGPU M5 |  |  | `ShouldRender` |
| `filtered-photo-chip` | Filtered Photo Chip | Filter,Image | KGPU M5 |  |  | `ShouldRender` |
| `tinted-avatar-card` | Tinted Avatar Card | Image,Clip,RRect,Filter | KGPU M5 |  |  | `ShouldRender` |
| `filter-dag-refusal-board` | Filter DAG Refusal Board | Rect,Filter | KGPU M5 | `KGPU-M5-004` |  | `ShouldRender` |
| `receipt-text-run` | Receipt Text Run | Text | KGPU M6 |  |  | `ShouldRender` |
| `text-handoff-boundary-board` | Text Handoff Boundary Board | Rect,RRect,Clip,Text | KGPU M6 | `KGPU-M6-001` |  | `ShouldRender` |
| `text-resource-binding-gate-board` | Text Resource Binding Gate Board | Rect,RRect,Clip,Text | KGPU M6 | `KGPU-M6-003` |  | `ShouldRender` |
| `a8-glyph-atlas-gate-board` | A8 Glyph Atlas Gate Board | Rect,RRect,Clip,Text | KGPU M6 | `KGPU-M6-002` |  | `ShouldRender` |
| `text-representation-gate-board` | Text Representation Gate Board | Rect,RRect,Clip,Text | KGPU M6 | `KGPU-M6-004` |  | `ShouldRender` |
| `runtime-effect-color-tile` | Runtime Effect Color Tile | RuntimeEffect | KGPU M7 |  |  | `ShouldRender` |
| `runtime-effect-descriptor-gate-board` | Runtime Effect Descriptor Gate Board | Rect,RRect,Clip,RuntimeEffect | KGPU M7 | `KGPU-M7-001` |  | `ShouldRender` |
| `runtime-effect-refusal-gate-board` | Runtime Effect Refusal Gate Board | Rect,RRect,Clip,RuntimeEffect | KGPU M7 | `KGPU-M7-002` |  | `ShouldRender` |
| `runtime-effect-uniform-ladder` | Runtime Effect Uniform Ladder | RuntimeEffect,RRect,Clip | KGPU M7 |  |  | `ShouldRender` |
| `custom-runtime-effect-valid-tile` | Custom Runtime Effect Valid Tile | RuntimeEffect,Rect | KGPU M32 | `KGPU-M32-019` |  | `ShouldRender` |
| `custom-runtime-effect-unregistered-refusal` | Custom Runtime Effect Unregistered Refusal | RuntimeEffect,Rect | KGPU M32 | `KGPU-M32-019` |  | `ShouldRefuse:unsupported.runtime_effect.custom_wgsl_not_registered` |
| `blend-mode-strip` | Blend Mode Strip | Rect | KGPU M7 |  |  | `ShouldRender` |
| `translucent-card-overlap` | Translucent Card Overlap | Rect,Blend | KGPU M7 | `KGPU-M7-003` |  | `ShouldRender` |
| `sdr-color-boundary-board` | SDR Color Boundary Board | Rect,RRect,Clip | KGPU M7 | `KGPU-M7-004` |  | `ShouldRender` |
| `mesh-ribbon` | Mesh Ribbon | Vertices | KGPU M8 |  |  | `ShouldRender` |
| `vertices-route-gate-board` | Vertices Route Gate Board | Rect,RRect,Clip,Vertices | KGPU M8 | `KGPU-M8-001`,`KGPU-M8-002`,`KGPU-M8-003` |  | `ShouldRender` |
| `mesh-ribbon-depth-stack` | Mesh Ribbon Depth Stack | Vertices,RRect,Clip | KGPU M8 |  |  | `ShouldRender` |
| `cache-pressure-deck` | Cache Pressure Deck | Rect | KGPU M9 |  |  | `ShouldRender` |
| `cache-frame-budget-strip` | Cache Frame Budget Strip | Rect,Cache | KGPU M9 |  |  | `ShouldRender` |
| `cache-source-ledger-board` | Cache Source Ledger Board | Rect,Cache | KGPU M9 | `KGPU-M9-001` |  | `ShouldRender` |
| `frame-gate-blocker-board` | Frame Gate Blocker Board | Rect | KGPU M9 | `KGPU-M9-002` |  | `ShouldRender` |
| `pm-readiness-freeze-board` | PM Readiness Freeze Board | Rect,RRect,Clip,Cache | KGPU M9 | `KGPU-M9-003` |  | `ShouldRender` |
| `legacy-route-comparison` | Legacy Route Comparison | Rect | KGPU M10 |  |  | `ShouldRender` |
| `legacy-inventory-hygiene-board` | Legacy Inventory Hygiene Board | Rect,RRect,Clip,LegacyComparison | KGPU M10 | `KGPU-M10-001`,`KGPU-M10-004` |  | `ShouldRender` |
| `shadow-parity-migration-gate-board` | Shadow Parity Migration Gate Board | Rect,RRect,Clip,LegacyComparison | KGPU M10 | `KGPU-M10-002` |  | `ShouldRender` |
| `legacy-retirement-blocker-board` | Legacy Retirement Blocker Board | Rect,RRect,Clip,LegacyComparison | KGPU M10 | `KGPU-M10-003` |  | `ShouldRender` |
| `legacy-parity-snapshot-board` | Legacy Parity Snapshot Board | LegacyComparison,Rect,RRect | KGPU M10 |  |  | `ShouldRender` |
| `rounded-rect-solids` | Rounded Rect Solids | RRect | KGPU M10 |  |  | `ShouldRender` |
| `linear-gradient-lanes` | Linear Gradient Lanes | Rect,Gradient | KGPU M10 |  |  | `ShouldRender` |
| `scissor-overlay` | Scissor Overlay | Rect,Clip | KGPU M10 |  |  | `ShouldRender` |
| `radial-swatch` | Radial Swatch | Rect,Gradient | KGPU M14 |  |  | `ShouldRender` |
| `sweep-disk` | Sweep Disk | Rect,Gradient | KGPU M14 |  |  | `ShouldRender` |
| `path-fill-stencil` | Path Fill Stencil | Path | KGPU M15 | `KGPU-M15-002` |  | `ShouldRender` |
| `convex-fan-mesh` | Convex Fan Mesh | Path | KGPU M15 | `KGPU-M15-003` |  | `ShouldRender` |
| `savelayer-isolated` | SaveLayer Isolated | Layer | KGPU M18 |  |  | `ShouldRender` |
| `savelayer-group-alpha` | SaveLayer Group Alpha | Layer,Blend | KGPU M28 | `KGPU-M28-006` |  | `ShouldRender` |
| `dst-read-strategy` | Destination Read Strategy | Layer,Blend | KGPU M18 |  |  | `ShouldRender` |
| `blur-radius-ladder` | Blur Radius Ladder | Filter | KGPU M19 |  |  | `ShouldRender` |
| `color-matrix-filter` | Color Matrix Filter | Filter | KGPU M19 |  |  | `ShouldRender` |
| `gaussian-blur-photo` | Gaussian Blur Photo | Filter | KGPU M19 |  |  | `ShouldRender` |
| `color-matrix-tint` | Color Matrix Tint | Filter | KGPU M19 |  |  | `ShouldRender` |
| `stroke-and-filter-card` | Stroke and Filter Card | Stroke,Filter | KGPU M16,M19 |  |  | `ShouldRender` |
| `glyph-atlas-strip` | Glyph Atlas Strip | Text | KGPU M20 |  |  | `ShouldRender` |
| `sdf-glyph-scale` | SDF Glyph Scale | Text | KGPU M20 |  |  | `ShouldRender` |
| `runtime-effect-uniform` | Runtime Effect Uniform | RuntimeEffect | KGPU M21 |  |  | `ShouldRender` |
| `runtime-effect-child` | Runtime Effect Child | RuntimeEffect | KGPU M21 |  |  | `ShouldRender` |
| `stroke-cap-join` | Stroke Cap Join | Stroke | KGPU M16 |  |  | `ShouldRender` |
| `dash-pattern-ladder` | Dash Pattern Ladder | Stroke | KGPU M16 |  |  | `ShouldRender` |
| `stroke-rect-outline` | Stroke Rect Outline | Stroke | KGPU M16 |  |  | `ShouldRender` |
| `tile-mode-strip` | Tile Mode Strip | Image | KGPU M17 |  |  | `ShouldRender` |
| `vertices-color-mesh` | Vertices Color Mesh | Vertices | KGPU M22 |  |  | `ShouldRender` |
| `mesh-ribbon-depth` | Mesh Ribbon Depth | Vertices | KGPU M22 |  |  | `ShouldRender` |
| `performance-budget-review` | Performance Budget Review | Rect,Cache | KGPU M23 | `KGPU-M23-001` |  | `ShouldRender` |
| `pipeline-cache-telemetry-review` | Pipeline Cache Telemetry Review | Rect,Cache | KGPU M23 | `KGPU-M23-002` |  | `ShouldRender` |
| `frame-gate-m23-baseline` | Frame Gate M23 Baseline | Rect,Cache | KGPU M23 | `KGPU-M23-003` |  | `ShouldRender` |
| `pm-evidence-m23-bundle` | PM Evidence M23 Bundle | Rect,Cache | KGPU M23 | `KGPU-M23-004` |  | `ShouldRender` |
| `performance-gates-product-flag` | Performance Gates Product Flag | Rect | KGPU M23 |  |  | `ShouldRender` |
| `path-star-gradient` | Path Star Gradient | Path,Gradient | KGPU M26 |  |  | `ShouldRender` |
| `text-a8-hello` | Text A8 Hello | Text | KGPU M26 |  |  | `ShouldRender` |
| `gradient-path-and-text` | Gradient Path And Text | Path,Gradient,Text | KGPU M26 |  |  | `ShouldRender` |
