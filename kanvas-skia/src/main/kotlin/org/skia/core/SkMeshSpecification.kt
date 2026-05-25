package org.skia.core

import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace

/**
 * Minimal CPU-raster skeleton for Skia's experimental `SkMeshSpecification`.
 *
 * This intentionally does not compile or execute mesh SkSL. The supported
 * subset is a direct vertex-buffer layout with a `float2` attribute named
 * `position` and, optionally, a `ubyte4_unorm` attribute named `color` encoded
 * as RGBA bytes. [SkCanvas.drawMesh] lowers that subset to [org.skia.foundation.SkVertices].
 * Mesh SkSL, uniforms, children, varyings, and fragment output remain out of
 * scope for this CPU path.
 */
public class SkMeshSpecification private constructor(
    private val attributesStorage: List<Attribute>,
    private val varyingsStorage: List<Varying>,
    private val vertexStride: Int,
    public val vertexProgram: String,
    public val fragmentProgram: String,
    public val colorSpace: SkColorSpace?,
    public val alphaType: SkAlphaType,
) {
    public data class Attribute(
        public val type: Type,
        public val offset: Int,
        public val name: String,
    ) {
        public enum class Type(public val size: Int) {
            kFloat(4),
            kFloat2(8),
            kFloat3(12),
            kFloat4(16),
            kUByte4_unorm(4),
        }
    }

    public data class Varying(
        public val type: Type,
        public val name: String,
    ) {
        public enum class Type {
            kFloat,
            kFloat2,
            kFloat3,
            kFloat4,
            kHalf,
            kHalf2,
            kHalf3,
            kHalf4,
        }
    }

    public data class Result(
        public val specification: SkMeshSpecification?,
        public val error: String,
    )

    public fun attributes(): List<Attribute> = attributesStorage

    public fun varyings(): List<Varying> = varyingsStorage

    public fun uniforms(): List<Nothing> = emptyList()

    public fun children(): List<Nothing> = emptyList()

    public fun uniformSize(): Int = 0

    public fun findAttribute(name: String): Attribute? =
        attributesStorage.firstOrNull { it.name == name }

    public fun findVarying(name: String): Varying? =
        varyingsStorage.firstOrNull { it.name == name }

    public fun stride(): Int = vertexStride

    internal val positionAttribute: Attribute?
        get() = findAttribute("position")?.takeIf { it.type == Attribute.Type.kFloat2 }

    internal val colorAttribute: Attribute?
        get() = findAttribute("color")?.takeIf { it.type == Attribute.Type.kUByte4_unorm }

    public companion object {
        public const val kMaxStride: Int = 1024
        public const val kMaxAttributes: Int = 8
        public const val kStrideAlignment: Int = 4
        public const val kOffsetAlignment: Int = 4
        public const val kMaxVaryings: Int = 6

        public fun Make(
            attributes: List<Attribute>,
            vertexStride: Int,
            varyings: List<Varying> = emptyList(),
            vs: String,
            fs: String,
            cs: SkColorSpace? = null,
            at: SkAlphaType = SkAlphaType.kPremul,
        ): Result {
            validate(attributes, vertexStride, varyings, at)?.let {
                return Result(null, it)
            }
            return Result(
                SkMeshSpecification(
                    attributesStorage = attributes.toList(),
                    varyingsStorage = varyings.toList(),
                    vertexStride = vertexStride,
                    vertexProgram = vs,
                    fragmentProgram = fs,
                    colorSpace = cs,
                    alphaType = at,
                ),
                "",
            )
        }

        private fun validate(
            attributes: List<Attribute>,
            vertexStride: Int,
            varyings: List<Varying>,
            alphaType: SkAlphaType,
        ): String? {
            if (attributes.isEmpty()) return "SkMeshSpecification requires at least one attribute"
            if (attributes.size > kMaxAttributes) return "SkMeshSpecification supports at most $kMaxAttributes attributes"
            if (varyings.isNotEmpty()) return "CPU SkMesh does not support varyings yet"
            if (vertexStride <= 0) return "SkMeshSpecification vertexStride must be positive"
            if (vertexStride > kMaxStride) return "SkMeshSpecification vertexStride exceeds $kMaxStride"
            if (vertexStride % kStrideAlignment != 0) {
                return "SkMeshSpecification vertexStride must be $kStrideAlignment-byte aligned"
            }
            if (!alphaType.isValid()) return "SkMeshSpecification alphaType must not be kUnknown"

            val seen = HashSet<String>()
            for (attribute in attributes) {
                if (attribute.name.isBlank()) return "SkMeshSpecification attribute name must not be blank"
                if (!seen.add(attribute.name)) return "duplicate SkMeshSpecification attribute '${attribute.name}'"
                if (attribute.offset < 0) return "attribute '${attribute.name}' has negative offset"
                if (attribute.offset % kOffsetAlignment != 0) {
                    return "attribute '${attribute.name}' offset must be $kOffsetAlignment-byte aligned"
                }
                if (attribute.offset + attribute.type.size > vertexStride) {
                    return "attribute '${attribute.name}' exceeds vertexStride"
                }
            }
            val position = attributes.firstOrNull { it.name == "position" }
                ?: return "CPU SkMesh requires a float2 attribute named 'position'"
            if (position.type != Attribute.Type.kFloat2) {
                return "CPU SkMesh requires 'position' to have type kFloat2"
            }
            for (attribute in attributes) {
                if (attribute.name == "position") continue
                if (attribute.name == "color" && attribute.type == Attribute.Type.kUByte4_unorm) continue
                return "CPU SkMesh only supports float2 position plus optional ubyte4_unorm color"
            }
            return null
        }
    }
}
