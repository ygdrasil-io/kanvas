package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.math.matrix.Matrix3x3F32

/** Stable identifier for a logical resource, never a renderer handle. */
@JvmInline
public value class ResourceId(public val value: String) {
    init { require(value.isNotBlank()) { "ResourceId.value must not be blank" } }
}

/** Foundational resource contract. Every implementation is immutable and backend-neutral. */
public sealed interface ResourceSnapshot : CanonicalValue {
    public data object None : ResourceSnapshot {
        override val canonicalId: CanonicalId = canonicalId("resource-none-v1")
    }
}

/** A resolved logical resource reference, never a backend allocation. */
public data class ResourceReference(public val id: ResourceId) : CanonicalValue {
    override val canonicalId: CanonicalId = canonicalId("resource-reference-v1", id.value)
}

/** Immutable copy of caller-owned bytes with structural equality. */
public class ImmutableBytes private constructor(bytes: ByteArray) : CanonicalValue {
    private val values: ByteArray = bytes.copyOf()

    internal val size: Int get() = values.size
    public fun copyToByteArray(): ByteArray = values.copyOf()
    override val canonicalId: CanonicalId = canonicalSequenceId("immutable-bytes-v1", values.map(Byte::toString))
    override fun equals(other: Any?): Boolean = other is ImmutableBytes && values.contentEquals(other.values)
    override fun hashCode(): Int = values.contentHashCode()
    override fun toString(): String = "ImmutableBytes(size=${values.size})"

    public companion object { public fun copyOf(bytes: ByteArray): ImmutableBytes = ImmutableBytes(bytes) }
}

/** Immutable copy of caller-owned floats with structural equality and raw-bit identity. */
public class ImmutableFloats private constructor(values: FloatArray) : CanonicalValue {
    private val storedValues: FloatArray = values.copyOf()

    internal val size: Int get() = storedValues.size
    public fun copyToFloatArray(): FloatArray = storedValues.copyOf()
    override val canonicalId: CanonicalId = canonicalSequenceId(
        "immutable-floats-v1",
        storedValues.map(Float::canonicalBits),
    )
    override fun equals(other: Any?): Boolean = other is ImmutableFloats && storedValues.contentEquals(other.storedValues)
    override fun hashCode(): Int = storedValues.contentHashCode()
    override fun toString(): String = "ImmutableFloats(size=${storedValues.size})"

    public companion object { public fun copyOf(values: FloatArray): ImmutableFloats = ImmutableFloats(values) }
}

/** Immutable copy of caller-owned unsigned bytes with structural equality. */
@OptIn(ExperimentalUnsignedTypes::class)
public class ImmutableUBytes private constructor(values: UByteArray) : CanonicalValue {
    private val storedValues: UByteArray = values.copyOf()

    public fun copyToUByteArray(): UByteArray = storedValues.copyOf()
    override val canonicalId: CanonicalId = canonicalSequenceId(
        "immutable-ubytes-v1",
        storedValues.map(UByte::toString),
    )
    override fun equals(other: Any?): Boolean = other is ImmutableUBytes && storedValues.contentEquals(other.storedValues)
    override fun hashCode(): Int = storedValues.contentHashCode()
    override fun toString(): String = "ImmutableUBytes(size=${storedValues.size})"

    public companion object { public fun copyOf(values: UByteArray): ImmutableUBytes = ImmutableUBytes(values) }
}

/** Immutable copy of caller-owned integers with structural equality. */
public class ImmutableInts private constructor(values: IntArray) : CanonicalValue {
    private val storedValues: IntArray = values.copyOf()

    public fun copyToIntArray(): IntArray = storedValues.copyOf()
    override val canonicalId: CanonicalId = canonicalSequenceId("immutable-ints-v1", storedValues.map(Int::toString))
    override fun equals(other: Any?): Boolean = other is ImmutableInts && storedValues.contentEquals(other.storedValues)
    override fun hashCode(): Int = storedValues.contentHashCode()
    override fun toString(): String = "ImmutableInts(size=${storedValues.size})"

    public companion object { public fun copyOf(values: IntArray): ImmutableInts = ImmutableInts(values) }
}

