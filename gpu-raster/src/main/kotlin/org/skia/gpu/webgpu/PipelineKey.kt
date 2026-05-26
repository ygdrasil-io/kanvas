package org.skia.gpu.webgpu

import java.security.MessageDigest

public data class PipelineKey(
    val preimage: String,
    val hash: String,
) {
    public fun dump(): String = "preimage=$preimage;hash=$hash"
}

internal fun canonicalPipelineKeyIdentity(
    parts: List<SkWebGpuDevice.PipelineKeyClassification>,
): PipelineKey {
    val grouped = parts
        .filter { it.axisClass != SkWebGpuDevice.PipelineKeyAxisClass.UniformOnly }
        .groupBy { it.axisClass }
    val preimage = "pipeline.key v=1 " +
        "layout=[${grouped.canonicalGroup(SkWebGpuDevice.PipelineKeyAxisClass.Layout)}] " +
        "code=[${grouped.canonicalGroup(SkWebGpuDevice.PipelineKeyAxisClass.Code)}] " +
        "state=[${grouped.canonicalGroup(SkWebGpuDevice.PipelineKeyAxisClass.PipelineState)}]"
    return PipelineKey(preimage = preimage, hash = sha256Hex(preimage))
}

private fun Map<SkWebGpuDevice.PipelineKeyAxisClass, List<SkWebGpuDevice.PipelineKeyClassification>>.canonicalGroup(
    axisClass: SkWebGpuDevice.PipelineKeyAxisClass,
): String =
    this[axisClass].orEmpty()
        .sortedBy { it.axis }
        .joinToString(",") { "${it.axis}=${it.value}" }

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
