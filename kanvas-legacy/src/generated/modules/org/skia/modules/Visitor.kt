package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * template<class... Ts>
 * struct Visitor : Ts... { using Ts::operator()...; }
 * ```
 */
public open class Visitor<Ts> : Ts()
