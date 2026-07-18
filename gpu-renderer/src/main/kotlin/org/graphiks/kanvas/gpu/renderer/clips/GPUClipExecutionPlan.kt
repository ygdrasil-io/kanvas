package org.graphiks.kanvas.gpu.renderer.clips

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

private const val GPU_CLIP_EXECUTION_IDENTITY_VERSION = "gpu-clip-execution-v1"
private const val CLIP_TEXTURE_BYTES_PER_PIXEL = 4

/** Planner-local identity that keeps stencil producer and consumer work atomic. */
@JvmInline
value class GPUClipAtomicGroupID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUClipAtomicGroupID.value must not be blank" }
    }
}

/** Explicit operation used while composing an ordered coverage mask. */
enum class GPUClipMaskCombine {
    Intersect,
    Difference,
}

/** Sampling promoted for a coverage-mask consumer. */
enum class GPUClipMaskSampling {
    Nearest,
}

/** Stencil comparison already selected by clip routing. */
enum class GPUClipStencilCompare {
    Always,
    Equal,
    NotEqual,
}

/** Stencil operation already selected by clip routing. */
enum class GPUClipStencilOperation {
    Keep,
    Zero,
    Replace,
    IncrementClamp,
    DecrementClamp,
    Invert,
    IncrementWrap,
    DecrementWrap,
}

/** Attachment load authority for one stencil step. */
enum class GPUClipStencilLoadOperation {
    Clear,
    Load,
}

/** Attachment store authority for one stencil step. */
enum class GPUClipStencilStoreOperation {
    Store,
    Discard,
}

/** Immutable geometry consumed by a classified clip producer. */
sealed interface GPUClipExecutionGeometry {
    data class Rect(val bounds: GPUBounds) : GPUClipExecutionGeometry {
        init {
            bounds.requireValidGeometryBounds("Rect")
        }
    }

    class RRect(
        val bounds: GPUBounds,
        radii: List<Float>,
    ) : GPUClipExecutionGeometry {
        val radii: List<Float> = immutableList(radii)

        init {
            bounds.requireValidGeometryBounds("RRect")
            require(radii.size == 8) { "RRect clip geometry requires four x/y radius pairs" }
            require(radii.all { it.isFinite() && it >= 0f }) {
                "RRect clip radii must be finite and non-negative"
            }
        }

        override fun equals(other: Any?): Boolean =
            this === other || other is RRect && bounds == other.bounds && radii == other.radii

        override fun hashCode(): Int = 31 * bounds.hashCode() + radii.hashCode()
    }

    class Path(
        vertices: List<Float>,
        contourStarts: List<Int>,
        val fillRule: GPUClipFillRule,
        val inverseFill: Boolean,
    ) : GPUClipExecutionGeometry {
        val vertices: List<Float> = immutableList(vertices)
        val contourStarts: List<Int> = immutableList(contourStarts)

        init {
            require(vertices.size >= 4 && vertices.size % 2 == 0 && vertices.all(Float::isFinite)) {
                "Path clip geometry requires finite x/y vertex pairs"
            }
            val vertexCount = vertices.size / 2
            require(
                contourStarts.isNotEmpty() && contourStarts.first() == 0 &&
                    contourStarts.zipWithNext().all { (left, right) -> left < right } &&
                    contourStarts.last() < vertexCount,
            ) { "Path clip contour starts must be strictly ordered from zero" }
            require(contourStarts.indices.all { index ->
                val start = contourStarts[index]
                val end = contourStarts.getOrElse(index + 1) { vertexCount }
                end - start >= 2
            }) { "Path clip contours must retain at least two vertices" }
        }

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is Path && vertices == other.vertices && contourStarts == other.contourStarts &&
                fillRule == other.fillRule && inverseFill == other.inverseFill

        override fun hashCode(): Int =
            listOf(vertices, contourStarts, fillRule, inverseFill).hashCode()
    }
}

/** One already-classified ordered coverage-mask producer. */
data class GPUClipMaskProducerPlan(
    val sourceOrder: Int,
    val geometry: GPUClipExecutionGeometry,
    val combine: GPUClipMaskCombine,
    val antiAlias: Boolean,
) {
    init {
        require(sourceOrder >= 0) { "GPUClipMaskProducerPlan.sourceOrder must be non-negative" }
    }
}

/** Exact sampling behavior for one coverage-mask consumer. */
data class GPUClipMaskConsumerPlan(
    val sampling: GPUClipMaskSampling = GPUClipMaskSampling.Nearest,
    val invert: Boolean = false,
)

