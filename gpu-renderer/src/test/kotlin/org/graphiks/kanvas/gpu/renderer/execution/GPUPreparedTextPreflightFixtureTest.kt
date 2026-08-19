package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPageArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.toPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult

/**
 * Tests for prepared-text preflight fixtures and mutation matrix.
 *
 * ## What this file proves
 *
 * - The baseline is valid: correct quad order, placement/instance/UV
 *   bijection, coherent page content, deterministic output.
 * - Every invariant has exactly one unique descriptive entry in the matrix.
 * - Fixtures are deeply immutable even when mutation is attempted via
 *   the returned collections.
 * - The mutation matrix is internally consistent.
 *
 * ## What this file does NOT prove
 *
 * - That a refusal reaches the production preflight. Those executable
 *   zero-native-side-effect checks live in `GPUPreparedTextNativePreflightTest`,
 *   which consumes this matrix as its single ordered mutation inventory.
 *
 * ## Counts
 *
 * - Fixture and matrix self-validation: this class.
 * - Production refusal coverage: `GPUPreparedTextNativePreflightTest`.
 * - The matrix size is asserted from its canonical inventory instead of
 *   being duplicated in this documentation.
 */
class GPUPreparedTextPreflightFixtureTest {

    // ===================================================================
    // 1. Mutation matrix consistency
    // ===================================================================

    @Test
    fun `mutation matrix is internally consistent`() {
        GPUPreparedTextPreflightMutationMatrix.assertInternalConsistency()
    }

    @Test
    fun `mutation matrix ordered by category priority`() {
        val mutations = GPUPreparedTextPreflightMutationMatrix.orderedMutations
        for (i in 0 until mutations.size - 1) {
            assertTrue(
                mutations[i].category.priority <= mutations[i + 1].category.priority,
                "${mutations[i].name} (${mutations[i].category.priority}) " +
                    "must precede ${mutations[i + 1].name} (${mutations[i + 1].category.priority})",
            )
        }
    }

    // ===================================================================
    // 2. Baseline validity
    // ===================================================================

    @Test
    fun `device quad has TL-TR-BR-BL corner order`() {
        val quad = GPUPreparedTextPreflightFixture.deviceQuadTL_TR_BR_BL(
            10f to 10f, 50f to 50f,
        )
        assertEquals(
            listOf(10f, 10f, 50f, 10f, 50f, 50f, 10f, 50f),
            quad,
            "Must be TL(10,10), TR(50,10), BR(50,50), BL(10,50)",
        )
    }

    @Test
    fun `placements and instances form exact bijection`() {
        val (page, instances) = GPUPreparedTextPreflightFixture.baselinePage0WithInstances()
        val placementKeys = page.placements.map { it.itemKey }.sorted()
        val instanceGlyphIds = instances.map { it.glyphId }

        assertEquals(3, placementKeys.size)
        assertEquals(3, instanceGlyphIds.size)
        assertEquals(
            listOf("glyph-11", "glyph-7", "glyph-9"),
            placementKeys,
        )
        assertEquals(
            listOf(7, 9, 11),
            instanceGlyphIds.sorted(),
        )
    }

    @Test
    fun `UV coords are computed from placement rects and page dimensions`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val instances = GPUPreparedTextPreflightFixture.baselineA8Instances(page)

        assertEquals(page.placements.size, instances.size)

