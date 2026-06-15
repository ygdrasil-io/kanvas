package org.graphiks.kanvas.glyph.gpu

import org.graphiks.kanvas.font.TypefaceID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUTextArtifactsSurfaceTest {
    @Test
    fun `artifact references enumerate typed bundle artifacts as deterministic dumpable records`() {
        val atlasKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441001",
            generation = 1,
            contentFingerprint = "glyph-atlas-a8",
        )
        val sdfAtlasKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441002",
            generation = 2,
            contentFingerprint = "glyph-atlas-sdf",
        )
        val glyphUploadKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441003",
            generation = 3,
            contentFingerprint = "glyph-upload-plan",
        )
        val outlineKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441004",
            generation = 4,
            contentFingerprint = "outline-glyph-plan",
        )
        val colorKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441005",
            generation = 5,
            contentFingerprint = "color-glyph-plan",
        )
        val bitmapKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441006",
            generation = 6,
            contentFingerprint = "bitmap-glyph-plan",
        )
        val svgKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441007",
            generation = 7,
            contentFingerprint = "svg-glyph-plan",
        )
        val bundle = TextGPUArtifactBundle(
            artifactKey = fixtureArtifactKey(
                uuid = "550e8400-e29b-41d4-a716-446655441000",
                generation = 0,
                contentFingerprint = "bundle-root",
            ),
            uploadPlans = listOf(
                GPUTextUploadPlan(
                    artifactKey = glyphUploadKey,
                    ranges = listOf(GPUTextUploadRange(offset = 0, size = 16, label = "glyph-upload")),
                    byteSize = 16,
                ),
            ),
            glyphUploadPlans = listOf(
                GlyphUploadPlan(
                    artifactKey = glyphUploadKey,
                    uploadPlan = GPUTextUploadPlan(
                        artifactKey = glyphUploadKey,
                        ranges = listOf(GPUTextUploadRange(offset = 0, size = 16, label = "glyph-upload")),
                        byteSize = 16,
                    ),
                    glyphIDs = listOf(11U),
                ),
            ),
            outlineGlyphPlans = listOf(
                OutlineGlyphPlan(
                    artifactKey = outlineKey,
                    glyphIDs = listOf(12U),
                    windingRule = "non-zero",
                ),
            ),
            colorGlyphPlans = listOf(
                ColorGlyphPlan(
                    artifactKey = colorKey,
                    glyphIDs = listOf(13U),
                    layerCount = 2,
                ),
            ),
            bitmapGlyphPlans = listOf(
                BitmapGlyphPlan(
                    artifactKey = bitmapKey,
                    glyphIDs = listOf(14U),
                    colorFormat = "rgba8888",
                ),
            ),
            svgGlyphPlans = listOf(
                SVGGlyphPlan(
                    artifactKey = svgKey,
                    glyphIDs = listOf(15U),
                    documentCount = 1,
                ),
            ),
            atlases = listOf(
                GlyphAtlasArtifact(
                    artifactKey = atlasKey,
                    width = 128,
                    height = 128,
                    format = "r8",
                ),
            ),
            sdfAtlases = listOf(
                SDFGlyphAtlasArtifact(
                    atlas = GlyphAtlasArtifact(
                        artifactKey = sdfAtlasKey,
                        width = 256,
                        height = 256,
                        format = "r8",
                    ),
                    distanceRange = 4.0f,
                ),
            ),
            diagnostics = GPUTextRouteDiagnostics(
                diagnostics = emptyList(),
                refusalRequired = false,
            ),
        )

        val references = bundle.artifactReferences()

        assertEquals(references, bundle.artifactReferences())
        assertEquals(
            listOf(
                "GlyphAtlasArtifact",
                "SDFGlyphAtlasArtifact",
                "GlyphUploadPlan",
                "OutlineGlyphPlan",
                "ColorGlyphPlan",
                "BitmapGlyphPlan",
                "SVGGlyphPlan",
            ),
            references.map { it.artifactName },
        )
        assertEquals(references.map { it.artifactName }, references.map { it.artifactType })
        assertEquals((1..7).toList(), references.map { it.generation.value })
        assertEquals(
            listOf(
                "glyph-atlas-a8",
                "glyph-atlas-sdf",
                "glyph-upload-plan",
                "outline-glyph-plan",
                "color-glyph-plan",
                "bitmap-glyph-plan",
                "svg-glyph-plan",
            ),
            references.map { it.contentFingerprint },
        )
        assertEquals(references.map { it.contentFingerprint }, references.map { it.artifactKeyHash })
        assertEquals(
            listOf(
                listOf("generation", "contentFingerprint", "atlasCapacity"),
                listOf("generation", "contentFingerprint", "distanceRange"),
                listOf("generation", "contentFingerprint", "payloadByteSize"),
                listOf("generation", "contentFingerprint", "outlinePolicy"),
                listOf("generation", "contentFingerprint", "colorLayerPolicy"),
                listOf("generation", "contentFingerprint", "bitmapPayloadPolicy"),
                listOf("generation", "contentFingerprint", "vectorDocumentPolicy"),
            ),
            references.map { it.invalidationFacts },
        )
        assertTrue(references.all { reference -> reference.diagnostics.isEmpty() })
        assertEquals(
            listOf(
                "TextGPUArtifactBundle.atlases",
                "TextGPUArtifactBundle.sdfAtlases",
                "TextGPUArtifactBundle.glyphUploadPlans",
                "TextGPUArtifactBundle.outlineGlyphPlans",
                "TextGPUArtifactBundle.colorGlyphPlans",
                "TextGPUArtifactBundle.bitmapGlyphPlans",
                "TextGPUArtifactBundle.svgGlyphPlans",
            ),
            references.map { it.sourceLabel },
        )

        val dump = references.joinToString(separator = "\n")
        assertTrue(dump.contains("GPUTextArtifactReference"))
        assertTrue(dump.contains("artifactName=GlyphAtlasArtifact"))
        assertTrue(dump.contains("artifactType=GlyphAtlasArtifact"))
        assertTrue(dump.contains("artifactKeyHash=glyph-atlas-a8"))
        assertTrue(dump.contains("invalidationFacts=[generation, contentFingerprint, atlasCapacity]"))
        listOf(
            "renderer=",
            "fontParser",
            "Sk",
            "Texture",
            "Sampler",
            "BindGroup",
            "CommandEncoder",
            "GPUHandle",
        ).forEach { forbiddenToken ->
            assertFalse(
                dump.contains(forbiddenToken),
                "Reference dump leaked forbidden token $forbiddenToken: $dump",
            )
        }
        assertTrue(references.all { it.toString() != it.contentFingerprint })
        assertFalse(references.map { it.artifactID.value.toHexDashString() }.contains("glyph-atlas-a8"))
        assertFalse(references.map { it.contentFingerprint }.contains("550e8400-e29b-41d4-a716-446655441001"))
    }

    @Test
    fun `artifact references attach matching artifact diagnostics deterministically`() {
        val atlasKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441011",
            generation = 11,
            contentFingerprint = "glyph-atlas-diagnostic",
        )
        val outlineKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441012",
            generation = 12,
            contentFingerprint = "outline-diagnostic",
        )
        val bundle = TextGPUArtifactBundle(
            artifactKey = atlasKey,
            uploadPlans = emptyList(),
            glyphUploadPlans = emptyList(),
            outlineGlyphPlans = listOf(
                OutlineGlyphPlan(
                    artifactKey = outlineKey,
                    glyphIDs = listOf(12U),
                    windingRule = "non-zero",
                ),
            ),
            colorGlyphPlans = emptyList(),
            bitmapGlyphPlans = emptyList(),
            svgGlyphPlans = emptyList(),
            atlases = listOf(
                GlyphAtlasArtifact(
                    artifactKey = atlasKey,
                    width = 128,
                    height = 128,
                    format = "r8",
                ),
            ),
            sdfAtlases = emptyList(),
            diagnostics = GPUTextRouteDiagnostics(
                diagnostics = listOf(
                    GPUTextArtifactDiagnostic(
                        code = GPUTextArtifactDiagnosticCode.ATLAS_CAPACITY_EXCEEDED,
                        message = "Atlas capacity exceeded for page 0.",
                        artifactKey = atlasKey,
                    ),
                    GPUTextArtifactDiagnostic(
                        code = GPUTextArtifactDiagnosticCode.UNSUPPORTED_GLYPH_FORMAT,
                        message = "Outline policy rejected glyph 12.",
                        artifactKey = outlineKey,
                    ),
                ),
                refusalRequired = true,
            ),
        )

        val references = bundle.artifactReferences()

        assertEquals(
            listOf(
                listOf("ATLAS_CAPACITY_EXCEEDED:Atlas capacity exceeded for page 0."),
                listOf("UNSUPPORTED_GLYPH_FORMAT:Outline policy rejected glyph 12."),
            ),
            references.map { reference -> reference.diagnostics },
        )
        assertEquals(references, bundle.artifactReferences())
    }

    @Test
    fun `gpu text artifact surface is composed from dumpable value objects`() {
        val layoutID = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655440200"))
        val runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655440201"))
        val runReference = GPUTextLayoutRunReference(
            layoutResultID = layoutID,
            glyphRunID = runID,
            runIndex = 0,
        )
        val runDescriptor = GPUGlyphRunDescriptor(
            runID = runID,
            layoutResultID = layoutID,
            typefaceID = TypefaceID(Uuid.parse("550e8400-e29b-41d4-a716-446655440202")),
            glyphIDs = listOf(42),
            advances = listOf(11.5f),
            textRangeStart = 0,
            textRangeEnd = 1,
            script = "Latn",
            bidiLevel = 0,
        )
        val artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440203"))
        val generation = GPUTextArtifactGeneration(7)
        val key = GPUTextArtifactKey(
            artifactID = artifactID,
            generation = generation,
            contentFingerprint = "latin-run-001",
        )
        val uploadRange = GPUTextUploadRange(
            offset = 64,
            size = 128,
            label = "sdf-page-0",
        )
        val uploadPlan = GPUTextUploadPlan(
            artifactKey = key,
            ranges = listOf(uploadRange),
            byteSize = 192,
        )
        val glyphUploadPlan = GlyphUploadPlan(
            artifactKey = key,
            uploadPlan = uploadPlan,
            glyphIDs = listOf(42U),
        )
        val outlinePlan = OutlineGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            windingRule = "non-zero",
        )
        val colorPlan = ColorGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            layerCount = 2,
        )
        val bitmapPlan = BitmapGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            colorFormat = "rgba8888",
        )
        val svgPlan = SVGGlyphPlan(
            artifactKey = key,
            glyphIDs = listOf(42U),
            documentCount = 1,
        )
        val atlas = GlyphAtlasArtifact(
            artifactKey = key,
            width = 256,
            height = 256,
            format = "r8",
        )
        val sdfAtlas = SDFGlyphAtlasArtifact(
            atlas = atlas,
            distanceRange = 4.0f,
        )
        val diagnostic = GPUTextArtifactDiagnostic(
            code = GPUTextArtifactDiagnosticCode.MISSING_GLYPH,
            message = "Glyph 42 is unavailable in the planned atlas.",
            artifactKey = key,
        )
        val routeDiagnostics = GPUTextRouteDiagnostics(
            diagnostics = listOf(diagnostic),
            refusalRequired = true,
        )
        val bundle = TextGPUArtifactBundle(
            artifactKey = key,
            uploadPlans = listOf(uploadPlan),
            glyphUploadPlans = listOf(glyphUploadPlan),
            outlineGlyphPlans = listOf(outlinePlan),
            colorGlyphPlans = listOf(colorPlan),
            bitmapGlyphPlans = listOf(bitmapPlan),
            svgGlyphPlans = listOf(svgPlan),
            atlases = listOf(atlas),
            sdfAtlases = listOf(sdfAtlas),
            diagnostics = routeDiagnostics,
        )

        assertEquals(artifactID, key.artifactID)
        assertEquals("550e8400-e29b-41d4-a716-446655440200", runReference.layoutResultID.value.toHexDashString())
        assertEquals("550e8400-e29b-41d4-a716-446655440202", runDescriptor.typefaceID?.value?.toHexDashString())
        assertEquals(192, bundle.uploadPlans.single().byteSize)
        assertEquals(42U, bundle.glyphUploadPlans.single().glyphIDs.single())
        assertEquals("r8", bundle.atlases.single().format)
        assertTrue(bundle.diagnostics.refusalRequired)
        assertFalse(bundle.diagnostics.isEmpty)
    }

    @Test
    fun `upload plan validation reports malformed ranges without renderer state`() {
        val key = GPUTextArtifactKey(
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440204")),
            generation = GPUTextArtifactGeneration(1),
            contentFingerprint = "a8-atlas",
        )
        val validPlan = GPUTextUploadPlan(
            artifactKey = key,
            ranges = listOf(
                GPUTextUploadRange(offset = 0, size = 4, label = "header"),
                GPUTextUploadRange(offset = 4, size = 12, label = "pixels"),
            ),
            byteSize = 16,
        )
        val invalidPlan = GPUTextUploadPlan(
            artifactKey = key,
            ranges = listOf(
                GPUTextUploadRange(offset = 8, size = 12, label = "overflow"),
                GPUTextUploadRange(offset = -1, size = 1, label = "negative"),
            ),
            byteSize = 16,
        )

        assertEquals(emptyList(), validPlan.validateRanges())
        assertEquals(
            listOf(
                GPUTextArtifactDiagnostic(
                    code = GPUTextArtifactDiagnosticCode.INVALID_UPLOAD_RANGE,
                    message = "Upload range overflow [8, 20) exceeds payload byteSize 16.",
                    artifactKey = key,
                ),
                GPUTextArtifactDiagnostic(
                    code = GPUTextArtifactDiagnosticCode.INVALID_UPLOAD_RANGE,
                    message = "Upload range negative has negative offset -1 or size 1.",
                    artifactKey = key,
                ),
            ),
            invalidPlan.validateRanges(),
        )
    }

    private fun fixtureArtifactKey(
        uuid: String,
        generation: Int,
        contentFingerprint: String,
    ): GPUTextArtifactKey = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse(uuid)),
        generation = GPUTextArtifactGeneration(generation),
        contentFingerprint = contentFingerprint,
    )
}
