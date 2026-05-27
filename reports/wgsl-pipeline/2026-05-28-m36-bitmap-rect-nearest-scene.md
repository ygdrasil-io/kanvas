# M36 Bitmap Rect Nearest Scene Evidence

Date: 2026-05-28
Linear: GRA-170
Scene: `bitmap-rect-nearest`

## Scope

This report backs the P0 bitmap/image-rect scene dashboard row. It reuses the
accepted M32 `DrawBitmapRectSkbug4734` evidence rather than regenerating or
rebaselining image fixtures.

## Source Evidence

Primary source reports:

- `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md`
- `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion.md`
- `.upstream/specs/wgsl-pipeline/08-bitmap-image-rect-sampling.md`

Accepted route facts from M32:

- strict source-rect constraints prevent guard-pixel bleed;
- strict nearest sampling uses integer texel loads matching the CPU path;
- `DrawBitmapRectSkbug4734WebGpuTest` is the single image-rect fixture promoted to required GPU smoke;
- final similarity is `100.00 >= 99.95`, with `4096/4096` matching pixels and max channel delta `0`.

## Dashboard Artifacts

The dashboard copies the accepted M32 after-fix artifacts into the scene export
root so `build/reports/wgsl-pipeline-scenes/` is self-contained for this row:

- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/bitmap-rect-nearest/stats.json`

## Threshold Policy

The scene keeps the M32 threshold at `99.95`; it does not lower floors or
promote broader image-rect inventory beyond the accepted smoke fixture.
