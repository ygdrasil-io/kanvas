package org.skia.utils

import org.skia.core.SkVertices
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * template <typename FACTORY>
 * struct FindContext {
 *     FindContext(const SkMatrix* viewMatrix, const FACTORY* factory)
 *             : fViewMatrix(viewMatrix), fFactory(factory) {}
 *     const SkMatrix* const fViewMatrix;
 *     // If this is valid after Find is called then we found the vertices and they should be drawn
 *     // with fTranslate applied.
 *     sk_sp<SkVertices> fVertices;
 *     SkVector fTranslate = {0, 0};
 *
 *     // If this is valid after Find then the caller should add the vertices to the tessellation set
 *     // and create a new CachedTessellationsRec and insert it into SkResourceCache.
 *     sk_sp<CachedTessellations> fTessellationsOnFailure;
 *
 *     const FACTORY* fFactory;
 * }
 * ```
 */
public data class FindContext<FACTORY> public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix* const fViewMatrix
   * ```
   */
  private val fViewMatrix: SkMatrix?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> fVertices
   * ```
   */
  private var fVertices: SkSp<SkVertices>,
  /**
   * C++ original:
   * ```cpp
   * SkVector fTranslate = {0, 0}
   * ```
   */
  private var fTranslate: SkVector,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<CachedTessellations> fTessellationsOnFailure
   * ```
   */
  private var fTessellationsOnFailure: SkSp<CachedTessellations>,
  /**
   * C++ original:
   * ```cpp
   * const FACTORY* fFactory
   * ```
   */
  private val fFactory: FACTORY,
)
