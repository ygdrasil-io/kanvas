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

public data class SkRuntimeEffectSupportMatrixEntry(
    val canonicalHash: Long,
    val descriptor: SkRuntimeEffectDescriptor,
) {
    public val cpuSupport: String =
        if (descriptor.cpuImplementationId.isBlank()) {
            "unsupported: CPU implementation id missing"
        } else {
            "supported:${descriptor.cpuImplementationId}"
        }

    public val gpuSupport: String =
        descriptor.wgslImplementationId
            ?.takeIf { it.isNotBlank() }
            ?.let { "supported:$it" }
            ?: "unsupported: WGSL implementation id missing"

    public val missingDiagnostic: String =
        "Runtime effect descriptor not registered: $canonicalHash"
}

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

    public fun supportMatrixEntries(): List<SkRuntimeEffectSupportMatrixEntry> =
        byHash.entries
            .map { SkRuntimeEffectSupportMatrixEntry(it.key, it.value) }
            .sortedWith(
                compareBy<SkRuntimeEffectSupportMatrixEntry> { it.descriptor.stableId }
                    .thenBy { it.canonicalHash },
            )

    public fun exportSupportMatrixMarkdown(): String = buildString {
        appendLine("# Runtime Effect Descriptor Support Matrix")
        appendLine()
        appendLine("Derived evidence. The descriptor registry is the source of truth.")
        appendLine()
        appendLine(
            "| Stable id | Canonical hash | Kind | Uniforms | Children | Flags | CPU support | GPU support | Missing diagnostic |",
        )
        appendLine("|---|---:|---|---|---|---:|---|---|---|")
        supportMatrixEntries().forEach { entry ->
            val descriptor = entry.descriptor
            append("| ")
            append(markdownCell(descriptor.stableId))
            append(" | ")
            append(entry.canonicalHash)
            append(" | ")
            append(descriptor.kind)
            append(" | ")
            append(markdownCell(descriptor.uniforms.joinToString(", ") { "${it.name}:${it.type}" }.ifEmpty { "-" }))
            append(" | ")
            append(markdownCell(descriptor.children.joinToString(", ") { "${it.name}:${it.type}" }.ifEmpty { "-" }))
            append(" | ")
            append(descriptor.flags)
            append(" | ")
            append(markdownCell(entry.cpuSupport))
            append(" | ")
            append(markdownCell(entry.gpuSupport))
            append(" | ")
            append(markdownCell(entry.missingDiagnostic))
            appendLine(" |")
        }
    }

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

    private fun markdownCell(value: String): String =
        value.replace("|", "\\|").replace("\n", " ")
}