/** Backend-neutral image sample storage formats, mirroring the public image data surface. */
public enum class ImagePixelFormat(public val bytesPerPixel: Int) {
    UNKNOWN(0), ALPHA_8(1), RGB_565(2), ARGB_4444(2), RGBA_8888(4), RGB_888X(4), BGRA_8888(4),
    RGBA_1010102(4), BGRA_1010102(4), RGB_101010X(4), BGR_101010X(4), BGR_101010X_XR(4),
    BGRA_10101010_XR(8), RGBA_10X6(8), GRAY_8(1), RGBA_F16_NORM(8), RGBA_F16(8),
    RGB_F16F16F16X(8), RGBA_F32(16), R8G8_UNORM(2), A16_FLOAT(2), R16G16_FLOAT(4),
    A16_UNORM(2), R16_UNORM(2), R16G16_UNORM(4), R16G16B16A16_UNORM(8), SRGBA_8888(4),
    R8_UNORM(1),
}

/** Alpha interpretation carried with a backend-neutral image resource. */
public enum class ImageAlphaType { OPAQUE, PREMUL, UNPREMUL, UNKNOWN }

/** An immutable image resource. Pixel-backed and external resources are intentionally distinct. */
public sealed interface ImageResourceSnapshot : ResourceSnapshot {
    public val sourceId: String
    public val width: Int
    public val height: Int
    public val pixelFormat: ImagePixelFormat
    public val alphaType: ImageAlphaType
    public val colorSpace: ColorSpace

    /** Pixel-backed image whose bytes are owned by this snapshot. */
    public class Pixels internal constructor(
        override val sourceId: String,
        override val width: Int,
        override val height: Int,
        override val pixelFormat: ImagePixelFormat,
        override val alphaType: ImageAlphaType,
        override val colorSpace: ColorSpace,
        public val rowBytes: Int,
        pixels: ByteArray,
    ) : ImageResourceSnapshot {
        private val storedPixels: ImmutableBytes = ImmutableBytes.copyOf(pixels)

        init {
            require(sourceId.isNotBlank()) { "ImageResourceSnapshot.sourceId must not be blank" }
            require(width > 0 && height > 0) { "Image resource dimensions must be positive" }
            require(pixelFormat != ImagePixelFormat.UNKNOWN) { "Owned pixels require a concrete pixel format" }
            require(alphaType != ImageAlphaType.UNKNOWN) { "Owned pixels require a concrete alpha type" }
            require(colorSpace.name.isNotBlank()) { "Owned pixels require a nonblank color-space identity" }
            require(rowBytes >= minimumRowBytes(width, pixelFormat)) { "rowBytes is smaller than one image row" }
            val requiredBytes = checkedPixelByteCount(rowBytes, height)
            require(storedPixels.size.toLong() >= requiredBytes) {
                "pixel bytes do not cover the declared image rows"
            }
        }

        public fun copyPixels(): ByteArray = storedPixels.copyToByteArray()
        override val canonicalId: CanonicalId = canonicalId(
            "image-resource-pixels-v1",
            sourceId,
            width.toString(),
            height.toString(),
            pixelFormat.name,
            alphaType.name,
            colorSpaceId(colorSpace).value,
            rowBytes.toString(),
            storedPixels.canonicalId.value,
        )
        override fun equals(other: Any?): Boolean = other is Pixels &&
            sourceId == other.sourceId && width == other.width && height == other.height &&
            pixelFormat == other.pixelFormat && alphaType == other.alphaType && colorSpace == other.colorSpace &&
            rowBytes == other.rowBytes && storedPixels == other.storedPixels
        override fun hashCode(): Int = canonicalId.hashCode()
    }

    public companion object {
        public fun rgba8(
            width: Int,
            height: Int,
            pixels: ByteArray,
            colorSpace: ColorSpace,
            alphaType: ImageAlphaType = ImageAlphaType.UNPREMUL,
            sourceId: String = "pixels",
        ): Pixels = Pixels(
            sourceId,
            width,
            height,
            ImagePixelFormat.RGBA_8888,
            alphaType,
            colorSpace,
            minimumRowBytes(width, ImagePixelFormat.RGBA_8888),
            pixels,
        )

        public fun fromPixels(
            sourceId: String,
            width: Int,
            height: Int,
            pixelFormat: ImagePixelFormat,
            alphaType: ImageAlphaType,
            colorSpace: ColorSpace,
            rowBytes: Int,
            pixels: ByteArray,
        ): Pixels = Pixels(sourceId, width, height, pixelFormat, alphaType, colorSpace, rowBytes, pixels)
    }
}

