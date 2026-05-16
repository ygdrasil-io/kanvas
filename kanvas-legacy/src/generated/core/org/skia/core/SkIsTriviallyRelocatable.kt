package org.skia.core

import kotlin.disjunction
import kotlin.is_trivially_copyable

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * struct sk_is_trivially_relocatable
 *         : std::disjunction<std::is_trivially_copyable<T>, sk_has_trivially_relocatable_member<T>>{}
 * ```
 */
public open class SkIsTriviallyRelocatable<T> : disjunction(), is_trivially_copyable, T,
    SkHasTriviallyRelocatableMember
