package testing

import testing.skia.AARectEffectGM
import testing.skia.AaClipGM
import testing.skia.AaRectModesGM
import testing.skia.AaXferModesGM
import testing.skia.AddArcGM
import testing.skia.AllBitmapConfigsGM
import testing.skia.AlphaGradientsGM
import testing.skia.AlphaImageGM
import testing.skia.AnalyticGradientsGM
import testing.skia.ArcOfZorroGM
import testing.skia.ArcToGM
import testing.skia.ArithmodeGM
import testing.skia.AttributesGM
import testing.skia.BatchedConvexPathsGM
import testing.skia.BeziersGM
import testing.skia.BicubicGM
import testing.skia.BigMatrixGM
import testing.skia.BigRectGM
import testing.skia.BitmapCopyGM
import testing.skia.BitmapRectGM
import testing.skia.BleedGM
import testing.skia.CircleSizesGM
import testing.skia.ClearSwizzleGM
import testing.skia.ColorspaceGM
import testing.skia.ConcavePathsGM
import testing.skia.ConvexPathsGM
import testing.skia.CubicPathsGM
import testing.skia.DashingGM
import testing.skia.DestColorGM
import testing.skia.FillRectGradientGM

/**
 * Central registration point for all Skia GM tests
 * This file automatically registers all implemented tests
 */
fun registerAllTests() {
    // Level 1: Basic Drawing Tests (High Priority)
    registerGM(AaClipGM())
    registerGM(AaRectModesGM())
    registerGM(AaXferModesGM())
    registerGM(AddArcGM())
    registerGM(AlphaImageGM())
    registerGM(AlphaGradientsGM())
    registerGM(ArcOfZorroGM())
    registerGM(ArcToGM())
    registerGM(BitmapRectGM())
    registerGM(CircleSizesGM())
    registerGM(ClearSwizzleGM())
    registerGM(ConvexPathsGM())
    
    // Newly implemented Level 1 tests
    registerGM(BleedGM())
    registerGM(ColorspaceGM())
    registerGM(ConcavePathsGM())
    registerGM(CubicPathsGM())
    registerGM(DashingGM())
    registerGM(DestColorGM())
    registerGM(FillRectGradientGM())
    
    // Level 2: Intermediate Tests (Medium Priority)
    // These will be implemented in future phases
    registerGM(BeziersGM())  // NEW: Bézier curves test
    registerGM(BatchedConvexPathsGM())  // NEW: Batched convex paths test
    registerGM(BigRectGM())  // NEW: Big rectangle with clipping test
    registerGM(BigMatrixGM())  // NEW: Large matrix transformations test
    registerGM(BitmapCopyGM())  // NEW: Bitmap copy between configurations test
    registerGM(AllBitmapConfigsGM())  // NEW: All bitmap configurations test
    registerGM(ArithmodeGM())  // NEW: Arithmetic blend modes test
    registerGM(AttributesGM())  // NEW: Path attributes test
    registerGM(AARectEffectGM())  // NEW: Anti-aliased rectangle effects test
    registerGM(AnalyticGradientsGM())  // NEW: Analytic gradients test
    registerGM(BicubicGM())  // NEW: Bicubic filtering test
    // registerGM(BitmapFiltersGM())
    // registerGM(BitmapImageGM())
    // registerGM(BitmapShaderGM())
    // registerGM(BitmapTiledGM())
    
    // Level 3: Advanced Tests (Lower Priority)
    // These will be implemented in future phases
    // registerGM(AnimatedImageBlursGM())
    // registerGM(AsyncRescaleAndReadGM())
    // registerGM(BackdropImageFilterCropRectGM())
    // registerGM(BC1TransparencyGM())
    // registerGM(BezierEffectsGM())
    // registerGM(BigBlursGM())
    // registerGM(BigRRectAaEffectGM())
    // registerGM(BigTextGM())
    // registerGM(BitmapPremulGM())
    // registerGM(BitmapRectTestGM())
    
    // Level 4: Complex/GPU Tests (Future Priority)
    // These will be implemented in future phases
    // registerGM(BlursGM())
    // registerGM(BlurCirclesGM())
    // registerGM(BlurImageVMaskGM())
    // registerGM(BlurPositioningGM())
    // registerGM(BlurQuickRejectGM())
    // registerGM(BlurRectGM())
    // registerGM(BlurredClippedCircleGM())
    // registerGM(BlurRoundRectGM())
    // registerGM(BlurTextSmallRadiiGM())
    // registerGM(BmpFilterQualityRepeatGM())
}

