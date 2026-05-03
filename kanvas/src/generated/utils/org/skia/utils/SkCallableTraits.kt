package org.skia.utils

import T.`operator`
import kotlin.Any

/**
 * C++ original:
 * ```cpp
 * struct SkCallableTraits : SkCallableTraits<decltype(&T::operator())> {}
 * ```
 */
public open class SkCallableTraits<T> : SkCallableTraits<T>(), Any, `operator`
