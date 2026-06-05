# FOR-415 M60 F16 AA stencil-cover blend/render-pass state

Date: 2026-06-05

## Result

Global classification: `fixed-function-blend-state-captured-no-mismatch`.

FOR-415 is a derived diagnostic over the existing opt-in FOR-405/FOR-412 runtime evidence. It adds no rendering hook, changes no route, and makes no support or score claim.

## Evidence

- Source draft memory: `global/kanvas/tickets/drafts/brouillon-ticket-for-415-m60-f16-capturer-etat-blend-render-pass-aa-stencil-cover`
- Source finding: `global/kanvas/findings/for-414-confirme-que-les-mutations-zero-return-sont-visibles-immediatement-apres-le-draw`
- FOR-401 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-405 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json`
- FOR-410 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-predraw-dst-readback-for410/m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json`
- FOR-412 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json`
- FOR-413 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-draw-transition-correlation-for413/m60-f16-aa-stencil-cover-draw-transition-correlation-for413.json`
- FOR-414 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-draw-readback-for414/m60-f16-aa-stencil-cover-post-draw-readback-for414.json`
- Source owner audited: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`

## Scope

- Selected pixels: 16
- Zero-return mutating transitions covered: 16
- Mutating draw counts: {'1': 6, '3': 10}
- Draw metadata: {'1': {'fillType': 'kWinding', 'scissor': [0, 0, 192, 128], 'edgeCount': 8, 'coverVertexCount': 6}, '3': {'fillType': 'kWinding', 'scissor': [0, 0, 192, 128], 'edgeCount': 39, 'coverVertexCount': 6}, '5': {'fillType': 'kWinding', 'scissor': [0, 0, 192, 128], 'edgeCount': 12, 'coverVertexCount': 6}}

## Captured state

The selected transitions are bounded to M60 F16 `StencilCoverAaPolygonDraw`, `kSrcOver`, the 16 FOR-401 pixels, and draw 1 / draw 3 mutations. Draw 5 remains context only.

- `BlendPlan.kind`: `FixedFunction`
- WebGPU color blend: `operation=Add`, `srcFactor=One`, `dstFactor=OneMinusSrcAlpha`
- WebGPU alpha blend: `operation=Add`, `srcFactor=One`, `dstFactor=OneMinusSrcAlpha`
- Intermediate color format: `RGBA16Float`
- Color attachment: `loadOp=Load`, `storeOp=Store`, `clearValue=background`
- Stencil attachment: `stencilLoadOp=Clear`, `stencilStoreOp=Discard`, `stencilReference=0`
- Inside/outside coverage sides use `fs_inside` / `fs_outside` with winding fill read mask `0xFF`.

## Interpretation

FOR-412 captures non-synthetic zero fragment returns for the mutating inside/outside subdraws. FOR-414 shows the same mutation is already visible immediately after the draw render pass. FOR-415 then audits the fixed WebGPU state used around those draws and does not expose a descriptor-level mismatch in blend state, color attachment format/load/store, scissor, geometry counts, or stencil setup.

With premultiplied `kSrcOver`, a source `[0, 0, 0, 0]` should leave the destination unchanged. The observed mutation therefore remains unexplained by the captured descriptor state and should be investigated below the descriptor level: actual color-target write path versus diagnostic shader-return side channel, or a minimized WebGPU/wgpu fixed-function reproduction.

## Non-goals preserved

- No rendering correction is applied.
- No default rendering behavior changes.
- No support or promotion claim is made for M60 F16.
- No score, threshold, route, fallback, or scene promotion policy changes.
- Unavailable FOR-412 shader returns are not converted into synthetic zero sources.
- No Ganesh, Graphite, or SkSL compiler work is introduced.

## Validation

- `rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py`
- `rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py`
- `rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py`
- `rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for415-pycache python3 -m py_compile scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py`
