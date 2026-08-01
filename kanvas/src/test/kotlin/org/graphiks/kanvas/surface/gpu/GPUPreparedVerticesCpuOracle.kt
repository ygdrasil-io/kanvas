package org.graphiks.kanvas.surface.gpu

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Per-channel comparison of two RGBA8 buffers produced for the same declared
 * pixel size.
 *
 * [maxChannelDelta] is the maximum unsigned per-channel difference,
 * [differingChannels] is the count of channels that differ at all, and
 * [comparedChannels] is the total number of channels compared. Two renders
 * that differ by at most one LSB per channel ([matchesWithinOneLsb]) are
 * treated as equivalent, which is the tolerance a physical f32 UNORM store
 * allows around the declared double→f32→UNORM quantization.
 */
data class GPUPreparedVerticesPixelDelta(
    val maxChannelDelta: Int,
    val differingChannels: Int,
    val comparedChannels: Int,
) {
    val matchesWithinOneLsb: Boolean
        get() = maxChannelDelta <= 1
}

/**
 * Independent CPU pixel oracle for prepared-vertices semantics.
 *
 * This oracle interprets only the public geometry/material semantics captured
 * by [GPUPreparedVerticesTestFixture]. It intentionally imports no production
 * packer, shader, or materializer type.
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
 *    fixed-function left/top rule: a sample exactly on an edge is covered
 *    only when the directed edge is a top edge (horizontal, interior below,
 *    i.e. traversed leftward) or a left edge (interior to the right of the
 *    directed edge, i.e. traversed upward: `y1 < y0`), so a pixel on a
 *    shared edge is claimed by exactly one triangle. Degenerate (zero-area)
 *    triangles cover nothing.
 * 4. Barycentric weights are computed in double precision from the oriented
 *    edge functions, then attributes (vertex colour, uv) are interpolated
 *    linearly in device space — perspective-free because the transform is
 *    affine.
 *
 * ### Shading and colour
 *
 * 5. Vertex colours and image texels are stored premultiplied sRGB-encoded
 *    bytes. The WebGPU vertex stage converts each stored byte to a float and
 *    interpolates it RAW — no sRGB decode — and the fragment shader multiplies
 *    the interpolated primitive colour by the material result without decoding.
 *    The oracle reproduces that arithmetic exactly: interpolation runs on the
 *    stored values (`byte / 255`), and the material result is the linear
 *    premultiplied solid the material compiler produces
 *    (`srgbToLinear(paintRgb) * paintAlpha`; the fixtures carry no paint
 *    colour, so paintRgb is opaque white and the material result is
 *    `paintAlpha`). Without vertex colours the primitive colour is opaque
 *    white.
 * 6. When an image is present, the texel at the interpolated uv is sampled
 *    with [GPUPreparedVerticesFilterMode.NEAREST] (`floor(u*width)`) or
 *    [GPUPreparedVerticesFilterMode.LINEAR] (texel-centre bilinear, clamped),
 *    raw (no sRGB decode), and the vertex-colour primitive blend modulates the
 *    texel: `primitive = vertexColour * texel` (component-wise
 *    premultiplied).
 * 7. Paint alpha is applied exactly once through the material result:
 *    `source = materialResult * primitive` (for the fixture scope,
 *    `source = paintAlpha * primitive`).
 * 8. The source is composited into the running premultiplied sRGB-encoded
 *    frame with the declared [GPUPreparedVerticesBlendMode]. Overlapping
 *    triangles within one draw composite in canonical paint order.
 * 9. The optional [GPUPreparedVerticesRectClip] rejects any device sample
 *    outside `[left, right) x [top, bottom)` before shading.
 *
 * ### Quantization rule (declared)
 *
 * Edge tests and barycentric weights run in IEEE-754 double precision.
 * Attribute interpolation runs in double precision on the stored
 * sRGB-encoded premultiplied values (exactly what the WebGPU vertex stage
 * interpolates), then the interpolated attributes are rounded through a
 * 32-bit float width (the fragment shader's f32 representation) before
 * blending. After final blending the channel value is clamped to [0,1],
 * rounded through a 32-bit float width, and quantized to 8-bit UNORM with
 * round-half-up (`round(value * 255)`). No sRGB encode or decode is applied
 * anywhere: the stored encoding IS the pipeline's working space, matching the
 * prepared-vertices packer and fragment shader. A physical f32 UNORM pipeline
 * may differ from this declared path by at most one LSB, which is exactly the
 * tolerance encoded by [GPUPreparedVerticesPixelDelta.matchesWithinOneLsb].
 */
