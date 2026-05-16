package org.skia.core

import org.skia.`external`.Hash

/**
 * C++ original:
 * ```cpp
 * class SkResourceCache::Hash :
 *     public THashTable<SkResourceCache::Rec*, SkResourceCache::Key, HashTraits> {}
 * ```
 */
public open class Hash : Hash(), THashTable, SkResourceCache.Rec, SkResourceCache.Key, HashTraits
