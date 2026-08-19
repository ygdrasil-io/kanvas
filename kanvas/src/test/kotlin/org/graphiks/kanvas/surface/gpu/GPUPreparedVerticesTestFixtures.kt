package org.graphiks.kanvas.surface.gpu

/**
 * Test-only immutable inputs for prepared-vertices pixel tests.
 *
 * A fixture captures the public geometry/material semantics of one
 * handle-free prepared-vertices draw without referencing any production
 * packer, shader, or materializer type:
 *
 * - [GPUPreparedVerticesTopology] plus [GPUPreparedVerticesTestFixture.create]'s
 *   positions describe the local-space triangle soup that the affine
 *   [GPUPreparedVerticesAffineTransform] maps to device space.
 * - [GPUPreparedVerticesRectClip] is an optional integer device-space clip.
 * - [GPUPreparedVerticesBlendMode] is the final blend mode, applied after the
 *   paint alpha is applied exactly once.
 * - The optional image ([GPUPreparedVerticesImage]) supplies texels sampled
 *   through [GPUPreparedVerticesFilterMode] at the interpolated UVs.
 *
 * Every array accessor returns a defensive copy, so mutating a returned array
 * can never corrupt the fixture.
 */
enum class GPUPreparedVerticesTopology { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }

enum class GPUPreparedVerticesFilterMode { NEAREST, LINEAR }

enum class GPUPreparedVerticesBlendMode { SRC_OVER, SRC, SRC_IN, PLUS }

/**
 * Immutable 2D affine transform in the same layout as the public matrix
 * semantic: deviceX = scaleX*x + skewX*y + transX and
 * deviceY = skewY*x + scaleY*y + transY. Perspective terms are absent, so the
 * transform is always affine.
 */
data class GPUPreparedVerticesAffineTransform(
    val scaleX: Float,
    val skewX: Float,
    val transX: Float,
    val skewY: Float,
    val scaleY: Float,
    val transY: Float,
) {
    init {
        require(listOf(scaleX, skewX, transX, skewY, scaleY, transY).all { it.isFinite() }) {
            "Affine transform entries must be finite"
        }
    }

    fun mapX(x: Float, y: Float): Double =
        scaleX.toDouble() * x + skewX.toDouble() * y + transX.toDouble()

    fun mapY(x: Float, y: Float): Double =
        skewY.toDouble() * x + scaleY.toDouble() * y + transY.toDouble()

    companion object {
        fun identity(): GPUPreparedVerticesAffineTransform =
            GPUPreparedVerticesAffineTransform(1f, 0f, 0f, 0f, 1f, 0f)

        fun translate(tx: Float, ty: Float): GPUPreparedVerticesAffineTransform =
            GPUPreparedVerticesAffineTransform(1f, 0f, tx, 0f, 1f, ty)

        fun scale(sx: Float, sy: Float): GPUPreparedVerticesAffineTransform =
            GPUPreparedVerticesAffineTransform(sx, 0f, 0f, 0f, sy, 0f)
    }
}

/**
 * Immutable integer device-space clip. A device sample at pixel-centre
 * coordinates (x + 0.5, y + 0.5) survives the clip iff
 * `left <= x+0.5 < right && top <= y+0.5 < bottom`.
 */
data class GPUPreparedVerticesRectClip(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left <= right && top <= bottom) {
            "Clip rect must satisfy left<=right and top<=bottom"
        }
    }

    fun containsDeviceSample(x: Double, y: Double): Boolean =
        x >= left.toDouble() && x < right.toDouble() &&
            y >= top.toDouble() && y < bottom.toDouble()
}

/**
 * Immutable premultiplied RGBA8 image (sRGB-encoded RGB, linear alpha) plus
 * its declared filter mode. [pixelsCopy] and [copy] return fresh arrays so no
 * returned buffer can mutate the image.
 */
