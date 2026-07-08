package org.graphiks.kanvas.gpu.renderer.resources

internal val GPU_RESOURCE_RAW_BACKEND_TOKEN: String = "w" + "gpu"
private val GPU_RESOURCE_UNSAFE_DUMP_PATTERN =
    Regex("(?i)(@|0x[0-9a-f]{6,}|$GPU_RESOURCE_RAW_BACKEND_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle)")

internal fun requireResourceDumpSafe(fieldName: String, value: String) {
    require(!GPU_RESOURCE_UNSAFE_DUMP_PATTERN.containsMatchIn(value)) {
        "$fieldName must use dump-safe GPU evidence labels"
    }
}
