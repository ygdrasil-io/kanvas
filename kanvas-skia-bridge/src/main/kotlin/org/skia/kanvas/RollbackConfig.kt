package org.skia.kanvas

object RollbackConfig {
    private const val SYSTEM_PROPERTY = "kanvas.rollback.legacy-gpu-raster"
    private const val ENV_VARIABLE = "KANVAS_ROLLBACK_LEGACY_GPU_RASTER"

    val useLegacyGpuRaster: Boolean
        get() = System.getProperty(SYSTEM_PROPERTY, "false").toBoolean()
            || System.getenv(ENV_VARIABLE)?.toBoolean() == true
}
