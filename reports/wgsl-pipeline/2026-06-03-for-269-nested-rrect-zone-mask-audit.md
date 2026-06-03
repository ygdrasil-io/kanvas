# FOR-269 Nested RRect Zone/Mask Audit

Linear: `FOR-269`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

Next action: `BOUNDED_CLIP_MASK_CORRECTION_FIRST`. The stable refusal remains
`coverage.nested-clip-visual-parity-below-threshold` on route `webgpu.coverage.nested-rrect-clip.expected-unsupported`. No support promotion,
threshold change, broad clip-stack support, fallback/readback path, Ganesh,
Graphite, or SkSL compiler was added. The existing Crop refusal
`image-filter.crop-input-nonnull-prepass-required` remains preserved.

## Route And Threshold

| Field | Value |
|---|---|
| CPU route | `cpu.coverage.nested-rrect-clip-oracle` |
| GPU route | `webgpu.coverage.nested-rrect-clip.expected-unsupported` |
| GPU status | `expected-unsupported` |
| Fallback | `coverage.nested-clip-visual-parity-below-threshold` |
| Strict support threshold | `99.95` |
| Reported CPU/reference similarity | `97.31` |
| Reported GPU/reference similarity | `71.22` |

## Zone Findings

| Comparison | Dominant >32 delta zone | Share | Max delta |
|---|---|---:|---:|
| GPU/reference | `outside_clip_removed_difference_oval` | 92.912221% | 255 |
| CPU/reference | `blurred_content_envelope` | 100.0% | 237 |

GPU/reference has 246288 pixels above
delta 32 inside `outside_clip_removed_difference_oval`, which is the oval
removed by `clipRRect(kDifference)`. CPU/reference has
15726 pixels above delta 32 in
`blurred_content_envelope`, so CPU/reference is still below the strict support
bar even after the GPU clip-mask issue is isolated.

The zone masks are analytic and non-exclusive. Their counters isolate likely
failure hypotheses and must not be summed as a partition of the image.

## Machine Artifact

`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/nested-rrect-zone-mask-audit-for269.json`

## Validation

```text
rtk python3 scripts/validate_for269_nested_rrect_zone_mask_audit.py
```