object GPUPreparedVerticesCpuOracle {

    /**
     * Renders one fixture at its declared pixel size into a fresh
     * premultiplied sRGB-encoded RGBA8 buffer.
     */
    fun renderVertices(fixture: GPUPreparedVerticesTestFixture): ByteArray =
        renderVertices(listOf(fixture))

    /**
     * Renders a list of fixtures (paint order) into one shared frame of the
     * declared pixel size. All fixtures must share the same pixel size.
     */
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
            quantize(frame[index].coerceIn(0.0, 1.0))
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

    /**
     * Returns the source colour for [triangle] at the device sample ([sx],
     * [sy]), or null when the sample is outside the triangle. The returned
     * source is premultiplied in the stored sRGB-encoded space, with paint
     * alpha applied exactly once through the material result (the fixture
     * scope models the material as opaque white, so the material result is
     * `paintAlpha`).
     */
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

        // Standard left/top edge rule: a sample exactly on an edge is covered
        // only when the directed edge is a top edge (horizontal with the
        // interior below, i.e. traversed leftward) or a left edge (the
        // interior lies to the right of the directed edge, i.e. the edge is
        // traversed upward in framebuffer coordinates: y1 < y0). Fixed-
        // function rasterizers (D3D/Metal/Vulkan left-top) claim a shared-
        // edge pixel for exactly one triangle, and this rule reproduces that
        // behaviour.
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
            // The WebGPU vertex stage interpolates the stored premultiplied
            // sRGB-encoded bytes RAW — no decode. The oracle reproduces that.
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
        if (image != null && imagePixels != null && uvs != null) {
            val u = lambdaA * uvs[ia * 2] + lambdaB * uvs[ib * 2] + lambdaC * uvs[ic * 2]
            val v = lambdaA * uvs[ia * 2 + 1] + lambdaB * uvs[ib * 2 + 1] + lambdaC * uvs[ic * 2 + 1]
            val texel = sampleImage(imagePixels, image.width, image.height, image.filterMode, u, v)
            red *= texel[0]
            green *= texel[1]
            blue *= texel[2]
            alpha *= texel[3]
        }

        // The fragment shader's f32 representation: interpolated attributes
        // and the shaded source round through a 32-bit float width before
        // blending, exactly as the WebGPU fragment stage does.
        red = roundF32(red)
        green = roundF32(green)
        blue = roundF32(blue)
        alpha = roundF32(alpha)

        // The material result is the linear premultiplied solid the material
        // compiler produces; the fixtures model an opaque white paint, so
        // srgbToLinear(white) * paintAlpha = paintAlpha. The fragment shader
        // multiplies the material result by the interpolated primitive colour.
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
            GPUPreparedVerticesFilterMode.NEAREST -> {
                val x = floor(cu * width).toInt().coerceIn(0, width - 1)
                val y = floor(cv * height).toInt().coerceIn(0, height - 1)
                readLinearTexel(pixels, width, x, y)
            }
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
                val p00 = readLinearTexel(pixels, width, x0, y0)
                val p10 = readLinearTexel(pixels, width, x1, y0)
                val p01 = readLinearTexel(pixels, width, x0, y1)
                val p11 = readLinearTexel(pixels, width, x1, y1)
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
     * Declared final store: clamp to [0,1], round through a 32-bit float width,
     * quantize to 8-bit UNORM with round-half-up.
     */
    private fun quantize(value: Double): Byte {
        val f32 = value.toFloat().toDouble()
        return ((f32 * 255.0).roundToInt().coerceIn(0, 255)).toByte()
    }

    /** Rounds a double through the fragment shader's 32-bit float width. */
    private fun roundF32(value: Double): Double = value.toFloat().toDouble()
}
