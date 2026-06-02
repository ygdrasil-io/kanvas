# M89 MEP-NEXT Feature Breadth Evidence

This pack aggregates post-RC-MEP PM evidence for `FOR-189` through `FOR-192`.
It is a headless evidence slice, not a renderer runtime change.

## Scope

| Linear | Family | PM evidence |
|---|---|---|
| `FOR-189` | Image filters | `crop-image-filter-nonnull-prepass` and `image-filter-compose-cf-matrix-transform` have reference/CPU/GPU/diff/stats/route artifacts; picture prepass and arbitrary DAGs stay refused. |
| `FOR-190` | Clips, RRects, Path AA | `clip-rect-difference` and `path-aa-stroke-primitive` are linked as bounded pass rows; `m57-aaclip-bounded-grid` and `m60-bounded-nested-rrect-clip` remain visible bounded evidence; edge-budget, dash, boolean clip, and stroke-outline gaps stay grouped. |
| `FOR-191` | Bitmap and texture sampling | `bitmap-subset-local-matrix-repeat` and `bitmap-shader-local-matrix` prove selected local-matrix/subset/repeat behavior; M79 keeps mipmap sampler refusal visible. |
| `FOR-192` | Registered WGSL runtime effects | `runtime.simple_rt` remains the selected descriptor-backed effect with Kotlin CPU behavior, WGSL GPU source, reflected layout, live-edit telemetry, and stable arbitrary Skia/SkSL or missing-WGSL refusals. |

## Dashboard And Bundle

- Dashboard expectation: `0 fail`, `0 tracked-gap`.
- PM bundle entry: `m89FeatureBreadth`.
- Headless validation: `validateMepNextFeatureBreadth`.
- Source RC-MEP merge evidence: PR #1324 commit `fbadbd3d4bd7ab8b86ffc2eabf01a02707b9068e`.

## Non-Claims

- No arbitrary recursive image-filter DAG support.
- No broad Path AA, broad clip-stack, or global AA budget increase.
- No broad image/texture/codec/mipmap/perspective/color-managed decode support.
- No dynamic SkSL compilation, SkSL IR, or SkSL VM.
- No new Kadre native runtime dependency for this headless proof.

## Validation

```text
rtk ./gradlew --no-daemon validateMepNextFeatureBreadth
rtk git diff --check
rtk ./gradlew --no-daemon pipelinePmBundle
```
