package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.uuid.Uuid
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPageArtifact
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPlacement
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextIntRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.gpu.renderer.artifacts.toPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver

/**
 * Deterministic, deeply immutable baseline fixtures for prepared-text
 * preflight tests.
 *
 * Every accessor returns a fresh snapshot. Page placements, glyph IDs,
 * instances, and UV rects form an exact bijection computed from the
 * placement layout.
 */
object GPUPreparedTextPreflightFixture {

    // ---- Common dimensions --------------------------------------------------

    const val PAGE_WIDTH: Int = 8
    const val PAGE_HEIGHT: Int = 8
    const val PAGE_ROW_BYTES: Int = 8
    const val GENERATION: Int = 1

    // ---- Artifact IDs -------------------------------------------------------

    private fun artifactId(name: String): GPUTextArtifactID =
        GPUTextArtifactID(Uuid.parse("00000000-0000-0000-0000-0000$name"))

    fun page0ArtifactId(): GPUTextArtifactID =
        artifactId("00000001")

    fun page1ArtifactId(): GPUTextArtifactID =
        artifactId("00000002")

    // ---- Glyph-level descriptors --------------------------------------------

    /** One glyph descriptor: glyph ID and its placement extents within the page. */
    data class GlyphDescriptor(
        val glyphId: Int,
        val itemKey: String,
        /** Placement rect (left, top, right, bottom) in page texels. */
        val allocationLeft: Int,
        val allocationTop: Int,
        val allocationRight: Int,
        val allocationBottom: Int,
        val deviceQuadTL: Pair<Float, Float>,
        val deviceQuadBR: Pair<Float, Float>,
    )

    /** Three glyphs placed in non-overlapping 2×2 cells on an 8×8 page. */
    fun glyphDescriptors(): List<GlyphDescriptor> = listOf(
        GlyphDescriptor(
            glyphId = 7,
            itemKey = "glyph-7",
            allocationLeft = 0, allocationTop = 0,
            allocationRight = 2, allocationBottom = 2,
            deviceQuadTL = 10f to 10f,
            deviceQuadBR = 50f to 50f,
        ),
        GlyphDescriptor(
            glyphId = 9,
            itemKey = "glyph-9",
            allocationLeft = 3, allocationTop = 0,
            allocationRight = 5, allocationBottom = 2,
            deviceQuadTL = 60f to 10f,
            deviceQuadBR = 100f to 50f,
        ),
        GlyphDescriptor(
            glyphId = 11,
            itemKey = "glyph-11",
            allocationLeft = 0, allocationTop = 3,
            allocationRight = 2, allocationBottom = 5,
            deviceQuadTL = 10f to 60f,
            deviceQuadBR = 50f to 100f,
        ),
    )

    // ---- UV helpers ---------------------------------------------------------

    /** Compute UV float rect from placement rect + page dimensions. */
    fun computeUv(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): GPUTextFloatRect {
        val w = PAGE_WIDTH.toFloat()
        val h = PAGE_HEIGHT.toFloat()
        return GPUTextFloatRect(
            left = left.toFloat() / w,
            top = top.toFloat() / h,
            right = right.toFloat() / w,
            bottom = bottom.toFloat() / h,
        )
    }

    /** Compute UV float rect from a GlyphDescriptor. */
    fun computeUv(gd: GlyphDescriptor): GPUTextFloatRect =
        computeUv(gd.allocationLeft, gd.allocationTop, gd.allocationRight, gd.allocationBottom)

    // ---- Device quad --------------------------------------------------------

    /**
     * Build a device quad with TL, TR, BR, BL corner order.
     *
     * [x0, y0] = top-left, [x1, y0] = top-right,
     * [x1, y1] = bottom-right, [x0, y1] = bottom-left.
     */
    fun deviceQuadTL_TR_BR_BL(tl: Pair<Float, Float>, br: Pair<Float, Float>): List<Float> =
        listOf(
            tl.first, tl.second,   // TL
            br.first, tl.second,   // TR
            br.first, br.second,   // BR
            tl.first, br.second,   // BL
        )

    /** Device quad from a GlyphDescriptor. */
    fun deviceQuad(gd: GlyphDescriptor): List<Float> =
        deviceQuadTL_TR_BR_BL(gd.deviceQuadTL, gd.deviceQuadBR)

    // ---- Page bytes ---------------------------------------------------------

    /** Deterministic A8 coverage: each glyph cell gets a distinct value. */
    fun pageBytes(
        descriptors: List<GlyphDescriptor> = glyphDescriptors(),
    ): List<Int> {
        val size = PAGE_WIDTH * PAGE_HEIGHT
        val bytes = IntArray(size) { 0 }
        descriptors.forEachIndexed { gi, gd ->
            val value = 64 + gi * 64  // 64, 128, 192
            for (row in gd.allocationTop until gd.allocationBottom) {
                for (col in gd.allocationLeft until gd.allocationRight) {
                    bytes[row * PAGE_ROW_BYTES + col] = value
                }
            }
        }
        return java.util.Collections.unmodifiableList(bytes.toList())
    }

    // ---- Page artifacts -----------------------------------------------------

