package org.skia.core

import org.graphiks.math.SkRect
import org.skia.foundation.SkData

/**
 * Minimal CPU-backed `SkMesh` container.
 *
 * Valid meshes reference immutable CPU buffers created through [SkMeshes].
 * The only executable specification subset is documented on
 * [SkMeshSpecification]: a `float2 position` attribute consumed by
 * [SkCanvas.drawMesh]. Optional `ubyte4_unorm color` attributes are lowered to
 * [org.skia.foundation.SkVertices.colors]; SkSL, uniforms, children, varyings,
 * and fragment output are not executed by the CPU path.
 */
public class SkMesh private constructor(
    private val specStorage: SkMeshSpecification?,
    private val modeStorage: Mode,
    private val vertexBufferStorage: VertexBuffer?,
    private val vertexCountStorage: Int,
    private val vertexOffsetStorage: Int,
    private val indexBufferStorage: IndexBuffer?,
    private val indexCountStorage: Int,
    private val indexOffsetStorage: Int,
    private val uniformsStorage: SkData?,
    private val childrenStorage: List<Any?>,
    private val boundsStorage: SkRect,
    private val valid: Boolean,
) {
    public constructor() : this(
        specStorage = null,
        modeStorage = Mode.kTriangles,
        vertexBufferStorage = null,
        vertexCountStorage = 0,
        vertexOffsetStorage = 0,
        indexBufferStorage = null,
        indexCountStorage = 0,
        indexOffsetStorage = 0,
        uniformsStorage = null,
        childrenStorage = emptyList(),
        boundsStorage = SkRect.MakeEmpty(),
        valid = false,
    )

    public abstract class IndexBuffer internal constructor() {
        public abstract fun size(): Int
        protected abstract fun onUpdate(data: ByteArray, offset: Int, size: Int): Boolean

        public fun update(data: ByteArray, offset: Int = 0, size: Int = data.size): Boolean {
            if (offset < 0 || size < 0 || offset + size > size()) return false
            if (offset % 4 != 0 || size % 4 != 0) return false
            if (size > data.size) return false
            return onUpdate(data, offset, size)
        }

        internal abstract fun bytesUnsafe(): ByteArray
    }

    public abstract class VertexBuffer internal constructor() {
        public abstract fun size(): Int
        protected abstract fun onUpdate(data: ByteArray, offset: Int, size: Int): Boolean

        public fun update(data: ByteArray, offset: Int = 0, size: Int = data.size): Boolean {
            if (offset < 0 || size < 0 || offset + size > size()) return false
            if (offset % 4 != 0 || size % 4 != 0) return false
            if (size > data.size) return false
            return onUpdate(data, offset, size)
        }

        internal abstract fun bytesUnsafe(): ByteArray
    }

    public enum class Mode {
        kTriangles,
        kTriangleStrip,
    }

    public data class Result(
        public val mesh: SkMesh,
        public val error: String,
    )

    public fun spec(): SkMeshSpecification? = specStorage

    public fun mode(): Mode = modeStorage

    public fun vertexBuffer(): VertexBuffer? = vertexBufferStorage

    public fun vertexOffset(): Int = vertexOffsetStorage

    public fun vertexCount(): Int = vertexCountStorage

    public fun indexBuffer(): IndexBuffer? = indexBufferStorage

    public fun indexOffset(): Int = indexOffsetStorage

    public fun indexCount(): Int = indexCountStorage

    public fun uniforms(): SkData? = uniformsStorage

    public fun children(): List<Any?> = childrenStorage

    public fun bounds(): SkRect = boundsStorage

    public fun isValid(): Boolean = valid

    public companion object {
        public fun Make(
            specification: SkMeshSpecification?,
            mode: Mode,
            vertexBuffer: VertexBuffer?,
            vertexCount: Int,
            vertexOffset: Int,
            uniforms: SkData? = null,
            children: List<Any?> = emptyList(),
            bounds: SkRect,
        ): Result = make(
            specification = specification,
            mode = mode,
            vertexBuffer = vertexBuffer,
            vertexCount = vertexCount,
            vertexOffset = vertexOffset,
            indexBuffer = null,
            indexCount = 0,
            indexOffset = 0,
            uniforms = uniforms,
            children = children,
            bounds = bounds,
        )

        public fun MakeIndexed(
            specification: SkMeshSpecification?,
            mode: Mode,
            vertexBuffer: VertexBuffer?,
            vertexCount: Int,
            vertexOffset: Int,
            indexBuffer: IndexBuffer?,
            indexCount: Int,
            indexOffset: Int,
            uniforms: SkData? = null,
            children: List<Any?> = emptyList(),
            bounds: SkRect,
        ): Result = make(
            specification = specification,
            mode = mode,
            vertexBuffer = vertexBuffer,
            vertexCount = vertexCount,
            vertexOffset = vertexOffset,
            indexBuffer = indexBuffer,
            indexCount = indexCount,
            indexOffset = indexOffset,
            uniforms = uniforms,
            children = children,
            bounds = bounds,
        )

        private fun make(
            specification: SkMeshSpecification?,
            mode: Mode,
            vertexBuffer: VertexBuffer?,
            vertexCount: Int,
            vertexOffset: Int,
            indexBuffer: IndexBuffer?,
            indexCount: Int,
            indexOffset: Int,
            uniforms: SkData?,
            children: List<Any?>,
            bounds: SkRect,
        ): Result {
            val error = validate(
                specification,
                vertexBuffer,
                vertexCount,
                vertexOffset,
                indexBuffer,
                indexCount,
                indexOffset,
                uniforms,
                children,
            )
            if (error != null) return Result(SkMesh(), error)
            return Result(
                SkMesh(
                    specStorage = specification,
                    modeStorage = mode,
                    vertexBufferStorage = vertexBuffer,
                    vertexCountStorage = vertexCount,
                    vertexOffsetStorage = vertexOffset,
                    indexBufferStorage = indexBuffer,
                    indexCountStorage = indexCount,
                    indexOffsetStorage = indexOffset,
                    uniformsStorage = uniforms,
                    childrenStorage = children.toList(),
                    boundsStorage = bounds,
                    valid = true,
                ),
                "",
            )
        }

        private fun validate(
            specification: SkMeshSpecification?,
            vertexBuffer: VertexBuffer?,
            vertexCount: Int,
            vertexOffset: Int,
            indexBuffer: IndexBuffer?,
            indexCount: Int,
            indexOffset: Int,
            uniforms: SkData?,
            children: List<Any?>,
        ): String? {
            val spec = specification ?: return "SkMesh requires a specification"
            val vb = vertexBuffer ?: return "SkMesh requires a vertex buffer"
            if (vertexCount < 3) return "SkMesh vertexCount must be at least 3"
            if (vertexOffset < 0) return "SkMesh vertexOffset must be non-negative"
            if (vertexOffset % spec.stride() != 0) return "SkMesh vertexOffset must be aligned to specification stride"
            val vertexBytes = spec.stride() * vertexCount
            if (vertexOffset + vertexBytes > vb.size()) return "SkMesh vertex buffer is too small"
            if (spec.uniformSize() == 0) {
                if (uniforms != null) return "SkMesh uniforms were provided but specification declares none"
            } else {
                val provided = uniforms ?: return "SkMesh requires a uniform block of ${spec.uniformSize()} bytes"
                if (provided.size != spec.uniformSize()) {
                    return "SkMesh uniform block size ${provided.size} does not match specification size ${spec.uniformSize()}"
                }
            }
            if (children.isNotEmpty()) return "CPU SkMesh does not support runtime-effect children yet"

            if (indexBuffer == null) {
                if (indexCount != 0) return "SkMesh indexCount must be 0 without an index buffer"
                if (indexOffset != 0) return "SkMesh indexOffset must be 0 without an index buffer"
            } else {
                if (indexCount < 3) return "SkMesh indexCount must be at least 3"
                if (indexOffset < 0) return "SkMesh indexOffset must be non-negative"
                if (indexOffset % 2 != 0) return "SkMesh indexOffset must be 2-byte aligned"
                if (indexOffset + indexCount * 2 > indexBuffer.size()) return "SkMesh index buffer is too small"
                val bytes = indexBuffer.bytesUnsafe()
                for (i in 0 until indexCount) {
                    val offset = indexOffset + i * 2
                    val index = (bytes[offset].toInt() and 0xFF) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                    if (index >= vertexCount) return "SkMesh index $index is outside vertexCount $vertexCount"
                }
            }
            return null
        }
    }
}