/** Explicit reference for an image whose pixels are owned externally, never a synthetic texture. */
public class ExternalImageReference private constructor(
    override val sourceId: String,
    override val width: Int,
    override val height: Int,
    override val pixelFormat: ImagePixelFormat,
    override val alphaType: ImageAlphaType,
    override val colorSpace: ColorSpace,
) : ImageResourceSnapshot {
    init {
        require(sourceId.isNotBlank()) { "ExternalImageReference.sourceId must not be blank" }
        require(width >= 0 && height >= 0) { "External image dimensions cannot be negative" }
    }

    override val canonicalId: CanonicalId = canonicalId(
        "external-image-reference-v1",
        sourceId,
        width.toString(),
        height.toString(),
        pixelFormat.name,
        alphaType.name,
        colorSpaceId(colorSpace).value,
    )
    override fun equals(other: Any?): Boolean = other is ExternalImageReference &&
        sourceId == other.sourceId && width == other.width && height == other.height &&
        pixelFormat == other.pixelFormat && alphaType == other.alphaType && colorSpace == other.colorSpace
    override fun hashCode(): Int = canonicalId.hashCode()

    public companion object {
        public fun of(
            sourceId: String,
            width: Int,
            height: Int,
            pixelFormat: ImagePixelFormat,
            alphaType: ImageAlphaType,
            colorSpace: ColorSpace,
        ): ExternalImageReference = ExternalImageReference(sourceId, width, height, pixelFormat, alphaType, colorSpace)
    }
}

/** Stable registered runtime-effect identity; it is never a compiled shader handle. */
@JvmInline
public value class RuntimeEffectId(public val value: String) {
    init { require(value.isNotBlank()) { "RuntimeEffectId.value must not be blank" } }
}

/** The public ABI role of a registered runtime effect. */
public enum class RuntimeEffectAbi { SHADER, COLOR_FILTER, IMAGE_FILTER, BLENDER }
public enum class RuntimeUniformType { FLOAT, FLOAT2, FLOAT3, FLOAT4, INT1, MAT3X3, MAT4X4 }
public enum class RuntimeChildType { SHADER, COLOR_FILTER, IMAGE_FILTER, BLENDER }
public enum class RuntimeVertexFormat { FLOAT32, FLOAT32X2, FLOAT32X3, FLOAT32X4, UINT8X4, SINT16X2, SINT16X4 }
public enum class RuntimeVertexStepMode { VERTEX, INSTANCE }

