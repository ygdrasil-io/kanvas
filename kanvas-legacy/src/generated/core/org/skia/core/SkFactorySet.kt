package org.skia.core

import org.skia.foundation.SkFlattenableFactory

/**
 * C++ original:
 * ```cpp
 * class SkFactorySet : public SkTPtrSet<SkFlattenable::Factory> {}
 * ```
 */
public open class SkFactorySet : SkTPtrSet(), SkFlattenableFactory