class GPUPreparedVerticesImage private constructor(
    private val pixels: ByteArray,
    val width: Int,
    val height: Int,
    val filterMode: GPUPreparedVerticesFilterMode,
) {
    val pixelCount: Int
        get() = width * height

    val pixelsCopy: ByteArray
        get() = pixels.copyOf()

    fun copy(): GPUPreparedVerticesImage =
        GPUPreparedVerticesImage(pixels.copyOf(), width, height, filterMode)

    companion object {
        fun create(
            pixels: ByteArray,
            width: Int,
            height: Int,
            filterMode: GPUPreparedVerticesFilterMode,
        ): GPUPreparedVerticesImage {
            require(width > 0 && height > 0) { "Image dimensions must be positive" }
            require(pixels.size.toLong() == width.toLong() * height.toLong() * 4L) {
                "Image pixel buffer must hold width*height*4 bytes"
            }
            for (offset in pixels.indices step 4) {
                val alpha = pixels[offset + 3].toInt() and 0xff
                require((pixels[offset].toInt() and 0xff) <= alpha) { "Image RGB must be premultiplied" }
                require((pixels[offset + 1].toInt() and 0xff) <= alpha) { "Image RGB must be premultiplied" }
                require((pixels[offset + 2].toInt() and 0xff) <= alpha) { "Image RGB must be premultiplied" }
            }
            return GPUPreparedVerticesImage(pixels.copyOf(), width, height, filterMode)
        }
    }
}

