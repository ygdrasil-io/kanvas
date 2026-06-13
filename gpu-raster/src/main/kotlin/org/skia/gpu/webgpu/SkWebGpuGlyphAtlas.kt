package org.skia.gpu.webgpu

import org.skia.foundation.SkCpuGlyphCache
import org.skia.foundation.SkCpuGlyphRecord
import java.security.MessageDigest
import java.util.Locale

public class SkWebGpuGlyphAtlas(
    public val scopeId: String,
    public val sourceCacheSha256: String,
    public val textureLabel: String,
    public val textureFormat: String,
    public val textureUsage: String,
    public val maskFormat: String,
    public val routeIdentifier: String,
    public val fallbackReason: String?,
    public val width: Int,
    public val height: Int,
    public val rowStrideBytes: Int,
    public val generation: Int,
    public val entries: List<SkWebGpuGlyphAtlasEntry>,
    public val uploadBytes: ByteArray,
    public val diagnostics: SkWebGpuGlyphAtlasDiagnostics,
) {
    public val uploadByteCount: Int get() = uploadBytes.size

    public val uploadSha256: String by lazy { sha256(uploadBytes) }

    public val dumpSha256: String by lazy {
        sha256(canonicalJson(includeDumpSha256 = false).toByteArray(Charsets.UTF_8))
    }

    public fun entryForKey(key: String): SkWebGpuGlyphAtlasEntry =
        entries.firstOrNull { it.glyphKey == key } ?: error("Glyph key not present in atlas: $key")

    public fun sample(glyphKey: String, x: Int, y: Int): Int {
        val entry = entryForKey(glyphKey)
        if (entry.maskWidth == 0 || entry.maskHeight == 0) return 0
        require(x in 0 until entry.maskWidth) { "x=$x outside glyph mask width ${entry.maskWidth}" }
        require(y in 0 until entry.maskHeight) { "y=$y outside glyph mask height ${entry.maskHeight}" }
        return uploadBytes[(entry.atlasY + y) * rowStrideBytes + entry.atlasX + x].toInt() and 0xFF
    }

    public fun toJson(): String = canonicalJson(includeDumpSha256 = true)

    private fun canonicalJson(includeDumpSha256: Boolean): String = buildString {
        append("{\n")
        appendJsonField("scopeId", scopeId, comma = true)
        appendJsonField("sourceCacheSha256", sourceCacheSha256, comma = true)
        appendJsonField("textureLabel", textureLabel, comma = true)
        appendJsonField("textureFormat", textureFormat, comma = true)
        appendJsonField("textureUsage", textureUsage, comma = true)
        appendJsonField("maskFormat", maskFormat, comma = true)
        appendJsonField("routeIdentifier", routeIdentifier, comma = true)
        appendJsonNullableField("fallbackReason", fallbackReason, comma = true)
        appendJsonField("width", width, comma = true)
        appendJsonField("height", height, comma = true)
        appendJsonField("rowStrideBytes", rowStrideBytes, comma = true)
        appendJsonField("generation", generation, comma = true)
        appendJsonField("uploadByteCount", uploadByteCount, comma = true)
        appendJsonField("uploadSha256", uploadSha256, comma = true)
        append("  \"diagnostics\": ")
        append(diagnostics.toJson().prependIndent("  ").trimStart())
        append(",\n")
        append("  \"entries\": [\n")
        append(entries.joinToString(",\n") { it.toJson().prependIndent("    ") })
        append("\n  ],\n")
        append("  \"uploadBytes\": [")
        append(uploadBytes.joinToString(", ") { (it.toInt() and 0xFF).toString() })
        append("]")
        if (includeDumpSha256) {
            append(",\n")
            appendJsonField("dumpSha256", dumpSha256, comma = false)
        } else {
            append("\n")
        }
        append("}\n")
    }

    override fun equals(other: Any?): Boolean =
        other is SkWebGpuGlyphAtlas &&
            scopeId == other.scopeId &&
            sourceCacheSha256 == other.sourceCacheSha256 &&
            textureLabel == other.textureLabel &&
            textureFormat == other.textureFormat &&
            textureUsage == other.textureUsage &&
            maskFormat == other.maskFormat &&
            routeIdentifier == other.routeIdentifier &&
            fallbackReason == other.fallbackReason &&
            width == other.width &&
            height == other.height &&
            rowStrideBytes == other.rowStrideBytes &&
            generation == other.generation &&
            entries == other.entries &&
            uploadBytes.contentEquals(other.uploadBytes) &&
            diagnostics == other.diagnostics

    override fun hashCode(): Int {
        var result = scopeId.hashCode()
        result = 31 * result + sourceCacheSha256.hashCode()
        result = 31 * result + textureLabel.hashCode()
        result = 31 * result + textureFormat.hashCode()
        result = 31 * result + textureUsage.hashCode()
        result = 31 * result + maskFormat.hashCode()
        result = 31 * result + routeIdentifier.hashCode()
        result = 31 * result + (fallbackReason?.hashCode() ?: 0)
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rowStrideBytes
        result = 31 * result + generation
        result = 31 * result + entries.hashCode()
        result = 31 * result + uploadBytes.contentHashCode()
        result = 31 * result + diagnostics.hashCode()
        return result
    }

    override fun toString(): String =
        "SkWebGpuGlyphAtlas(scopeId=$scopeId, format=$textureFormat, size=${width}x$height, " +
            "entries=${entries.size}, uploadBytes=$uploadByteCount, generation=$generation)"

    public companion object {
        public const val RouteIdentifier: String = "webgpu.text.glyph-atlas.simple-latin"
        public const val TextureFormat: String = "R8Unorm"
        public const val MaskFormat: String = "A8"
        public const val TextureUsage: String = "TextureBinding|CopyDst"

        public fun build(
            cache: SkCpuGlyphCache,
            generation: Int,
            maxTextureWidth: Int = 256,
            padding: Int = 1,
        ): SkWebGpuGlyphAtlas {
            require(cache.representation == SkCpuGlyphCache.AlphaMaskRepresentation) {
                "SkWebGpuGlyphAtlas requires ${SkCpuGlyphCache.AlphaMaskRepresentation}, got ${cache.representation}"
            }
            require(generation > 0) { "generation must be positive" }
            require(maxTextureWidth > padding * 2) { "maxTextureWidth must leave room for padding" }
            require(padding >= 0) { "padding must be non-negative" }

            val placements = placeGlyphs(cache.glyphs, maxTextureWidth, padding)
            val height = maxOf(1, placements.atlasHeight)
            val uploadBytes = ByteArray(maxTextureWidth * height)
            placements.placed.forEach { placed ->
                val mask = placed.record.mask
                if (mask.width > 0 && mask.height > 0) {
                    for (row in 0 until mask.height) {
                        System.arraycopy(
                            mask.pixels,
                            row * mask.width,
                            uploadBytes,
                            (placed.atlasY + row) * maxTextureWidth + placed.atlasX,
                            mask.width,
                        )
                    }
                }
            }

            val entries = placements.placed.map { placed ->
                val mask = placed.record.mask
                SkWebGpuGlyphAtlasEntry(
                    glyphKey = placed.record.key,
                    glyphId = placed.record.glyphId,
                    codePoints = placed.record.codePoints,
                    maskLeft = mask.left,
                    maskTop = mask.top,
                    maskWidth = mask.width,
                    maskHeight = mask.height,
                    atlasX = placed.atlasX,
                    atlasY = placed.atlasY,
                    u0 = placed.atlasX.toFloat() / maxTextureWidth.toFloat(),
                    v0 = placed.atlasY.toFloat() / height.toFloat(),
                    u1 = (placed.atlasX + mask.width).toFloat() / maxTextureWidth.toFloat(),
                    v1 = (placed.atlasY + mask.height).toFloat() / height.toFloat(),
                    nonZeroPixels = mask.nonZeroPixels,
                    maskSha256 = mask.sha256,
                    atlasGeneration = generation,
                )
            }

            val nonEmptyGlyphs = entries.count { it.maskWidth > 0 && it.maskHeight > 0 }
            return SkWebGpuGlyphAtlas(
                scopeId = cache.scopeId,
                sourceCacheSha256 = cache.dumpSha256,
                textureLabel = "kan-011.${cache.scopeId}.glyph-atlas.generation-$generation",
                textureFormat = TextureFormat,
                textureUsage = TextureUsage,
                maskFormat = MaskFormat,
                routeIdentifier = RouteIdentifier,
                fallbackReason = null,
                width = maxTextureWidth,
                height = height,
                rowStrideBytes = maxTextureWidth,
                generation = generation,
                entries = entries,
                uploadBytes = uploadBytes,
                diagnostics = SkWebGpuGlyphAtlasDiagnostics(
                    sourceRepresentation = cache.representation,
                    sampler = "nearest-clamp-to-edge",
                    resourceKind = "webgpu.texture-upload-plan",
                    uploadBoundary = "queue.writeTexture-compatible row-major A8 bytes",
                    glyphEntryCount = entries.size,
                    nonEmptyGlyphCount = nonEmptyGlyphs,
                    emptyGlyphCount = entries.size - nonEmptyGlyphs,
                    nonClaims = listOf(
                        "no-line-text-render-claim",
                        "no-shaping-claim",
                        "no-fallback-font-claim",
                        "no-emoji-or-color-font-claim",
                        "no-sdf-or-lcd-claim",
                        "no-dynamic-atlas-eviction-claim",
                    ),
                ),
            )
        }

        private fun placeGlyphs(
            glyphs: List<SkCpuGlyphRecord>,
            maxTextureWidth: Int,
            padding: Int,
        ): GlyphPlacements {
            val placed = ArrayList<PlacedGlyph>(glyphs.size)
            var cursorX = padding
            var cursorY = padding
            var rowHeight = 0
            glyphs.forEach { record ->
                val mask = record.mask
                if (mask.width == 0 || mask.height == 0) {
                    placed += PlacedGlyph(record, atlasX = 0, atlasY = 0)
                    return@forEach
                }
                require(mask.width + padding * 2 <= maxTextureWidth) {
                    "Glyph ${record.glyphId} mask width ${mask.width} exceeds atlas width $maxTextureWidth"
                }
                if (cursorX + mask.width + padding > maxTextureWidth) {
                    cursorY += rowHeight + padding
                    cursorX = padding
                    rowHeight = 0
                }
                placed += PlacedGlyph(record, atlasX = cursorX, atlasY = cursorY)
                cursorX += mask.width + padding
                rowHeight = maxOf(rowHeight, mask.height)
            }
            val atlasHeight = if (rowHeight == 0) 1 else cursorY + rowHeight + padding
            return GlyphPlacements(placed = placed, atlasHeight = atlasHeight)
        }
    }
}

