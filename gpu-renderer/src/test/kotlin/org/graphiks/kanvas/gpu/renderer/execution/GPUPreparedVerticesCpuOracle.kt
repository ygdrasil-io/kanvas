package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Independent CPU pixel oracle for prepared-vertices semantics.
 *
 * NOTE: This file mirrors the FP-06 Task 13 oracle kept in the kanvas TEST sources
 * (`org.graphiks.kanvas.surface.gpu.GPUPreparedVerticesCpuOracle` plus the fixture
 * types and factory fixtures). gpu-renderer tests cannot depend on the kanvas test
 * classpath (module dependency direction), so the oracle and its fixture contract are
 * duplicated here with identical semantics. Any change to the kanvas oracle must be
 * mirrored here, and vice versa.
 *
 * ### Geometry
 *
 * 1. The affine [GPUPreparedVerticesAffineTransform] is applied to local
 *    vertex positions before rasterization: `device = T(local)`.
 * 2. [GPUPreparedVerticesTopology] is canonicalized to a triangle list:
 *    - TRIANGLES emits `(i, i+1, i+2)` triples,
 *    - TRIANGLE_STRIP emits `(j, j+1, j+2)` for even `j` and
 *      `(j+1, j, j+2)` for odd `j` (alternating strip winding),
 *    - TRIANGLE_FAN emits `(0, j, j+1)` for `j >= 1`,
 *    - `indices` are applied before canonicalization (sequential when absent).
 * 3. A fragment at device pixel-centre `(px + 0.5, py + 0.5)` is covered by a
 *    triangle when all three edge functions are non-negative after the
 *    triangle is oriented counter-clockwise. Edge inclusion follows the
 *    fixed-function left/top rule. Degenerate (zero-area) triangles cover
 *    nothing.
 * 4. Barycentric weights are computed in double precision from the oriented
 *    edge functions, then attributes (vertex colour, uv) are interpolated
 *    linearly in device space — perspective-free because the transform is
 *    affine.
 *
 * ### Shading and colour (WebGPU `rgba8unorm-srgb` LinearPremul model)
 *
 * The prepared-vertices route renders into an sRGB attachment whose fragment
 * output is interpreted as LINEAR and re-encoded to sRGB on store; WebGPU
 * blends sRGB attachments in LINEAR space. The oracle models exactly that
 * pipeline:
 *
 * 5. Vertex colours and image texels are stored premultiplied sRGB-encoded
 *    bytes. The WebGPU vertex stage converts each stored byte to a float and
 *    interpolates it RAW — no vertex-stage sRGB decode — and the fragment
 *    then DECODES the interpolated RGB sRGB→linear (alpha stays linear)
 *    before any shading math. The oracle reproduces that arithmetic exactly:
 *    interpolation runs on the stored values (`byte / 255`), the interpolated
 *    attributes round through the fragment's f32 width, and the interpolated
 *    RGB is decoded with the standard piecewise transfer function
 *    (`c <= 0.04045 -> c / 12.92`, else `((c + 0.055) / 1.055)^2.4`).
 *    Without vertex colours the decoded primitive colour is opaque white.
 * 6. The material result is the linear premultiplied solid the material
 *    compiler produces (`srgbToLinear(paintRgb) * paintAlpha`; the fixtures
 *    carry no paint colour, so paintRgb is opaque white and the material
 *    result is `paintAlpha`). The fragment multiplies it by the DECODED
 *    primitive colour: `source = materialResult * decodedPrimitive`.
 * 7. When an image is present, the texture is sRGB-encoded and WebGPU
 *    decodes sRGB→linear BEFORE filtering, so NEAREST (`floor(u*width)`) or
 *    LINEAR (texel-centre bilinear, clamped) operate on DECODED texel values
 *    (for LINEAR each of the four texels is decoded before the weighted
 *    average). The vertex-colour primitive blend modulates the sampled
 *    texel: `primitive = decodedVertexColour * decodedTexel` (component-wise
 *    premultiplied, linear).
 * 8. The source is composited into the running LINEAR premultiplied frame
 *    with the declared blend mode — all blend math runs in linear space
 *    because the sRGB attachment blends in linear. Overlapping triangles
 *    within one draw composite in canonical paint order.
 * 9. The optional rect clip rejects any device sample outside its bounds
 *    before shading.
 *
 * ### Quantization rule (declared)
 *
 * Edge tests and barycentric weights run in IEEE-754 double precision.
 * Attribute interpolation runs in double precision on the stored
 * sRGB-encoded premultiplied values, then the interpolated attributes are
 * rounded through a 32-bit float width and the RGB channels DECODED to
 * linear. The shaded source rounds through a 32-bit float width before
 * blending, and after final blending the channel value is clamped to [0,1],
 * rounded through a 32-bit float width, ENCODED linear→sRGB (RGB channels
 * only; alpha stores linear, never encoded), and quantized to 8-bit UNORM
 * with round-half-up. A physical f32 sRGB pipeline may differ from this
 * declared path by at most one LSB, which is exactly the tolerance encoded
 * by [GPUPreparedVerticesPixelDelta].
 */
