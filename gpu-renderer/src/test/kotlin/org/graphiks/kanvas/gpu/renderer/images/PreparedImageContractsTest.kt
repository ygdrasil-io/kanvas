package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class PreparedImageContractsTest {
    @Test
    fun `factory snapshots A8 bytes and expands tight premultiplied upload bytes`() {
        val callerBytes = byteArrayOf(1, 2, 3)
        val artifact = ready(
            GPUPreparedImageSourceInput(
                GPUPreparedImageSourceClass.DecodedCpu, "ignored", 3, 1,
                GPUPreparedImageSourceFormat.A8, AlphaType.PREMUL, 3,
                GPUPreparedImageProfile.Srgb, GPUPreparedImageOrientation.AppliedIdentity,
                GPUPreparedImageProvenance.CallerPixels, 7, callerBytes,
            ),
        )
        callerBytes[0] = 99

        assertEquals(3L, artifact.pixelLayout.sourceRowBytes)
        assertEquals(12L, artifact.pixelLayout.normalizedRgba8RowBytes)
        assertContentEquals(
            byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3),
            artifact.tightRgba8BytesForUpload(),
        )
        val returned = artifact.tightRgba8BytesForUpload()
        returned[0] = 55
        assertEquals(1, artifact.tightRgba8BytesForUpload()[0])
    }

    @Test
    fun `factory converts BGRA and accepts only authoritative opaque RGBA`() {
        assertContentEquals(byteArrayOf(1, 2, 3, 4), ready(input()).tightRgba8BytesForUpload())
        val bgra = ready(input(format = GPUPreparedImageSourceFormat.Bgra8, alpha = AlphaType.PREMUL, bytes = byteArrayOf(3, 2, 1, 4)))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), bgra.tightRgba8BytesForUpload())
        assertIs<GPUPreparedImageArtifactResult.Ready>(GPUPreparedImageArtifactFactory.prepare(input(alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1, 2, 3, -1))))
        assertRefusal(input(alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1, 2, 3, 4)), "image.alpha.opaque_bytes")
    }

    @Test
    fun `factory refuses every unsupported source authority and metadata route`() {
        assertRefusal(input(alpha = AlphaType.UNPREMUL), "image.alpha.unpremul")
        assertRefusal(input(format = GPUPreparedImageSourceFormat.A8, alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1)), "image.alpha.a8_requires_premul")
        assertRefusal(input(sourceClass = GPUPreparedImageSourceClass.Encoded), "image.source.class")
        assertRefusal(input(sourceClass = GPUPreparedImageSourceClass.Animated), "image.source.class")
        assertRefusal(input(sourceClass = GPUPreparedImageSourceClass.Yuv), "image.source.class")
        assertRefusal(input(sourceClass = GPUPreparedImageSourceClass.Hdr), "image.source.class")
        assertRefusal(input(sourceClass = GPUPreparedImageSourceClass.Imported), "image.source.class")
        assertRefusal(input(profile = GPUPreparedImageProfile.Other), "image.profile")
        assertRefusal(input(profile = GPUPreparedImageProfile.Unresolved), "image.profile")
        assertRefusal(input(orientation = GPUPreparedImageOrientation.Unresolved), "image.orientation")
        assertRefusal(input(format = GPUPreparedImageSourceFormat.Unsupported), "image.format")
    }

    @Test
    fun `upload key includes physical conversion facts but excludes source id and padding`() {
        val a = ready(input(sourceId = "one", sourceRowBytes = 8, bytes = byteArrayOf(1, 2, 3, 4, 9, 9, 9, 9)))
        val same = ready(input(sourceId = "two", sourceRowBytes = 8, bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertEquals(a.key, same.key)
        assertNotEquals(a.key, ready(input(bytes = byteArrayOf(2, 2, 3, 4))).key)
        assertNotEquals(a.key, ready(input(width = 2, sourceRowBytes = 8, bytes = byteArrayOf(1, 2, 3, 4, 1, 2, 3, 4))).key)
        assertNotEquals(a.key, ready(input(sourceRowBytes = 4, bytes = byteArrayOf(1, 2, 3, 4))).key)
        assertNotEquals(a.key, ready(input(format = GPUPreparedImageSourceFormat.Bgra8, bytes = byteArrayOf(3, 2, 1, 4))).key)
        assertNotEquals(a.key, ready(input(alpha = AlphaType.OPAQUE, bytes = byteArrayOf(1, 2, 3, -1))).key)
        assertNotEquals(a.key, ready(input(provenance = GPUPreparedImageProvenance.SurfaceReadback)).key)
        assertNotEquals(a.key, ready(input(generation = 8)).key)
    }

    private fun ready(input: GPUPreparedImageSourceInput): GPUPreparedImageUploadArtifact =
        assertIs<GPUPreparedImageArtifactResult.Ready>(GPUPreparedImageArtifactFactory.prepare(input)).artifact

    private fun assertRefusal(input: GPUPreparedImageSourceInput, code: String) {
        assertEquals(code, assertIs<GPUPreparedImageArtifactResult.Refused>(GPUPreparedImageArtifactFactory.prepare(input)).code)
    }

    private fun input(
        sourceClass: GPUPreparedImageSourceClass = GPUPreparedImageSourceClass.DecodedCpu,
        sourceId: String = "source",
        width: Int = 1,
        format: GPUPreparedImageSourceFormat = GPUPreparedImageSourceFormat.Rgba8,
        alpha: AlphaType = AlphaType.PREMUL,
        sourceRowBytes: Long = 4,
        profile: GPUPreparedImageProfile = GPUPreparedImageProfile.Srgb,
        orientation: GPUPreparedImageOrientation = GPUPreparedImageOrientation.AppliedIdentity,
        provenance: GPUPreparedImageProvenance = GPUPreparedImageProvenance.CallerPixels,
        generation: Long = 7,
        bytes: ByteArray = byteArrayOf(1, 2, 3, 4),
    ) = GPUPreparedImageSourceInput(sourceClass, sourceId, width, 1, format, alpha, sourceRowBytes, profile, orientation, provenance, generation, bytes)
}
