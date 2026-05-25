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
    private val uniformsStorage: List<Uniform>,
    private val uniformSizeStorage: Int,
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

    public data class Uniform(
        public val type: Type,
        public val offset: Int,
        public val name: String,
        public val colorManaged: Boolean = false,
    ) {
        public enum class Type(
            public val size: Int,
            public val alignment: Int,
        ) {
            kFloat(4, 4),
            kFloat2(8, 8),
            kFloat3(12, 16),
            kFloat4(16, 16),
            kHalf(4, 4),
            kHalf2(8, 8),
            kHalf3(12, 16),
            kHalf4(16, 16),
        }
    }

    public data class Result(
        public val specification: SkMeshSpecification?,
        public val error: String,
    )

    public fun attributes(): List<Attribute> = attributesStorage

    public fun varyings(): List<Varying> = varyingsStorage

    public fun uniforms(): List<Uniform> = uniformsStorage

    public fun children(): List<Nothing> = emptyList()

    public fun uniformSize(): Int = uniformSizeStorage

    public fun findAttribute(name: String): Attribute? =
        attributesStorage.firstOrNull { it.name == name }

    public fun findVarying(name: String): Varying? =
        varyingsStorage.firstOrNull { it.name == name }

    public fun stride(): Int = vertexStride

    internal val positionAttribute: Attribute?
        get() = (findAttribute("position") ?: findAttribute("pos"))?.takeIf { it.type == Attribute.Type.kFloat2 }

    internal val colorAttribute: Attribute?
        get() = findAttribute("color")?.takeIf {
            it.type == Attribute.Type.kUByte4_unorm || it.type == Attribute.Type.kFloat4
        }

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
            val uniforms = parseUniforms(vs, fs)
            return Result(
                SkMeshSpecification(
                    attributesStorage = attributes.toList(),
                    varyingsStorage = varyings.toList(),
                    uniformsStorage = uniforms,
                    uniformSizeStorage = uniforms.lastOrNull()?.let { alignUp(it.offset + it.type.size, 16) } ?: 0,
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
            val position = attributes.firstOrNull { it.name == "position" || it.name == "pos" }
                ?: return "CPU SkMesh requires a float2 attribute named 'position' or 'pos'"
            if (position.type != Attribute.Type.kFloat2) {
                return "CPU SkMesh requires '${position.name}' to have type kFloat2"
            }
            for (attribute in attributes) {
                if (attribute.name == position.name) continue
                if (attribute.name == "color" &&
                    (attribute.type == Attribute.Type.kUByte4_unorm || attribute.type == Attribute.Type.kFloat4)
                ) {
                    continue
                }
                return "CPU SkMesh only supports float2 position plus optional ubyte4_unorm/float4 color"
            }
            return null
        }

        private val uniformDecl = Regex(
            """(?:(layout\s*\(\s*color\s*\))\s*)?\buniform\s+(float|float2|float3|float4|half|half2|half3|half4)\s+([A-Za-z_][A-Za-z0-9_]*)\s*;""",
        )

        private fun parseUniforms(vs: String, fs: String): List<Uniform> {
            val source = "$vs\n$fs"
            val uniforms = ArrayList<Uniform>()
            val seen = HashSet<String>()
            var cursor = 0
            uniformDecl.findAll(source).forEach { match ->
                val type = when (match.groupValues[2]) {
                    "float" -> Uniform.Type.kFloat
                    "float2" -> Uniform.Type.kFloat2
                    "float3" -> Uniform.Type.kFloat3
                    "float4" -> Uniform.Type.kFloat4
                    "half" -> Uniform.Type.kHalf
                    "half2" -> Uniform.Type.kHalf2
                    "half3" -> Uniform.Type.kHalf3
                    "half4" -> Uniform.Type.kHalf4
                    else -> return@forEach
                }
                val name = match.groupValues[3]
                if (!seen.add(name)) return@forEach
                cursor = alignUp(cursor, type.alignment)
                uniforms.add(Uniform(type = type, offset = cursor, name = name, colorManaged = match.groupValues[1].isNotEmpty()))
                cursor += type.size
            }
            return uniforms
        }

        private fun alignUp(value: Int, alignment: Int): Int =
            ((value + alignment - 1) / alignment) * alignment
    }
}