object GPUPreparedVerticesCpuOracle {

    /** Renders one fixture at its declared pixel size into a fresh RGBA8 buffer. */
    fun renderVertices(fixture: GPUPreparedVerticesTestFixture): ByteArray =
        renderVertices(listOf(fixture))

    /** Renders a list of fixtures (paint order) into one shared frame. */
    fun renderVertices(fixtures: List<GPUPreparedVerticesTestFixture>): ByteArray {
        require(fixtures.isNotEmpty()) { "At least one fixture is required" }
        val width = fixtures.first().pixelWidth
        val height = fixtures.first().pixelHeight
        require(fixtures.all { it.pixelWidth == width && it.pixelHeight == height }) {
            "All fixtures must share the same declared pixel size"
        }
        val frame = DoubleArray(Math.multiplyExact(Math.multiplyExact(width, height), 4))
        fixtures.forEach { fixture -> renderFixture(fixture, frame) }
        return ByteArray(frame.size) { index ->
            // RGB channels are encoded linear→sRGB on store; the alpha
            // channel of an sRGB attachment stores linear and is not encoded.
            if (index % 4 == 3) quantizeLinear(frame[index]) else quantize(frame[index])
        }
    }

    /** Compares two RGBA8 buffers channel by channel. */
    fun comparePixels(actual: ByteArray, expected: ByteArray): GPUPreparedVerticesPixelDelta {
        require(actual.size == expected.size) { "Pixel buffers must have equal size" }
        var maxDelta = 0
        var differing = 0
        for (index in actual.indices) {
            val delta = kotlin.math.abs((actual[index].toInt() and 0xff) - (expected[index].toInt() and 0xff))
            if (delta > maxDelta) maxDelta = delta
            if (delta != 0) differing++
        }
        return GPUPreparedVerticesPixelDelta(maxDelta, differing, actual.size)
    }

    // ---- Rasterisation ---------------------------------------------------------

    private class DeviceTriangle(
        val ax: Double,
        val ay: Double,
        val bx: Double,
        val by: Double,
        val cx: Double,
        val cy: Double,
        val v0: Int,
        val v1: Int,
        val v2: Int,
    )

    private fun renderFixture(
        fixture: GPUPreparedVerticesTestFixture,
        frame: DoubleArray,
    ) {
        val triangles = canonicalize(fixture)
        val colors = fixture.colorsRgba8Copy
        val uvs = fixture.texCoordsCopy
        val image = fixture.imageCopy
        val imagePixels = image?.pixelsCopy
        val width = fixture.pixelWidth
        val clip = fixture.clip
        for (py in 0 until fixture.pixelHeight) {
            for (px in 0 until fixture.pixelWidth) {
                val sx = px + 0.5
                val sy = py + 0.5
                if (clip != null && !clip.containsDeviceSample(sx, sy)) continue
                val base = (py * width + px) * 4
                for (triangle in triangles) {
                    val source = shade(
                        triangle, sx, sy, colors, uvs, image, imagePixels, fixture.paintAlpha,
                    ) ?: continue
                    blendInto(frame, base, source, fixture.blendMode)
                }
            }
        }
    }