/** Exact producer-side stencil state selected by the mapper. */
data class GPUClipStencilProducerPlan(
    val geometry: GPUClipExecutionGeometry,
    val scissor: GPUPixelBounds?,
    val fillRule: GPUClipFillRule,
    val reference: UInt,
    val compare: GPUClipStencilCompare,
    val frontPassOperation: GPUClipStencilOperation,
    val backPassOperation: GPUClipStencilOperation,
    val failOperation: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    val depthFailOperation: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    val readMask: UInt = 0xffu,
    val writeMask: UInt = 0xffu,
    val loadOperation: GPUClipStencilLoadOperation,
    val storeOperation: GPUClipStencilStoreOperation,
    val clearValue: UInt?,
) {
    init {
        require(reference <= 0xffu && readMask <= 0xffu && writeMask in 1u..0xffu) {
            "Stencil producer reference and masks must fit stencil8"
        }
        validateStencilClear(loadOperation, clearValue)
    }
}

/** Exact consumer-side stencil state selected by the mapper. */
data class GPUClipStencilConsumerPlan(
    val scissor: GPUPixelBounds?,
    val reference: UInt,
    val compare: GPUClipStencilCompare,
    val passOperation: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    val failOperation: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    val depthFailOperation: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
    val readMask: UInt = 0xffu,
    val writeMask: UInt = 0u,
    val loadOperation: GPUClipStencilLoadOperation = GPUClipStencilLoadOperation.Load,
    val storeOperation: GPUClipStencilStoreOperation = GPUClipStencilStoreOperation.Store,
    val clearValue: UInt? = null,
) {
    init {
        require(reference <= 0xffu && readMask <= 0xffu && writeMask == 0u) {
            "Stencil consumer reference/read mask must fit stencil8 and its write mask must be zero"
        }
        validateStencilClear(loadOperation, clearValue)
    }
}

/** One immutable rect or simple-rrect input in an ordered analytic intersection. */
data class GPUClipAnalyticElement(
    val geometry: GPUClipExecutionGeometry,
    val antiAlias: Boolean,
) {
    init {
        require(geometry is GPUClipExecutionGeometry.Rect || geometry is GPUClipExecutionGeometry.RRect) {
            "GPUClipAnalyticElement accepts only rect or rrect geometry"
        }
    }
}

/** Ordered, handle-free execution authority produced once by Canvas-state mapping. */
sealed interface GPUClipExecutionPlan {
    fun canonicalIdentity(): String

    data object NoClip : GPUClipExecutionPlan {
        override fun canonicalIdentity(): String = identity("NoClip")
    }

    data class ScissorOnly(val scissor: GPUPixelBounds) : GPUClipExecutionPlan {
        init {
            require(!scissor.isEmpty) { "ScissorOnly requires non-empty bounds" }
        }

        override fun canonicalIdentity(): String = identity("ScissorOnly") { pixelBounds(scissor) }
    }

    data class AnalyticCoverage(
        val geometry: GPUClipExecutionGeometry,
        val scissor: GPUPixelBounds?,
        val antiAlias: Boolean,
    ) : GPUClipExecutionPlan {
        init {
            require(geometry is GPUClipExecutionGeometry.Rect || geometry is GPUClipExecutionGeometry.RRect) {
                "AnalyticCoverage accepts only rect or rrect geometry"
            }
        }

        override fun canonicalIdentity(): String = identity("AnalyticCoverage") {
            geometry(geometry)
            nullablePixelBounds(scissor)
            boolean(antiAlias)
        }
    }

    class AnalyticIntersection(
        elements: List<GPUClipAnalyticElement>,
    ) : GPUClipExecutionPlan {
        val elements: List<GPUClipAnalyticElement> = immutableList(elements)

        init {
            require(elements.size in 2..4) {
                "AnalyticIntersection requires two to four ordered analytic elements"
            }
        }

        override fun canonicalIdentity(): String = identity("AnalyticIntersection") {
            integer(elements.size)
            elements.forEach(::analyticElement)
        }

        override fun equals(other: Any?): Boolean =
            this === other || other is AnalyticIntersection && elements == other.elements

        override fun hashCode(): Int = elements.hashCode()

        override fun toString(): String = "AnalyticIntersection(elements=$elements)"
    }

