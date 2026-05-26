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

    public fun register(source: String, descriptor: SkRuntimeEffectDescriptor) {
        byHash[SkRuntimeEffectDispatch.canonicalHash(source)] = descriptor
    }

    public fun lookup(source: String): SkRuntimeEffectDescriptor? =
        byHash[SkRuntimeEffectDispatch.canonicalHash(source)]

    public fun missingDiagnostic(source: String): String =
        "Runtime effect descriptor not registered: ${SkRuntimeEffectDispatch.canonicalHash(source)}"

    internal fun clearForTest() {
        byHash.clear()
    }
}