public data class SkWebGpuGlyphAtlasEntry(
    public val glyphKey: String,
    public val glyphId: Int,
    public val codePoints: List<Int>,
    public val maskLeft: Int,
    public val maskTop: Int,
    public val maskWidth: Int,
    public val maskHeight: Int,
    public val atlasX: Int,
    public val atlasY: Int,
    public val u0: Float,
    public val v0: Float,
    public val u1: Float,
    public val v1: Float,
    public val nonZeroPixels: Int,
    public val maskSha256: String,
    public val atlasGeneration: Int,
) {
    internal fun toJson(): String = buildString {
        append("{\n")
        appendJsonField("glyphKey", glyphKey, comma = true)
        appendJsonField("glyphId", glyphId, comma = true)
        append("  \"codePoints\": [")
        append(codePoints.joinToString(", "))
        append("],\n")
        appendJsonField("maskLeft", maskLeft, comma = true)
        appendJsonField("maskTop", maskTop, comma = true)
        appendJsonField("maskWidth", maskWidth, comma = true)
        appendJsonField("maskHeight", maskHeight, comma = true)
        appendJsonField("atlasX", atlasX, comma = true)
        appendJsonField("atlasY", atlasY, comma = true)
        appendJsonField("u0", u0, comma = true)
        appendJsonField("v0", v0, comma = true)
        appendJsonField("u1", u1, comma = true)
        appendJsonField("v1", v1, comma = true)
        appendJsonField("nonZeroPixels", nonZeroPixels, comma = true)
        appendJsonField("maskSha256", maskSha256, comma = true)
        appendJsonField("atlasGeneration", atlasGeneration, comma = false)
        append("\n}")
    }
}