    private fun canonicalize(fixture: GPUPreparedVerticesTestFixture): List<DeviceTriangle> {
        val positions = fixture.positionsCopy
        val vertexCount = positions.size / 2
        val indices = fixture.indicesCopy ?: IntArray(vertexCount) { it }
        val transform = fixture.transform

        fun deviceX(vertex: Int): Double =
            transform.mapX(positions[vertex * 2], positions[vertex * 2 + 1])

        fun deviceY(vertex: Int): Double =
            transform.mapY(positions[vertex * 2], positions[vertex * 2 + 1])

        fun emit(a: Int, b: Int, c: Int): DeviceTriangle = DeviceTriangle(
            deviceX(a), deviceY(a),
            deviceX(b), deviceY(b),
            deviceX(c), deviceY(c),
            a, b, c,
        )

        return when (fixture.topology) {
            GPUPreparedVerticesTopology.TRIANGLES -> buildList {
                for (i in 0 until indices.size - 2 step 3) {
                    add(emit(indices[i], indices[i + 1], indices[i + 2]))
                }
            }
            GPUPreparedVerticesTopology.TRIANGLE_STRIP -> buildList {
                for (j in 0 until indices.size - 2) {
                    if (j % 2 == 0) {
                        add(emit(indices[j], indices[j + 1], indices[j + 2]))
                    } else {
                        add(emit(indices[j + 1], indices[j], indices[j + 2]))
                    }
                }
            }
            GPUPreparedVerticesTopology.TRIANGLE_FAN -> buildList {
                for (j in 1 until indices.size - 1) {
                    add(emit(indices[0], indices[j], indices[j + 1]))
                }
            }
        }
    }

    private fun shade(
        triangle: DeviceTriangle,
        sx: Double,
        sy: Double,
        colors: ByteArray?,
        uvs: FloatArray?,
        image: GPUPreparedVerticesImage?,
        imagePixels: ByteArray?,
        paintAlpha: Float,
    ): DoubleArray? {
        var ax = triangle.ax
        var ay = triangle.ay
        var bx = triangle.bx
        var by = triangle.by
        var cx = triangle.cx
        var cy = triangle.cy
        var ia = triangle.v0
        var ib = triangle.v1
        var ic = triangle.v2

        val area2 = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if (area2 == 0.0) return null
        if (area2 < 0.0) {
            val swapX = bx; val swapY = by
            bx = cx; by = cy
            cx = swapX; cy = swapY
            val swapVertex = ib
            ib = ic
            ic = swapVertex
        }

        val e1 = (bx - ax) * (sy - ay) - (by - ay) * (sx - ax)
        val e2 = (cx - bx) * (sy - by) - (cy - by) * (sx - bx)
        val e3 = (ax - cx) * (sy - cy) - (ay - cy) * (sx - cx)

        val leftTopAb = by < ay || (by == ay && bx < ax)
        val leftTopBc = cy < by || (cy == by && cx < bx)
        val leftTopCa = ay < cy || (ay == cy && ax < cx)
        val insideAb = e1 > 0.0 || (e1 == 0.0 && leftTopAb)
        val insideBc = e2 > 0.0 || (e2 == 0.0 && leftTopBc)
        val insideCa = e3 > 0.0 || (e3 == 0.0 && leftTopCa)
        if (!insideAb || !insideBc || !insideCa) return null

        val total = e1 + e2 + e3
        if (total <= 0.0) return null
        val lambdaA = e2 / total
        val lambdaB = e3 / total
        val lambdaC = e1 / total

        var red = 1.0
        var green = 1.0
        var blue = 1.0
        var alpha = 1.0
        if (colors != null) {
            red = lambdaA * vertexChannel(colors, ia, 0) +
                lambdaB * vertexChannel(colors, ib, 0) +
                lambdaC * vertexChannel(colors, ic, 0)
            green = lambdaA * vertexChannel(colors, ia, 1) +
                lambdaB * vertexChannel(colors, ib, 1) +
                lambdaC * vertexChannel(colors, ic, 1)
            blue = lambdaA * vertexChannel(colors, ia, 2) +
                lambdaB * vertexChannel(colors, ib, 2) +
                lambdaC * vertexChannel(colors, ic, 2)
            alpha = lambdaA * vertexAlpha(colors, ia) +
                lambdaB * vertexAlpha(colors, ib) +
                lambdaC * vertexAlpha(colors, ic)
        }
        // The fragment then DECODES the interpolated RGB sRGB→linear (alpha
        // stays linear) before any shading math. The interpolated attributes
        // round through the fragment's f32 width first.
        red = decodeSrgb(roundF32(red))
        green = decodeSrgb(roundF32(green))
        blue = decodeSrgb(roundF32(blue))
        alpha = roundF32(alpha)
        if (image != null && imagePixels != null && uvs != null) {
            val u = lambdaA * uvs[ia * 2] + lambdaB * uvs[ib * 2] + lambdaC * uvs[ic * 2]
            val v = lambdaA * uvs[ia * 2 + 1] + lambdaB * uvs[ib * 2 + 1] + lambdaC * uvs[ic * 2 + 1]
            val texel = sampleImage(imagePixels, image.width, image.height, image.filterMode, u, v)
            red *= texel[0]
            green *= texel[1]
            blue *= texel[2]
            alpha *= texel[3]
        }

        // The fragment shader's f32 representation: the shaded linear source
        // rounds through a 32-bit float width before blending.
        red = roundF32(red)
        green = roundF32(green)
        blue = roundF32(blue)
        alpha = roundF32(alpha)

        val paintMultiplier = paintAlpha.toDouble()
        return doubleArrayOf(
            roundF32(red * paintMultiplier),
            roundF32(green * paintMultiplier),
            roundF32(blue * paintMultiplier),
            roundF32(alpha * paintMultiplier),
        )
    }

