# FOR-436 - M60 F16 host draw-to-paint binding

## Conclusion

FOR-436 is a diagnostic-only step for the M60 F16 stroke cap/join regression. It links the six residual pixels from FOR-435 to the CPU-side WebGPU host binding for effective `drawIndex = 3`.

The trace classifies the current evidence as `cpu-reference-source-expects-different-draw`: WebGPU binds the expected host draw and paint for the round/round stroke, but the CPU reference still requires a much bluer source payload.

## Evidence

- Scene artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-host-draw-paint-binding-for436/m60-f16-host-draw-paint-binding-for436.json`
- Source draft memory: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-lier-draw-hote-et-paint-selectionne-pour-aa-stencil-cover-draw-index-3`
- Source finding memory: `global/kanvas/findings/for-435-web-gpu-paint-stroke-input-trace-identifies-host-paint-input-mismatch`
- Captured host binding count: `3`
- Selected host draw: `drawIndex = 3`
- Selected paint: `0xFF008A4C`, RGBA8 `[0, 138, 76, 255]`
- Selected stroke: width `10`, cap `round`, join `round`
- Selected band: `48..96`
- Six pixel set: `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`, `(87,80)`

## Non-goals preserved

- No default rendering change.
- No threshold, scoring, fallback, shader resource, `PipelineKey`, or wgsl4k change.
- No FOR-431 promotion.
- No support claim raised for the M60 F16 scene.

## Next step

Inspect the CPU/reference source expectation and draw/source mapping for these six pixels before changing WebGPU paint binding, coverage, quantization, target colorspace, thresholds, or FOR-431 promotion.
