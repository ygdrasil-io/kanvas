package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest

/** Shared SHA-256 hash utility for WGSL identity and reflection. */
internal object WGSLHashUtils {
    /** SHA-256 hex digest truncated to 12 characters (48 effective bits).
     *  Collision probability ~1% at ~300K entries — acceptable for custom
     *  runtime-effect identity where effects are user-registered and counts
     *  are bounded by resource limits. */
    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
}