    private fun blendInto(
        linear: DoubleArray,
        base: Int,
        source: DoubleArray,
        mode: GPUPreparedVerticesBlendMode,
    ) {
        val sourceAlpha = source[3]
        val destinationAlpha = linear[base + 3]
        when (mode) {
            GPUPreparedVerticesBlendMode.SRC_OVER -> {
                val inverseSourceAlpha = 1.0 - sourceAlpha
                linear[base] = source[0] + linear[base] * inverseSourceAlpha
                linear[base + 1] = source[1] + linear[base + 1] * inverseSourceAlpha
                linear[base + 2] = source[2] + linear[base + 2] * inverseSourceAlpha
                linear[base + 3] = sourceAlpha + destinationAlpha * inverseSourceAlpha
            }
            GPUPreparedVerticesBlendMode.SRC -> {
                linear[base] = source[0]
                linear[base + 1] = source[1]
                linear[base + 2] = source[2]
                linear[base + 3] = sourceAlpha
            }
            GPUPreparedVerticesBlendMode.SRC_IN -> {
                linear[base] = source[0] * destinationAlpha
                linear[base + 1] = source[1] * destinationAlpha
                linear[base + 2] = source[2] * destinationAlpha
                linear[base + 3] = sourceAlpha * destinationAlpha
            }
            GPUPreparedVerticesBlendMode.PLUS -> {
                linear[base] = (source[0] + linear[base]).coerceAtMost(1.0)
                linear[base + 1] = (source[1] + linear[base + 1]).coerceAtMost(1.0)
                linear[base + 2] = (source[2] + linear[base + 2]).coerceAtMost(1.0)
                linear[base + 3] = (sourceAlpha + destinationAlpha).coerceAtMost(1.0)
            }
        }
    }

    // ---- Image sampling ---------------------------------------------------------

    private fun sampleImage(
        pixels: ByteArray,
        width: Int,
        height: Int,
        filter: GPUPreparedVerticesFilterMode,
        u: Double,
        v: Double,
    ): DoubleArray {
        val cu = u.coerceIn(0.0, 1.0)
        val cv = v.coerceIn(0.0, 1.0)
        return when (filter) {
            // WebGPU decodes the sRGB-encoded texture to linear BEFORE
            // filtering, so the selected texel is decoded first.
            GPUPreparedVerticesFilterMode.NEAREST -> {
                val x = floor(cu * width).toInt().coerceIn(0, width - 1)
                val y = floor(cv * height).toInt().coerceIn(0, height - 1)
                readLinearTexel(pixels, width, x, y).decoded()
            }
            // Each of the four texel centres is decoded before the bilinear
            // weighted average runs in linear space.
            GPUPreparedVerticesFilterMode.LINEAR -> {
                val fx = cu * width - 0.5
                val fy = cv * height - 0.5
                val rawX0 = floor(fx).toInt()
                val rawY0 = floor(fy).toInt()
                val wx1 = fx - rawX0
                val wy1 = fy - rawY0
                val x0 = rawX0.coerceIn(0, width - 1)
                val y0 = rawY0.coerceIn(0, height - 1)
                val x1 = (rawX0 + 1).coerceIn(0, width - 1)
                val y1 = (rawY0 + 1).coerceIn(0, height - 1)
                val wx0 = 1.0 - wx1
                val wy0 = 1.0 - wy1
                val p00 = readLinearTexel(pixels, width, x0, y0).decoded()
                val p10 = readLinearTexel(pixels, width, x1, y0).decoded()
                val p01 = readLinearTexel(pixels, width, x0, y1).decoded()
                val p11 = readLinearTexel(pixels, width, x1, y1).decoded()
                DoubleArray(4) { channel ->
                    p00[channel] * wx0 * wy0 +
                        p10[channel] * wx1 * wy0 +
                        p01[channel] * wx0 * wy1 +
                        p11[channel] * wx1 * wy1
                }
            }
        }
    }

