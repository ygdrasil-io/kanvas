package org.graphiks.kanvas.gpu.renderer.routing

import java.security.MessageDigest

/**
 * Concrete registry of typed CPU-prepared GPU artifact descriptors.
 * Contains all 12 accepted artifact families as defined in spec 04.
 */
class KanvasPreparedGPUArtifactRegistry : CPUPreparedGPUArtifactRegistry {
    private val descriptors: Map<CPUPreparedGPUArtifactKey, CPUPreparedGPUArtifactDescriptor>

    init {
        descriptors = artifactFamilySpecs.associate { (type, version, lifetime, consumer, budget, policy) ->
            val keyVal = "prepared.${type}.v$version"
            val key = CPUPreparedGPUArtifactKey(keyVal)
            val hash = descriptorHashKeyed(keyVal)
            key to CPUPreparedGPUArtifactDescriptor(
                artifactType = type,
                version = version,
                lifetimeClass = lifetime,
                consumerKind = consumer,
                budgetClass = budget,
                invalidationPolicy = policy,
                descriptorHash = hash,
            )
        }
    }

    override fun descriptor(key: CPUPreparedGPUArtifactKey): CPUPreparedGPUArtifactDescriptor? =
        descriptors[key]

    companion object {
        private val artifactFamilySpecs = listOf(
            Triple("CoverageMaskArtifact", 1, "atlas-resident") to Triple("gpu.coverage_mask.sample", "atlas-total", "content-key"),
            Triple("PathAtlasArtifact", 1, "atlas-resident") to Triple("gpu.path_atlas.sample", "atlas-total", "content-key"),
            Triple("GlyphAtlasArtifact", 1, "atlas-resident") to Triple("gpu.glyph_atlas.sample", "atlas-total", "content-key"),
            Triple("SDFGlyphAtlasArtifact", 1, "atlas-resident") to Triple("gpu.sdf_glyph_atlas.sample", "atlas-total", "content-key"),
            Triple("GlyphUploadPlan", 1, "frame-local") to Triple("gpu.glyph_upload.consume", "glyph-upload", "generation"),
            Triple("OutlineGlyphPlan", 1, "cache-resident") to Triple("gpu.outline_glyph.consume", "glyph-outline", "generation"),
            Triple("ColorGlyphPlan", 1, "cache-resident") to Triple("gpu.color_glyph.composite", "glyph-color", "generation"),
            Triple("BitmapGlyphPlan", 1, "cache-resident") to Triple("gpu.bitmap_glyph.sample", "glyph-bitmap", "generation"),
            Triple("SVGGlyphPlan", 1, "cache-resident") to Triple("gpu.svg_glyph.route", "glyph-svg", "generation"),
            Triple("UploadedTextureArtifact", 1, "frame-local") to Triple("gpu.uploaded_texture.bind", "texture-upload", "content-key"),
            Triple("PrecomputedGeometryArtifact", 1, "cache-resident") to Triple("gpu.precomputed_geometry.bind", "geometry-buffer", "content-key"),
            Triple("FilterIntermediateArtifact", 1, "recording-local") to Triple("gpu.filter_intermediate.bind", "filter-intermediate", "generation"),
            Triple("GPUComputeTessellationArtifact", 1, "cache-resident") to Triple("gpu.tessellation.shader", "geometry-buffer", "content-key"),
        ).map { (artifact, consumer) ->
            ArtifactFamilySpec(
                type = artifact.first,
                version = artifact.second,
                lifetime = artifact.third,
                consumer = consumer.first,
                budget = consumer.second,
                policy = consumer.third,
            )
        }

        private data class ArtifactFamilySpec(
            val type: String,
            val version: Int,
            val lifetime: String,
            val consumer: String,
            val budget: String,
            val policy: String,
        )

        private fun descriptorHashKeyed(keyValue: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(keyValue.toByteArray(Charsets.UTF_8))
            return "sha256:" + hash.joinToString("") { "%02x".format(it) }
        }
    }
}
