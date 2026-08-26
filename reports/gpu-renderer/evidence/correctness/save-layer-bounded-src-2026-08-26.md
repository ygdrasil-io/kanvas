# Bounded saveLayer SRC restore — 2026-08-26

## Route

`saveLayer(bounds, Paint(blendMode = SRC))` is admitted only for a finite,
single, non-nested isolated target. The layer texture is cleared transparent,
its children render into that texture, and the restore uses the WebGPU
fixed-function Porter-Duff `SRC` blend state (`one`, `zero`) on the parent
target. The layer alpha is applied to the sampled premultiplied source before
the restore.

The route neither samples nor reads the parent target. Its diagnostics retain
`destinationRead=none` and route label `fixed-function-src`.

## Native pixel and CPU oracle

`GPUSaveLayerCompositeRegressionTest.bounded saveLayer restores SRC without a
destination read` renders on the native offscreen WebGPU backend:

- parent: opaque white 8x8 target;
- bounded layer: `[2,2) - [6,6)`;
- child: 50% red fill;
- restore: `SRC`.

The independent CPU oracle converts the premultiplied linear source to RGBA8
sRGB. It expects the inside pixel to be the source-only value (red 188,
alpha 128, within two bytes) rather than the parent/source-over result, and
expects a pixel outside the bounds to remain opaque white. The native readback
passes both checks.

## Refusal policy

The gate continues to refuse all restore blends besides `SrcOver` and `Src`,
including `multiply`, with `unsupported.layer.restore_blend`. It also retains
the existing refusal policy for unbounded targets, target/usage budgets,
destination aliasing, filters, `initWithPrevious`, F16 and nested layers.

## Reproduction

```sh
./gradlew --no-daemon :kanvas:test \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest.bounded saveLayer restores SRC without a destination read'

./gradlew --no-daemon :gpu-renderer:test \
  --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerIsolatedTargetGateTest \
  --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerLiveMaterializationTest
```

## GM replay

Registered `rasterallocator` was replayed with `-Dkanvas.gm.name=rasterallocator
-Dkanvas.gm.includeBlocking=true`. It still refuses at
`unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted`, triggered
by its AA oval before the bounded layer is reached. This is not a saveLayer
regression or a promotion claim; no reference PNG or score threshold changed.
