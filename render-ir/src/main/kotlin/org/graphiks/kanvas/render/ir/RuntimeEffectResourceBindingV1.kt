package org.graphiks.kanvas.render.ir

public sealed interface RuntimeEffectResourceBindingV1 : CanonicalValue {
    public class StorageRead(public val bytes: ImmutableBytes) : RuntimeEffectResourceBindingV1 {
        init { require(bytes.sizeBytesI32 > 0) }
        override val canonicalId: CanonicalId = canonicalId("runtime-storage-read-v1", bytes.canonicalId.value)
    }
    public class SampledTexture(public val image: ImageResourceSnapshot.Pixels) : RuntimeEffectResourceBindingV1 {
        override val canonicalId: CanonicalId = canonicalId("runtime-sampled-texture-v1", image.canonicalId.value)
    }
    public data class Sampler(public val type: RuntimeSamplerTypeV1) : RuntimeEffectResourceBindingV1 {
        override val canonicalId: CanonicalId = canonicalId("runtime-sampler-v1", type.tagU32().toString())
    }
}
public data class RuntimeEffectResourceBindingEntryV1(
    public val logicalSlotI32: Int, public val name: String, public val binding: RuntimeEffectResourceBindingV1,
)
public class RuntimeEffectResourceBindingSetV1 private constructor(entries: Collection<RuntimeEffectResourceBindingEntryV1>) :
    Iterable<RuntimeEffectResourceBindingEntryV1>, CanonicalValue {
    private val values = immutableList(entries.sortedBy { it.logicalSlotI32 })
    public val sizeI32: Int get() = values.size
    public val sizeBytesI64: Long
    init {
        require(values.all { it.logicalSlotI32 >= 0 && it.name.isNotBlank() })
        require(values.map { it.logicalSlotI32 }.distinct().size == values.size)
        require(values.map { it.name }.distinct().size == values.size)
        sizeBytesI64 = values.fold(0L) { sizeI64, entry -> Math.addExact(sizeI64, when (val binding = entry.binding) {
            is RuntimeEffectResourceBindingV1.StorageRead -> binding.bytes.sizeBytesI32.toLong()
            is RuntimeEffectResourceBindingV1.SampledTexture -> binding.image.sizeBytesI32.toLong()
            is RuntimeEffectResourceBindingV1.Sampler -> 0L
        }) }
    }
    public fun entryAt(indexI32: Int): RuntimeEffectResourceBindingEntryV1 = values[indexI32]
    /** Validates the captured names, logical slots and typed facts against the local ABI. */
    public fun requireMatches(descriptor: RuntimeEffectDescriptor) {
        require(values.none { entry -> descriptor.logicalResources.none { it.name == entry.name } }) {
            "invalid.material.runtime_effect.resource_extra"
        }
        descriptor.logicalResources.forEach { slot ->
            val entry = requireNotNull(values.firstOrNull { it.name == slot.name }) { "invalid.material.runtime_effect.resource_missing" }
            require(entry.logicalSlotI32 == slot.logicalSlotI32 && when (val facts = slot.facts) {
                is RuntimeLogicalResourceFactsV1.StorageRead -> (entry.binding as? RuntimeEffectResourceBindingV1.StorageRead)?.let {
                    it.bytes.sizeBytesI32.toLong() >= facts.minBindingSizeBytesI64
                } == true
                is RuntimeLogicalResourceFactsV1.Texture2DFloatFilterable -> entry.binding is RuntimeEffectResourceBindingV1.SampledTexture
                is RuntimeLogicalResourceFactsV1.Sampler -> (entry.binding as? RuntimeEffectResourceBindingV1.Sampler)?.type == facts.type
            }) { "invalid.material.runtime_effect.resource_mismatch" }
        }
    }
    override fun iterator(): Iterator<RuntimeEffectResourceBindingEntryV1> = values.iterator()
    override val canonicalId: CanonicalId = canonicalSequenceId("runtime-resource-bindings-v1", values.map {
        canonicalId("runtime-resource-binding-v1", it.logicalSlotI32.toString(), it.name, it.binding.canonicalId.value).value
    })
    public companion object {
        public fun of(entries: Collection<RuntimeEffectResourceBindingEntryV1>): RuntimeEffectResourceBindingSetV1 = RuntimeEffectResourceBindingSetV1(entries)
        public val Empty: RuntimeEffectResourceBindingSetV1 = of(emptyList())
    }
}
