# W29 — image-filter native inventory and refusal tranche

Date: 2026-08-28

## Decision

W29 does not claim a newly rendered image-filter route.  The smallest public
candidate is a single RGBA8 image with `ImageFilter.Blur(2, 2, CLAMP)`, an
integer destination and identity transform.  It is refused before submission
with `unsupported.image.native_binding`; the W29 catalog entry and promoted
bundle preserve that public boundary.

## Evidence-backed inventory

- `GPUOpMapper.toImageFilterPlan` accepts only a bounded single-node CLAMP
  blur (sigma 0–12, identity transform, output no larger than 2048); its
  ColorMatrix path is a stable `unsupported.image-filter.image.kind` refusal.
- `GPUImageFilterDispatch.renderImageCommand` has a real local WebGPU encoder
  for source, horizontal blur, vertical blur, and scene composite passes.
- It has no production caller; its only callers are the tests in
  `GPUImageFilterDispatchTest`. The legacy image dispatcher instead emits
  `unsupported.image-filter.blur.route-bypass`; the public prepared Surface
  lowerer rejects `Paint.imageFilter` before native resources are created.
- The existing `separable-blur-rect` evidence is mask-filter blur, not image
  filter support, and was not repurposed.

## Delivered refusal evidence

`image-filter-blur-refusal` executes a public `Surface` program: a 9×9 RGBA8
red impulse, `ImageFilter.Blur(2f, 2f, TileMode.CLAMP)`, at an integer 9×9
destination.  The promoted bundle observes `unsupported.image.native_binding`
and zero submissions, passes, textures, bind groups, samplers and queue writes.
It is a refusal proof only, not CPU-only render evidence.

The independent `ImageFilterBlurCpuOracle` remains exercised by
`GPUImageFilterSurfaceTest`: its edge fixture distinguishes CLAMP from DECAL
by more than 100 alpha units.  The evidence model intentionally forbids a
pixel oracle on `StableRefusal`; it is therefore not presented as a rendered
comparison.

## Minimal next route

Add a prepared image-filter frame payload/materializer that owns three
intermediate textures and encodes four passes (source, horizontal, vertical,
scene composite), then connect `GPUImageFilterPlan.Blur` to it.  It must retain
the current sigma/output/transform bounds, publish structural telemetry, and
pass a CPU/GPU pixel comparison before a positive bundle can be promoted.

## Artifacts

- `reports/gpu-renderer/evidence/image-filter-blur-refusal-2026-08-28.md`
- `reports/gpu-renderer/evidence/correctness/promoted/image-filter-blur-refusal/`
- Source commit: `3a9123e359f7b4b2b41b87e7381f4fdbfc3670dc`

## Tests

- `:integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest`
- `:kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUImageFilterSurfaceTest --tests org.graphiks.kanvas.surface.gpu.GPUImageFilterDispatchTest --tests org.graphiks.kanvas.surface.gpu.GPUImageFilterPlanTest`
- `:integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=3a9123e359f7b4b2b41b87e7381f4fdbfc3670dc -Pscene=image-filter-blur-refusal`
- `:integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -PsourceCommit=3a9123e359f7b4b2b41b87e7381f4fdbfc3670dc -Pscene=image-filter-blur-refusal`
- `:integration-tests:gpu-evidence:promoteGpuEvidence -PsourceCommit=3a9123e359f7b4b2b41b87e7381f4fdbfc3670dc -PpromotionReviewer=codex -PpromotionReason='W29 public image-filter blur refusal boundary' -Pscene=image-filter-blur-refusal`

All commands passed.
