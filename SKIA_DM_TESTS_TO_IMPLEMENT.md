# Skia DM Tests to Implement in Kanvas

This document lists the Skia DM (Draw Module) tests that should be implemented in Kanvas, categorized by functionality and complexity.

## Total Tests: 436

## Priority Categories

### 🟢 Level 1: Basic Drawing (High Priority - Foundational)
These tests cover basic drawing operations that Kanvas should support first.

1. ✅ `aaclip.cpp` - Anti-aliased clipping (AaClipGM.kt)
2. ✅ `aarectmodes.cpp` - Anti-aliased rectangle drawing modes (AaRectModesGM.kt)
3. ✅ `aaxfermodes.cpp` - Anti-aliased transfer modes (AaXferModesGM.kt)
4. ✅ `addarc.cpp` - Arc drawing (AddArcGM.kt)
5. ✅ `alpha_image.cpp` - Alpha channel image handling (AlphaImageGM.kt)
6. ✅ `alphagradients.cpp` - Alpha gradients (AlphaGradientsGM.kt)
7. ✅ `arcofzorro.cpp` - Arc drawing patterns (ArcOfZorroGM.kt)
8. ✅ `arcto.cpp` - Arc-to path operations (ArcToGM.kt)
9. ✅ `bigrect.cpp` - Large rectangle drawing (BigRectGM.kt) - MOVED TO LEVEL 2
10. ✅ `bitmaprect.cpp` - Bitmap rectangle drawing (BitmapRectGM.kt)
11. ✅ `bleed.cpp` - Color bleeding tests (BleedGM.kt) - NEW
12. ✅ `circle_sizes.cpp` - Circle drawing with different sizes (CircleSizesGM.kt)
13. ✅ `clear_swizzle.cpp` - Clear operations (ClearSwizzleGM.kt)
14. ✅ `colorspace.cpp` - Color space handling (ColorspaceGM.kt) - NEW
15. ✅ `concavepaths.cpp` - Concave path drawing (ConcavePathsGM.kt) - NEW
16. ✅ `convexpaths.cpp` - Convex path drawing (ConvexPathsGM.kt)
17. ✅ `cubicpaths.cpp` - Cubic path drawing (CubicPathsGM.kt) - NEW
18. ✅ `dashing.cpp` - Dashed line drawing (DashingGM.kt) - NEW
19. ✅ `destcolor.cpp` - Destination color operations (DestColorGM.kt) - NEW
20. ✅ `fillrect_gradient.cpp` - Gradient-filled rectangles (FillRectGradientGM.kt) - NEW

### 🟡 Level 2: Intermediate Features (Medium Priority)
These tests cover more advanced features that build on basic drawing.

21. `3d.cpp` - 3D transformations
22. `aarecteffect.cpp` - Rectangle effects
23. `all_bitmap_configs.cpp` - All bitmap configurations
24. `analytic_gradients.cpp` - Analytic gradient calculations
25. `androidblendmodes.cpp` - Android-specific blend modes
26. `animated_gif.cpp` - Animated GIF handling
27. `anisotropic.cpp` - Anisotropic filtering
28. `arithmode.cpp` - Arithmetic blend modes
29. `attributes.cpp` - Path attributes
30. `backdrop.cpp` - Backdrop effects
31. `batchedconvexpaths.cpp` - Batched convex path drawing
32. `beziers.cpp` - Bezier curve drawing
33. `bicubic.cpp` - Bicubic interpolation
34. `bigmatrix.cpp` - Large transformation matrices
35. `bigtileimagefilter.cpp` - Large tile image filters
36. `bitmapcopy.cpp` - Bitmap copying
37. `bitmapfilters.cpp` - Bitmap filtering
38. `bitmapimage.cpp` - Bitmap image handling
39. `bitmapshader.cpp` - Bitmap shaders
40. `bitmaptiled.cpp` - Tiled bitmap drawing

### 🔴 Level 3: Advanced Features (Lower Priority)
These tests cover advanced features that may require significant implementation.

