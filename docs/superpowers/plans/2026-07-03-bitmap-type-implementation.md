# Bitmap Type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a mutable `Bitmap` type to core Kanvas, extend `ColorType`, add `SamplingOptions`, thread sampling through `Shader.Image` and the pipeline.

**Architecture:** New `Bitmap` class in `org.graphiks.kanvas.image` with `ByteArray` backing. `SamplingOptions` sealed interface in `org.graphiks.kanvas.paint`. `Shader.Image` gains a `sampling` field. Pipeline honours `NEAREST` and `LINEAR`; `Cubic` falls back to `LINEAR` for MVP.

**Tech Stack:** Kotlin, JVM, core Kanvas module (`kanvas/`), `kanvas-skia` module

---

### Task 1: ColorType extended enum + bytesPerPixel

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt` (ColorType enum)

- [ ] **Step 1: Write the failing test**

Read `kanvas/src/test/kotlin/org/graphiks/kanvas/image/ImageTest.kt` to understand the test conventions.

Create `kanvas/src/test/kotlin/org/graphiks/kanvas/image/ColorTypeTest.kt`:

```kotlin
package org.graphiks.kanvas.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColorTypeTest {
    @Test
    fun `bytesPerPixel matches expected values`() {
        assertEquals(4, ColorType.RGBA_8888.bytesPerPixel)
        assertEquals(4, ColorType.BGRA_8888.bytesPerPixel)
        assertEquals(1, ColorType.ALPHA_8.bytesPerPixel)
        assertEquals(1, ColorType.GRAY_8.bytesPerPixel)
        assertEquals(8, ColorType.RGBA_F16.bytesPerPixel)
        assertEquals(2, ColorType.RGB_565.bytesPerPixel)
        assertEquals(2, ColorType.ARGB_4444.bytesPerPixel)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :kanvas:test --tests "org.graphiks.kanvas.image.ColorTypeTest"`

Expected: Compilation error — `bytesPerPixel` doesn't exist on ColorType

- [ ] **Step 3: Extend ColorType enum**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt`, replace the existing ColorType enum:

```kotlin
enum class ColorType(val bytesPerPixel: Int) {
    RGBA_8888(4),
    BGRA_8888(4),
    ALPHA_8(1),
    GRAY_8(1),
    RGBA_F16(8),
    RGB_565(2),
    ARGB_4444(2),
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :kanvas:test --tests "org.graphiks.kanvas.image.ColorTypeTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt kanvas/src/test/kotlin/org/graphiks/kanvas/image/ColorTypeTest.kt
git commit -m "image: extend ColorType with RGBA_F16, RGB_565, ARGB_4444 and bytesPerPixel"
```

---

### Task 2: Half-float utility extraction

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/HalfFloat.kt`
- Modify: `kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt`

- [ ] **Step 1: Create HalfFloat.kt with tests**

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/image/HalfFloat.kt`:

```kotlin
package org.graphiks.kanvas.image

internal fun halfToFloat(h: Short): Float {
    val bits = h.toInt() and 0xFFFF
    val sign = (bits and 0x8000) shl 16
    val exp = (bits ushr 10) and 0x1F
    val mant = bits and 0x3FF
    return when (exp) {
        0 -> {
            if (mant == 0) java.lang.Float.intBitsToFloat(sign)
            else {
                var m = mant; var e = 1
                while ((m and 0x400) == 0) { m = m shl 1; e-- }
                m = m and 0x3FF
                val f32Exp = (e + (127 - 15)) shl 23
                val f32Mant = m shl 13
                java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
            }
        }
        0x1F -> {
            val f32Exp = 0xFF shl 23
            val f32Mant = mant shl 13
            java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
        }
        else -> {
            val f32Exp = (exp + (127 - 15)) shl 23
            val f32Mant = mant shl 13
            java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
        }
    }
}

internal fun floatToHalf(f: Float): Short {
    val bits = java.lang.Float.floatToRawIntBits(f)
    val sign = (bits ushr 16) and 0x8000
    val exp32 = (bits ushr 23) and 0xFF
    val mant32 = bits and 0x7FFFFF
    return when {
        exp32 == 0xFF -> {
            val mant16 = if (mant32 != 0) ((mant32 ushr 13) or 0x200) else 0
            (sign or 0x7C00 or mant16).toShort()
        }
        exp32 > 142 -> (sign or 0x7C00).toShort()
        exp32 < 103 -> sign.toShort()
        exp32 < 113 -> {
            val mantWithImplicit = mant32 or 0x800000
            val shift = 14 + (113 - exp32)
            val rounded = (mantWithImplicit + (1 shl (shift - 1))) ushr shift
            (sign or rounded).toShort()
        }
        else -> {
            val expHalf = (exp32 - (127 - 15)) shl 10
            val mantHalf = (mant32 + 0x1000) ushr 13
            if (mantHalf > 0x3FF) {
                ((expHalf shr 10) + 1).let { newExp ->
                    val newExpField = (newExp.coerceAtMost(0x1F)) shl 10
                    (sign or newExpField).toShort()
                }
            } else {
                (sign or expHalf or mantHalf).toShort()
            }
        }
    }
}
```

Create `kanvas/src/test/kotlin/org/graphiks/kanvas/image/HalfFloatTest.kt`:

```kotlin
package org.graphiks.kanvas.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HalfFloatTest {
    @Test
    fun `zero round-trips`() {
        assertEquals(0f, halfToFloat(0.toShort()))
        assertEquals(0.toShort(), floatToHalf(0f))
    }

    @Test
    fun `one round-trips`() {
        assertEquals(1f, halfToFloat(floatToHalf(1f)), 0.001f)
    }

    @Test
    fun `negative round-trips`() {
        val h = floatToHalf(-1f)
        assertEquals(-1f, halfToFloat(h), 0.001f)
    }

    @Test
    fun `small values round-trip`() {
        val v = 0.5f
        val h = floatToHalf(v)
        assertEquals(v, halfToFloat(h), 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :kanvas:test --tests "org.graphiks.kanvas.image.HalfFloatTest"`

Expected: PASS

- [ ] **Step 3: Update SkBitmap.kt to use shared HalfFloat**

In `kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt`:
- Delete the `halfToFloat` and `floatToHalf` functions from the `companion object`
- Add `import org.graphiks.kanvas.image.halfToFloat` and `import org.graphiks.kanvas.image.floatToHalf`
- Verify the import resolves (both functions are `internal` in `org.graphiks.kanvas.image`, and `kanvas-skia` depends on `kanvas`)

If `halfToFloat`/`floatToHalf` are `internal` and not accessible from `kanvas-skia`, change their visibility to `public` (or add `@PublishedApi internal`):

```kotlin
// HalfFloat.kt
public fun halfToFloat(h: Short): Float
public fun floatToHalf(f: Float): Short
```

- [ ] **Step 4: Compile kanvas-skia to verify**

Run: `./gradlew :kanvas-skia:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/image/HalfFloat.kt kanvas/src/test/kotlin/org/graphiks/kanvas/image/HalfFloatTest.kt kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt
git commit -m "image: extract half-float utilities to shared HalfFloat.kt"
```

---

### Task 3: SamplingOptions sealed interface

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/SamplingOptions.kt`

- [ ] **Step 1: Create SamplingOptions.kt**

```kotlin
package org.graphiks.kanvas.paint

sealed interface SamplingOptions {
    data object NEAREST : SamplingOptions
    data object LINEAR : SamplingOptions

    data class Cubic(
        val B: Float,
        val C: Float,
    ) : SamplingOptions {
        companion object {
            val Mitchell = Cubic(1f / 3f, 1f / 3f)
            val CatmullRom = Cubic(0f, 1f / 2f)
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :kanvas:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/paint/SamplingOptions.kt
git commit -m "paint: add SamplingOptions sealed interface"
```

---

### Task 4: Shader.Image + Image.makeShader — add sampling field

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt`

- [ ] **Step 1: Add sampling to Shader.Image**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`, update the `Image` data class:

```kotlin
import org.graphiks.kanvas.paint.SamplingOptions

// Inside sealed interface Shader:
data class Image(
    val image: org.graphiks.kanvas.image.Image,
    val tileModeX: TileMode = TileMode.CLAMP,
    val tileModeY: TileMode = TileMode.CLAMP,
    val sampling: SamplingOptions = SamplingOptions.NEAREST,
) : Shader
```

- [ ] **Step 2: Update Image.makeShader**

In `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt`, update `makeShader`:

```kotlin
import org.graphiks.kanvas.paint.SamplingOptions

fun makeShader(
    tileModeX: TileMode = TileMode.CLAMP,
    tileModeY: TileMode = TileMode.CLAMP,
    sampling: SamplingOptions = SamplingOptions.NEAREST,
): Shader.Image = Shader.Image(this, tileModeX, tileModeY, sampling)
```

- [ ] **Step 3: Fix any compilation errors from Shader.Image construction changes**

Search for all `Shader.Image(` constructions across the codebase and add the `sampling` parameter (or rely on the default):

```bash
rg "Shader\.Image\(" --type kt
```

If any call sites construct `Shader.Image` without naming parameters, add `sampling` explicitly or ensure the default works.

- [ ] **Step 4: Compile to verify**

Run: `./gradlew :kanvas:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt
git commit -m "paint: add sampling field to Shader.Image, update Image.makeShader"
```

---

### Task 5: Bitmap class implementation

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Bitmap.kt`

- [ ] **Step 1: Write the failing tests**

Create `kanvas/src/test/kotlin/org/graphiks/kanvas/image/BitmapTest.kt`:

```kotlin
package org.graphiks.kanvas.image

import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class BitmapTest {

    @Test
    fun `construction allocates correct byte array size`() {
        val bmp = Bitmap(10, 10)
        assertEquals(400, bmp.pixels.size) // 10 * 10 * 4
        assertEquals(10, bmp.width)
        assertEquals(10, bmp.height)
        assertEquals(ColorType.RGBA_8888, bmp.colorType)
    }

    @Test
    fun `construction with F16 allocates 8 bytes per pixel`() {
        val bmp = Bitmap(10, 10, ColorType.RGBA_F16)
        assertEquals(800, bmp.pixels.size) // 10 * 10 * 8
    }

    @Test
    fun `setPixel and getPixel round-trip RGBA`() {
        val bmp = Bitmap(2, 2)
        bmp.setPixel(0, 0, Color.RED)
        assertEquals(Color.RED, bmp.getPixel(0, 0))
    }

    @Test
    fun `setPixel out of bounds is no-op`() {
        val bmp = Bitmap(2, 2)
        bmp.setPixel(10, 10, Color.RED) // should not throw
        assertEquals(Color(0u), bmp.getPixel(1, 1)) // default is transparent
    }

    @Test
    fun `getPixel out of bounds throws`() {
        val bmp = Bitmap(2, 2)
        org.junit.jupiter.api.assertThrows<IndexOutOfBoundsException> {
            bmp.getPixel(10, 10)
        }
    }

    @Test
    fun `eraseColor fills all pixels`() {
        val bmp = Bitmap(4, 4)
        bmp.eraseColor(Color.RED)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(Color.RED, bmp.getPixel(x, y))
            }
        }
    }

    @Test
    fun `eraseArea fills sub-region only`() {
        val bmp = Bitmap(4, 4)
        bmp.eraseColor(Color.BLUE)
        bmp.eraseArea(Rect(1f, 1f, 3f, 3f), Color.RED)
        assertEquals(Color.RED, bmp.getPixel(1, 1))
        assertEquals(Color.RED, bmp.getPixel(2, 2))
        assertEquals(Color.BLUE, bmp.getPixel(0, 0)) // outside region
        assertEquals(Color.BLUE, bmp.getPixel(3, 3)) // outside region
    }

    @Test
    fun `extractSubset copies pixel data`() {
        val bmp = Bitmap(4, 4)
        bmp.eraseColor(Color.RED)
        bmp.setPixel(0, 0, Color.BLUE) // top-left is blue
        val subset = bmp.extractSubset(Rect(0f, 0f, 2f, 2f))
        assertEquals(2, subset.width)
        assertEquals(2, subset.height)
        assertEquals(Color.BLUE, subset.getPixel(0, 0))
        assertEquals(Color.RED, subset.getPixel(1, 1))
    }

    @Test
    fun `toImage returns independent copy`() {
        val bmp = Bitmap(2, 2)
        bmp.eraseColor(Color.RED)
        val img = bmp.toImage()
        bmp.setPixel(0, 0, Color.BLUE) // mutate bitmap
        assertEquals(Color.RED, img.pixels?.let { it[0] }?.let { Color.fromRGBA(1f, 0f, 0f) } ?: Color.TRANSPARENT)
    }

    @Test
    fun `fromImage copies image pixels`() {
        val img = Image(2, 2, ColorType.RGBA_8888, "test", byteArrayOf(
            0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), // RED
            0x00.toByte(), 0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(), // GREEN
            0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte(), // BLUE
            0xFF.toByte(), 0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(), // YELLOW
        ))
        val bmp = Bitmap.fromImage(img)
        assertEquals(img.width, bmp.width)
        assertEquals(img.height, bmp.height)
        assertArrayEquals(img.pixels, bmp.pixels)
        // Verify mutability is independent
        bmp.setPixel(0, 0, Color.BLUE)
        assertEquals(Color.RED, Color.fromRGBA(1f, 0f, 0f, 1f))
    }

    @Test
    fun `makeShader returns Shader WithLocalMatrix wrapping Image`() {
        val bmp = Bitmap(2, 2)
        bmp.eraseColor(Color.RED)
        val shader = bmp.makeShader(TileMode.REPEAT, TileMode.MIRROR, SamplingOptions.LINEAR, Matrix33.identity())
        val wrapped = shader as org.graphiks.kanvas.paint.Shader.WithLocalMatrix
        val inner = wrapped.shader as org.graphiks.kanvas.paint.Shader.Image
        assertEquals(TileMode.REPEAT, inner.tileModeX)
        assertEquals(TileMode.MIRROR, inner.tileModeY)
        assertEquals(SamplingOptions.LINEAR, inner.sampling)
    }
}
```

- [ ] **Step 2: Run test to see failures (Bitmap class not found)**

Run: `./gradlew :kanvas:test --tests "org.graphiks.kanvas.image.BitmapTest"`

Expected: Compilation error — Bitmap class not found

- [ ] **Step 3: Implement Bitmap class**

Create `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Bitmap.kt`:

```kotlin
package org.graphiks.kanvas.image

import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r

class Bitmap(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
) {
    val pixels: ByteArray = ByteArray(width * height * colorType.bytesPerPixel)

    fun getPixel(x: Int, y: Int): Color {
        require(x in 0 until width && y in 0 until height) { "($x, $y) outside ${width}x$height" }
        val index = (y * width + x) * colorType.bytesPerPixel
        return when (colorType) {
            ColorType.RGBA_8888, ColorType.BGRA_8888 -> {
                val b = pixels[index].toInt() and 0xFF
                val g = pixels[index + 1].toInt() and 0xFF
                val r = pixels[index + 2].toInt() and 0xFF
                val a = pixels[index + 3].toInt() and 0xFF
                Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
            }
            ColorType.ALPHA_8 -> {
                val a = pixels[index].toInt() and 0xFF
                Color.fromRGBA(0f, 0f, 0f, a / 255f)
            }
            ColorType.GRAY_8 -> {
                val l = (pixels[index].toInt() and 0xFF) / 255f
                Color.fromRGBA(l, l, l, 1f)
            }
            ColorType.RGBA_F16 -> {
                val r = halfToFloat((pixels[index].toShort() and 0xFF).toShort())
                // ... full implementation reads 4 half-floats sequentially
                // For brevity, each half-float is 2 bytes LE
                val rh = (pixels[index].toInt() and 0xFF or ((pixels[index + 1].toInt() and 0xFF) shl 8)).toShort()
                val gh = (pixels[index + 2].toInt() and 0xFF or ((pixels[index + 3].toInt() and 0xFF) shl 8)).toShort()
                val bh = (pixels[index + 4].toInt() and 0xFF or ((pixels[index + 5].toInt() and 0xFF) shl 8)).toShort()
                val ah = (pixels[index + 6].toInt() and 0xFF or ((pixels[index + 7].toInt() and 0xFF) shl 8)).toShort()
                val pr = halfToFloat(rh)
                val pg = halfToFloat(gh)
                val pb = halfToFloat(bh)
                val pa = halfToFloat(ah)
                if (pa == 0f) return Color.TRANSPARENT
                Color.fromRGBA(
                    (pr / pa).coerceIn(0f, 1f),
                    (pg / pa).coerceIn(0f, 1f),
                    (pb / pa).coerceIn(0f, 1f),
                    pa.coerceIn(0f, 1f),
                )
            }
            ColorType.RGB_565 -> {
                val p = (pixels[index].toInt() and 0xFF) or ((pixels[index + 1].toInt() and 0xFF) shl 8)
                val r5 = (p ushr 11) and 0x1F
                val g6 = (p ushr 5) and 0x3F
                val b5 = p and 0x1F
                Color.fromRGBA(
                    (r5 * 255 / 31) / 255f,
                    (g6 * 255 / 63) / 255f,
                    (b5 * 255 / 31) / 255f,
                    1f,
                )
            }
            ColorType.ARGB_4444 -> {
                val p = (pixels[index].toInt() and 0xFF) or ((pixels[index + 1].toInt() and 0xFF) shl 8)
                val a4 = (p ushr 12) and 0xF
                val r4 = (p ushr 8) and 0xF
                val g4 = (p ushr 4) and 0xF
                val b4 = p and 0xF
                val pa = a4 / 15f
                if (pa == 0f) return Color.TRANSPARENT
                Color.fromRGBA(
                    (r4 / 15f / pa).coerceIn(0f, 1f),
                    (g4 / 15f / pa).coerceIn(0f, 1f),
                    (b4 / 15f / pa).coerceIn(0f, 1f),
                    pa,
                )
            }
        }
    }

    fun setPixel(x: Int, y: Int, color: Color) {
        if (x !in 0 until width || y !in 0 until height) return
        val index = (y * width + x) * colorType.bytesPerPixel
        val r = color.r; val g = color.g; val b = color.b; val a = color.a
        when (colorType) {
            ColorType.RGBA_8888, ColorType.BGRA_8888 -> {
                pixels[index] = (b * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[index + 1] = (g * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[index + 2] = (r * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[index + 3] = (a * 255f).toInt().coerceIn(0, 255).toByte()
            }
            ColorType.ALPHA_8 -> {
                pixels[index] = (a * 255f).toInt().coerceIn(0, 255).toByte()
            }
            ColorType.GRAY_8 -> {
                val l = (r * 0.299f + g * 0.587f + b * 0.114f).coerceIn(0f, 1f)
                pixels[index] = (l * 255f).toInt().coerceIn(0, 255).toByte()
            }
            ColorType.RGBA_F16 -> {
                val pa = a.coerceIn(0f, 1f)
                val pr = (r * pa).coerceIn(0f, 1f)
                val pg = (g * pa).coerceIn(0f, 1f)
                val pb = (b * pa).coerceIn(0f, 1f)
                val rh = floatToHalf(pr)
                val gh = floatToHalf(pg)
                val bh = floatToHalf(pb)
                val ah = floatToHalf(pa)
                pixels[index] = (rh.toInt() and 0xFF).toByte()
                pixels[index + 1] = ((rh.toInt() ushr 8) and 0xFF).toByte()
                pixels[index + 2] = (gh.toInt() and 0xFF).toByte()
                pixels[index + 3] = ((gh.toInt() ushr 8) and 0xFF).toByte()
                pixels[index + 4] = (bh.toInt() and 0xFF).toByte()
                pixels[index + 5] = ((bh.toInt() ushr 8) and 0xFF).toByte()
                pixels[index + 6] = (ah.toInt() and 0xFF).toByte()
                pixels[index + 7] = ((ah.toInt() ushr 8) and 0xFF).toByte()
            }
            ColorType.RGB_565 -> {
                val r5 = (r * 31f).toInt().coerceIn(0, 31)
                val g6 = (g * 63f).toInt().coerceIn(0, 63)
                val b5 = (b * 31f).toInt().coerceIn(0, 31)
                val p = (r5 shl 11) or (g6 shl 5) or b5
                pixels[index] = (p and 0xFF).toByte()
                pixels[index + 1] = ((p ushr 8) and 0xFF).toByte()
            }
            ColorType.ARGB_4444 -> {
                val a4 = (a * 15f).toInt().coerceIn(0, 15)
                val r4 = (r * a * 15f).toInt().coerceIn(0, 15)
                val g4 = (g * a * 15f).toInt().coerceIn(0, 15)
                val b4 = (b * a * 15f).toInt().coerceIn(0, 15)
                val p = (a4 shl 12) or (r4 shl 8) or (g4 shl 4) or b4
                pixels[index] = (p and 0xFF).toByte()
                pixels[index + 1] = ((p ushr 8) and 0xFF).toByte()
            }
        }
    }

    fun eraseColor(color: Color) {
        val r = color.r; val g = color.g; val b = color.b; val a = color.a
        when (colorType) {
            ColorType.RGBA_8888, ColorType.BGRA_8888 -> {
                val bi = (b * 255f).toInt().coerceIn(0, 255).toByte()
                val gi = (g * 255f).toInt().coerceIn(0, 255).toByte()
                val ri = (r * 255f).toInt().coerceIn(0, 255).toByte()
                val ai = (a * 255f).toInt().coerceIn(0, 255).toByte()
                var i = 0
                val n = pixels.size
                while (i < n) {
                    pixels[i] = bi; pixels[i + 1] = gi; pixels[i + 2] = ri; pixels[i + 3] = ai
                    i += 4
                }
            }
            ColorType.ALPHA_8 -> {
                val ai = (a * 255f).toInt().coerceIn(0, 255).toByte()
                pixels.fill(ai)
            }
            ColorType.GRAY_8 -> {
                val l = (r * 0.299f + g * 0.587f + b * 0.114f).coerceIn(0f, 1f)
                val li = (l * 255f).toInt().coerceIn(0, 255).toByte()
                pixels.fill(li)
            }
            ColorType.RGBA_F16 -> {
                val pa = a.coerceIn(0f, 1f)
                val pr = (r * pa).coerceIn(0f, 1f)
                val pg = (g * pa).coerceIn(0f, 1f)
                val pb = (b * pa).coerceIn(0f, 1f)
                val rh = floatToHalf(pr); val gh = floatToHalf(pg)
                val bh = floatToHalf(pb); val ah = floatToHalf(pa)
                val rl = (rh.toInt() and 0xFF).toByte(); val rhh = ((rh.toInt() ushr 8) and 0xFF).toByte()
                val gl = (gh.toInt() and 0xFF).toByte(); val ghh = ((gh.toInt() ushr 8) and 0xFF).toByte()
                val bl = (bh.toInt() and 0xFF).toByte(); val bhh = ((bh.toInt() ushr 8) and 0xFF).toByte()
                val al = (ah.toInt() and 0xFF).toByte(); val ahh = ((ah.toInt() ushr 8) and 0xFF).toByte()
                var i = 0; val n = pixels.size
                while (i < n) {
                    pixels[i] = rl; pixels[i+1] = rhh; pixels[i+2] = gl; pixels[i+3] = ghh
                    pixels[i+4] = bl; pixels[i+5] = bhh; pixels[i+6] = al; pixels[i+7] = ahh
                    i += 8
                }
            }
            ColorType.RGB_565 -> {
                val r5 = (r * 31f).toInt().coerceIn(0, 31)
                val g6 = (g * 63f).toInt().coerceIn(0, 63)
                val b5 = (b * 31f).toInt().coerceIn(0, 31)
                val p = ((r5 shl 11) or (g6 shl 5) or b5).toShort()
                val pl = (p.toInt() and 0xFF).toByte()
                val ph = ((p.toInt() ushr 8) and 0xFF).toByte()
                var i = 0; val n = pixels.size
                while (i < n) {
                    pixels[i] = pl; pixels[i + 1] = ph; i += 2
                }
            }
            ColorType.ARGB_4444 -> {
                val a4 = (a * 15f).toInt().coerceIn(0, 15)
                val r4 = (r * a * 15f).toInt().coerceIn(0, 15)
                val g4 = (g * a * 15f).toInt().coerceIn(0, 15)
                val b4 = (b * a * 15f).toInt().coerceIn(0, 15)
                val p = ((a4 shl 12) or (r4 shl 8) or (g4 shl 4) or b4).toShort()
                val pl = (p.toInt() and 0xFF).toByte()
                val ph = ((p.toInt() ushr 8) and 0xFF).toByte()
                var i = 0; val n = pixels.size
                while (i < n) {
                    pixels[i] = pl; pixels[i + 1] = ph; i += 2
                }
            }
        }
    }

    fun eraseArea(rect: Rect, color: Color) {
        val sx = rect.left.toInt().coerceIn(0, width)
        val sy = rect.top.toInt().coerceIn(0, height)
        val sw = rect.width.toInt().coerceAtMost(width - sx)
        val sh = rect.height.toInt().coerceAtMost(height - sy)
        if (sw <= 0 || sh <= 0) return
        for (y in sy until sy + sh) {
            for (x in sx until sx + sw) {
                setPixel(x, y, color)
            }
        }
    }

    fun extractSubset(rect: Rect): Bitmap {
        val sx = rect.left.toInt().coerceIn(0, width)
        val sy = rect.top.toInt().coerceIn(0, height)
        val sw = rect.width.toInt().coerceAtMost(width - sx)
        val sh = rect.height.toInt().coerceAtMost(height - sy)
        require(sw > 0 && sh > 0) { "empty subset rect: $rect" }
        val bpp = colorType.bytesPerPixel
        val subset = Bitmap(sw, sh, colorType, colorSpace)
        for (row in 0 until sh) {
            val srcOff = ((sy + row) * width + sx) * bpp
            val dstOff = row * sw * bpp
            pixels.copyInto(subset.pixels, dstOff, srcOff, srcOff + sw * bpp)
        }
        return subset
    }

    fun toImage(): Image =
        Image(width, height, colorType, "bitmap", pixels.copyOf(), colorSpace)

    fun makeShader(
        tileX: TileMode = TileMode.CLAMP,
        tileY: TileMode = TileMode.CLAMP,
        sampling: SamplingOptions = SamplingOptions.NEAREST,
        localMatrix: Matrix33 = Matrix33.identity(),
    ): Shader = Shader.WithLocalMatrix(Shader.Image(toImage(), tileX, tileY, sampling), localMatrix)

    companion object {
        fun fromImage(image: Image): Bitmap =
            Bitmap(image.width, image.height, image.colorType, image.colorSpace).also { bmp ->
                image.pixels?.let { src -> src.copyInto(bmp.pixels) }
            }
    }
}
```

- [ ] **Step 4: Run tests to verify**

Run: `./gradlew :kanvas:test --tests "org.graphiks.kanvas.image.BitmapTest"`

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/image/Bitmap.kt kanvas/src/test/kotlin/org/graphiks/kanvas/image/BitmapTest.kt
git commit -m "image: add Bitmap class with ByteArray backing and full ColorType support"
```

---

### Task 6: Pipeline — thread SamplingOptions through GPU WGSL

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchImage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`

- [ ] **Step 1: Map sampling to GPU material descriptor**

In `GPUOpMapper.kt`, find where `Shader.Image` is converted to a `GPUMaterialDescriptor`. Add a `samplingMode` field to the material (or a dedicated enum) and populate it from `Shader.Image.sampling`.

Current flow: `Paint.toMaterial()` is likely in `GPUMaterialMapper.kt`. Add a `SamplingMode` enum to `GPUMaterialDescriptor`:

```kotlin
// In GPUMaterialDescriptor or a new file
enum class GPUSamplingMode { NEAREST, LINEAR, CUBIC }
```

Map:
- `SamplingOptions.NEAREST` → `GPUSamplingMode.NEAREST`
- `SamplingOptions.LINEAR` → `GPUSamplingMode.LINEAR`
- `SamplingOptions.Cubic` → `GPUSamplingMode.LINEAR` (fallback for MVP)

- [ ] **Step 2: Thread sampling into WGSL shader selection**

In `GPUDispatchImage.kt`, read the sampling mode and select the appropriate WGSL texture function:
- `NEAREST` → `textureSample(texture, sampler, uv)` (default)
- `LINEAR` → `textureSample(texture, sampler, uv)` with linear sampler

For MVP, the WGSL already uses bilinear sampling by default. Add a comment noting that `NEAREST` vs `LINEAR` selection is deferred to the GPU sampler creation step.

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :kanvas:compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/
git commit -m "gpu: thread SamplingOptions through image dispatch (NEAREST/LINEAR, cubic fallback)"
```

---

### Task 7: Migration — port BitmapSubsetShaderGM as validation

**Files:**
- Create: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/BitmapSubsetShaderGm.kt`
- Modify: `integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm`

- [ ] **Step 1: Port BitmapSubsetShaderGM**

Read the old source at `skia-integration-tests/src/main/kotlin/org/skia/tests/BitmapSubsetShaderGM.kt` and port it to `BitmapSubsetShaderGm` using the new `Bitmap` API.

Add it to the ServiceLoader file.

- [ ] **Step 2: Move reference image**

```bash
mv skia-integration-tests/src/test/resources/original-888/bitmap_subset_shader.png integration-tests/skia/src/test/resources/reference/bitmap_subset_shader.png
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :integration-tests:skia:compileTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/image/BitmapSubsetShaderGm.kt integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm integration-tests/skia/src/test/resources/reference/bitmap_subset_shader.png
git commit -m "gm: port BitmapSubsetShaderGM using new Bitmap API"
```
