package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * static struct Initializer {
 *     Initializer() {
 *         SK_REGISTER_FLATTENABLE(IntDrawable);
 *         SK_REGISTER_FLATTENABLE(PaintDrawable);
 *         SK_REGISTER_FLATTENABLE(CompoundDrawable);
 *         SK_REGISTER_FLATTENABLE(RootDrawable);
 *     }
 * } initializer
 * ```
 */
public open class Initializer