    data class StencilCoverage(
        val contentKey: String,
        val bounds: GPUPixelBounds,
        val sampleCount: Int,
        val atomicGroup: GPUClipAtomicGroupID,
        val orderingToken: GPUClipOrderingToken,
        val producer: GPUClipStencilProducerPlan,
        val consumer: GPUClipStencilConsumerPlan,
    ) : GPUClipExecutionPlan {
        val depthStencilBytes: Long = bounds.checkedClipByteSize(sampleCount)
        val requiredBytes: Long get() = depthStencilBytes

        init {
            require(contentKey.isNotBlank()) { "StencilCoverage.contentKey must not be blank" }
            require(!bounds.isEmpty) { "StencilCoverage.bounds must not be empty" }
            require(sampleCount > 0) { "StencilCoverage.sampleCount must be positive" }
        }

        override fun canonicalIdentity(): String = identity("StencilCoverage") {
            string(contentKey)
            pixelBounds(bounds)
            integer(sampleCount)
            long(depthStencilBytes)
            string(atomicGroup.value)
            string(orderingToken.value)
            stencilProducer(producer)
            stencilConsumer(consumer)
        }
    }

    class CoverageMask(
        val contentKey: String,
        val bounds: GPUPixelBounds,
        val sampleCount: Int,
        val depthStencilRequired: Boolean,
        val orderingToken: GPUClipOrderingToken,
        producers: List<GPUClipMaskProducerPlan>,
        val consumer: GPUClipMaskConsumerPlan,
    ) : GPUClipExecutionPlan {
        val producers: List<GPUClipMaskProducerPlan> = immutableList(producers)
        val resolvedBytes: Long = bounds.checkedClipByteSize(1)
        val multisampleColorBytes: Long = if (sampleCount > 1) bounds.checkedClipByteSize(sampleCount) else 0L
        val depthStencilBytes: Long = if (depthStencilRequired) bounds.checkedClipByteSize(sampleCount) else 0L
        val requiredBytes: Long = Math.addExact(
            resolvedBytes,
            Math.addExact(multisampleColorBytes, depthStencilBytes),
        )

        init {
            require(contentKey.isNotBlank()) { "CoverageMask.contentKey must not be blank" }
            require(!bounds.isEmpty) { "CoverageMask.bounds must not be empty" }
            require(sampleCount > 0) { "CoverageMask.sampleCount must be positive" }
            require(producers.isNotEmpty()) { "CoverageMask requires at least one producer" }
            require(producers.zipWithNext().all { (left, right) -> left.sourceOrder < right.sourceOrder }) {
                "CoverageMask producers must retain strict source order"
            }
        }

        override fun canonicalIdentity(): String = identity("CoverageMask") {
            string(contentKey)
            pixelBounds(bounds)
            integer(sampleCount)
            boolean(depthStencilRequired)
            long(resolvedBytes)
            long(multisampleColorBytes)
            long(depthStencilBytes)
            long(requiredBytes)
            string(orderingToken.value)
            integer(producers.size)
            producers.forEach(::maskProducer)
            maskConsumer(consumer)
        }

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is CoverageMask && contentKey == other.contentKey && bounds == other.bounds &&
                sampleCount == other.sampleCount && depthStencilRequired == other.depthStencilRequired &&
                orderingToken == other.orderingToken && producers == other.producers && consumer == other.consumer

        override fun hashCode(): Int =
            listOf(contentKey, bounds, sampleCount, depthStencilRequired, orderingToken, producers, consumer).hashCode()
    }

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUClipExecutionPlan {
        init {
            require(code.isNotBlank()) { "GPUClipExecutionPlan.Refused.code must not be blank" }
            require(message.isNotBlank()) { "GPUClipExecutionPlan.Refused.message must not be blank" }
        }

        override fun canonicalIdentity(): String = identity("Refused") {
            string(code)
            string(message)
        }
    }
}

private fun GPUBounds.requireValidGeometryBounds(label: String) {
    require(listOf(left, top, right, bottom).all(Float::isFinite) && left < right && top < bottom) {
        "$label clip bounds must be finite and non-empty"
    }
}

private fun validateStencilClear(load: GPUClipStencilLoadOperation, clearValue: UInt?) {
    require(clearValue == null || clearValue <= 0xffu) { "Stencil clear value must fit stencil8" }
    require((load == GPUClipStencilLoadOperation.Clear) == (clearValue != null)) {
        "Stencil Clear requires one value and Stencil Load forbids one"
    }
}

private fun GPUPixelBounds.checkedClipByteSize(sampleCount: Int): Long {
    require(sampleCount > 0) { "Clip sample count must be positive" }
    require(!isEmpty) { "Clip allocation bounds must not be empty" }
    return checkedByteSize(CLIP_TEXTURE_BYTES_PER_PIXEL, sampleCount)
}