41. `animatedimageblurs.cpp` - Animated image blurs
42. `asyncrescaleandread.cpp` - Async image operations
43. `backdrop_imagefilter_croprect.cpp` - Backdrop image filter cropping
44. `bc1_transparency.cpp` - BC1 texture compression
45. `beziereffects.cpp` - Bezier curve effects
46. `bigblurs.cpp` - Large blur operations
47. `bigrrectaaeffect.cpp` - Large rounded rectangle effects
48. `bigtext.cpp` - Large text rendering
49. `bitmappremul.cpp` - Bitmap premultiplication
50. `bitmaprecttest.cpp` - Bitmap rectangle testing

### 🔵 Level 4: Complex/GPU Features (Future Priority)
These tests cover complex features, often GPU-related, that may not be immediately needed.

51. `blurs.cpp` - Various blur operations
52. `blurcircles.cpp` - Blurred circles
53. `blurimagevmask.cpp` - Blur with vector masks
54. `blurpositioning.cpp` - Blur positioning
55. `blurquickreject.cpp` - Blur quick rejection
56. `blurrect.cpp` - Blurred rectangles
57. `blurredclippedcircle.cpp` - Blurred clipped circles
58. `blurroundrect.cpp` - Blurred rounded rectangles
59. `blurtextsmallradii.cpp` - Blurred text with small radii
60. `bmpfilterqualityrepeat.cpp` - Bitmap filter quality and repeat

## Functional Categories

### 🎨 Basic Shapes & Paths
- `aaclip.cpp`
- `aarectmodes.cpp`
- `addarc.cpp`
- `arcofzorro.cpp`
- `arcto.cpp`
- `bigrect.cpp`
- `circle_sizes.cpp`
- `concavepaths.cpp`
- `convexpaths.cpp`
- `cubicpaths.cpp`

### 🖼️ Bitmap & Image Operations
- `alpha_image.cpp`
- `all_bitmap_configs.cpp`
- `bitmapcopy.cpp`
- `bitmapfilters.cpp`
- `bitmapimage.cpp`
- `bitmaprect.cpp`
- `bitmapshader.cpp`
- `bitmaptiled.cpp`
- `animated_gif.cpp`
- `animatedimageblurs.cpp`

### 🎨️ Gradients & Colors
- `alphagradients.cpp`
- `analytic_gradients.cpp`
- `colorspace.cpp`
- `fillrect_gradient.cpp`
- `arithmode.cpp`
- `destcolor.cpp`
- `bleed.cpp`
- `clear_swizzle.cpp`

### 🔄 Transformations & Effects
- `3d.cpp`
- `bigmatrix.cpp`
- `aarecteffect.cpp`
- `beziereffects.cpp`
- `backdrop.cpp`
- `batchedconvexpaths.cpp`
- `bicubic.cpp`
- `anisotropic.cpp`

### 📝 Text & Fonts
- `annotated_text.cpp`
- `attributes.cpp`
- `dftext.cpp`
- `fontmgr.cpp`
- `fontscaler.cpp`
- `glyph_pos.cpp`
- `lcdtext.cpp`
- `textblob.cpp`

### 🧩 Advanced Features
- `asyncrescaleandread.cpp`
- `bc1_transparency.cpp`
- `bigblurs.cpp`
- `bigrrectaaeffect.cpp`
- `bigtext.cpp`
- `bitmappremul.cpp`
- `blurcircles.cpp`
- `blurimagevmask.cpp`
- `blurpositioning.cpp`
- `blurrect.cpp`

## Implementation Strategy

### Phase 1: Foundational Tests (Level 1)
- Implement basic shape drawing
- Add rectangle and path operations
- Support basic clipping and anti-aliasing
- Add color and alpha handling

### Phase 2: Intermediate Features (Level 2)
- Add bitmap operations
- Implement gradients
- Add transformations
- Support basic effects

### Phase 3: Advanced Features (Level 3)
- Add animation support
- Implement advanced filters
- Add complex path operations
- Support advanced blending modes

