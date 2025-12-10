package testing

import testing.skia.*

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
    registerGM(BigRectGM())
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
    // registerGM(AnalyticGradientsGM())
    // registerGM(ArithModeGM())
    // registerGM(AttributesGM())
    // registerGM(BatchedConvexPathsGM())
    // registerGM(BeziersGM())
    // registerGM(BicubicGM())
    // registerGM(BigMatrixGM())
    // registerGM(BitmapCopyGM())
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
        "AnalyticGradientsGM", "ArithModeGM", "AttributesGM", "BatchedConvexPathsGM",
        "BeziersGM", "BicubicGM", "BigMatrixGM", "BitmapCopyGM", "BitmapFiltersGM",
        "BitmapImageGM", "BitmapShaderGM", "BitmapTiledGM"
    )
    
    status["Level 2: Intermediate"] = TestCategoryStatus(
        total = level2Tests.size,
        implemented = 0,
        progress = 0
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