/** Handle-free runtime module provenance retained for descriptor compatibility checks. */
public data class RuntimeModuleMetadata(
    public val source: String,
    public val entryPoint: String,
) : CanonicalValue {
    init { require(entryPoint.isNotBlank()) { "RuntimeModuleMetadata.entryPoint must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("runtime-module-metadata-v1", source, entryPoint)
}

/** One ABI uniform declaration. */
public data class RuntimeUniformSlot(
    public val name: String,
    public val binding: Int,
    public val type: RuntimeUniformType,
    /** ABI-declared storage size, preserved without reinterpreting it as a value arity. */
    public val size: Int,
) : CanonicalValue {
    init {
        require(name.isNotBlank()) { "RuntimeUniformSlot.name must not be blank" }
        require(binding >= 0) { "RuntimeUniformSlot.binding must not be negative" }
        require(size >= 0) { "RuntimeUniformSlot.size must not be negative" }
    }
    override val canonicalId: CanonicalId = canonicalId("runtime-uniform-slot-v1", name, binding.toString(), type.name, size.toString())
}

/** Immutable ABI uniform layout. */
public class RuntimeUniformLayout private constructor(slots: Collection<RuntimeUniformSlot>) : CanonicalValue, Iterable<RuntimeUniformSlot> {
    private val values: List<RuntimeUniformSlot> = immutableList(slots)
    init {
        require(values.map(RuntimeUniformSlot::name).distinct().size == values.size) { "Runtime uniform slot names must be unique" }
        require(values.map(RuntimeUniformSlot::binding).distinct().size == values.size) { "Runtime uniform slot bindings must be unique" }
    }
    public val slotCount: Int get() = values.size
    public fun slotAt(index: Int): RuntimeUniformSlot = values[index]
    override fun iterator(): Iterator<RuntimeUniformSlot> = values.iterator()
    override val canonicalId: CanonicalId = canonicalSequenceId("runtime-uniform-layout-v1", values.map { it.canonicalId.value })
    override fun equals(other: Any?): Boolean = other is RuntimeUniformLayout && canonicalId == other.canonicalId
    override fun hashCode(): Int = canonicalId.hashCode()
    public companion object { public fun of(slots: Collection<RuntimeUniformSlot>): RuntimeUniformLayout = RuntimeUniformLayout(slots) }
}

/** A child declaration in a runtime-effect ABI. */
public data class RuntimeChildSlot(public val name: String, public val type: RuntimeChildType) : CanonicalValue {
    init { require(name.isNotBlank()) { "RuntimeChildSlot.name must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("runtime-child-slot-v1", name, type.name)
}

/** One neutral vertex attribute required by a runtime effect. */
public data class RuntimeVertexAttribute(
    public val format: RuntimeVertexFormat,
    public val offset: Int,
    public val shaderLocation: Int,
) : CanonicalValue {
    init {
        require(offset >= 0) { "RuntimeVertexAttribute.offset must not be negative" }
        require(shaderLocation >= 0) { "RuntimeVertexAttribute.shaderLocation must not be negative" }
    }
    override val canonicalId: CanonicalId = canonicalId("runtime-vertex-attribute-v1", format.name, offset.toString(), shaderLocation.toString())
}

/** Immutable neutral vertex metadata; it is not a backend vertex buffer or layout object. */
public class RuntimeVertexLayout private constructor(
    public val stride: Int,
    public val stepMode: RuntimeVertexStepMode,
    attributes: Collection<RuntimeVertexAttribute>,
) : CanonicalValue, Iterable<RuntimeVertexAttribute> {
    private val values: List<RuntimeVertexAttribute> = immutableList(attributes)
    init {
        require(stride >= 0) { "RuntimeVertexLayout.stride must not be negative" }
        require(values.map(RuntimeVertexAttribute::shaderLocation).distinct().size == values.size) {
            "Runtime vertex shader locations must be unique"
        }
        values.forEach { attribute ->
            val extent = try {
                Math.addExact(attribute.offset, attribute.format.byteSize())
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("Runtime vertex attribute extent overflows Int", error)
            }
            require(extent <= stride) { "Runtime vertex attribute extends beyond stride" }
        }
    }
    public val attributeCount: Int get() = values.size
    public fun attributeAt(index: Int): RuntimeVertexAttribute = values[index]
    override fun iterator(): Iterator<RuntimeVertexAttribute> = values.iterator()
    override val canonicalId: CanonicalId = canonicalId(
        "runtime-vertex-layout-v1",
        stride.toString(),
        stepMode.name,
        canonicalSequenceId("attributes", values.map { it.canonicalId.value }).value,
    )
    override fun equals(other: Any?): Boolean = other is RuntimeVertexLayout && canonicalId == other.canonicalId
    override fun hashCode(): Int = canonicalId.hashCode()
    public companion object {
        public fun of(
            stride: Int,
            attributes: Collection<RuntimeVertexAttribute>,
            stepMode: RuntimeVertexStepMode = RuntimeVertexStepMode.VERTEX,
        ): RuntimeVertexLayout = RuntimeVertexLayout(stride, stepMode, attributes)
    }
}

/** Registered runtime-effect contract retained by the IR without a compiled implementation. */
public class RuntimeEffectDescriptor private constructor(
    public val id: RuntimeEffectId,
    public val abi: RuntimeEffectAbi,
    public val uniformLayout: RuntimeUniformLayout,
    childSlots: Collection<RuntimeChildSlot>,
    public val vertexLayout: RuntimeVertexLayout?,
    public val module: RuntimeModuleMetadata?,
) : CanonicalValue, Iterable<RuntimeChildSlot> {
    private val values: List<RuntimeChildSlot> = immutableList(childSlots)
    init { require(values.map(RuntimeChildSlot::name).distinct().size == values.size) { "Runtime child slot names must be unique" } }
    public val childSlotCount: Int get() = values.size
    public fun childSlotAt(index: Int): RuntimeChildSlot = values[index]
    override fun iterator(): Iterator<RuntimeChildSlot> = values.iterator()
    override val canonicalId: CanonicalId = canonicalId(
        "runtime-effect-descriptor-v2",
        id.value,
        abi.name,
        uniformLayout.canonicalId.value,
        canonicalSequenceId("child-slots", values.map { it.canonicalId.value }).value,
        canonicalOptionalId("vertex-layout", vertexLayout?.canonicalId).value,
        canonicalOptionalId("module", module?.canonicalId).value,
    )
    override fun equals(other: Any?): Boolean = other is RuntimeEffectDescriptor && canonicalId == other.canonicalId
    override fun hashCode(): Int = canonicalId.hashCode()
    public companion object {
        public fun of(
            id: RuntimeEffectId,
            abi: RuntimeEffectAbi,
            uniformLayout: RuntimeUniformLayout,
            childSlots: Collection<RuntimeChildSlot>,
            vertexLayout: RuntimeVertexLayout? = null,
            module: RuntimeModuleMetadata? = null,
        ): RuntimeEffectDescriptor = RuntimeEffectDescriptor(id, abi, uniformLayout, childSlots, vertexLayout, module)
    }
}

/** Immutable runtime-uniform values. */
public sealed interface RuntimeUniformValue : CanonicalValue {
    public data class F1(public val value: Float) : RuntimeUniformValue {
        override val canonicalId: CanonicalId = canonicalId("runtime-uniform-f1-v1", value.canonicalBits())
    }
    public data class F2(public val x: Float, public val y: Float) : RuntimeUniformValue {
        override val canonicalId: CanonicalId = canonicalId("runtime-uniform-f2-v1", x.canonicalBits(), y.canonicalBits())
    }
    public data class F3(public val x: Float, public val y: Float, public val z: Float) : RuntimeUniformValue {
        override val canonicalId: CanonicalId = canonicalId("runtime-uniform-f3-v1", x.canonicalBits(), y.canonicalBits(), z.canonicalBits())
    }
    public data class F4(public val x: Float, public val y: Float, public val z: Float, public val w: Float) : RuntimeUniformValue {
        override val canonicalId: CanonicalId = canonicalId("runtime-uniform-f4-v1", x.canonicalBits(), y.canonicalBits(), z.canonicalBits(), w.canonicalBits())
    }
    public data class I1(public val value: Int) : RuntimeUniformValue {
        override val canonicalId: CanonicalId = canonicalId("runtime-uniform-i1-v1", value.toString())
    }
    public data class M3(public val value: Matrix3x3F32) : RuntimeUniformValue {
        override val canonicalId: CanonicalId = matrixCanonicalId("runtime-uniform-m3-v1", value)
    }
    public class M4 private constructor(values: FloatArray) : RuntimeUniformValue {
        private val storedValues: ImmutableFloats = ImmutableFloats.copyOf(values)
        init { require(storedValues.size == 16) { "Runtime M4 uniforms require exactly 16 values" } }
        public fun copyValues(): FloatArray = storedValues.copyToFloatArray()
        override val canonicalId: CanonicalId = canonicalId("runtime-uniform-m4-v1", storedValues.canonicalId.value)
        override fun equals(other: Any?): Boolean = other is M4 && storedValues == other.storedValues
        override fun hashCode(): Int = storedValues.hashCode()
        public companion object { public operator fun invoke(values: FloatArray): M4 = M4(values) }
    }
}

/** A neutral runtime child binding supplied to a registered effect descriptor. */
public data class RuntimeChildBinding(public val name: String, public val type: RuntimeChildType) {
    init { require(name.isNotBlank()) { "RuntimeChildBinding.name must not be blank" } }
}

/** Public, backend-free outcome of checking runtime values against a registered ABI. */
public sealed interface RuntimeBindingValidationResult {
    public data object Valid : RuntimeBindingValidationResult
    public data class MissingUniform(public val name: String) : RuntimeBindingValidationResult
    public data class UnexpectedUniform(public val name: String) : RuntimeBindingValidationResult
    public data class UniformTypeMismatch(
        public val name: String,
        public val expected: RuntimeUniformType,
        public val actual: RuntimeUniformType,
    ) : RuntimeBindingValidationResult
    public data class MissingChild(public val name: String) : RuntimeBindingValidationResult
    public data class UnexpectedChild(public val name: String) : RuntimeBindingValidationResult
    public data class DuplicateChild(public val name: String) : RuntimeBindingValidationResult
    public data class ChildTypeMismatch(
        public val name: String,
        public val expected: RuntimeChildType,
        public val actual: RuntimeChildType,
    ) : RuntimeBindingValidationResult
}

/** Checks a runtime binding before renderer planning or backend allocation. */
public object RuntimeBindingValidator {
    public fun validate(
        descriptor: RuntimeEffectDescriptor,
        uniforms: Map<String, RuntimeUniformValue>,
        children: Collection<RuntimeChildBinding>,
    ): RuntimeBindingValidationResult {
        children.groupBy(RuntimeChildBinding::name).entries.firstOrNull { it.value.size > 1 }?.let { duplicate ->
            return RuntimeBindingValidationResult.DuplicateChild(duplicate.key)
        }
        descriptor.uniformLayout.forEach { slot ->
            val value = uniforms[slot.name] ?: return RuntimeBindingValidationResult.MissingUniform(slot.name)
            val actual = value.runtimeType()
            if (actual != slot.type) {
                return RuntimeBindingValidationResult.UniformTypeMismatch(slot.name, slot.type, actual)
            }
        }
        uniforms.keys.firstOrNull { name -> descriptor.uniformLayout.none { it.name == name } }?.let { name ->
            return RuntimeBindingValidationResult.UnexpectedUniform(name)
        }

        descriptor.forEach { slot ->
            val child = children.firstOrNull { it.name == slot.name }
                ?: return RuntimeBindingValidationResult.MissingChild(slot.name)
            if (child.type != slot.type) {
                return RuntimeBindingValidationResult.ChildTypeMismatch(slot.name, slot.type, child.type)
            }
        }
        children.firstOrNull { child -> descriptor.none { it.name == child.name } }?.let { child ->
            return RuntimeBindingValidationResult.UnexpectedChild(child.name)
        }
        return RuntimeBindingValidationResult.Valid
    }
}

internal fun RuntimeBindingValidationResult.requireValid() {
    require(this is RuntimeBindingValidationResult.Valid) { "Runtime effect bindings are invalid: $this" }
}

private fun RuntimeUniformValue.runtimeType(): RuntimeUniformType = when (this) {
    is RuntimeUniformValue.F1 -> RuntimeUniformType.FLOAT
    is RuntimeUniformValue.F2 -> RuntimeUniformType.FLOAT2
    is RuntimeUniformValue.F3 -> RuntimeUniformType.FLOAT3
    is RuntimeUniformValue.F4 -> RuntimeUniformType.FLOAT4
    is RuntimeUniformValue.I1 -> RuntimeUniformType.INT1
    is RuntimeUniformValue.M3 -> RuntimeUniformType.MAT3X3
    is RuntimeUniformValue.M4 -> RuntimeUniformType.MAT4X4
}

/** Ordered material child retained by a runtime shader effect. */
public data class RuntimeMaterialChild(public val name: String, public val material: MaterialNode) : CanonicalValue {
    init { require(name.isNotBlank()) { "RuntimeMaterialChild.name must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("runtime-material-child-v1", name, material.canonicalId.value)
}

internal fun immutableUniformMap(values: Map<String, RuntimeUniformValue>): Map<String, RuntimeUniformValue> {
    values.keys.forEach { require(it.isNotBlank()) { "Runtime uniform names must not be blank" } }
    return immutableInsertionOrderMap(values)
}

internal fun uniformMapId(values: Map<String, RuntimeUniformValue>): CanonicalId = canonicalSequenceId(
    "runtime-uniform-values-v1",
    values.map { (name, value) -> canonicalId("uniform", name, value.canonicalId.value).value },
)

internal fun colorSpaceId(value: ColorSpace): CanonicalId = canonicalId(
    "color-space-v1",
    value.name,
    value.transferFunction.name,
    value.gamut.name,
)

internal fun matrixCanonicalId(tag: String, value: Matrix3x3F32): CanonicalId = canonicalId(
    tag,
    value.sx.canonicalBits(), value.kx.canonicalBits(), value.tx.canonicalBits(),
    value.ky.canonicalBits(), value.sy.canonicalBits(), value.ty.canonicalBits(),
    value.persp0.canonicalBits(), value.persp1.canonicalBits(), value.persp2.canonicalBits(),
)

private fun minimumRowBytes(width: Int, format: ImagePixelFormat): Int {
    require(width > 0) { "Image width must be positive" }
    return try {
        Math.multiplyExact(width, format.bytesPerPixel)
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("Image row byte count overflows Int", error)
    }
}

private fun checkedPixelByteCount(rowBytes: Int, height: Int): Long = try {
    Math.multiplyExact(rowBytes.toLong(), height.toLong())
} catch (error: ArithmeticException) {
    throw IllegalArgumentException("Image byte count overflows Long", error)
}

private fun RuntimeVertexFormat.byteSize(): Int = when (this) {
    RuntimeVertexFormat.FLOAT32 -> 4
    RuntimeVertexFormat.FLOAT32X2 -> 8
    RuntimeVertexFormat.FLOAT32X3 -> 12
    RuntimeVertexFormat.FLOAT32X4 -> 16
    RuntimeVertexFormat.UINT8X4 -> 4
    RuntimeVertexFormat.SINT16X2 -> 4
    RuntimeVertexFormat.SINT16X4 -> 8
}
