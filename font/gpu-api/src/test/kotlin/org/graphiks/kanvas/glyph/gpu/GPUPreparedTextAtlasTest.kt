package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUPreparedTextAtlasTest {
    @Test
    fun `shared packer preserves first-use order guard and row wrapping`() {
        val result = GPUTextAtlasRectPacker.pack(
            items = listOf(
                GPUTextAtlasRectItem("first", width = 2, height = 2, guardPx = 1),
                GPUTextAtlasRectItem("second", width = 2, height = 3, guardPx = 1),
                GPUTextAtlasRectItem("third", width = 3, height = 2, guardPx = 1),
            ),
            pageWidth = 8,
            pageHeight = 8,
            maxPages = 2,
        )

        val ready = assertIs<GPUTextAtlasPackingResult.Ready>(result)
        assertEquals(2, ready.pageCount)
        assertEquals(listOf("first", "second", "third"), ready.placements.map { it.itemKey })
        assertEquals(
            listOf(
                GPUTextIntRect(1, 1, 3, 3),
                GPUTextIntRect(5, 1, 7, 4),
                GPUTextIntRect(1, 1, 4, 3),
            ),
            ready.placements.map { it.contentRect },
        )
        assertEquals(listOf(0, 0, 1), ready.placements.map { it.pageIndex })
    }

    @Test
    fun `packer publishes no partial placements when page budget is exceeded`() {
        val result = GPUTextAtlasRectPacker.pack(
            items = listOf(
                GPUTextAtlasRectItem("one", 3, 3, 1),
                GPUTextAtlasRectItem("two", 3, 3, 1),
            ),
            pageWidth = 5,
            pageHeight = 5,
            maxPages = 1,
        )

        val refused = assertIs<GPUTextAtlasPackingResult.Refused>(result)
        assertEquals(GPUTextAtlasPackingRefusal.PAGE_LIMIT, refused.reason)
        assertEquals(emptyList(), refused.placements)
    }

    @Test
    fun `packer rejects an item that cannot fit one page including guard`() {
        val result = GPUTextAtlasRectPacker.pack(
            items = listOf(GPUTextAtlasRectItem("too-wide", 4, 1, 1)),
            pageWidth = 5,
            pageHeight = 5,
            maxPages = 2,
        )

        val refused = assertIs<GPUTextAtlasPackingResult.Refused>(result)
        assertEquals(GPUTextAtlasPackingRefusal.ITEM_TOO_LARGE, refused.reason)
        assertEquals("too-wide", refused.itemKey)
    }

    @Test
    fun `page artifact snapshots finalized bytes placements and content hash`() {
        val bytes = mutableListOf(0, 1, 128, 255)
        val placements = mutableListOf(
            GPUTextA8AtlasPlacement(
                itemKey = "mask-a",
                pageIndex = 0,
                allocationRect = GPUTextIntRect(0, 0, 2, 2),
                contentRect = GPUTextIntRect(0, 0, 2, 2),
            ),
        )
        val contentHash = "0ff830e8c68aca18063bce54c3191d5c116a2dfe33249538b252746cb777ef10"
        val page = GPUTextA8AtlasPageArtifact.create(
            artifactKey = artifactKey(
                GPUTextA8AtlasPageArtifact.contentFingerprint(
                    width = 2,
                    height = 2,
                    rowBytes = 2,
                    contentSha256 = contentHash,
                    placements = placements,
                ),
            ),
            pageIndex = 0,
            width = 2,
            height = 2,
            rowBytes = 2,
            bytes = bytes,
            contentSha256 = contentHash,
            placements = placements,
        )

        bytes.fill(7)
        placements.clear()
        assertEquals(listOf(0, 1, 128, 255), page.bytes)
        assertEquals(1, page.uniqueMaskCount())
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (page.bytes as MutableList<Int>)[0] = 9
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (page.placements as MutableList<GPUTextA8AtlasPlacement>).clear()
        }
    }

    @Test
    fun `instance snapshots mutable quad coordinates`() {
        val quad = mutableListOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f)
        val instance = GPUTextA8Instance.create(
            glyphId = 7,
            deviceQuad = quad,
            uvRect = GPUTextFloatRect(0.25f, 0.5f, 0.75f, 1f),
            pageIndex = 1,
            colorLayerIndex = 3,
        )
        quad.fill(0f)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f), instance.deviceQuad)
        assertEquals(3, instance.colorLayerIndex)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (instance.deviceQuad as MutableList<Float>)[0] = 0f
        }
    }

    @Test
    fun `page hashes distinguish a single finalized byte`() {
        val first = GPUTextA8AtlasPageArtifact.sha256(listOf(0, 1, 2, 3))
        val second = GPUTextA8AtlasPageArtifact.sha256(listOf(0, 1, 2, 4))

        assertNotEquals(first, second)
        assertEquals(first, GPUTextA8AtlasPageArtifact.sha256(listOf(0, 1, 2, 3)))
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `page fingerprint includes layout and ordered placements`() {
        val bytesHash = GPUTextA8AtlasPageArtifact.sha256(listOf(0, 1, 2, 3))
        val placement = GPUTextA8AtlasPlacement(
            itemKey = "mask",
            pageIndex = 0,
            allocationRect = GPUTextIntRect(0, 0, 2, 2),
            contentRect = GPUTextIntRect(0, 0, 2, 2),
        )
        val base = GPUTextA8AtlasPageArtifact.contentFingerprint(
            width = 2,
            height = 2,
            rowBytes = 2,
            contentSha256 = bytesHash,
            placements = listOf(placement),
        )

        assertNotEquals(
            base,
            GPUTextA8AtlasPageArtifact.contentFingerprint(
                width = 1,
                height = 4,
                rowBytes = 1,
                contentSha256 = bytesHash,
                placements = listOf(
                    placement.copy(
                        allocationRect = GPUTextIntRect(0, 0, 1, 2),
                        contentRect = GPUTextIntRect(0, 0, 1, 2),
                    ),
                ),
            ),
        )
        assertNotEquals(
            base,
            GPUTextA8AtlasPageArtifact.contentFingerprint(
                width = 2,
                height = 2,
                rowBytes = 2,
                contentSha256 = bytesHash,
                placements = listOf(placement.copy(itemKey = "other")),
            ),
        )
    }

    @Test
    fun `page artifact rejects mismatched identity bounds duplicate keys and overlap`() {
        val bytes = listOf(0, 0, 0, 0)
        val bytesHash = GPUTextA8AtlasPageArtifact.sha256(bytes)
        val valid = GPUTextA8AtlasPlacement(
            itemKey = "a",
            pageIndex = 0,
            allocationRect = GPUTextIntRect(0, 0, 1, 1),
            contentRect = GPUTextIntRect(0, 0, 1, 1),
        )
        fun create(
            placements: List<GPUTextA8AtlasPlacement>,
            fingerprint: String = GPUTextA8AtlasPageArtifact.contentFingerprint(
                width = 2,
                height = 2,
                rowBytes = 2,
                contentSha256 = bytesHash,
                placements = placements,
            ),
        ) = GPUTextA8AtlasPageArtifact.create(
            artifactKey = artifactKey(fingerprint),
            pageIndex = 0,
            width = 2,
            height = 2,
            rowBytes = 2,
            bytes = bytes,
            contentSha256 = bytesHash,
            placements = placements,
        )

        assertFailsWith<IllegalArgumentException> {
            create(listOf(valid), fingerprint = "wrong")
        }
        assertFailsWith<IllegalArgumentException> {
            create(
                listOf(
                    valid,
                    valid.copy(
                        allocationRect = GPUTextIntRect(1, 0, 2, 1),
                        contentRect = GPUTextIntRect(1, 0, 2, 1),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            create(
                listOf(
                    valid,
                    valid.copy(
                        itemKey = "b",
                        allocationRect = GPUTextIntRect(0, 0, 2, 2),
                        contentRect = GPUTextIntRect(0, 0, 2, 2),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            create(
                listOf(
                    valid.copy(
                        allocationRect = GPUTextIntRect(0, 0, 3, 1),
                        contentRect = GPUTextIntRect(0, 0, 3, 1),
                    ),
                ),
            )
        }
    }

    private fun artifactKey(fingerprint: String): GPUTextArtifactKey = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(
            Uuid.parse("550e8400-e29b-41d4-a716-446655440501"),
        ),
        generation = GPUTextArtifactGeneration(3),
        contentFingerprint = fingerprint,
    )
}
