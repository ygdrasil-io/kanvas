# Task 1 report — inverse-Winding translated RRects

Baseline reviewed: `e0f1956c4c1659e223118ced977abb62dd2683cb`.

## TDD

RED was observed with:

```text
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathRRectCpuOracleTest
```

The new planner expectation failed at `FirstRoutePlannerTest.kt:1635` because the inverse translated RRect was refused as `unsupported.clip.complex_stack`; the public Surface case was terminal for the same route. Existing oracle cases passed. This is the expected missing-admission failure.

GREEN was observed with the focused wave gate from the brief, including planner, stencil-native route, public inventory/Surface, catalog, catalog-oracle, executor, promotion CLI, and oracle tests. All selected tests passed. The catalog now reports 70 render cases, 2 refusal cases, 72 total.

## Implementation

- Planner admission removes only the inverse-fill exclusion from the exact finite non-zero translation branch for `FillRRect`; Winding fill-rule, identity captured clip, opaque non-AA `SRC_OVER`, and every existing guard remain unchanged.
- Public inverse clips retain `FillRRect` / semantic `RRect` mapping. The existing direct RRect mapper already preserves that exact public representation; the new inventory contract covers the four translations plus `(0,0)` identity.
- The independent RRect oracle accepts `TriangleClip.Winding` (default) or `TriangleClip.InverseWinding`; inverse membership is the triangle complement.
- Added the four required public programs/catalog IDs and structural/oracle assertions. No DRRect, shader, ABI, clip-stencil, or `gpu-renderer-scenes` code changed.

## Source commit and native proof

Source/tests commit: `4e6ba7d71d812fd88b3b3f6959beaa834641068b` (`GPU: render exact translated RRects through inverse hard clips`).

Each generated and promoted manifest has that exact `sourceCommit`:

- `clip-path-inverse-axis-x-translated-solid-rrect`
- `clip-path-inverse-axis-y-translated-asymmetric-solid-rrect`
- `clip-path-inverse-negative-x-translated-ellipse-solid-rrect`
- `clip-path-inverse-negative-y-translated-solid-rrect`

For every bundle: `rendered/pass`, empty diagnostics, `submissionDelta=1`, `differingPixels=0`, `maxChannelDifference=0`, `similarityPercent=100.0`, and route events `[HardClipStencilProducer, AnalyticRRect]`.

Generation was run one named inverse scene at a time with `-PsourceCommit=4e6ba7d71d812fd88b3b3f6959beaa834641068b`, each followed by `verifyGeneratedGpuEvidence`; final generated verification passed all 72 cases.

Promotion used reviewer `oracle`, reason `exact-finite-translation-rrect-inverse-hard-path-clip`, and rebaseline metadata `68 scenes: 66 renders and 2 refusals` -> `72 scenes: 70 renders and 2 refusals`. `verifyPromotedGpuEvidence` passed.

## Commits and concerns

- Source/tests: `4e6ba7d71d812fd88b3b3f6959beaa834641068b`.
- Promoted evidence and this report: `7708e7d3774216c58548eda1b329bd07d5fee3e9`.

No concern remains: all four native bundles are exact and the scoped focused gate is green.
