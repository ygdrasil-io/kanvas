package org.graphiks.kanvas.pipeline

import java.util.Collections
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.render.ir.RuntimeEffectDescriptor
import org.graphiks.kanvas.render.ir.RuntimeLogicalResourceFactsV1
import org.graphiks.kanvas.render.ir.RuntimeSamplerTypeV1

public sealed interface RuntimeEffectResourceBinding {
    public class StorageRead private constructor(bytes: ByteArray) : RuntimeEffectResourceBinding {
        private val bytes = bytes.copyOf()
        public val sizeBytesI32: Int get() = bytes.size
        public fun copyBytes(): ByteArray = bytes.copyOf()
        public companion object {
            public fun copyOf(bytes: ByteArray): StorageRead {
                require(bytes.size.toLong() in 1L..Int.MAX_VALUE.toLong()) { "invalid.material.runtime_effect.resource_storage" }
                return StorageRead(bytes)
            }
        }
    }
    public data class SampledTexture(public val image: Image) : RuntimeEffectResourceBinding
    public data class Sampler(public val type: RuntimeSamplerTypeV1) : RuntimeEffectResourceBinding
}

public class RuntimeEffectResourceBindings private constructor(bindings: Map<String, RuntimeEffectResourceBinding>) {
    private val values: Map<String, RuntimeEffectResourceBinding> = Collections.unmodifiableMap(LinkedHashMap(bindings))
    public val sizeI32: Int get() = values.size
    public operator fun get(name: String): RuntimeEffectResourceBinding? = values[name]
    public fun entries(): Set<Map.Entry<String, RuntimeEffectResourceBinding>> = values.entries
    public companion object {
        public fun of(bindings: Map<String, RuntimeEffectResourceBinding>): RuntimeEffectResourceBindings {
            val names = HashSet<String>()
            bindings.entries.forEach { require(it.key.isNotBlank() && names.add(it.key)) }
            return RuntimeEffectResourceBindings(bindings)
        }
        public val Empty: RuntimeEffectResourceBindings = of(emptyMap())
    }
}

/** Order-independent public matching; scene capture will assign the declared logical order. */
internal fun RuntimeEffectResourceBindings.requireMatches(descriptor: RuntimeEffectDescriptor) {
    require(entries().none { binding -> descriptor.logicalResources.none { it.name == binding.key } }) {
        "invalid.material.runtime_effect.resource_extra"
    }
    descriptor.logicalResources.forEach { slot ->
        val binding = requireNotNull(this[slot.name]) { "invalid.material.runtime_effect.resource_missing" }
        require(when (val facts = slot.facts) {
            is RuntimeLogicalResourceFactsV1.StorageRead -> binding is RuntimeEffectResourceBinding.StorageRead &&
                binding.sizeBytesI32.toLong() >= facts.minBindingSizeBytesI64
            is RuntimeLogicalResourceFactsV1.Texture2DFloatFilterable -> binding is RuntimeEffectResourceBinding.SampledTexture
            is RuntimeLogicalResourceFactsV1.Sampler -> binding is RuntimeEffectResourceBinding.Sampler && binding.type == facts.type
        }) { "invalid.material.runtime_effect.resource_mismatch" }
    }
}
