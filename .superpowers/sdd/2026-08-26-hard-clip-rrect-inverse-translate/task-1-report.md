# Task 1 report — inverse-Winding translated RRects

Baseline reviewed: `e0f1956c4c1659e223118ced977abb62dd2683cb`.

## TDD

RED was observed with:

```text
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest :integration-tests:gpu-evidence:test --tests org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbClipPathRRectCpuOracleTest
```

The new planner expectation failed at `FirstRoutePlannerTest.kt:1635` because the inverse translated RRect was refused as `unsupported.clip.complex_stack`; the public Surface case was terminal for the same route. Existing oracle cases passed. This is the expected missing-admission failure.

The initial GREEN was invalid for catalog metadata: `clipPathRRectCase` applied inverse-comparison wording to every RRect case. The pre-existing Winding policy assertion then failed at `GpuEvidenceCatalogTest.kt:448`, which is the required correction RED.

Correction GREEN passed with `GpuEvidenceCatalogTest`, `GpuEvidenceCatalogOracleTest`, and `SurfaceSrgbClipPathRRectCpuOracleTest`, then with the full focused wave gate from the brief (planner, stencil-native route, public inventory/Surface, catalog, catalog-oracle, executor, promotion CLI, and oracle tests). All selected tests passed. The catalog reports 70 render cases, 2 refusal cases, 72 total.

## Implementation

- Planner admission removes only the inverse-fill exclusion from the exact finite non-zero translation branch for `FillRRect`; Winding fill-rule, identity captured clip, opaque non-AA `SRC_OVER`, and every existing guard remain unchanged.
- Public inverse clips retain `FillRRect` / semantic `RRect` mapping. The existing direct RRect mapper already preserves that exact public representation; the new inventory contract covers the four translations plus `(0,0)` identity.
- The independent RRect oracle accepts `TriangleClip.Winding` (default) or `TriangleClip.InverseWinding`; inverse membership is the triangle complement.
- Added the four required public programs/catalog IDs and structural/oracle assertions. No DRRect, shader, ABI, clip-stencil, or `gpu-renderer-scenes` code changed.
- The correction adds `inverseWinding: Boolean = false` to the catalog factory and selects inverse wording only for the four new rows, preserving the exact Winding wording for prior rows. It also corrects inverse oracle counts for the literal oblique edge: `784`, `835`, `413`, and `789`.

## Source commit and native proof

Initial source/tests commit: `4e6ba7d71d812fd88b3b3f6959beaa834641068b` (`GPU: render exact translated RRects through inverse hard clips`).

Correction source/tests commit: `5e17c488571dbe7ae2999fee4fcd2f5597970377` (`GPU: correct inverse RRect evidence metadata`).

Each regenerated and promoted manifest has correction `sourceCommit` `5e17c488571dbe7ae2999fee4fcd2f5597970377`:

- `clip-path-inverse-axis-x-translated-solid-rrect`
- `clip-path-inverse-axis-y-translated-asymmetric-solid-rrect`
- `clip-path-inverse-negative-x-translated-ellipse-solid-rrect`
- `clip-path-inverse-negative-y-translated-solid-rrect`

For every bundle: `rendered/pass`, empty diagnostics, `submissionDelta=1`, `differingPixels=0`, `maxChannelDifference=0`, `similarityPercent=100.0`, and route events `[HardClipStencilProducer, AnalyticRRect]`.

`generateGpuEvidence` and `verifyGeneratedGpuEvidence` ran at the correction source SHA and passed all 72 cases. The four inspected native bundles carry the exact correction SHA, have `submissionDelta=1`, and retain `[HardClipStencilProducer, AnalyticRRect]`.

Promotion used reviewer `oracle`, reason `exact-finite-translation-rrect-inverse-hard-path-clip`, and rebaseline metadata `72 scenes: 70 renders and 2 refusals` -> `72 scenes: 70 renders and 2 refusals` to refresh the full evidence root at the correction SHA. `verifyPromotedGpuEvidence` passed all 72 cases.

## Commits and concerns

- Initial source/tests: `4e6ba7d71d812fd88b3b3f6959beaa834641068b`.
- Correction source/tests: `5e17c488571dbe7ae2999fee4fcd2f5597970377`.
- Corrected promoted evidence: `6ffdc4d0d17e7acc55d3827a1e80639898a3f82c`.

The original false GREEN is corrected. No remaining concern: all four native bundles are exact and the scoped focused gate is green.
