package org.graphiks.kanvas.image

import org.graphiks.math.floatToHalf
import org.graphiks.math.halfToFloat
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r
import org.graphiks.kanvas.types.toArgbInt

class Bitmap(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
) {
    val pixels: ByteArray = ByteArray(width * height * colorType.bytesPerPixel)

    fun getPixel(x: Int, y: Int): Color {
        if (x !in 0 until width || y !in 0 until height) throw IndexOutOfBoundsException("($x, $y) outside ${width}x$height")
        val index = (y * width + x) * colorType.bytesPerPixel
        return when (colorType) {
            ColorType.RGBA_8888 -> {
                val r = pixels[index].toInt() and 0xFF
                val g = pixels[index + 1].toInt() and 0xFF
                val b = pixels[index + 2].toInt() and 0xFF
                val a = pixels[index + 3].toInt() and 0xFF
                Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
            }
            ColorType.BGRA_8888 -> {
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
                val rh = ((pixels[index + 1].toInt() and 0xFF) shl 8) or (pixels[index].toInt() and 0xFF)
                val gh = ((pixels[index + 3].toInt() and 0xFF) shl 8) or (pixels[index + 2].toInt() and 0xFF)
                val bh = ((pixels[index + 5].toInt() and 0xFF) shl 8) or (pixels[index + 4].toInt() and 0xFF)
                val ah = ((pixels[index + 7].toInt() and 0xFF) shl 8) or (pixels[index + 6].toInt() and 0xFF)
                val pr = halfToFloat(rh.toShort())
                val pg = halfToFloat(gh.toShort())
                val pb = halfToFloat(bh.toShort())
                val pa = halfToFloat(ah.toShort())
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
                    ((r4 / 15f) / pa).coerceIn(0f, 1f),
                    ((g4 / 15f) / pa).coerceIn(0f, 1f),
                    ((b4 / 15f) / pa).coerceIn(0f, 1f),
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
            ColorType.RGBA_8888 -> {
                pixels[index] = (r * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[index + 1] = (g * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[index + 2] = (b * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[index + 3] = (a * 255f).toInt().coerceIn(0, 255).toByte()
            }
            ColorType.BGRA_8888 -> {
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

    fun getArgb(x: Int, y: Int): Int = getPixel(x, y).toArgbInt()

    fun setArgb(x: Int, y: Int, argb: Int) {
        setPixel(x, y, Color.fromArgbInt(argb))
    }

    fun eraseColor(color: Color) {
        val r = color.r; val g = color.g; val b = color.b; val a = color.a
        when (colorType) {
            ColorType.RGBA_8888 -> {
                val ri = (r * 255f).toInt().coerceIn(0, 255).toByte()
                val gi = (g * 255f).toInt().coerceIn(0, 255).toByte()
                val bi = (b * 255f).toInt().coerceIn(0, 255).toByte()
                val ai = (a * 255f).toInt().coerceIn(0, 255).toByte()
                var i = 0; val n = pixels.size
                while (i < n) {
                    pixels[i] = ri; pixels[i + 1] = gi; pixels[i + 2] = bi; pixels[i + 3] = ai
                    i += 4
                }
            }
            ColorType.BGRA_8888 -> {
                val bi = (b * 255f).toInt().coerceIn(0, 255).toByte()
                val gi = (g * 255f).toInt().coerceIn(0, 255).toByte()
                val ri = (r * 255f).toInt().coerceIn(0, 255).toByte()
                val ai = (a * 255f).toInt().coerceIn(0, 255).toByte()
                var i = 0; val n = pixels.size
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