public object SkMeshes {
    public fun MakeVertexBuffer(data: ByteArray?, size: Int): SkMesh.VertexBuffer {
        require(size >= 0) { "SkMesh vertex buffer size must be non-negative" }
        return CpuVertexBuffer(copyOrZero(data, size))
    }

    public fun MakeVertexBuffer(data: SkData): SkMesh.VertexBuffer =
        MakeVertexBuffer(data.toByteArray(), data.size)

    public fun CopyVertexBuffer(buffer: SkMesh.VertexBuffer): SkMesh.VertexBuffer =
        MakeVertexBuffer(buffer.bytesUnsafe(), buffer.size())

    public fun MakeIndexBuffer(data: ByteArray?, size: Int): SkMesh.IndexBuffer {
        require(size >= 0) { "SkMesh index buffer size must be non-negative" }
        return CpuIndexBuffer(copyOrZero(data, size))
    }

    public fun MakeIndexBuffer(data: SkData): SkMesh.IndexBuffer =
        MakeIndexBuffer(data.toByteArray(), data.size)

    public fun CopyIndexBuffer(buffer: SkMesh.IndexBuffer): SkMesh.IndexBuffer =
        MakeIndexBuffer(buffer.bytesUnsafe(), buffer.size())

    private fun copyOrZero(data: ByteArray?, size: Int): ByteArray {
        if (data == null) return ByteArray(size)
        require(data.size >= size) { "source data (${data.size}) is smaller than requested buffer size ($size)" }
        return data.copyOf(size)
    }

    private class CpuVertexBuffer(private val bytes: ByteArray) : SkMesh.VertexBuffer() {
        override fun size(): Int = bytes.size

        override fun onUpdate(data: ByteArray, offset: Int, size: Int): Boolean {
            data.copyInto(bytes, destinationOffset = offset, startIndex = 0, endIndex = size)
            return true
        }

        override fun bytesUnsafe(): ByteArray = bytes
    }

    private class CpuIndexBuffer(private val bytes: ByteArray) : SkMesh.IndexBuffer() {
        override fun size(): Int = bytes.size

        override fun onUpdate(data: ByteArray, offset: Int, size: Int): Boolean {
            data.copyInto(bytes, destinationOffset = offset, startIndex = 0, endIndex = size)
            return true
        }

        override fun bytesUnsafe(): ByteArray = bytes
    }
}