private inline fun identity(
    variant: String,
    fields: GPUClipExecutionIdentityBuilder.() -> Unit = {},
): String = GPUClipExecutionIdentityBuilder()
    .apply {
        string(GPU_CLIP_EXECUTION_IDENTITY_VERSION)
        string(variant)
        fields()
    }
    .build()

private class GPUClipExecutionIdentityBuilder {
    private val value = StringBuilder()

    fun string(field: String) {
        value.append('s').append(field.length).append(':').append(field)
    }

    fun integer(field: Int) {
        value.append('i').append(field).append(';')
    }

    fun long(field: Long) {
        value.append('l').append(field).append(';')
    }

    fun boolean(field: Boolean) {
        value.append(if (field) "b1;" else "b0;")
    }

    fun float(field: Float) {
        value.append('f').append(field.toRawBits().toUInt().toString(16)).append(';')
    }

    fun pixelBounds(bounds: GPUPixelBounds) {
        integer(bounds.left)
        integer(bounds.top)
        integer(bounds.right)
        integer(bounds.bottom)
    }

    fun nullablePixelBounds(bounds: GPUPixelBounds?) {
        boolean(bounds != null)
        bounds?.let(::pixelBounds)
    }

    fun geometry(value: GPUClipExecutionGeometry) {
        when (value) {
            is GPUClipExecutionGeometry.Rect -> {
                string("Rect")
                scalarBounds(value.bounds)
            }
            is GPUClipExecutionGeometry.RRect -> {
                string("RRect")
                scalarBounds(value.bounds)
                integer(value.radii.size)
                value.radii.forEach(::float)
            }
            is GPUClipExecutionGeometry.Path -> {
                string("Path")
                integer(value.vertices.size)
                value.vertices.forEach(::float)
                integer(value.contourStarts.size)
                value.contourStarts.forEach(::integer)
                string(value.fillRule.name)
                boolean(value.inverseFill)
            }
        }
    }

    fun maskProducer(producer: GPUClipMaskProducerPlan) {
        integer(producer.sourceOrder)
        geometry(producer.geometry)
        string(producer.combine.name)
        boolean(producer.antiAlias)
    }

    fun analyticElement(element: GPUClipAnalyticElement) {
        geometry(element.geometry)
        boolean(element.antiAlias)
    }

    fun maskConsumer(consumer: GPUClipMaskConsumerPlan) {
        string(consumer.sampling.name)
        boolean(consumer.invert)
    }

    fun stencilProducer(producer: GPUClipStencilProducerPlan) {
        geometry(producer.geometry)
        nullablePixelBounds(producer.scissor)
        string(producer.fillRule.name)
        string("front")
        stencilState(
            producer.reference,
            producer.compare,
            producer.frontPassOperation,
            producer.failOperation,
            producer.depthFailOperation,
            producer.readMask,
            producer.writeMask,
            producer.loadOperation,
            producer.storeOperation,
            producer.clearValue,
        )
        string("back")
        stencilState(
            producer.reference,
            producer.compare,
            producer.backPassOperation,
            producer.failOperation,
            producer.depthFailOperation,
            producer.readMask,
            producer.writeMask,
            producer.loadOperation,
            producer.storeOperation,
            producer.clearValue,
        )
    }

    fun stencilConsumer(consumer: GPUClipStencilConsumerPlan) {
        nullablePixelBounds(consumer.scissor)
        stencilState(
            consumer.reference,
            consumer.compare,
            consumer.passOperation,
            consumer.failOperation,
            consumer.depthFailOperation,
            consumer.readMask,
            consumer.writeMask,
            consumer.loadOperation,
            consumer.storeOperation,
            consumer.clearValue,
        )
    }

    private fun scalarBounds(bounds: GPUBounds) {
        float(bounds.left)
        float(bounds.top)
        float(bounds.right)
        float(bounds.bottom)
    }

    private fun stencilState(
        reference: UInt,
        compare: GPUClipStencilCompare,
        pass: GPUClipStencilOperation,
        fail: GPUClipStencilOperation,
        depthFail: GPUClipStencilOperation,
        readMask: UInt,
        writeMask: UInt,
        load: GPUClipStencilLoadOperation,
        store: GPUClipStencilStoreOperation,
        clear: UInt?,
    ) {
        long(reference.toLong())
        string(compare.name)
        string(pass.name)
        string(fail.name)
        string(depthFail.name)
        long(readMask.toLong())
        long(writeMask.toLong())
        string(load.name)
        string(store.name)
        boolean(clear != null)
        clear?.let { long(it.toLong()) }
    }

    fun build(): String = value.toString()
}
