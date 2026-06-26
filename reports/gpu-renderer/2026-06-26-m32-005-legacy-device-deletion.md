# M32-005: Legacy GPU Device Deletion (Surgical Option B)

**Date:** 2026-06-26  
**Ticket:** KGPU-M32-005  
**Plan:** `docs/superpowers/plans/2026-06-26-legacy-gpu-raster-decommission.md` (Step 5B.2)

## Summary

Deleted the legacy `SkWebGpuDevice` and its device-dependent classes from `:gpu-raster` while preserving the module and its shared infrastructure (WGSL validation, generated WGSL, retirement/shadow gates, inventory tooling). Option A (full module removal) is deferred.

## Files Deleted

### Main source files (9)
- `SkWebGpuDevice.kt` (~26,000 LOC)
- `WebGpuContext.kt`
- `HeadlessTarget.kt`
- `WebGpuCoveragePlanSelector.kt`
- `SkWebGpuGlyphAtlas.kt`
- `GpuRendererFirstRouteWebGpuSubmitter.kt`
- `GpuRendererFirstRouteRollbackParity.kt`
- `GpuRendererFirstRouteExecutedPMEvidenceExport.kt`
- `GpuRendererShadowAdapter.kt`

### Test files (~405)
All test files under `gpu-raster/src/test/` except:
- `tools/WgslValidationReportTest.kt`
- `tools/WgslStrictValidationReportTest.kt`
- `tools/GeneratedSolidRectWgslTest.kt`
- `tools/GeneratedLinearGradientWgslTest.kt`
- `tools/RuntimeEffectsLayoutV2ReportTest.kt`
- `tools/GpuInventoryFailureReportTest.kt`
- `BlendPlanTest.kt`
- `RuntimeEffectDescriptorWebGpuTest.kt` (cleaned: device tests removed, WGSL validation tests kept)
- `TextWgslValidationPipelineConformanceTest.kt`
- `GpuRendererLegacyRetirementGateTest.kt`
- `GpuRendererShadowParityMigrationGateTest.kt`

Also deleted: `benchmarks/` directory, `testing/CrossTestHarness.kt`, `testing/CrossBackendHarness.kt`, `crossbackend/` directory, all `*SceneEvidence*`, `*SceneCapture*`, `PipelineKeyTelemetryTest.kt`, `SkWebGpuGlyphAtlasTest.kt`, `WebGpuCoveragePlanSelectorTest.kt`.

### Other
- `kanvas-skia-bridge/.../CompareBridgeVsLegacyGpuRaster.kt`

**Total: 424 files changed, 96 insertions, 106,242 deletions**

## Files Kept

### Main source (3 + shared infra)
- `PipelineKey.kt` (PipelineKeyClassification/AxisClass)
- `GpuRendererLegacyRetirementGates.kt`
- `GpuRendererShadowParityGates.kt`
- `tools/`: WgslValidationReport, WgslStrictValidationReport, WgslParserSmokeMain, GeneratedSolidRectWgsl, GeneratedLinearGradientWgsl, RuntimeEffectsLayoutV2Report, GpuInventoryFailureReport
- `BlendPlan.kt` (extracted from deleted `SkWebGpuDevice.kt`; standalone file)
- `resources/shaders/*.wgsl`
- `resources/.../wgsl-diagnostics-allowlist.txt`

### Module preserved
- `settings.gradle.kts` still includes `include(":gpu-raster")`

## Rollback Severed

- `RollbackConfig.kt`: removed `useLegacyGpuRaster` + `SYSTEM_PROPERTY` + `ENV_VARIABLE`; kept `productActivation`
- `SkiaKanvasSurface.kt`: `isKanvasRendererEnabled()` returns `true` unconditionally; `wrapIfEnabled` simplified; removed rollback diagnostic wording
- `KanvasSkiaBridgeTest.kt`: removed rollback tests (lines ~244-280, ~440-444)
- `KanvasSkiaBridge.kt`: removed "No silent fallback to legacy gpu-raster" wording

## Build Cleanup

### gpu-raster/build.gradle.kts
- Removed `gpuRendererR6ExecutedFirstRoutePmEvidenceBundle` + `validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle` tasks
- Emptied `gpuSmokePatternSpecs` / `imageRectSimilarityGuards` (referenced deleted tests)
- Removed `gpuSmokeTest` + `kan054WebGpuGlyphAtlasSamplingRouteTest` tasks

### kanvas-skia-bridge/build.gradle.kts
- Removed `implementation(project(":gpu-raster"))` dependency
- Removed `compareBridgeVsLegacyGpuRaster` JavaExec task

### root build.gradle.kts
- `pipelineConformanceTest` for `:gpu-raster` cut to 7 device-independent patterns: WgslValidationReportTest, WgslStrictValidationReportTest, GeneratedSolidRectWgslTest, GeneratedLinearGradientWgslTest, BlendPlanTest, RuntimeEffectDescriptorWebGpuTest, TextWgslValidationPipelineConformanceTest
- `requiredPipelineConformanceSuites` reduced to non-deleted entries
- `gpuAdapterEvidenceForReport` updated to use BlendPlanTest + RuntimeEffectDescriptorWebGpuTest
- Removed `inputs.file("gpu-raster/.../SkWebGpuDevice.kt")` from `pipelineRuntimeChildShaderEffectLaneReport` and `pipelineRuntimeBlenderBoundaryReport`
- Removed `:gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle` dependsOn + executedSummary from `validateGpuRendererR6AdapterBackedPromotionReadinessBoundary`

