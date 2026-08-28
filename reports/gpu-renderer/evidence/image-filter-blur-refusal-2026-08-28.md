# Image-filter blur — refusal boundary (W29, 2026-08-28)

## Outcome

No public image-filter rendering claim is promoted.  The promoted
`image-filter-blur-refusal` bundle records the public `Surface` invocation of
one 9×9 RGBA8 red impulse with `ImageFilter.Blur(2, 2, CLAMP)` and its stable
pre-submission refusal: `unsupported.image.native_binding` with
`submissionDelta=0`.

This is deliberately distinct from `separable-blur-rect`, which is an already
supported **mask** blur of a rectangle, not an image filter.

## Native inventory and blocker

`GPUOpMapper.toImageFilterPlan` recognizes a bounded single-node CLAMP blur:
finite sigma in `[0, 12]`, no input filter, identity transform, bounded output
extent (at most 2048).  `GPUImageFilterDispatch.renderImageCommand` also
contains a real WebGPU encoder for its source, horizontal, vertical, and scene
composite passes.

Those pieces do not make a public route: `renderImageCommand` has no production
caller; its only callers are the tests in `GPUImageFilterDispatchTest`;
`GPUBackendRenderRecorder.dispatchImageRect` refuses a blur plan with
`unsupported.image-filter.blur.route-bypass`; and public Surface reaches
`GPUPreparedDrawImageLowerer`, which rejects `Paint.imageFilter` at native
binding before resources or submission.  `ColorMatrix` is earlier still: it is
not a `GPUImageFilterPlan.Blur` and is refused as
`unsupported.image-filter.image.kind`.  No dormant encoder is claimed as
product support.

The smallest honest future implementation is a prepared image-filter payload
and materializer that owns the three intermediate textures and encodes the four
existing passes, then connects the bounded plan to that payload.  It must keep
the above limits, expose pass/texture telemetry, and compare readback to the
independent CPU blur oracle before a render bundle is promoted.

## Oracle and evidence

`ImageFilterBlurCpuOracle` is independent CPU reference code used by
`GPUImageFilterSurfaceTest`.  Its CLAMP edge fixture rejects the historical
DECAL result by more than 100 alpha units, so it pins the sampling rule required
for the future render route.  A refusal case intentionally has no pixel oracle:
the evidence schema enforces `StableRefusal` and no oracle for a no-submission
outcome.

The refusal bundle was generated from source commit
`3a9123e359f7b4b2b41b87e7381f4fdbfc3670dc` and promoted with reviewer
`codex`:

- `correctness/promoted/image-filter-blur-refusal/{manifest,route,stats,diagnostics,verdict}.json`
- `correctness/promoted/catalog.json`
- `correctness/promoted/promotion.json`

## Commands

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence \
  -PsourceCommit=3a9123e359f7b4b2b41b87e7381f4fdbfc3670dc \
  -Pscene=image-filter-blur-refusal
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence \
  -PsourceCommit=3a9123e359f7b4b2b41b87e7381f4fdbfc3670dc \
  -Pscene=image-filter-blur-refusal
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
