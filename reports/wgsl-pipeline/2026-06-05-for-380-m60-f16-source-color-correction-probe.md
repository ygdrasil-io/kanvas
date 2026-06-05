# FOR-380 M60 F16 source/color correction probe

Decision: `M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED`

Classification: `regression-detected`

The minimal renderer point is the `StencilCoverAaPolygonDraw` uniform payload in
`SkWebGpuDevice.buildStencilCoverAaDrawResources`: `colorFilterKindMode.z`
decides whether the AA stencil-cover shader applies `targetColorSpaceBlend` to
the solid source color before coverage.

FOR-380 added a bounded probe flag,
`kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled`, scoped to the M60 F16
experimental stroke cap/join renderer, `RGBA16Float`, `SrcOver`, solid color,
stroke width `10`, and the three selected cap/join pairs. The flag is disabled
by default, so `correctionKept=false` and dashboard thresholds are unchanged.

Sample result:

| Metric | Before | Probe | Direct source proof |
|---|---:|---:|---:|
| 10-sample residual | 856 | 816 | 19 |
| Delta vs current | 0 | -40 | -837 |
| Delta vs direct proof | 837 | 797 | 0 |

The probe only improves the 10 selected samples by 40 and remains far from the
FOR-379 direct-source proof. The full-scene guard regresses: uncorrected
similarity stays `95.91%`, while the probe drops to `87.06%`; mismatch pixels
increase from `1004` to `3181`, and `>8` residual pixels increase from `10` to
`3164`.

Conclusion: the source/color divergence point is defensible, but the naive
correction is too broad inside the bounded stroke draw. It is recorded as a
reversed/refused experimental correction, not a renderer default. Stable refusal
remains `coverage.stroke-cap-join-visual-parity-below-threshold`; no support
claim or scene score promotion is made.