## Verification Results

### Full Build (excluding unpublished kadre-runtime)
```
BUILD SUCCESSFUL in 25s
188 actionable tasks: 4 executed, 184 up-to-date
```

### Kept Infra Tasks
```
:gpu-raster:wgslValidateStrict    — PASSED
:gpu-raster:wgslValidateAll       — PASSED  
:gpu-raster:pipelineConformanceTest — PASSED
:gpu-raster:test                  — 26/26 PASSED (11 keep tests)
```

### Module Tests
```
:kanvas-skia-bridge:test  — PASSED
:gpu-renderer:test        — PASSED
:kanvas:test              — PASSED
```

### Clean Diff
```
rtk git diff --check  — clean (no whitespace errors)
```

### No Remaining References
- `useLegacyGpuRaster` / `kanvas.rollback.legacy-gpu-raster`: 0 matches across codebase
- `settings.gradle.kts` includes `:gpu-raster` (module preserved)

## Concerns
- Option A (full module removal) is deferred to a later step per the plan
- ~30 validateKan* tasks in root build.gradle.kts still have `inputs.file(...)` references to deleted gpu-raster files (not evaluated during default build lifecycle, no breakage)
- `checkGpuRasterImageToolingNoAwt` task is now a no-op (references deleted `CrossBackendHarness.kt`); left as-is to preserve build graph consistency

## Deferred cleanup

The following are explicitly deferred to the agreed "later step" (Option A) per
`docs/superpowers/plans/2026-06-26-legacy-gpu-raster-decommission.md`.

### (a) Stale `inputs.file(...)` references in root `build.gradle.kts`

~30 custom verify tasks (`validateKan*`) still reference deleted `:gpu-raster`
files via `inputs.file(...)`. These tasks are outside the default `build`
lifecycle and do not cause build failures. Confirmed stale references (sample):

| Line | Stale reference |
|------|----------------|
| 3392 | `gpu-raster/src/test/.../testing/CrossBackendHarness.kt` |
| 5091 | `gpu-raster/src/main/.../WebGpuCoveragePlanSelector.kt` |
| 5092 | `gpu-raster/src/main/.../SkWebGpuDevice.kt` |
| 5110 | `gpu-raster/src/main/.../WebGpuCoveragePlanSelector.kt` |
| 5141 | `gpu-raster/src/test/.../testing/CrossBackendHarness.kt` |
| 5215 | `gpu-raster/src/main/.../WebGpuCoveragePlanSelector.kt` |
| 5216 | `gpu-raster/src/test/.../WebGpuCoveragePlanSelectorTest.kt` |
| 5256 | `gpu-raster/src/main/.../WebGpuCoveragePlanSelector.kt` |
| 5336 | `gpu-raster/src/main/.../WebGpuCoveragePlanSelector.kt` |
| 5337 | `gpu-raster/src/test/.../WebGpuCoveragePlanSelectorTest.kt` |
| 5491 | `gpu-raster/src/main/.../SkWebGpuGlyphAtlas.kt` |
| 5492 | `gpu-raster/src/test/.../SkWebGpuGlyphAtlasTest.kt` |
| 5573 | `gpu-raster/src/main/.../SkWebGpuDevice.kt` |
| 5632 | `gpu-raster/src/main/.../SkWebGpuGlyphAtlas.kt` |
| 5659 | `gpu-raster/src/main/.../SkWebGpuDevice.kt` |
| 5660 | `gpu-raster/src/main/.../SkWebGpuGlyphAtlas.kt` |
| 5889 | `gpu-raster/src/main/.../SkWebGpuDevice.kt` |
| 5900 | `gpu-raster/src/main/.../SkWebGpuDevice.kt` |

(Full list: 21 matches confirmed via `rtk rg -n "gpu-raster/src.*(SkWebGpuDevice|WebGpuCoveragePlanSelector|SkWebGpuGlyphAtlas|CrossBackendHarness)" build.gradle.kts`.)

These references are in custom `JavaExec` / verify tasks that are not triggered
by `build`, `check`, or `test`. Cleanup is deferred to the Option A relocation
phase.

### (b) Option A — relocate shared infra out of `:gpu-raster` and delete the module

The `:gpu-raster` module currently hosts:
- `tools/`: WgslValidationReport, WgslStrictValidationReport,
  WgslParserSmokeMain, GeneratedSolidRectWgsl, GeneratedLinearGradientWgsl,
  RuntimeEffectsLayoutV2Report, GpuInventoryFailureReport
- `BlendPlan.kt`
- `GpuRendererLegacyRetirementGates.kt`, `GpuRendererShadowParityGates.kt`
- `resources/shaders/*.wgsl`
- `resources/wgsl-diagnostics-allowlist.txt`

These must be relocated to `:gpu-renderer` before the `:gpu-raster` module
include can be removed from `settings.gradle.kts`. This includes repointing
all root `build.gradle.kts` task registrations and `inputs.file(...)` paths.
Full module removal is deferred to a later step per the decommission plan.
