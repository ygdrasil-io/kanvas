package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPageArtifact
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.toPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat

class GPUR8FrameResourcePlanTest {
    @Test
    fun `R8 artifact snapshots caller and getter bytes with exact metadata and hash`() {
        val callerBytes = byteArrayOf(0, 1, 128.toByte(), 255.toByte())
        val artifact = prepareR8(
            key = "page-0",
            width = 2,
            height = 2,
            rowBytes = 2,
            generation = 3,
            bytes = callerBytes,
        )

        callerBytes.fill(0)
        val getterBytes = artifact.tightBytesForUpload()
        getterBytes.fill(7)

        assertEquals("page-0", artifact.key)
        assertEquals(2, artifact.width)
        assertEquals(2, artifact.height)
        assertEquals(2, artifact.rowBytes)
        assertEquals(3, artifact.generation)
        assertEquals(
            sha256(byteArrayOf(0, 1, 128.toByte(), 255.toByte())),
            artifact.contentHash,
        )
        assertContentEquals(
            byteArrayOf(0, 1, 128.toByte(), 255.toByte()),
            artifact.tightBytesForUpload(),
        )
        assertNotSame(getterBytes, artifact.tightBytesForUpload())
    }

    @Test
    fun `R8 artifact rejects invalid layout generation hash and capacity before snapshot`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val hash = sha256(bytes)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact("", 2, 2, 2, 0, hash, bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact("page", 0, 2, 2, 0, hash, bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact("page", 2, 0, 2, 0, hash, bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact("page", 3, 2, 2, 0, hash, bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact("page", 2, 2, 2, -1, hash, bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact("page", 2, 2, 2, 0, "0".repeat(64), bytes)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact("page", 2, 2, 2, 0, hash, bytes.copyOf(3))
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedR8UploadArtifact(
                key = "page",
                width = Int.MAX_VALUE,
                height = Int.MAX_VALUE,
                rowBytes = Int.MAX_VALUE,
                generation = 0,
                contentHash = sha256(byteArrayOf()),
                bytes = byteArrayOf(),
            )
        }
    }

    @Test
    fun `atlas page adapter transfers canonical identity generation layout hash and bytes`() {
        val bytes = listOf(0, 1, 128, 255)
        val contentHash = GPUTextA8AtlasPageArtifact.sha256(bytes)
        val artifactKey = GPUTextArtifactKey(
            artifactID = GPUTextArtifactID(
                Uuid.parse("550e8400-e29b-41d4-a716-446655440506"),
            ),
            generation = GPUTextArtifactGeneration(7),
            contentFingerprint = GPUTextA8AtlasPageArtifact.contentFingerprint(
                width = 2,
                height = 2,
                rowBytes = 2,
                contentSha256 = contentHash,
                placements = emptyList(),
            ),
        )
        val page = GPUTextA8AtlasPageArtifact.create(
            artifactKey = artifactKey,
            pageIndex = 0,
            width = 2,
            height = 2,
            rowBytes = 2,
            bytes = bytes,
            contentSha256 = contentHash,
            placements = emptyList(),
        )

        val artifact = page.toPreparedR8UploadArtifact()

        assertTrue(artifact.key.contains(artifactKey.artifactID.value.toString()))
        assertTrue(artifact.key.contains("pageIndex=0"))
        assertTrue(artifact.key.contains(artifactKey.contentFingerprint))
        assertEquals(artifactKey.generation.value.toLong(), artifact.generation)
        assertEquals(page.width, artifact.width)
        assertEquals(page.height, artifact.height)
        assertEquals(page.rowBytes, artifact.rowBytes)
        assertEquals(page.contentSha256, artifact.contentHash)
        assertContentEquals(
            byteArrayOf(0, 1, 128.toByte(), 255.toByte()),
            artifact.tightBytesForUpload(),
        )
    }

    @Test
    fun `atlas pages sharing artifact ID and generation retain distinct page identities and resources`() {
        val firstPage = atlasPage(
            pageIndex = 0,
            generation = 7,
            bytes = listOf(1, 2, 3, 4),
        )
        val secondPage = atlasPage(
            pageIndex = 1,
            generation = 7,
            bytes = listOf(4, 3, 2, 1),
        )
        val firstArtifact = firstPage.toPreparedR8UploadArtifact()
        val secondArtifact = secondPage.toPreparedR8UploadArtifact()
        val firstPlan = buildR8FrameResourcePlan(
            firstArtifact,
            capabilities(),
            "frame-page-identity",
        )
        val secondPlan = buildR8FrameResourcePlan(
            secondArtifact,
            capabilities(),
            "frame-page-identity",
        )

        assertNotEquals(firstArtifact.key, secondArtifact.key)
        assertTrue(firstArtifact.key.contains(firstPage.artifactKey.artifactID.value.toString()))
        assertTrue(firstArtifact.key.contains("pageIndex=0"))
        assertTrue(firstArtifact.key.contains(firstPage.artifactKey.contentFingerprint))
        assertTrue(secondArtifact.key.contains("pageIndex=1"))
        assertTrue(secondArtifact.key.contains(secondPage.artifactKey.contentFingerprint))
        assertNotEquals(firstPlan.stagingRef, secondPlan.stagingRef)
        assertNotEquals(firstPlan.frameTextureRef, secondPlan.frameTextureRef)
        assertNotEquals(
            firstPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
            secondPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
        )
    }

    @Test
    fun `same atlas page on two generations has distinct resource refs and allocation labels`() {
        val firstArtifact = atlasPage(
            pageIndex = 0,
            generation = 7,
            bytes = listOf(1, 2, 3, 4),
        ).toPreparedR8UploadArtifact()
        val secondArtifact = atlasPage(
            pageIndex = 0,
            generation = 8,
            bytes = listOf(1, 2, 3, 4),
        ).toPreparedR8UploadArtifact()
        val firstPlan = buildR8FrameResourcePlan(
            firstArtifact,
            capabilities(),
            "frame-generation-identity",
        )
        val secondPlan = buildR8FrameResourcePlan(
            secondArtifact,
            capabilities(),
            "frame-generation-identity",
        )

        assertEquals(firstArtifact.key, secondArtifact.key)
        assertNotEquals(firstPlan.stagingRef, secondPlan.stagingRef)
        assertNotEquals(firstPlan.frameTextureRef, secondPlan.frameTextureRef)
        assertNotEquals(
            firstPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
            secondPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
        )
    }

    @Test
    fun `R8 resource identity includes content hash even when key and generation are reused`() {
        val firstBytes = byteArrayOf(1, 2, 3, 4)
        val secondBytes = byteArrayOf(4, 3, 2, 1)
        val firstPlan = buildR8FrameResourcePlan(
            GPUPreparedR8UploadArtifact(
                key = "reused-key",
                width = 2,
                height = 2,
                rowBytes = 2,
                generation = 4,
                contentHash = sha256(firstBytes),
                bytes = firstBytes,
            ),
            capabilities(),
            "frame-content-identity",
        )
        val secondPlan = buildR8FrameResourcePlan(
            GPUPreparedR8UploadArtifact(
                key = "reused-key",
                width = 2,
                height = 2,
                rowBytes = 2,
                generation = 4,
                contentHash = sha256(secondBytes),
                bytes = secondBytes,
            ),
            capabilities(),
            "frame-content-identity",
        )

        assertNotEquals(firstPlan.stagingRef, secondPlan.stagingRef)
        assertNotEquals(firstPlan.frameTextureRef, secondPlan.frameTextureRef)
        assertNotEquals(
            firstPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
            secondPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
        )
    }

    @Test
    fun `R8 resource identity includes exact layout provenance`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val firstPlan = buildR8FrameResourcePlan(
            GPUPreparedR8UploadArtifact(
                key = "layout-key",
                width = 2,
                height = 2,
                rowBytes = 2,
                generation = 4,
                contentHash = sha256(bytes),
                bytes = bytes,
            ),
            capabilities(),
            "frame-layout-identity",
        )
        val secondPlan = buildR8FrameResourcePlan(
            GPUPreparedR8UploadArtifact(
                key = "layout-key",
                width = 1,
                height = 2,
                rowBytes = 2,
                generation = 4,
                contentHash = sha256(bytes),
                bytes = bytes,
            ),
            capabilities(),
            "frame-layout-identity",
        )

        assertNotEquals(firstPlan.stagingRef, secondPlan.stagingRef)
        assertNotEquals(firstPlan.frameTextureRef, secondPlan.frameTextureRef)
        assertNotEquals(
            firstPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
            secondPlan.memoryAllocations.map(GPUFrameMemoryAllocation::label),
        )
    }

    @Test
    fun `R8 staging copies only width bytes per row and zeroes WebGPU padding`() {
        val artifact = prepareR8(
            key = "page-padded",
            width = 3,
            height = 2,
            rowBytes = 5,
            generation = 9,
            bytes = byteArrayOf(1, 2, 3, 99, 98, 4, 5, 6, 97, 96),
        )

        val plan = buildR8FrameResourcePlan(
            artifact = artifact,
            capabilities = capabilities(),
            frameIdentity = "frame-r8",
        )
        val upload = plan.bytesForUpload()

        assertEquals(256, plan.uploadTaskLayout.bytesPerRow)
        assertEquals(2, plan.uploadTaskLayout.rowsPerImage)
        assertEquals(512, plan.uploadTaskLayout.byteSize)
        assertEquals(512, upload.size)
        assertContentEquals(byteArrayOf(1, 2, 3), upload.copyOfRange(0, 3))
        assertTrue(upload.copyOfRange(3, 256).all { it == 0.toByte() })
        assertContentEquals(byteArrayOf(4, 5, 6), upload.copyOfRange(256, 259))
        assertTrue(upload.copyOfRange(259, 512).all { it == 0.toByte() })

        assertEquals(
            listOf(GPUFrameResourceRole.UploadStaging, GPUFrameResourceRole.GlyphAtlas),
            plan.preparationRequests.map(GPUResourcePreparationRequest::role),
        )
        val textureDescriptor =
            plan.preparationRequests[1].descriptor as GPUFrameTextureDescriptor
        assertEquals(GPUColorFormat("r8unorm"), textureDescriptor.format)
        assertEquals(6, plan.preparationRequests[1].byteSize)
        assertEquals(
            setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
            plan.preparationRequests[1].usages,
        )
    }

    @Test
    fun `R8 staging stride satisfies WebGPU and observed device alignment`() {
        val artifact = prepareR8(
            key = "page-alignment",
            width = 3,
            height = 1,
            rowBytes = 3,
            generation = 0,
            bytes = byteArrayOf(1, 2, 3),
        )

        val alignment512 = buildR8FrameResourcePlan(
            artifact,
            capabilities(copyBytesPerRowAlignment = 512),
            "frame-alignment-512",
        )
        val alignment384 = buildR8FrameResourcePlan(
            artifact,
            capabilities(copyBytesPerRowAlignment = 384),
            "frame-alignment-384",
        )

        assertEquals(512, alignment512.uploadTaskLayout.bytesPerRow)
        assertEquals(512, alignment512.uploadTaskLayout.byteSize)
        assertEquals(768, alignment384.uploadTaskLayout.bytesPerRow)
        assertEquals(768, alignment384.uploadTaskLayout.byteSize)
        assertEquals(0, alignment512.uploadTaskLayout.bytesPerRow % 256)
        assertEquals(0, alignment512.uploadTaskLayout.bytesPerRow % 512)
        assertEquals(0, alignment384.uploadTaskLayout.bytesPerRow % 256)
        assertEquals(0, alignment384.uploadTaskLayout.bytesPerRow % 384)
    }

    @Test
    fun `R8 plan rejects texture and staging limits before allocation`() {
        val artifact = prepareR8(
            key = "page-limit",
            width = 3,
            height = 2,
            rowBytes = 3,
            generation = 0,
            bytes = byteArrayOf(1, 2, 3, 4, 5, 6),
        )

        assertFailsWith<IllegalArgumentException> {
            buildR8FrameResourcePlan(
                artifact = artifact,
                capabilities = capabilities(maxTextureDimension2D = 2),
                frameIdentity = "frame-r8-limit",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            buildR8FrameResourcePlan(
                artifact = artifact,
                capabilities = capabilities(maxBufferSize = 511),
                frameIdentity = "frame-r8-limit",
            )
        }
    }

    @Test
    fun `R8 plan identities collections and bytes are deterministic and immutable`() {
        val artifact = prepareR8(
            key = "page-immutable",
            width = 2,
            height = 2,
            rowBytes = 2,
            generation = 4,
            bytes = byteArrayOf(1, 2, 3, 4),
        )
        val first = buildR8FrameResourcePlan(artifact, capabilities(), "frame-stable")
        val second = buildR8FrameResourcePlan(artifact, capabilities(), "frame-stable")

        assertEquals(first.stagingRef, second.stagingRef)
        assertEquals(first.frameTextureRef, second.frameTextureRef)
        assertEquals(
            first.preparationRequests.map(GPUResourcePreparationRequest::diagnosticLabel),
            second.preparationRequests.map(GPUResourcePreparationRequest::diagnosticLabel),
        )
        assertEquals(
            first.memoryAllocations.map(GPUFrameMemoryAllocation::label),
            second.memoryAllocations.map(GPUFrameMemoryAllocation::label),
        )
        val mutated = first.bytesForUpload().also { it[0] = 99 }
        assertTrue(mutated[0] != first.bytesForUpload()[0])
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (first.preparationRequests as MutableList<GPUResourcePreparationRequest>).clear()
        }
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (first.memoryAllocations as MutableList<GPUFrameMemoryAllocation>).clear()
        }
    }

    private fun prepareR8(
        key: String,
        width: Int,
        height: Int,
        rowBytes: Int,
        generation: Long,
        bytes: ByteArray,
    ): GPUPreparedR8UploadArtifact = GPUPreparedR8UploadArtifact(
        key = key,
        width = width,
        height = height,
        rowBytes = rowBytes,
        generation = generation,
        contentHash = sha256(bytes),
        bytes = bytes,
    )

    private fun capabilities(
        maxTextureDimension2D: Long = 8192,
        maxBufferSize: Long = 1L shl 30,
        copyBytesPerRowAlignment: Long = 256,
    ) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = emptyList(),
        snapshotId = "r8-resource-plan",
        limits = GPULimits(
            maxTextureDimension2D = maxTextureDimension2D,
            copyBytesPerRowAlignment = copyBytesPerRowAlignment,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun atlasPage(
        pageIndex: Int,
        generation: Int,
        bytes: List<Int>,
    ): GPUTextA8AtlasPageArtifact {
        val contentHash = GPUTextA8AtlasPageArtifact.sha256(bytes)
        val fingerprint = GPUTextA8AtlasPageArtifact.contentFingerprint(
            width = 2,
            height = 2,
            rowBytes = 2,
            contentSha256 = contentHash,
            placements = emptyList(),
        )
        return GPUTextA8AtlasPageArtifact.create(
            artifactKey = GPUTextArtifactKey(
                artifactID = GPUTextArtifactID(
                    Uuid.parse("550e8400-e29b-41d4-a716-446655440506"),
                ),
                generation = GPUTextArtifactGeneration(generation),
                contentFingerprint = fingerprint,
            ),
            pageIndex = pageIndex,
            width = 2,
            height = 2,
            rowBytes = 2,
            bytes = bytes,
            contentSha256 = contentHash,
            placements = emptyList(),
        )
    }
}