/**
 * Get the current implementation status of all test categories
 */
fun getTestImplementationStatus(): Map<String, TestCategoryStatus> {
    val status = mutableMapOf<String, TestCategoryStatus>()
    
    // Level 1: Basic Drawing Tests
    val level1Tests = listOf(
        "AaClipGM", "AaRectModesGM", "AaXferModesGM", "AddArcGM", "AlphaImageGM",
        "AlphaGradientsGM", "ArcOfZorroGM", "ArcToGM", "BigRectGM", "BitmapRectGM",
        "CircleSizesGM", "ClearSwizzleGM", "ConvexPathsGM", "BleedGM", "ColorspaceGM",
        "ConcavePathsGM", "CubicPathsGM", "DashingGM", "DestColorGM", "FillRectGradientGM"
    )
    
    val level1Implemented = listOf(
        "AaClipGM", "AaRectModesGM", "AaXferModesGM", "AddArcGM", "AlphaImageGM",
        "AlphaGradientsGM", "ArcOfZorroGM", "ArcToGM", "BigRectGM", "BitmapRectGM",
        "CircleSizesGM", "ClearSwizzleGM", "ConvexPathsGM", "BleedGM", "ColorspaceGM",
        "ConcavePathsGM", "CubicPathsGM", "DashingGM", "DestColorGM", "FillRectGradientGM"
    )
    
    status["Level 1: Basic Drawing"] = TestCategoryStatus(
        total = level1Tests.size,
        implemented = level1Implemented.size,
        progress = level1Implemented.size * 100 / level1Tests.size
    )
    
    // Level 2: Intermediate Tests
    val level2Tests = listOf(
        "BeziersGM", "BatchedConvexPathsGM", "BigMatrixGM", "AnalyticGradientsGM", "ArithModeGM", "AttributesGM",
        "BicubicGM", "BitmapCopyGM", "BitmapFiltersGM",
        "BitmapImageGM", "BitmapShaderGM", "BitmapTiledGM"
    )
    
    val level2Implemented = listOf("BeziersGM", "BatchedConvexPathsGM", "BigMatrixGM", "BitmapCopyGM", "AllBitmapConfigsGM", "ArithmodeGM", "AttributesGM")
    
    status["Level 2: Intermediate"] = TestCategoryStatus(
        total = level2Tests.size,
        implemented = level2Implemented.size,
        progress = level2Implemented.size * 100 / level2Tests.size
    )
    
    // Level 3: Advanced Tests
    val level3Tests = listOf(
        "AnimatedImageBlursGM", "AsyncRescaleAndReadGM", "BackdropImageFilterCropRectGM",
        "BC1TransparencyGM", "BezierEffectsGM", "BigBlursGM", "BigRRectAaEffectGM",
        "BigTextGM", "BitmapPremulGM", "BitmapRectTestGM"
    )
    
    status["Level 3: Advanced"] = TestCategoryStatus(
        total = level3Tests.size,
        implemented = 0,
        progress = 0
    )
    
    // Level 4: Complex/GPU Tests
    val level4Tests = listOf(
        "BlursGM", "BlurCirclesGM", "BlurImageVMaskGM", "BlurPositioningGM",
        "BlurQuickRejectGM", "BlurRectGM", "BlurredClippedCircleGM", "BlurRoundRectGM",
        "BlurTextSmallRadiiGM", "BmpFilterQualityRepeatGM"
    )
    
    status["Level 4: Complex/GPU"] = TestCategoryStatus(
        total = level4Tests.size,
        implemented = 0,
        progress = 0
    )
    
    return status
}

data class TestCategoryStatus(
    val total: Int,
    val implemented: Int,
    val progress: Int
)