package org.skia.utils

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class ShadowedPath {
 * public:
 *     ShadowedPath(const SkPath* path, const SkMatrix* viewMatrix)
 *             : fPath(path)
 *             , fViewMatrix(viewMatrix)
 * #if defined(SK_GANESH)
 *             , fShapeForKey(*path, GrStyle::SimpleFill())
 * #endif
 *     {}
 *
 *     const SkPath& path() const { return *fPath; }
 *     const SkMatrix& viewMatrix() const { return *fViewMatrix; }
 * #if defined(SK_GANESH)
 *     /** Negative means the vertices should not be cached for this path. */
 *     int keyBytes() const { return fShapeForKey.unstyledKeySize() * sizeof(uint32_t); }
 *     void writeKey(void* key) const {
 *         fShapeForKey.writeUnstyledKey(reinterpret_cast<uint32_t*>(key));
 *     }
 *     bool isRRect(SkRRect* rrect) { return fShapeForKey.asRRect(rrect, nullptr); }
 * #else
 *     int keyBytes() const { return -1; }
 *     void writeKey(void* key) const { SK_ABORT("Should never be called"); }
 *     bool isRRect(SkRRect* rrect) { return false; }
 * #endif
 *
 * private:
 *     const SkPath* fPath;
 *     const SkMatrix* fViewMatrix;
 * #if defined(SK_GANESH)
 *     GrStyledShape fShapeForKey;
 * #endif
 * }
 * ```
 */
public data class ShadowedPath public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkPath* fPath
   * ```
   */
  private val fPath: SkPath?,
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix* fViewMatrix
   * ```
   */
  private val fViewMatrix: SkMatrix?,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkPath& path() const { return *fPath; }
   * ```
   */
  public fun path(): SkPath {
    TODO("Implement path")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkMatrix& viewMatrix() const { return *fViewMatrix; }
   * ```
   */
  public fun viewMatrix(): SkMatrix {
    TODO("Implement viewMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * int keyBytes() const { return -1; }
   * ```
   */
  public fun keyBytes(): Int {
    TODO("Implement keyBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeKey(void* key) const { SK_ABORT("Should never be called"); }
   * ```
   */
  public fun writeKey(key: Unit?) {
    TODO("Implement writeKey")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRRect(SkRRect* rrect) { return false; }
   * ```
   */
  public fun isRRect(rrect: SkRRect?): Boolean {
    TODO("Implement isRRect")
  }
}