### Phase 4: Complex Features (Level 4)
- Add GPU acceleration (future)
- Implement complex blur operations
- Add advanced image processing
- Support sophisticated effects

## Test Porting Process

1. **Analyze**: Examine the Skia C++ test code
2. **Translate**: Convert to Kotlin using Kanvas API
3. **Adapt**: Adjust for API differences
4. **Test**: Verify visual output
5. **Document**: Add comments and documentation

## Tracking

Use the following format to track progress:

```markdown
- [ ] aaclip.cpp - Anti-aliased clipping
- [ ] aarectmodes.cpp - Anti-aliased rectangle drawing modes
- [ ] aaxfermodes.cpp - Anti-aliased transfer modes
```

This provides a comprehensive roadmap for implementing Skia DM tests in Kanvas, starting with foundational features and progressing to more advanced capabilities.

## 📊 Implementation Progress

### Level 1 Tests Status
- **Total Tests**: 19
- **Implemented**: 19 ✅
- **Remaining**: 0 ❌
- **Progress**: 100%

### Level 2 Tests Status
- **Total Tests**: 12
- **Implemented**: 4 ✅
- **Remaining**: 8 ❌
- **Progress**: 33%

### Implemented Tests

#### Level 1 Tests
- ✅ AaClipGM.kt
- ✅ AaRectModesGM.kt
- ✅ AaXferModesGM.kt
- ✅ AddArcGM.kt
- ✅ AlphaImageGM.kt
- ✅ AlphaGradientsGM.kt
- ✅ ArcOfZorroGM.kt
- ✅ ArcToGM.kt
- ✅ BigRectGM.kt
- ✅ BitmapRectGM.kt
- ✅ CircleSizesGM.kt
- ✅ ClearSwizzleGM.kt
- ✅ ConvexPathsGM.kt
- ✅ BleedGM.kt (NEW)
- ✅ ColorspaceGM.kt (NEW)
- ✅ ConcavePathsGM.kt (NEW)
- ✅ CubicPathsGM.kt (NEW)
- ✅ DashingGM.kt (NEW)
- ✅ DestColorGM.kt (NEW)
- ✅ FillRectGradientGM.kt (NEW)

#### Level 2 Tests
- ✅ BeziersGM.kt (NEW) - Bézier curves test
- ✅ BatchedConvexPathsGM.kt (NEW) - Batched convex paths test
- ✅ BigRectGM.kt (NEW) - Big rectangle with clipping test
- ✅ ComplexPathsGM.kt (NEW) - Complex paths with transformations and gradients

### APIs Added
During this implementation phase, the following APIs were added:

1. **Color Space Handling** - Basic color space support in `ColorSpace.kt`
2. **Advanced Path Operations** - Cubic curve support and concave path rendering
3. **Dashed Line Drawing** - Basic dash simulation using `drawLine()` method
4. **Destination Color Operations** - Semi-transparent color blending support
5. **Gradient Fill Operations** - Enhanced shader integration for gradient-filled rectangles
6. **Color Bleeding Tests** - Basic edge handling and color mixing tests
7. **Line Drawing** - Added `drawLine()` method to Canvas class
8. **Random Number Generation** - Added `SkRandom` class for generating random values
9. **Bézier Curve Testing** - Added `BeziersGM` for testing quadratic and cubic Bézier curves
10. **Batched Convex Paths** - Added `BatchedConvexPathsGM` for testing complex path transformations
11. **Big Rectangle Clipping** - Added `BigRectGM` for testing large rectangle drawing with clipping
12. **Complex Path Operations** - Added `ComplexPathsGM` for testing advanced path operations with gradients and transformations

### Next Steps
1. Implement remaining Level 2 tests (intermediate features)
2. Add more advanced path effects and transformations
3. Implement proper dashing with PathEffect interface
4. Add visual comparison functionality for test validation
5. Expand color space support with proper conversions
6. Implement Level 3 tests (advanced features)