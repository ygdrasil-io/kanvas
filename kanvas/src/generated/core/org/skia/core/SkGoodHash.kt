package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct SkGoodHash {
 *     template <typename K>
 *     std::enable_if_t<std::has_unique_object_representations<K>::value && sizeof(K) == 4, uint32_t>
 *     operator()(const K& k) const {
 *         return SkChecksum::Mix(*(const uint32_t*)&k);
 *     }
 *
 *     template <typename K>
 *     std::enable_if_t<std::has_unique_object_representations<K>::value && sizeof(K) != 4, uint32_t>
 *     operator()(const K& k) const {
 *         return SkChecksum::Hash32(&k, sizeof(K));
 *     }
 *
 *     uint32_t operator()(const SkString& k) const {
 *         return SkChecksum::Hash32(k.c_str(), k.size());
 *     }
 *
 *     uint32_t operator()(const std::string& k) const {
 *         return SkChecksum::Hash32(k.c_str(), k.size());
 *     }
 *
 *     uint32_t operator()(std::string_view k) const {
 *         return SkChecksum::Hash32(k.data(), k.size());
 *     }
 * }
 * ```
 */
public open class SkGoodHash
