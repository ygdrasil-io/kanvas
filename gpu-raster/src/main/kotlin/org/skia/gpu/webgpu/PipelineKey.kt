package org.skia.gpu.webgpu

import java.security.MessageDigest

public enum class PipelineKeyAxisClass {
    Layout,
    Code,
    PipelineState,
    UniformOnly,
}

public data class PipelineKeyClassification(
    val axis: String,
    val axisClass: PipelineKeyAxisClass,
    val value: String,
)

public data class PipelineKey(
    val preimage: String,
    val hash: String,
    val uniformFacts: String,
) {
    public fun dump(): String = "preimage=$preimage;hash=$hash;uniformFacts=$uniformFacts"
}

internal class PipelineKeyedCache<T>(
    private val name: String,
    private val collisionFatal: Boolean = System.getProperty(PIPELINE_KEY_COLLISION_FATAL_FLAG, "false").toBoolean(),
) {
    private data class Entry<T>(
        val key: PipelineKey,
        val value: T,
    )

    private val buckets: MutableMap<String, MutableList<Entry<T>>> = mutableMapOf()

    val size: Int
        get() = buckets.values.sumOf { it.size }

    fun values(): List<T> = buckets.values.flatMap { bucket -> bucket.map { it.value } }

    fun clear() {
        buckets.clear()
    }

    fun getOrPut(key: PipelineKey, create: () -> T): T {
        val bucket = buckets.getOrPut(key.hash) { mutableListOf() }
        bucket.firstOrNull { it.key.preimage == key.preimage }?.let { return it.value }
        if (bucket.isNotEmpty() && collisionFatal) {
            val existing = bucket.first().key
            error(
                "PipelineKey hash collision in $name: hash=${key.hash}; " +
                    "existingPreimage=${existing.preimage}; newPreimage=${key.preimage}",
            )
        }
        return create().also { value -> bucket += Entry(key, value) }
    }

    fun dump(): String =
        buckets.values
            .flatten()
            .sortedBy { it.key.hash }
            .joinToString("\n") { it.key.dump() }
}

internal const val PIPELINE_KEY_COLLISION_FATAL_FLAG: String = "kanvas.gpu.pipelineKey.collisionFatal"

internal fun canonicalPipelineKeyIdentity(
    parts: List<PipelineKeyClassification>,
): PipelineKey {
    val grouped = parts
        .filter { it.axisClass != PipelineKeyAxisClass.UniformOnly }
        .groupBy { it.axisClass }
    val preimage = "pipeline.key v=1 " +
        "layout=[${grouped.canonicalGroup(PipelineKeyAxisClass.Layout)}] " +
        "code=[${grouped.canonicalGroup(PipelineKeyAxisClass.Code)}] " +
        "state=[${grouped.canonicalGroup(PipelineKeyAxisClass.PipelineState)}]"
    return PipelineKey(
        preimage = preimage,
        hash = sha256Hex(preimage),
        uniformFacts = parts
            .filter { it.axisClass == PipelineKeyAxisClass.UniformOnly }
            .sortedBy { it.axis }
            .joinToString(prefix = "[", postfix = "]") { "${it.axis}=${it.value}" },
    )
}

private fun Map<PipelineKeyAxisClass, List<PipelineKeyClassification>>.canonicalGroup(
    axisClass: PipelineKeyAxisClass,
): String =
    this[axisClass].orEmpty()
        .sortedBy { it.axis }
        .joinToString(",") { "${it.axis}=${it.value}" }

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