    // ---- Colour math ------------------------------------------------------------

    private fun readLinearTexel(pixels: ByteArray, width: Int, x: Int, y: Int): DoubleArray {
        val base = (y * width + x) * 4
        return doubleArrayOf(
            (pixels[base].toInt() and 0xff) / 255.0,
            (pixels[base + 1].toInt() and 0xff) / 255.0,
            (pixels[base + 2].toInt() and 0xff) / 255.0,
            (pixels[base + 3].toInt() and 0xff) / 255.0,
        )
    }

    private fun vertexChannel(colors: ByteArray, vertex: Int, channel: Int): Double {
        val base = vertex * 4 + channel
        return (colors[base].toInt() and 0xff) / 255.0
    }

    private fun vertexAlpha(colors: ByteArray, vertex: Int): Double {
        val base = vertex * 4 + 3
        return (colors[base].toInt() and 0xff) / 255.0
    }

    /**
     * Declared final store for RGB: clamp to [0,1], round through a 32-bit
     * float width, ENCODE linear→sRGB, quantize to 8-bit UNORM with
     * round-half-up.
     */
    private fun quantize(value: Double): Byte {
        val clamped = value.coerceIn(0.0, 1.0)
        val f32 = clamped.toFloat().toDouble()
        val encoded = encodeSrgb(f32)
        return ((encoded * 255.0).roundToInt().coerceIn(0, 255)).toByte()
    }

    /**
     * Declared final store for ALPHA: an sRGB attachment stores alpha linear
     * (never encoded), so only clamp, round through a 32-bit float width, and
     * quantize to 8-bit UNORM with round-half-up.
     */
    private fun quantizeLinear(value: Double): Byte {
        val clamped = value.coerceIn(0.0, 1.0)
        val f32 = clamped.toFloat().toDouble()
        return ((f32 * 255.0).roundToInt().coerceIn(0, 255)).toByte()
    }

    /**
     * Standard piecewise sRGB decode: `c <= 0.04045 -> c / 12.92`, else
     * `((c + 0.055) / 1.055)^2.4`. The RGB channels are decoded; alpha is
     * linear already and is passed through unchanged by the callers.
     */
    private fun decodeSrgb(value: Double): Double {
        val clamped = value.coerceIn(0.0, 1.0)
        return if (clamped <= 0.04045) {
            clamped / 12.92
        } else {
            ((clamped + 0.055) / 1.055).pow(2.4)
        }
    }

