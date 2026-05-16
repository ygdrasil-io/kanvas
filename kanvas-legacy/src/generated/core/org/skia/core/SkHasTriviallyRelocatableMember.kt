package org.skia.core

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * template<typename, typename = void>
 * struct sk_has_trivially_relocatable_member : std::false_type {}
 * ```
 */
public open class SkHasTriviallyRelocatableMember<> : Boolean()
