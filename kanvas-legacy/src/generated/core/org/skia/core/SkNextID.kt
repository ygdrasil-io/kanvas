package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkNextID {
 * public:
 *     /**
 *      *  Shared between SkPixelRef's generationID and SkImage's uniqueID
 *      */
 *     static uint32_t ImageID();
 * }
 * ```
 */
public open class SkNextID {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * uint32_t SkNextID::ImageID() {
     *     // We never set the low bit.... see SkPixelRef::genIDIsUnique().
     *     static std::atomic<uint32_t> nextID{2};
     *
     *     uint32_t id;
     *     do {
     *         id = nextID.fetch_add(2, std::memory_order_relaxed);
     *     } while (id == 0);
     *     return id;
     * }
     * ```
     */
    public fun imageID(): Int {
      TODO("Implement imageID")
    }
  }
}
