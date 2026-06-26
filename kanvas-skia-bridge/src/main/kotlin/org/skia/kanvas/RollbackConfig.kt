package org.skia.kanvas

object RollbackConfig {
    private const val PRODUCT_ACTIVATION_DISABLE = "kanvas.product.activation.disable"

    val productActivation: Boolean
        get() = !System.getProperty(PRODUCT_ACTIVATION_DISABLE, "false").toBoolean()
}
