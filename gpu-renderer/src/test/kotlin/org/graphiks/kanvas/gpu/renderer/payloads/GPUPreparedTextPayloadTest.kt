package org.graphiks.kanvas.gpu.renderer.payloads

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import kotlin.uuid.Uuid

class GPUPreparedTextPayloadTest {
    @Test
    fun `prepared material snapshots its uniform bytes before payload gathering`() {
        val mutableUniforms = mutableListOf(1, 2, 3, 4)
        val prepared = material(mutableUniforms)
        val expectedUniforms = prepared.uniformBytes

        mutableUniforms.fill(0)

        assertEquals(expectedUniforms, prepared.uniformBytes)
        assertTrue(
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (prepared.uniformBytes as MutableList<Int>).clear()
            }.isFailure,
        )
    }

    @Test
    fun `prepared material compiler aligns fragment and final uniform topology`() {
        val prepared = material(mutableListOf(1, 2, 3, 4))

        assertEquals(
            prepared.uniformBytes.size,
            prepared.composableFragment.uniformBinding?.minBindingSizeBytes,
        )
    }

    @Test
    fun `prepared ColorGlyph consumes padded shared R8 directly and hashes its exact key`() {
        val paddedBytes = byteArrayOf(0x10, 0, 0x20, 0)
        val planKey = artifactKey("550e8400-e29b-41d4-a716-446655440051", "color-plan")
        val atlasKey = artifactKey("550e8400-e29b-41d4-a716-446655440052", "atlas-page")
        val layer = GPUColorGlyphLayerPayloadInput(
            planArtifactKey = planKey,
            layerGlyphID = 11u,
            paletteIndex = 2,
            atlasBounds = GPUPixelBounds(0, 0, 1, 2),
            deviceBounds = GPUPixelBounds(1, 1, 5, 5),
            premultipliedRgba = floatArrayOf(0f, 0.5f, 0f, 0.5f),
            useForeground = false,
            foregroundResolved = true,
            placementProof = GPUColorGlyphAtlasPlacementProofInput(
                atlasArtifactKey = atlasKey,
                strikeGlyphId = 11,
                strikeSize = 16f,
                strikeSubpixelX = 0,
                strikeSubpixelY = 0,
                atlasBounds = GPUPixelBounds(0, 0, 1, 2),
            ),
            colorLayerIndex = 1,
        )
        fun input(atlas: GPUPreparedR8UploadArtifact) = GPUPreparedColorGlyphPayloadInput(
            commandIdValue = 4,
            planArtifactKey = planKey,
            atlasArtifactKey = atlasKey,
            atlas = atlas,
            instances = listOf(
                instance(
                    deviceQuad = listOf(1f, 1f, 5f, 1f, 5f, 5f, 1f, 5f),
                    uvRect = GPUTextFloatRect(0f, 0f, 1f, 1f),
                    colorLayerIndex = 1,
                ),
                instance(
                    deviceQuad = listOf(6f, 1f, 10f, 1f, 10f, 5f, 6f, 5f),
                    uvRect = GPUTextFloatRect(0f, 0f, 1f, 1f),
                    colorLayerIndex = 1,
                ),
            ),
            layers = listOf(
                layer,
                layer.copy(deviceBounds = GPUPixelBounds(6, 1, 10, 5)),
            ),
            material = material(mutableListOf(1, 2, 3, 4)),
            targetBounds = TARGET,
            scissorBounds = SCISSOR,
            clipIdentity = "prepared-text-clip:wide-open",
            blendPlanIdentity = "blend:src-over",
            capabilitySnapshotHash = "capability:unit",
            frameProvenance = GPUFrameProvenance.GmContent,
        )
        val firstAtlas = r8Artifact(
            bytes = paddedBytes,
            key = "prepared-text-a8-page:v1:padded-a",
            width = 1,
            height = 2,
            rowBytes = 2,
        )
        val secondAtlas = r8Artifact(
            bytes = paddedBytes,
            key = "prepared-text-a8-page:v1:padded-b",
            width = 1,
            height = 2,
            rowBytes = 2,
        )

        val first = GPUColorGlyphPayloadGatherer().gatherPreparedSemantic(input(firstAtlas))
        val second = GPUColorGlyphPayloadGatherer().gatherPreparedSemantic(input(secondAtlas))

        assertContentEquals(paddedBytes, first.atlas.tightBytesForUpload())
        assertEquals(2, first.atlas.rowBytes)
        assertEquals(listOf(1, 1), first.layers.map { it.colorLayerIndex })
        assertNotEquals(first.canonicalHash, second.canonicalHash)
        assertTrue(first.hasCanonicalHashIntegrity())

        val source = java.io.File(
            repositoryRoot(),
            "gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt",
        ).readText()
        val preparedGatherer = source.substringAfter("fun gatherPreparedSemantic(")
            .substringBefore("\n    fun gatherSemantic(")
        assertTrue(
            "atlas.tightBytesForUpload()" !in preparedGatherer,
            "Prepared COLRv0 gathering must consume the shared R8 artifact without a legacy byte round-trip.",
        )
    }

    @Test
    fun `prepared ColorGlyph snapshots shared R8 instances layers material and exact layer index`() {
        val atlasBytes = byteArrayOf(0, 1, 128.toByte(), 255.toByte())
        val atlas = r8Artifact(atlasBytes)
        val instances = mutableListOf(instance(colorLayerIndex = 1))
        val color = floatArrayOf(0f, 0.5f, 0f, 0.5f)
        val planKey = artifactKey("550e8400-e29b-41d4-a716-446655440051", "color-plan")
        val atlasKey = artifactKey("550e8400-e29b-41d4-a716-446655440052", "atlas-page")
        val layers = mutableListOf(
            GPUColorGlyphLayerPayloadInput(
                planArtifactKey = planKey,
                layerGlyphID = 11u,
                paletteIndex = 2,
                atlasBounds = GPUPixelBounds(0, 0, 1, 2),
                deviceBounds = GPUPixelBounds(1, 1, 5, 5),
                premultipliedRgba = color,
                useForeground = false,
                foregroundResolved = true,
                placementProof = GPUColorGlyphAtlasPlacementProofInput(
                    atlasArtifactKey = atlasKey,
                    strikeGlyphId = 11,
                    strikeSize = 16f,
                    strikeSubpixelX = 0,
                    strikeSubpixelY = 0,
                    atlasBounds = GPUPixelBounds(0, 0, 1, 2),
                ),
                colorLayerIndex = 1,
            ),
        )
        val uniforms = mutableListOf(1, 2, 3, 4)
        val material = material(uniforms)
        val expectedUniforms = material.uniformBytes

        val input = GPUPreparedColorGlyphPayloadInput(
            commandIdValue = 4,
            planArtifactKey = planKey,
            atlasArtifactKey = atlasKey,
            atlas = atlas,
            instances = instances,
            layers = layers,
            material = material,
            targetBounds = TARGET,
            scissorBounds = SCISSOR,
            clipIdentity = "prepared-text-clip:wide-open",
            blendPlanIdentity = "blend:src-over",
            capabilitySnapshotHash = "capability:unit",
            frameProvenance = GPUFrameProvenance.GmContent,
        )
        val gatherer = GPUColorGlyphPayloadGatherer()
        val semantic = gatherer.gatherPreparedSemantic(input)
        val hash = semantic.canonicalHash

        assertFailsWith<IllegalArgumentException> {
            gatherer.gatherPreparedSemantic(
                input.copy(instances = listOf(instance(colorLayerIndex = 0))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gatherer.gatherPreparedSemantic(
                input.copy(instances = listOf(instance(glyphId = 12, colorLayerIndex = 1))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            gatherer.gatherPreparedSemantic(
                input.copy(
                    instances = listOf(
                        instance(
                            uvRect = GPUTextFloatRect(0f, 0f, 1f, 1f),
                            colorLayerIndex = 1,
                        ),
                    ),
                ),
            )
        }

        atlasBytes.fill(0)
        instances.clear()
        color.fill(1f)
        layers.clear()
        uniforms.fill(0)

        assertContentEquals(byteArrayOf(0, 1, 128.toByte(), 255.toByte()), semantic.atlas.tightBytesForUpload())
        assertEquals(listOf(1), semantic.instances.map { it.colorLayerIndex })
        assertEquals(listOf(1), semantic.layers.map { it.colorLayerIndex })
        assertEquals(expectedUniforms, semantic.material!!.uniformBytes)
        assertEquals(hash, semantic.canonicalHash)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `TextA8 snapshots atlas instances material and generation`() {
        val atlasBytes = byteArrayOf(0, 1, 128.toByte(), 255.toByte())
        val atlas = r8Artifact(atlasBytes)
        val instances = mutableListOf(instance())
        val uniformBytes = mutableListOf(1, 2, 3, 4)
        val material = material(uniformBytes)
        val expectedUniforms = material.uniformBytes
        val input = GPUPreparedTextA8PayloadInput(
            commandIdValue = 3,
            atlas = atlas,
            atlasGeneration = GPUTextArtifactGeneration(7),
            pageIndex = 0,
            instances = instances,
            material = material,
            targetBounds = TARGET,
            scissorBounds = SCISSOR,
            clipIdentity = "prepared-text-clip:wide-open",
            blendPlanIdentity = "blend:src-over",
            capabilitySnapshotHash = "capability:unit",
            frameProvenance = GPUFrameProvenance.GmContent,
        )

        val semantic = GPUPreparedTextPayloadGatherer().gather(input)
        val stableHash = semantic.canonicalHash

        atlasBytes.fill(0)
        instances.clear()
        uniformBytes.fill(0)

        assertContentEquals(byteArrayOf(0, 1, 128.toByte(), 255.toByte()), semantic.atlas.tightBytesForUpload())
        assertEquals(GPUTextArtifactGeneration(7), semantic.atlasGeneration)
        assertEquals(1, semantic.instances.size)
        assertEquals(expectedUniforms, semantic.material.uniformBytes)
        assertNotSame(material, semantic.material)
        assertEquals(stableHash, semantic.canonicalHash)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `prepared material snapshot preserves value semantics without exposing copy`() {
        val source = material(mutableListOf(1, 2, 3, 4))
        val snapshot = GPUPreparedTextPayloadGatherer().gather(
            input().copy(material = source),
        ).material

        assertNotSame(source, snapshot)
        assertEquals(source, snapshot)
        assertEquals(source.hashCode(), snapshot.hashCode())
        assertEquals(source.toString(), snapshot.toString())
        assertEquals(source.materialKey, source.component1())
        assertEquals(source.wgslSource, source.component2())
        assertEquals(source.entryPoint, source.component3())
        assertEquals(source.composableFragment, source.component4())
        assertEquals(source.uniformBytes, source.component5())
        assertEquals(source.sampledResources, source.component6())
        assertEquals(source.paintAlpha, source.component7())
        assertEquals(source.sourceKind, source.component8())
        assertEquals(source.abiHash, source.component9())

        val publicMethodNames = GPUPreparedMaterialProgram::class.java.methods
            .map { method -> method.name }
            .toSet()
        assertTrue((1..9).all { index -> "component$index" in publicMethodNames })
        assertTrue(publicMethodNames.none { name -> name == "copy" || name.startsWith("copy$") })
    }

    @Test
    fun `prepared material snapshot refuses WGSL detached from its admitted program facts`() {
        val source = material(mutableListOf(1, 2, 3, 4))
        source.javaClass.getDeclaredField("wgslSource").run {
            isAccessible = true
            set(source, "invalid-unparsed-wgsl")
        }

        assertFailsWith<IllegalArgumentException> {
            source.authenticatedSnapshot()
        }
    }

    @Test
    fun `prepared material snapshot refuses payload and final ABI detached from admission`() {
        val modifiedUniforms = material(mutableListOf(1, 2, 3, 4))
        modifiedUniforms.javaClass.getDeclaredField("uniformBytes").run {
            isAccessible = true
            set(
                modifiedUniforms,
                modifiedUniforms.uniformBytes.mapIndexed { index, value ->
                    if (index == 0) value xor 0x7f else value
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            modifiedUniforms.authenticatedSnapshot()
        }

        val modifiedAbi = material(mutableListOf(1, 2, 3, 4))
        modifiedAbi.javaClass.getDeclaredField("abiHash").run {
            isAccessible = true
            set(modifiedAbi, "sha256:" + "0".repeat(64))
        }
        assertFailsWith<IllegalArgumentException> {
            modifiedAbi.authenticatedSnapshot()
        }
    }

    @Test
    fun `TextA8 canonical hash covers exact semantic facts`() {
        val gatherer = GPUPreparedTextPayloadGatherer()
        val base = input()

        val original = gatherer.gather(base)
        val changedInstance = gatherer.gather(
            base.copy(
                instances = listOf(
                    instance(
                        deviceQuad = listOf(2f, 1f, 6f, 1f, 6f, 5f, 2f, 5f),
                    ),
                ),
            ),
        )
        val changedMaterial = gatherer.gather(
            base.copy(material = material(mutableListOf(9, 2, 3, 4))),
        )
        val changedSourceOccurrence = gatherer.gather(
            base.copy(
                instances = listOf(instance(sourceGlyphIndex = 1)),
            ),
        )
        val changedClip = gatherer.gather(
            base.copy(
                scissorBounds = GPUPixelBounds(1, 1, 31, 31),
                clipIdentity = "prepared-text-clip:rect",
            ),
        )

        assertTrue(original.canonicalHash != changedInstance.canonicalHash)
        assertTrue(original.canonicalHash != changedMaterial.canonicalHash)
        assertTrue(original.canonicalHash != changedSourceOccurrence.canonicalHash)
        assertTrue(original.canonicalHash != changedClip.canonicalHash)
        assertTrue(changedInstance.hasCanonicalHashIntegrity())
        assertTrue(changedMaterial.hasCanonicalHashIntegrity())
        assertTrue(changedSourceOccurrence.hasCanonicalHashIntegrity())
        assertTrue(changedClip.hasCanonicalHashIntegrity())
    }

    private fun input(): GPUPreparedTextA8PayloadInput = GPUPreparedTextA8PayloadInput(
        commandIdValue = 3,
        atlas = r8Artifact(byteArrayOf(0, 1, 128.toByte(), 255.toByte())),
        atlasGeneration = GPUTextArtifactGeneration(7),
        pageIndex = 0,
        instances = listOf(instance()),
        material = material(mutableListOf(1, 2, 3, 4)),
        targetBounds = TARGET,
        scissorBounds = SCISSOR,
        clipIdentity = "prepared-text-clip:wide-open",
        blendPlanIdentity = "blend:src-over",
        capabilitySnapshotHash = "capability:unit",
        frameProvenance = GPUFrameProvenance.GmContent,
    )

    private fun r8Artifact(
        bytes: ByteArray,
        key: String = "prepared-text-a8-page:v1:unit",
        width: Int = 2,
        height: Int = 2,
        rowBytes: Int = 2,
    ): GPUPreparedR8UploadArtifact =
        GPUPreparedR8UploadArtifact(
            key = key,
            width = width,
            height = height,
            rowBytes = rowBytes,
            generation = 7,
            contentHash = sha256(bytes),
            bytes = bytes,
        )

    private fun instance(
        glyphId: Int = 11,
        deviceQuad: List<Float> = listOf(1f, 1f, 5f, 1f, 5f, 5f, 1f, 5f),
        uvRect: GPUTextFloatRect = GPUTextFloatRect(0f, 0f, 0.5f, 1f),
        colorLayerIndex: Int? = null,
        sourceGlyphIndex: Int = 0,
    ): GPUTextA8Instance = GPUTextA8Instance.create(
        glyphId = glyphId,
        sourceGlyphIndex = GPUTextSourceGlyphIndex(sourceGlyphIndex),
        deviceQuad = deviceQuad,
        uvRect = uvRect,
        pageIndex = 0,
        colorLayerIndex = colorLayerIndex,
    )

    private fun artifactKey(uuid: String, fingerprint: String): GPUTextArtifactKey =
        GPUTextArtifactKey(
            artifactID = GPUTextArtifactID(Uuid.parse(uuid)),
            generation = GPUTextArtifactGeneration(7),
            contentFingerprint = fingerprint,
        )

    private fun material(
        uniformBytes: MutableList<Int>,
    ): GPUPreparedMaterialProgram {
        val channels = List(4) { index ->
            (uniformBytes.getOrElse(index) { 0 } and 0xff) / 255f
        }
        val result = GPUPreparedMaterialProgramCompiler.compile(
            descriptor = GPUMaterialDescriptor.SolidColor(
                r = channels[0],
                g = channels[1],
                b = channels[2],
                a = channels[3],
            ),
            paintAlpha = 0.5f,
            context = GPUMaterialLoweringContext(
                capabilityClass = "prepared-text-payload-test",
                targetFormatClass = "rgba8unorm",
                dictionaryVersion = "material-dictionary:prepared-text-test:v1",
            ),
        )
        return checkNotNull((result as? GPUPreparedMaterialProgramResult.Ready)?.program) {
            "The admitted payload material must compile: $result"
        }
    }

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun repositoryRoot(): java.io.File =
        generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { candidate -> java.io.File(candidate, "settings.gradle.kts").isFile }

    private companion object {
        val TARGET = GPUPixelBounds(0, 0, 32, 32)
        val SCISSOR = GPUPixelBounds(0, 0, 32, 32)
    }
}