        instances.forEach { instance ->
            val placement = page.placements.single {
                it.itemKey.startsWith("glyph-${instance.glyphId}")
            }
            val expectedUv = GPUPreparedTextPreflightFixture.computeUv(
                placement.allocationRect.left,
                placement.allocationRect.top,
                placement.allocationRect.right,
                placement.allocationRect.bottom,
            )
            assertEquals(
                expectedUv,
                instance.uvRect,
                "UV for glyph ${instance.glyphId} must match placement rect",
            )
        }
    }

    @Test
    fun `UV coords are within 0-1 range`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val instances = GPUPreparedTextPreflightFixture.baselineA8Instances(page)

        instances.forEach { instance ->
            val uv = instance.uvRect
            assertTrue(uv.left >= 0f && uv.left <= 1f, "UV left=${uv.left} out of range")
            assertTrue(uv.top >= 0f && uv.top <= 1f, "UV top=${uv.top} out of range")
            assertTrue(uv.right >= 0f && uv.right <= 1f, "UV right=${uv.right} out of range")
            assertTrue(uv.bottom >= 0f && uv.bottom <= 1f, "UV bottom=${uv.bottom} out of range")
        }
    }

    @Test
    fun `instance pageIndex matches page pageIndex`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val instances = GPUPreparedTextPreflightFixture.baselineA8Instances(page)

        instances.forEach { instance ->
            assertEquals(
                page.pageIndex,
                instance.pageIndex,
                "Instance pageIndex must match page",
            )
        }
    }

    @Test
    fun `page content hash matches computed bytes`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        assertEquals(
            GPUTextA8AtlasPageArtifact.sha256(page.bytes),
            page.contentSha256,
        )
    }

    @Test
    fun `page bytes cover all placements with distinct values`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val bytes = page.bytes

        page.placements.forEach { placement ->
            val r = placement.allocationRect
            val values = mutableSetOf<Int>()
            for (row in r.top until r.bottom) {
                for (col in r.left until r.right) {
                    values.add(bytes[row * page.rowBytes + col])
                }
            }
            assertEquals(1, values.size, "Placement ${placement.itemKey} must have uniform coverage")
            assertTrue(values.first() > 0, "Coverage must be non-zero")
        }
    }

    @Test
    fun `second page has different identity than first`() {
        val p0 = GPUPreparedTextPreflightFixture.baselinePage0()
        val p1 = GPUPreparedTextPreflightFixture.baselinePage1()

        assertNotEquals(p0.artifactKey, p1.artifactKey)
        assertEquals(0, p0.pageIndex)
        assertEquals(1, p1.pageIndex)
    }

    @Test
    fun `material program is compiled by the shared Task 3 authority`() {
        val material = GPUPreparedTextPreflightFixture.baselineMaterialProgram()

        assertTrue(material.materialKey.isNotBlank(), "materialKey must not be blank")
        assertTrue(material.abiHash.isNotBlank(), "abiHash must not be blank")
        assertTrue(material.wgslSource.isNotBlank(), "wgslSource must not be blank")
        assertTrue(material.entryPoint.isNotBlank(), "entryPoint must not be blank")
        assertEquals(1f, material.paintAlpha)
    }

    @Test
    fun `paint alpha changes material key but not ABI`() {
        val m1 = GPUPreparedTextPreflightFixture.baselineMaterialProgram(paintAlpha = 0.5f)
        val m2 = GPUPreparedTextPreflightFixture.baselineMaterialProgram(paintAlpha = 1f)

        // MaterialKey includes paintAlpha per Task 3 contract
        assertNotEquals(m1.materialKey, m2.materialKey)
        // ABI hash excludes paintAlpha per Task 3 contract — only source, entry, uniforms, resources
        assertEquals(m1.abiHash, m2.abiHash)
        // paintAlpha itself differs
        assertNotEquals(m1.paintAlpha, m2.paintAlpha)
    }

    // ===================================================================
    // 3. Invariant coverage
    // ===================================================================

    @Test
    fun `matrix covers every violation kind exactly once`() {
        val covered = GPUPreparedTextPreflightMutationMatrix.orderedMutations
            .map { it.violationKind }
        assertEquals(
            covered.size,
            covered.distinct().size,
            "Every violation kind must appear exactly once",
        )
        val allKinds = GPUPreparedTextViolationKind.entries
        assertEquals(
            allKinds.size,
            covered.size,
            "Matrix must cover every declared violation kind",
        )
        assertEquals(
            allKinds.toSet(),
            covered.toSet(),
            "Matrix must not contain undeclared violation kinds",
        )
    }

    @Test
    fun `generation identity is the earliest priority`() {
        val first = GPUPreparedTextPreflightMutationMatrix.orderedMutations.first()
        assertEquals(
            GPUPreparedTextPreflightCategory.GENERATION_IDENTITY,
            first.category,
        )
    }

    @Test
    fun `matrix contains exactly 28 invariants`() {
        assertEquals(
            28,
            GPUPreparedTextPreflightMutationMatrix.totalMutations,
        )
    }

    // ===================================================================
    // 4. Deep immutability
    // ===================================================================

    @Test
    fun `page bytes refuse mutation and retain original values`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val original = page.bytes.toList()

        val threw = try {
            @Suppress("UNCHECKED_CAST")
            (page.bytes as MutableList<Int>).clear()
            false
        } catch (_: UnsupportedOperationException) {
            true
        }

        assertTrue(threw, "Mutating the returned bytes list must throw")
        assertEquals(original, page.bytes, "Original values must be preserved")
    }

    @Test
    fun `page placements refuse mutation and retain original values`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val original = page.placements.map { it.itemKey }

        val threw = try {
            @Suppress("UNCHECKED_CAST")
            (page.placements as MutableList<*>).clear()
            false
        } catch (_: UnsupportedOperationException) {
            true
        }

        assertTrue(threw, "Mutating the returned placements list must throw")
        assertEquals(original, page.placements.map { it.itemKey }, "Placements must be preserved")
    }

    @Test
    fun `instances refuse mutation and retain original values`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val instances = GPUPreparedTextPreflightFixture.baselineA8Instances(page)
        val originalIds = instances.map { it.glyphId }

        val threw = try {
            @Suppress("UNCHECKED_CAST")
            (instances as MutableList<*>).clear()
            false
        } catch (_: UnsupportedOperationException) {
            true
        }

        assertTrue(threw, "Mutating the returned instances list must throw")
        assertEquals(originalIds, instances.map { it.glyphId }, "Instances must be preserved")
    }

    @Test
    fun `R8 conversion is deterministic from identical pages`() {
        val page = GPUPreparedTextPreflightFixture.baselinePage0()
        val r8 = page.toPreparedR8UploadArtifact()
        val originalBytes = r8.tightBytesForUpload().copyOf()

        val page2 = GPUPreparedTextPreflightFixture.baselinePage0()
        val r8_2 = page2.toPreparedR8UploadArtifact()

        assertEquals(originalBytes.size, r8_2.tightBytesForUpload().size)
        originalBytes.forEachIndexed { i, b ->
            assertEquals(b, r8_2.tightBytesForUpload()[i], "Byte $i must match")
        }
    }

    // ===================================================================
    // 5. Determinism
    // ===================================================================

    @Test
    fun `two baseline page 0 artifacts are byte-equal`() {
        val a = GPUPreparedTextPreflightFixture.baselinePage0()
        val b = GPUPreparedTextPreflightFixture.baselinePage0()

        assertEquals(a.bytes, b.bytes)
        assertEquals(a.contentSha256, b.contentSha256)
        assertEquals(a.artifactKey.contentFingerprint, b.artifactKey.contentFingerprint)
        assertEquals(a.placements.map { it.itemKey }, b.placements.map { it.itemKey })
    }

    @Test
    fun `two material programs from same descriptor are identical`() {
        val a = GPUPreparedTextPreflightFixture.baselineMaterialProgram(paintAlpha = 1f)
        val b = GPUPreparedTextPreflightFixture.baselineMaterialProgram(paintAlpha = 1f)

        assertEquals(a.materialKey, b.materialKey)
        assertEquals(a.abiHash, b.abiHash)
        assertEquals(a.wgslSource, b.wgslSource)
        assertEquals(a.entryPoint, b.entryPoint)
        assertEquals(a.uniformBytes, b.uniformBytes)
    }
}