    /** Standard sRGB encode: `c <= 0.0031308 -> c * 12.92`, else `1.055 * c^(1/2.4) - 0.055`. */
    private fun encodeSrgb(value: Double): Double {
        val clamped = value.coerceIn(0.0, 1.0)
        return if (clamped <= 0.0031308) {
            clamped * 12.92
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
    }

    /** Rounds a double through the fragment shader's 32-bit float width. */
    private fun roundF32(value: Double): Double = value.toFloat().toDouble()

    /** Decodes one RGBA texel: RGB sRGB→linear, alpha linear. */
    private fun DoubleArray.decoded(): DoubleArray = doubleArrayOf(
        decodeSrgb(this[0]),
        decodeSrgb(this[1]),
        decodeSrgb(this[2]),
        this[3],
    )
}

/** Per-channel comparison of two RGBA8 buffers produced for the same declared pixel size. */
data class GPUPreparedVerticesPixelDelta(
    val maxChannelDelta: Int,
    val differingChannels: Int,
    val comparedChannels: Int,
) {
    val matchesWithinOneLsb: Boolean
        get() = maxChannelDelta <= 1
}

enum class GPUPreparedVerticesTopology { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }

enum class GPUPreparedVerticesFilterMode { NEAREST, LINEAR }

enum class GPUPreparedVerticesBlendMode { SRC_OVER, SRC, SRC_IN, PLUS }

/** Row-major affine transform: device = (scaleX*x + skewX*y + transX, skewY*x + scaleY*y + transY). */
data class GPUPreparedVerticesAffineTransform(
    val scaleX: Float,
    val skewX: Float,
    val transX: Float,
    val skewY: Float,
    val scaleY: Float,
    val transY: Float,
) {
    fun mapX(x: Float, y: Float): Double =
        (scaleX * x + skewX * y + transX).toDouble()

    fun mapY(x: Float, y: Float): Double =
        (skewY * x + scaleY * y + transY).toDouble()

    companion object {
        fun identity(): GPUPreparedVerticesAffineTransform =
            GPUPreparedVerticesAffineTransform(1f, 0f, 0f, 0f, 1f, 0f)

        fun translate(tx: Float, ty: Float): GPUPreparedVerticesAffineTransform =
            GPUPreparedVerticesAffineTransform(1f, 0f, tx, 0f, 1f, ty)

        fun scale(sx: Float, sy: Float): GPUPreparedVerticesAffineTransform =
            GPUPreparedVerticesAffineTransform(sx, 0f, 0f, 0f, sy, 0f)
    }
}

/** Integral device-rect clip: a device sample is covered only inside `[left, right) x [top, bottom)`. */
data class GPUPreparedVerticesRectClip(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun containsDeviceSample(x: Double, y: Double): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

class GPUPreparedVerticesImage private constructor(
    val width: Int,
    val height: Int,
    val filterMode: GPUPreparedVerticesFilterMode,
    private val pixels: ByteArray,
) {
    val pixelsCopy: ByteArray
        get() = pixels.copyOf()

    fun copy(): GPUPreparedVerticesImage = GPUPreparedVerticesImage(
        width,
        height,
        filterMode,
        pixels.copyOf(),
    )

    companion object {
        fun create(
            pixels: ByteArray,
            width: Int,
            height: Int,
            filterMode: GPUPreparedVerticesFilterMode,
        ): GPUPreparedVerticesImage {
            require(width > 0 && height > 0) { "Image dimensions must be positive" }
            require(pixels.size == width * height * 4) { "Image pixels must be width*height*4 bytes" }
            return GPUPreparedVerticesImage(width, height, filterMode, pixels.copyOf())
        }
    }
}

/**
 * Immutable fixture contract shared with the Task 13 CPU oracle. The fixture
 * declares geometry and shading facts only; it imports no GPU production type.
 */
class GPUPreparedVerticesTestFixture private constructor(
    private val positions: FloatArray,
    private val colorsRgba8: ByteArray?,
    private val texCoords: FloatArray?,
    private val indices: IntArray?,
    val topology: GPUPreparedVerticesTopology,
    val transform: GPUPreparedVerticesAffineTransform,
    val clip: GPUPreparedVerticesRectClip?,
    val blendMode: GPUPreparedVerticesBlendMode,
    val paintAlpha: Float,
    private val image: GPUPreparedVerticesImage?,
    val pixelWidth: Int,
    val pixelHeight: Int,
) {
    val vertexCount: Int
        get() = positions.size / 2

    val positionsCopy: FloatArray
        get() = positions.copyOf()

    val colorsRgba8Copy: ByteArray?
        get() = colorsRgba8?.copyOf()

    val texCoordsCopy: FloatArray?
        get() = texCoords?.copyOf()

    val indicesCopy: IntArray?
        get() = indices?.copyOf()

    val imageCopy: GPUPreparedVerticesImage?
        get() = image?.copy()

    companion object {
        fun create(
            positions: FloatArray,
            topology: GPUPreparedVerticesTopology,
            pixelWidth: Int,
            pixelHeight: Int,
            colorsRgba8: ByteArray? = null,
            texCoords: FloatArray? = null,
            indices: IntArray? = null,
            transform: GPUPreparedVerticesAffineTransform =
                GPUPreparedVerticesAffineTransform.identity(),
            clip: GPUPreparedVerticesRectClip? = null,
            blendMode: GPUPreparedVerticesBlendMode = GPUPreparedVerticesBlendMode.SRC_OVER,
            paintAlpha: Float = 1f,
            image: GPUPreparedVerticesImage? = null,
        ): GPUPreparedVerticesTestFixture {
            require(pixelWidth > 0 && pixelHeight > 0) { "Pixel dimensions must be positive" }
            require(positions.size >= 6) { "At least three vertices are required" }
            require(positions.size % 2 == 0) { "Positions must be x,y pairs" }
            require(positions.all { it.isFinite() }) { "Positions must be finite" }
            val vertexCount = positions.size / 2
            if (colorsRgba8 != null) {
                require(colorsRgba8.size == vertexCount * 4) { "Colors must be vertexCount*4 bytes" }
                for (offset in colorsRgba8.indices step 4) {
                    val alpha = colorsRgba8[offset + 3].toInt() and 0xff
                    require((colorsRgba8[offset].toInt() and 0xff) <= alpha) { "Colors must be premultiplied" }
                    require((colorsRgba8[offset + 1].toInt() and 0xff) <= alpha) { "Colors must be premultiplied" }
                    require((colorsRgba8[offset + 2].toInt() and 0xff) <= alpha) { "Colors must be premultiplied" }
                }
            }
            if (texCoords != null) {
                require(texCoords.size == positions.size) { "Tex coords must be one u,v pair per vertex" }
                require(texCoords.all { it.isFinite() }) { "Tex coords must be finite" }
            }
            if (indices != null) {
                require(indices.size >= 3) { "Indices must reference at least one triangle" }
                require(indices.all { it in 0 until vertexCount }) { "Indices must reference existing vertices" }
                if (topology == GPUPreparedVerticesTopology.TRIANGLES) {
                    require(indices.size % 3 == 0) { "Triangle topology requires a multiple of three indices" }
                }
            }
            if (image != null) {
                require(texCoords != null) { "An image requires per-vertex tex coords" }
            }
            require(paintAlpha.isFinite() && paintAlpha in 0f..1f) {
                "Paint alpha must be finite and in 0..1"
            }
            return GPUPreparedVerticesTestFixture(
                positions = positions.copyOf(),
                colorsRgba8 = colorsRgba8?.copyOf(),
                texCoords = texCoords?.copyOf(),
                indices = indices?.copyOf(),
                topology = topology,
                transform = transform,
                clip = clip,
                blendMode = blendMode,
                paintAlpha = paintAlpha,
                image = image?.copy(),
                pixelWidth = pixelWidth,
                pixelHeight = pixelHeight,
            )
        }
    }
}

/** Deterministic prepared-vertices fixtures mirrored from the kanvas test sources. */
object GPUPreparedVerticesTestFixtures {

    private fun premul(r: Int, g: Int, b: Int, a: Int): ByteArray = byteArrayOf(
        r.toByte(), g.toByte(), b.toByte(), a.toByte(),
    )

    private fun vertexColors(vararg colors: ByteArray): ByteArray =
        ByteArray(colors.size * 4).also { out ->
            colors.forEachIndexed { index, color -> color.copyInto(out, destinationOffset = index * 4) }
        }

    /** Unit triangle on a 3x3 canvas probing the inclusive left/top edge rule. */
    fun edgeInclusionTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 3,
            pixelHeight = 3,
        )

    /** Unit triangle with opaque red/green/blue vertex colours on a 2x2 canvas. */
    fun barycentricColorTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            colorsRgba8 = vertexColors(
                premul(255, 0, 0, 255),
                premul(0, 255, 0, 255),
                premul(0, 0, 255, 255),
            ),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
        )