    /** Create ONE valid A8 atlas page artifact for the baseline. */
    fun baselinePage0(): GPUTextA8AtlasPageArtifact {
        val descriptors = glyphDescriptors()
        val bytes = pageBytes(descriptors)
        val placements = descriptors.map { gd ->
            GPUTextA8AtlasPlacement(
                itemKey = gd.itemKey,
                pageIndex = 0,
                allocationRect = GPUTextIntRect(
                    left = gd.allocationLeft,
                    top = gd.allocationTop,
                    right = gd.allocationRight,
                    bottom = gd.allocationBottom,
                ),
                contentRect = GPUTextIntRect(
                    left = gd.allocationLeft,
                    top = gd.allocationTop,
                    right = gd.allocationRight,
                    bottom = gd.allocationBottom,
                ),
            )
        }
        val contentSha256 = GPUTextA8AtlasPageArtifact.sha256(bytes)
        val fingerprint = GPUTextA8AtlasPageArtifact.contentFingerprint(
            width = PAGE_WIDTH,
            height = PAGE_HEIGHT,
            rowBytes = PAGE_ROW_BYTES,
            contentSha256 = contentSha256,
            placements = placements,
        )
        return GPUTextA8AtlasPageArtifact.create(
            artifactKey = GPUTextArtifactKey(
                artifactID = page0ArtifactId(),
                generation = GPUTextArtifactGeneration(GENERATION),
                contentFingerprint = fingerprint,
            ),
            pageIndex = 0,
            width = PAGE_WIDTH,
            height = PAGE_HEIGHT,
            rowBytes = PAGE_ROW_BYTES,
            bytes = bytes,
            contentSha256 = contentSha256,
            placements = placements,
        )
    }

    /** A second page for multi-page tests. */
    fun baselinePage1(): GPUTextA8AtlasPageArtifact {
        val descriptors = listOf(
            GlyphDescriptor(
                glyphId = 13,
                itemKey = "glyph-13",
                allocationLeft = 0, allocationTop = 0,
                allocationRight = 2, allocationBottom = 2,
                deviceQuadTL = 10f to 110f,
                deviceQuadBR = 50f to 150f,
            ),
        )
        val bytes = pageBytes(descriptors)
        val placements = descriptors.map { gd ->
            GPUTextA8AtlasPlacement(
                itemKey = gd.itemKey,
                pageIndex = 1,
                allocationRect = GPUTextIntRect(
                    left = gd.allocationLeft,
                    top = gd.allocationTop,
                    right = gd.allocationRight,
                    bottom = gd.allocationBottom,
                ),
                contentRect = GPUTextIntRect(
                    left = gd.allocationLeft,
                    top = gd.allocationTop,
                    right = gd.allocationRight,
                    bottom = gd.allocationBottom,
                ),
            )
        }
        val contentSha256 = GPUTextA8AtlasPageArtifact.sha256(bytes)
        val fingerprint = GPUTextA8AtlasPageArtifact.contentFingerprint(
            width = PAGE_WIDTH,
            height = PAGE_HEIGHT,
            rowBytes = PAGE_ROW_BYTES,
            contentSha256 = contentSha256,
            placements = placements,
        )
        return GPUTextA8AtlasPageArtifact.create(
            artifactKey = GPUTextArtifactKey(
                artifactID = page1ArtifactId(),
                generation = GPUTextArtifactGeneration(GENERATION),
                contentFingerprint = fingerprint,
            ),
            pageIndex = 1,
            width = PAGE_WIDTH,
            height = PAGE_HEIGHT,
            rowBytes = PAGE_ROW_BYTES,
            bytes = bytes,
            contentSha256 = contentSha256,
            placements = placements,
        )
    }

    // ---- Instances ----------------------------------------------------------

    /** Build correct instances from page descriptors — exact bijection, deeply immutable. */
    fun baselineA8Instances(
        page: GPUTextA8AtlasPageArtifact,
        descriptors: List<GlyphDescriptor> = glyphDescriptors(),
    ): List<GPUTextA8Instance> {
        require(page.placements.map { it.itemKey }.sorted() ==
            descriptors.map { it.itemKey }.sorted()
        ) { "Placement item keys must exactly match glyph descriptor item keys" }

        val placementByKey = page.placements.associateBy { it.itemKey }
        val raw = descriptors.mapIndexed { index, gd ->
            val placement = requireNotNull(placementByKey[gd.itemKey])
            val uv = computeUv(gd)
            GPUTextA8Instance.create(
                glyphId = gd.glyphId,
                sourceGlyphIndex = GPUTextSourceGlyphIndex(index),
                deviceQuad = deviceQuad(gd),
                uvRect = uv,
                pageIndex = page.pageIndex,
            )
        }
        return java.util.Collections.unmodifiableList(ArrayList(raw))
    }

    /** Quick-access: baseline page 0 + its instances. */
    fun baselinePage0WithInstances(): Pair<GPUTextA8AtlasPageArtifact, List<GPUTextA8Instance>> {
        val page = baselinePage0()
        return page to baselineA8Instances(page)
    }

    // ---- Material program from Task 3 canonical compiler --------------------

    /**
     * A real compiled material program produced by the shared
     * [GPUPreparedMaterialProgramCompiler] — the single authority
     * defined in Task 3. No arbitrary WGSL or abiHash values.
     */
    fun baselineMaterialProgram(
        paintAlpha: Float = 1f,
    ): GPUPreparedMaterialProgram {
        val descriptor = GPUMaterialDescriptor.SolidColor(
            r = 0.25f, g = 0.5f, b = 0.75f, a = 0.9f,
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "webgpu-test",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = "material-dictionary:prepared-material:v1",
            runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
        )
        val result = GPUPreparedMaterialProgramCompiler.compile(descriptor, paintAlpha, context)
        check(result is GPUPreparedMaterialProgramResult.Ready) {
            "Baseline material program refused: " +
                (result as? GPUPreparedMaterialProgramResult.Refused)?.let {
                    "code=${it.code} sourceKind=${it.sourceKind} message=${it.message}"
                } ?: "unexpected result type ${result::class.simpleName}"
        }
        return result.program
    }
}
