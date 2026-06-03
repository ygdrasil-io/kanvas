# FOR-271 Nested RRect Blurred Envelope Audit

Linear: `FOR-271`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

The remaining residual after FOR-270 is localized to `blurred_content_envelope`.
The route remains `webgpu.coverage.nested-rrect-clip.expected-unsupported` with fallback `coverage.nested-clip-visual-parity-below-threshold`;
no promotion is valid because GPU/reference `98.48`
and CPU/reference `97.31` are both below
the strict `99.95` threshold.

## Subzone Findings

| Comparison | >32 pixels | Dominant subzone | Share | Max delta |
|---|---:|---|---:|---:|
| GPU/reference | 2869 | `draw_oval_outer_boundary` | 97.455559% | 57 |
| CPU/reference | 15726 | `draw_oval_outer_boundary` | 51.360804% | 237 |
| GPU/CPU | 12200 | `difference_oval_inner_boundary` | 42.098361% | 236 |

| Subzone | GPU/reference >32 | CPU/reference >32 | GPU/CPU >32 |
|---|---:|---:|---:|
| `draw_oval_outer_boundary` | 2796 | 8077 | 4616 |
| `difference_oval_inner_boundary` | 73 | 5201 | 5136 |
| `halo_interior` | 0 | 2448 | 2448 |
| `removed_difference_oval_interior` | 0 | 0 | 0 |
| `outside_draw_oval` | 0 | 0 | 0 |

## Color And Premul

| Comparison | Classification | Alpha >32 | RGB >32 | RGB payload shift |
|---|---|---:|---:|---:|
| GPU/reference | `COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED` | 0 | 2869 | 2869 |
| CPU/reference | `COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED` | 0 | 15726 | 15726 |
| GPU/CPU | `COLOR_PAYLOAD_SHIFT_ALPHA_UNCHANGED` | 0 | 12200 | 12200 |

The high-delta pixels have unchanged alpha and are classified as RGB payload
differences, not premultiplied-alpha coverage differences. GPU/reference skews
toward darker/black envelope samples, while CPU/reference skews toward
lighter/white samples.

## CPU/GPU Locality

| Measure | Value |
|---|---:|
| Shared CPU+GPU/reference >32 pixels | 2833 |
| Share of GPU/reference >32 also CPU/reference >32 | 98.745207% |
| Share of CPU/reference >32 also GPU/reference >32 | 18.014753% |
| GPU/reference-only >32 pixels | 36 |
| CPU/reference-only >32 pixels | 12893 |
| GPU/CPU >32 pixels | 12200 |

Verdict: `SHARED_REFERENCE_RESIDUAL_WITH_BACKEND_COLOR_POLARITY_DIFFERENCE`. The residual is not safely WebGPU-local:
CPU/reference is already below the strict threshold in the same blurred-envelope
region, while GPU/CPU still proves a backend color/layer divergence.

## Support Verdict

Keep `expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold`. Do not weaken the
threshold, add broad clip-stack support, fallback/readback, Ganesh/Graphite, or
SkSL compiler behavior. Preserve
`image-filter.crop-input-nonnull-prepass-required`.

Next action: reconcile the blur content/layer color model against reference and
CPU before considering any bounded blur/mask-filter correction.

## Validation

```text
rtk python3 scripts/validate_for271_nested_rrect_blurred_envelope_audit.py
rtk python3 scripts/validate_for270_nested_rrect_difference_oval_mask.py
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/nested-rrect-blurred-envelope-audit-for271.json`