    /** Unit triangle mapped to a 4x4 texture by uv. */
    fun texturedTriangle(filterMode: GPUPreparedVerticesFilterMode): GPUPreparedVerticesTestFixture {
        val pixels = ByteArray(4 * 4 * 4) { offset ->
            val x = (offset / 4) % 4
            val y = (offset / 4) / 4
            val channel = offset % 4
            val rgba = when {
                x == 0 && y == 0 -> intArrayOf(255, 0, 0, 255)
                x == 1 && y == 0 -> intArrayOf(255, 255, 255, 255)
                x == 0 && y == 1 -> intArrayOf(255, 255, 255, 255)
                x == 1 && y == 1 -> intArrayOf(0, 255, 0, 255)
                else -> intArrayOf(0, 0, 255, 255)
            }
            rgba[channel].toByte()
        }
        return GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
            image = GPUPreparedVerticesImage.create(
                pixels = pixels,
                width = 4,
                height = 4,
                filterMode = filterMode,
            ),
        )
    }

    /** Unit triangle with opaque red vertex colours and paintAlpha 1/2. */
    fun halfAlphaRedTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            colorsRgba8 = vertexColors(
                premul(255, 0, 0, 255),
                premul(255, 0, 0, 255),
                premul(255, 0, 0, 255),
            ),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
            paintAlpha = 0.5f,
        )

    /** A 4-vertex triangle strip forming the unit square with alternating winding. */
    fun stripQuad(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f, 2f, 2f),
            colorsRgba8 = vertexColors(
                premul(255, 0, 0, 255),
                premul(0, 255, 0, 255),
                premul(0, 0, 255, 255),
                premul(255, 255, 255, 255),
            ),
            topology = GPUPreparedVerticesTopology.TRIANGLE_STRIP,
            pixelWidth = 2,
            pixelHeight = 2,
        )

    /** A 4-vertex triangle fan forming the unit square. */
    fun fanQuad(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 3f, 0f, 3f, 2f, 0f, 2f),
            colorsRgba8 = vertexColors(
                premul(255, 0, 0, 255),
                premul(0, 255, 0, 255),
                premul(0, 0, 255, 255),
                premul(255, 255, 255, 255),
            ),
            topology = GPUPreparedVerticesTopology.TRIANGLE_FAN,
            pixelWidth = 3,
            pixelHeight = 2,
        )

    /** Unit white triangle clipped to the device rect (0,0,1,1) on a 2x2 canvas. */
    fun clippedTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
            clip = GPUPreparedVerticesRectClip(0, 0, 1, 1),
        )

    /** Unit white triangle translated by (2,2) on a 5x5 canvas. */
    fun translatedTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 5,
            pixelHeight = 5,
            transform = GPUPreparedVerticesAffineTransform.translate(2f, 2f),
        )

    /** Two unit triangles side by side; indices select which one renders. */
    fun indexedTriangleSelection(indices: IntArray): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f, 4f, 0f, 6f, 0f, 4f, 2f),
            colorsRgba8 = vertexColors(
                premul(255, 0, 0, 255),
                premul(0, 255, 0, 255),
                premul(0, 0, 255, 255),
                premul(255, 0, 255, 255),
                premul(0, 255, 255, 255),
                premul(255, 255, 0, 255),
            ),
            indices = indices,
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 6,
            pixelHeight = 3,
        )

    /** A solid-color unit triangle for final-blend compositing. */
    fun solidColorTriangle(
        color: IntArray,
        blendMode: GPUPreparedVerticesBlendMode,
        paintAlpha: Float,
    ): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            colorsRgba8 = vertexColors(
                premul(color[0], color[1], color[2], color[3]),
                premul(color[0], color[1], color[2], color[3]),
                premul(color[0], color[1], color[2], color[3]),
            ),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
            blendMode = blendMode,
            paintAlpha = paintAlpha,
        )
}


/** Task-14 smoke helper: replaces the fixture indices (or adds them) while keeping geometry. */
fun GPUPreparedVerticesTestFixture.copyFixtureWithIndices(indices: IntArray): GPUPreparedVerticesTestFixture =
    GPUPreparedVerticesTestFixture.create(
        positions = positionsCopy,
        colorsRgba8 = colorsRgba8Copy,
        texCoords = texCoordsCopy,
        indices = indices,
        topology = topology,
        transform = transform,
        clip = clip,
        blendMode = blendMode,
        paintAlpha = paintAlpha,
        image = imageCopy,
        pixelWidth = pixelWidth,
        pixelHeight = pixelHeight,
    )