public data class SkWebGpuGlyphAtlasDiagnostics(
    public val sourceRepresentation: String,
    public val sampler: String,
    public val resourceKind: String,
    public val uploadBoundary: String,
    public val glyphEntryCount: Int,
    public val nonEmptyGlyphCount: Int,
    public val emptyGlyphCount: Int,
    public val nonClaims: List<String>,
) {
    internal fun toJson(): String = buildString {
        append("{\n")
        appendJsonField("sourceRepresentation", sourceRepresentation, comma = true)
        appendJsonField("sampler", sampler, comma = true)
        appendJsonField("resourceKind", resourceKind, comma = true)
        appendJsonField("uploadBoundary", uploadBoundary, comma = true)
        appendJsonField("glyphEntryCount", glyphEntryCount, comma = true)
        appendJsonField("nonEmptyGlyphCount", nonEmptyGlyphCount, comma = true)
        appendJsonField("emptyGlyphCount", emptyGlyphCount, comma = true)
        append("  \"nonClaims\": [")
        append(nonClaims.joinToString(", ") { jsonString(it) })
        append("]\n")
        append("}")
    }
}

private data class PlacedGlyph(
    val record: SkCpuGlyphRecord,
    val atlasX: Int,
    val atlasY: Int,
)

private data class GlyphPlacements(
    val placed: List<PlacedGlyph>,
    val atlasHeight: Int,
)

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

