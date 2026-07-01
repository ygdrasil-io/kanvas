You are adding the 29 missing effect subtypes to Kanvas to complete the sealed interface hierarchies.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

Read the current files first to see existing patterns:
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ColorFilter.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/MaskFilter.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/PathEffect.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ImageFilter.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/BlendMode.kt
- kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Paint.kt (for existing styles)

All subtypes are `data class` or `data object` inside `sealed interface { }`. Follow the exact same patterns.

### PART A: Shader.kt — add 4 subtypes + 1 enum + gradient param

**New subtypes** (add to sealed interface):
```kotlin
data class PerlinNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader
data class FractalNoise(val baseX: Float, val baseY: Float, val numOctaves: Int, val seed: Int, val tileSize: Size?) : Shader
data class WithWorkingColorSpace(val shader: Shader, val interpolation: ColorSpaceInterpolation) : Shader
data class CoordClamp(val shader: Shader, val subset: Rect) : Shader
```

**New enum** (add to Shader.kt, before the sealed interface):
```kotlin
enum class ColorSpaceInterpolation { SRGB, LINEAR, OKLAB, HSL, OKLCH }
```

**Gradient parameter**: Add `val interpolation: ColorSpaceInterpolation = ColorSpaceInterpolation.SRGB` to all 4 gradient subtypes: LinearGradient, RadialGradient, SweepGradient, ConicalGradient. Put it as the LAST parameter (before the closing paren).

### PART B: ColorFilter.kt — add 5 subtypes

```kotlin
data class HSLAMatrix(val values: FloatArray) : ColorFilter  // 20 floats, operates in HSLA space
data class Lerp(val t: Float, val dst: ColorFilter, val src: ColorFilter) : ColorFilter
data object HighContrast : ColorFilter
data object Luma : ColorFilter
data object Overdraw : ColorFilter
```

### PART C: MaskFilter.kt — add 2 subtypes

```kotlin
data class Shader(val shader: Shader) : MaskFilter   // shader-based alpha mask
data class Table(val table: UByteArray) : MaskFilter  // 256-entry lookup table
```

### PART D: PathEffect.kt — add 3 subtypes + 1 enum

**New enum** (add before sealed interface):
```kotlin
enum class Path1DStyle { TRANSLATE, ROTATE, MORPH }
```

**New subtypes**:
```kotlin
data class Path1D(val path: Path, val advance: Float, val phase: Float, val style: Path1DStyle) : PathEffect
data class Path2D(val matrix: Matrix33, val path: Path) : PathEffect
data class Trim(val start: Float, val stop: Float) : PathEffect  // 0.0 to 1.0 fraction of path
```

### PART E: ImageFilter.kt — add 15 subtypes + 1 enum

**New enum** (add before sealed interface):
```kotlin
enum class ColorChannel { R, G, B, A }
```

**Morphology (2):**
```kotlin
data class Dilate(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter
data class Erode(val radiusX: Float, val radiusY: Float, val input: ImageFilter?) : ImageFilter
```

**Lighting — Diffuse (3) and Specular (3):**
```kotlin
data class DistantLitDiffuse(val direction: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
data class PointLitDiffuse(val location: Point, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
data class SpotLitDiffuse(val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float, val lightColor: Color, val surfaceScale: Float, val kd: Float, val input: ImageFilter?) : ImageFilter
data class DistantLitSpecular(val direction: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
data class PointLitSpecular(val location: Point, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
data class SpotLitSpecular(val location: Point, val target: Point, val specularExponent: Float, val cutoffAngle: Float, val lightColor: Color, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: ImageFilter?) : ImageFilter
```

**Transform/Compositing (3):**
```kotlin
data class Offset(val dx: Float, val dy: Float, val input: ImageFilter?) : ImageFilter
data class Tile(val src: Rect, val dst: Rect, val input: ImageFilter?) : ImageFilter
data class Merge(val inputs: List<ImageFilter>) : ImageFilter
```

**Advanced (3):**
```kotlin
data class DisplacementMap(val xChannelSelector: ColorChannel, val yChannelSelector: ColorChannel, val scale: Float, val displacement: ImageFilter, val input: ImageFilter?) : ImageFilter
data class Magnifier(val src: Rect, val zoom: Float, val inset: Float, val input: ImageFilter?) : ImageFilter
data class MatrixConvolution(val kernelSize: Size, val kernel: FloatArray, val gain: Float, val bias: Float, val kernelOffset: Point, val tileMode: TileMode, val convolveAlpha: Boolean, val input: ImageFilter?) : ImageFilter
```

### PART F: Update GPURenderer.kt

In `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`, the exhaustive `when` on these sealed types may need new branches. If any compilation errors appear, add `diagnostics.degrade("unsupported effect", op, "not_yet_implemented")` for each new subtype.

### PART G: Tests

Create `kanvas/src/test/kotlin/org/graphiks/kanvas/paint/EffectsExpansionTest.kt`:

Test that each new subtype can be instantiated:
```kotlin
@Test fun `PerlinNoise constructs`() {
    val s = Shader.PerlinNoise(0f, 0f, 3, 42, null)
    assertTrue(s is Shader)
}
@Test fun `FractalNoise constructs`() { ... }
@Test fun `HSLAMatrix constructs`() { ... }
// ... one test per new subtype
```

Also test the new enums:
```kotlin
@Test fun `ColorSpaceInterpolation has 5 values`() { assertEquals(5, ColorSpaceInterpolation.entries.size) }
@Test fun `Path1DStyle has 3 values`() { assertEquals(3, Path1DStyle.entries.size) }
@Test fun `ColorChannel has 4 values`() { assertEquals(4, ColorChannel.entries.size) }
```

### VERIFICATION

```bash
./gradlew :kanvas:compileKotlin 2>&1 | tail -5
./gradlew :kanvas:test --tests "org.graphiks.kanvas.paint.EffectsExpansionTest" 2>&1 | tail -15
```

Commit: `git add -A && git commit -m "feat(kanvas): Phase 5 effects — 29 missing sealed subtypes + 4 enums"`