/**
 * Immutable prepared-vertices fixture. Arrays are owned privately and every
 * array accessor returns a copy, so the fixture is safe to share across
 * frames and tests.
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
        /**
         * Validates and snapshots one fixture.
         *
         * [colorsRgba8] is per-vertex premultiplied RGBA8 (sRGB-encoded RGB,
         * linear alpha) with exactly `positions.size / 2 * 4` bytes when
         * present. [texCoords] is per-vertex u,v pairs with exactly
         * `positions.size` floats when present. [indices] indexes vertices and
         * may be omitted to use implicit sequential indices. An [image]
         * requires [texCoords] so texel coordinates exist.
         */
        fun create(
            positions: FloatArray,
            topology: GPUPreparedVerticesTopology,
            pixelWidth: Int,
            pixelHeight: Int,
            colorsRgba8: ByteArray? = null,
            texCoords: FloatArray? = null,
            indices: IntArray? = null,
            transform: GPUPreparedVerticesAffineTransform = GPUPreparedVerticesAffineTransform.identity(),
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
            require(paintAlpha.isFinite() && paintAlpha in 0f..1f) { "Paint alpha must be finite and in 0..1" }
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

/**
 * Deterministic prepared-vertices fixtures. Every accessor builds a fresh
 * immutable fixture (whose own array accessors return copies), so tests cannot
 * observe prior mutation.
 */
object GPUPreparedVerticesTestFixtures {

    private fun premul(r: Int, g: Int, b: Int, a: Int): ByteArray = byteArrayOf(
        r.toByte(), g.toByte(), b.toByte(), a.toByte(),
    )

    private fun vertexColors(vararg colors: ByteArray): ByteArray =
        ByteArray(colors.size * 4).also { out ->
            colors.forEachIndexed { index, color -> color.copyInto(out, destinationOffset = index * 4) }
        }

    /**
     * Opaque white unit triangle (0,0),(2,0),(0,2) on a 3x3 canvas used to
     * probe the inclusive edge rule: pixels (0,1) and (1,0) sample exactly on
     * the x+y=2 hypotenuse.
     */
    fun edgeInclusionTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 3,
            pixelHeight = 3,
        )

    /**
     * The same unit triangle with opaque red/green/blue vertex colours on a
     * 2x2 canvas. Pixel (0,0) samples at barycentric (1/2,1/4,1/4).
     */
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

    /**
     * Unit triangle mapped to a 4x4 texture by uv (0,0),(1,0),(0,1). The
     * texture is red at (0,0), white at (1,0) and (0,1), green at (1,1), and
     * blue everywhere else. Pixel (0,0) interpolates uv (1/4,1/4): nearest
     * selects texel (1,1) (green) while linear averages the 2x2 red/white/
     * white/green block.
     */
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

    /**
     * Unit triangle with mid-gray vertex colours (188 sRGB ≈ 1/2 linear) over
     * an opaque red 2x2 texture. The vertex-colour primitive blend modulates
     * the texel, producing red at half intensity.
     */
    fun primitiveBlendTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            colorsRgba8 = vertexColors(
                premul(188, 188, 188, 255),
                premul(188, 188, 188, 255),
                premul(188, 188, 188, 255),
            ),
            texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
            image = GPUPreparedVerticesImage.create(
                pixels = ByteArray(2 * 2 * 4) { index ->
                    if (index % 4 == 0 || index % 4 == 3) 255.toByte() else 0.toByte()
                },
                width = 2,
                height = 2,
                filterMode = GPUPreparedVerticesFilterMode.NEAREST,
            ),
        )

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

    /**
     * A 4-vertex triangle strip v0(0,0) red, v1(2,0) green, v2(0,2) blue,
     * v3(2,2) white forming the unit square. Strips alternate winding: the
     * emitted triangles are (v0,v1,v2) and (v2,v1,v3).
     */
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

    /**
     * The same strip quad with translucent (alpha 128) premultiplied vertex
     * colours. A double-claimed pixel would composite the source twice
     * (src-over alpha 128 + 128·(1-128/255) = 192), so the single-claim
     * left/top rule is observable in the output.
     */
    fun translucentStripQuad(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f, 2f, 2f),
            colorsRgba8 = vertexColors(
                premul(128, 0, 0, 128),
                premul(0, 128, 0, 128),
                premul(0, 0, 128, 128),
                premul(128, 128, 128, 128),
            ),
            topology = GPUPreparedVerticesTopology.TRIANGLE_STRIP,
            pixelWidth = 2,
            pixelHeight = 2,
        )

    /**
     * A 4-vertex triangle fan v0(0,0) red, v1(3,0) green, v2(3,2) blue,
     * v3(0,2) white on a 3x2 canvas. The fan emits (v0,v1,v2) and (v0,v2,v3).
     */
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

    /** Unit white triangle scaled by 3/4 on a 2x2 canvas. */
    fun scaledTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
            transform = GPUPreparedVerticesAffineTransform.scale(0.75f, 0.75f),
        )

    /**
     * Two opaque unit triangles side by side on a 6x3 canvas: triangle A at
     * (0,0),(2,0),(0,2) with red/green/blue, triangle B at (4,0),(6,0),(4,2)
     * with magenta/cyan/yellow. [indices] selects which triangle renders —
     * `{0,1,2}` renders A only, `{3,4,5}` renders B only — proving the
     * oracle applies explicit indices.
     */
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

    /**
     * Unit white triangle (0,0),(2,0),(0,2) under the skew transform
     * device = (x, x + y) on a 3x3 canvas. The sheared footprint
     * (0,0),(2,2),(0,2) has interior x <= y with its x=y edge traversed
     * downward (excluded by the left/top rule), so only device pixel (0,1)
     * is covered — a pixel the unskewed triangle (hypotenuse x+y=2) would
     * not cover.
     */
    fun skewedTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 3,
            pixelHeight = 3,
            transform = GPUPreparedVerticesAffineTransform(
                scaleX = 1f, skewX = 0f, transX = 0f,
                skewY = 1f, scaleY = 1f, transY = 0f,
            ),
        )

    /**
     * Opaque white unit triangle shifted down by half a pixel,
     * (0,0.5),(2,0.5),(0,2.5) on a 3x3 canvas. Its top horizontal edge
     * y=0.5 passes exactly through the sample centres (0.5,0.5) and
     * (1.5,0.5), pinning the left/top rule's horizontal clause: a
     * rightward-traversed horizontal edge is excluded, so pixels (0,0) and
     * (1,0) stay empty while the interior pixel (0,1) is covered.
     */
    fun horizontalEdgeTriangle(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0.5f, 2f, 0.5f, 0f, 2.5f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 3,
            pixelHeight = 3,
        )

    /** A solid-color unit triangle on a 2x2 canvas for final-blend compositing. */
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