private fun StringBuilder.appendJsonField(name: String, value: String, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ").append(jsonString(value))
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendJsonNullableField(name: String, value: String?, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ")
    append(if (value == null) "null" else jsonString(value))
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendJsonField(name: String, value: Int, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ").append(value)
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendJsonField(name: String, value: Float, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ").append(floatToken(value))
    if (comma) append(",")
    append("\n")
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { c ->
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
    append('"')
}

private fun floatToken(value: Float): String =
    String.format(Locale.US, "%.8f", value)
        .trimEnd('0')
        .trimEnd('.')

public data class SkWebGpuGlyphAtlasQuad(
    val glyphKey: String,
    val glyphId: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
)

public fun SkWebGpuGlyphAtlas.quadsForPositionedGlyphs(
    glyphKeysInDrawOrder: List<String>,
    glyphDeviceX: List<Float>,
    baselineY: Float,
): List<SkWebGpuGlyphAtlasQuad> {
    require(glyphKeysInDrawOrder.size == glyphDeviceX.size) {
        "glyph key count ${glyphKeysInDrawOrder.size} must match x count ${glyphDeviceX.size}"
    }
    return glyphKeysInDrawOrder.mapIndexedNotNull { index, key ->
        val entry = entryForKey(key)
        if (entry.maskWidth == 0 || entry.maskHeight == 0) return@mapIndexedNotNull null
        val left = glyphDeviceX[index] + entry.maskLeft
        val top = baselineY + entry.maskTop
        SkWebGpuGlyphAtlasQuad(
            glyphKey = key,
            glyphId = entry.glyphId,
            left = left,
            top = top,
            right = left + entry.maskWidth,
            bottom = top + entry.maskHeight,
            u0 = entry.u0,
            v0 = entry.v0,
            u1 = entry.u1,
            v1 = entry.v1,
        )
    }
}
