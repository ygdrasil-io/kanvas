package org.skia.core

import kotlin.Char

/**
 * C++ original:
 * ```cpp
 * class SkAnnotationKeys {
 * public:
 *     /**
 *      *  Returns the canonical key whose payload is a URL
 *      */
 *     static const char* URL_Key();
 *
 *     /**
 *      *  Returns the canonical key whose payload is the name of a destination to
 *      *  be defined.
 *      */
 *     static const char* Define_Named_Dest_Key();
 *
 *     /**
 *      *  Returns the canonical key whose payload is the name of a destination to
 *      *  be linked to.
 *      */
 *     static const char* Link_Named_Dest_Key();
 * }
 * ```
 */
public open class SkAnnotationKeys {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * const char* SkAnnotationKeys::URL_Key() {
     *     return "SkAnnotationKey_URL";
     * }
     * ```
     */
    public fun urlKey(): Char {
      TODO("Implement urlKey")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkAnnotationKeys::Define_Named_Dest_Key() {
     *     return "SkAnnotationKey_Define_Named_Dest";
     * }
     * ```
     */
    public fun defineNamedDestKey(): Char {
      TODO("Implement defineNamedDestKey")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkAnnotationKeys::Link_Named_Dest_Key() {
     *     return "SkAnnotationKey_Link_Named_Dest";
     * }
     * ```
     */
    public fun linkNamedDestKey(): Char {
      TODO("Implement linkNamedDestKey")
    }
  }
}
