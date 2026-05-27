package org.skia.effects.runtime

public data class SkRuntimeEffectDescriptor(
    val stableId: String,
    val kind: SkRuntimeEffect.Kind,
    val uniforms: List<SkRuntimeEffect.Uniform>,
    val children: List<SkRuntimeEffect.Child>,
    val flags: Int,
    val cpuImplementationId: String,
    val wgslImplementationId: String?,
)

public object SkRuntimeEffectDescriptorRegistry {
    private val byHash: MutableMap<Long, SkRuntimeEffectDescriptor> = HashMap()
    private val byStableId: MutableMap<String, SkRuntimeEffectDescriptor> = HashMap()

    public fun register(source: String, descriptor: SkRuntimeEffectDescriptor) {
        val hash = SkRuntimeEffectDispatch.canonicalHash(source)
        rejectDuplicate(hash, descriptor)
        byHash[hash] = descriptor
        byStableId[descriptor.stableId] = descriptor
    }

    public fun lookup(source: String): SkRuntimeEffectDescriptor? =
        byHash[SkRuntimeEffectDispatch.canonicalHash(source)]

    public fun missingDiagnostic(source: String): String =
        "Runtime effect descriptor not registered: ${SkRuntimeEffectDispatch.canonicalHash(source)}"

    internal fun registerBuiltinIfAbsent(source: String, descriptor: SkRuntimeEffectDescriptor) {
        val hash = SkRuntimeEffectDispatch.canonicalHash(source)
        val existingByHash = byHash[hash]
        val existingByStableId = byStableId[descriptor.stableId]
        if (existingByHash != null || existingByStableId != null) {
            check(existingByHash?.stableId == descriptor.stableId && existingByStableId != null) {
                duplicateDiagnostic(hash, descriptor)
            }
            return
        }
        byHash[hash] = descriptor
        byStableId[descriptor.stableId] = descriptor
    }

    internal fun registerForTestOverride(source: String, descriptor: SkRuntimeEffectDescriptor) {
        val hash = SkRuntimeEffectDispatch.canonicalHash(source)
        byHash.remove(hash)?.let { byStableId.remove(it.stableId) }
        byStableId.remove(descriptor.stableId)
        byHash.entries.removeIf { it.value.stableId == descriptor.stableId }
        byHash[hash] = descriptor
        byStableId[descriptor.stableId] = descriptor
    }

    internal fun clearForTest() {
        byHash.clear()
        byStableId.clear()
    }

    private fun rejectDuplicate(hash: Long, descriptor: SkRuntimeEffectDescriptor) {
        check(hash !in byHash && descriptor.stableId !in byStableId) {
            duplicateDiagnostic(hash, descriptor)
        }
    }

    private fun duplicateDiagnostic(hash: Long, descriptor: SkRuntimeEffectDescriptor): String =
        "Duplicate runtime effect descriptor registration: canonicalHash=$hash stableId=${descriptor.stableId}"
}
