# FOR-242 Crop Image-Filter Pre-Pass Fidelity

Linear: FOR-242

## Decision

Keep `crop-image-filter-nonnull-prepass` as a bounded WebGPU route, but do not
promote it to strict Skia fidelity.

The current evidence proves that the selected `Crop(kDecal, input =
Offset(null))` route no longer falls back on WebGPU, while the rendered result
still misses strict parity:

| Signal | Value |
|---|---:|
| CPU/reference similarity | 84.88% |
| GPU/reference similarity | 98.44% |
| Strict promotion target | 99.95% |
| Pixels | 128000 |
| GPU matching pixels | 126000 |
| CPU matching pixels | 108644 |

The row is therefore classified as `risk.fidelity-gap`, not `risk.none`.
FOR-242 improves the GPU score from 98.13% to 98.44% by removing a double
origin compensation in the selected WebGPU pre-pass, but the remaining gap is
still too large for strict promotion.

## Cause

The visible difference is not a one-byte color rounding residue. The
pre-FOR-242 `gpu-diff.png` artifact showed missing or shifted rectangular
regions across the SimpleOffset GM rows. The first confirmed cause was a double
origin compensation: `drawRect` reaches this selected M38 shape through an
implicit saveLayer whose transform has already shifted the crop rectangle into
layer-local pixels, so subtracting `originX` / `originY` again decals translated
GM cells incorrectly.

After removing that second subtraction, WebGPU improves to 98.44%. The route
is still only close because the selected WebGPU pre-pass materialises the
offset child into `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch`, then
composites through the crop UV remap. The remaining gap is still a
crop/pre-pass bounds semantics issue against the upstream Skia reference:
filtered output or clip regions can lie outside the original source-layer
extent, while the current source layer and final scissor are still sized from
the original draw bounds.

CPU/reference parity is weaker than GPU/reference parity, so FOR-242 does not
claim strict support from this evidence alone. Any broader bounds/pre-pass
rewrite needs fresh before/after CPU, GPU, reference, route and diff artifacts
proving that both backends moved toward the strict threshold.

## Preserved Boundaries

- `image-filter.crop-input-nonnull-prepass-required` remains the stable refusal
  for out-of-scope non-null crop graphs.
- No arbitrary image-filter DAG compiler is claimed.
- No CPU/readback fallback is introduced.
- No Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM work is introduced.

## Validation

FOR-242 adds a focused classification guard:

```text
rtk python3 scripts/validate_for242_crop_prepass_fidelity.py
```

The guard requires the selected GPU route to stay fallback-free, requires the
out-of-scope refusal row to keep
`image-filter.crop-input-nonnull-prepass-required`, and requires the supported
row to carry `risk.fidelity-gap` while either CPU or GPU remains below the
strict fidelity target.
