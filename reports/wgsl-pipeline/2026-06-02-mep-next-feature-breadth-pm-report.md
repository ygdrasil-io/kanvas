# MEP-NEXT Feature Breadth PM Report

Date: 2026-06-02
Scope: `FOR-189`, `FOR-190`, `FOR-191`, `FOR-192`

## Result

The post-RC-MEP breadth slice is documented as a durable PM evidence pack under
`reports/wgsl-pipeline/m89-feature-breadth/`. It links bounded visual evidence
that already exists in the generated dashboard and runtime evidence folders,
then validates that the claims remain narrow.

## Evidence Summary

| Linear | Supported evidence | Refusal evidence |
|---|---|---|
| `FOR-189` | `crop-image-filter-nonnull-prepass`, `image-filter-compose-cf-matrix-transform` | `image-filter.crop-input-nonnull-prepass-required`, `m83.filter.placeholder-dag-not-routed` |
| `FOR-190` | `clip-rect-difference`, `path-aa-stroke-primitive`; bounded evidence rows `m57-aaclip-bounded-grid`, `m60-bounded-nested-rrect-clip` | `coverage.edge-count-exceeded`, `m78.clip.unsupported-complex-clip`, `coverage.stroke-outline-edge-count-exceeded` |
| `FOR-191` | `bitmap-subset-local-matrix-repeat`, `bitmap-shader-local-matrix`, M79 replay evidence | `m79.bitmap.unsupported-sampler.mipmap` |
| `FOR-192` | `runtime.simple_rt` descriptor, `runtime_simple_rt.wgsl`, M87 live-edit evidence | `runtime-effect.arbitrary-sksl-unsupported`, `runtime-effect.wgsl-descriptor-missing` |

## Dashboard Link

After `pipelinePmBundle`, open:

```text
build/reports/wgsl-pipeline-pm-bundle/dashboard/index.html
```

The bundle manifest contains `m89FeatureBreadth` with links to the evidence
JSON, Markdown report, source rows, validation rows, and non-claims.

## Limits

- This is evidence aggregation and validation, not new renderer code.
- It does not claim arbitrary image-filter DAG support.
- It does not claim broad Path AA, broad clip-stack support, or a larger AA
  edge budget.
- It does not claim broad bitmap/texture/image/codec support.
- It does not claim arbitrary Skia/SkSL runtime shader input; WGSL remains the
  implementation target for registered effects.
