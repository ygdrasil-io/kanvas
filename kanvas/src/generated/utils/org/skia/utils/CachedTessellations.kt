package org.skia.utils

import kotlin.Array
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkVertices
import org.skia.foundation.SkPath
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkRandom
import org.skia.math.SkVector
import undefined.FACTORY

/**
 * C++ original:
 * ```cpp
 * class CachedTessellations : public SkRefCnt {
 * public:
 *     size_t size() const { return fAmbientSet.size() + fSpotSet.size(); }
 *
 *     sk_sp<SkVertices> find(const AmbientVerticesFactory& ambient, const SkMatrix& matrix,
 *                            SkVector* translate) const {
 *         return fAmbientSet.find(ambient, matrix, translate);
 *     }
 *
 *     sk_sp<SkVertices> add(const SkPath& devPath, const AmbientVerticesFactory& ambient,
 *                           const SkMatrix& matrix, SkVector* translate) {
 *         return fAmbientSet.add(devPath, ambient, matrix, translate);
 *     }
 *
 *     sk_sp<SkVertices> find(const SpotVerticesFactory& spot, const SkMatrix& matrix,
 *                            SkVector* translate) const {
 *         return fSpotSet.find(spot, matrix, translate);
 *     }
 *
 *     sk_sp<SkVertices> add(const SkPath& devPath, const SpotVerticesFactory& spot,
 *                           const SkMatrix& matrix, SkVector* translate) {
 *         return fSpotSet.add(devPath, spot, matrix, translate);
 *     }
 *
 * private:
 *     template <typename FACTORY, int MAX_ENTRIES>
 *     class Set {
 *     public:
 *         size_t size() const { return fSize; }
 *
 *         sk_sp<SkVertices> find(const FACTORY& factory, const SkMatrix& matrix,
 *                                SkVector* translate) const {
 *             for (int i = 0; i < MAX_ENTRIES; ++i) {
 *                 if (fEntries[i].fFactory.isCompatible(factory, translate)) {
 *                     const SkMatrix& m = fEntries[i].fMatrix;
 *                     if (matrix.hasPerspective() || m.hasPerspective()) {
 *                         if (matrix != fEntries[i].fMatrix) {
 *                             continue;
 *                         }
 *                     } else if (matrix.getScaleX() != m.getScaleX() ||
 *                                matrix.getSkewX() != m.getSkewX() ||
 *                                matrix.getScaleY() != m.getScaleY() ||
 *                                matrix.getSkewY() != m.getSkewY()) {
 *                         continue;
 *                     }
 *                     return fEntries[i].fVertices;
 *                 }
 *             }
 *             return nullptr;
 *         }
 *
 *         sk_sp<SkVertices> add(const SkPath& path, const FACTORY& factory, const SkMatrix& matrix,
 *                               SkVector* translate) {
 *             sk_sp<SkVertices> vertices = factory.makeVertices(path, matrix, translate);
 *             if (!vertices) {
 *                 return nullptr;
 *             }
 *             int i;
 *             if (fCount < MAX_ENTRIES) {
 *                 i = fCount++;
 *             } else {
 *                 i = fRandom.nextULessThan(MAX_ENTRIES);
 *                 fSize -= fEntries[i].fVertices->approximateSize();
 *             }
 *             fEntries[i].fFactory = factory;
 *             fEntries[i].fVertices = vertices;
 *             fEntries[i].fMatrix = matrix;
 *             fSize += vertices->approximateSize();
 *             return vertices;
 *         }
 *
 *     private:
 *         struct Entry {
 *             FACTORY fFactory;
 *             sk_sp<SkVertices> fVertices;
 *             SkMatrix fMatrix;
 *         };
 *         Entry fEntries[MAX_ENTRIES];
 *         int fCount = 0;
 *         size_t fSize = 0;
 *         SkRandom fRandom;
 *     };
 *
 *     Set<AmbientVerticesFactory, 4> fAmbientSet;
 *     Set<SpotVerticesFactory, 4> fSpotSet;
 * }
 * ```
 */
public open class CachedTessellations : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * Set<AmbientVerticesFactory, 4> fAmbientSet
   * ```
   */
  private var fAmbientSet: Set = TODO("Initialize fAmbientSet")

  /**
   * C++ original:
   * ```cpp
   * Set<SpotVerticesFactory, 4> fSpotSet
   * ```
   */
  private var fSpotSet: Set = TODO("Initialize fSpotSet")

  /**
   * C++ original:
   * ```cpp
   * size_t size() const { return fAmbientSet.size() + fSpotSet.size(); }
   * ```
   */
  public fun size(): ULong {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> find(const AmbientVerticesFactory& ambient, const SkMatrix& matrix,
   *                            SkVector* translate) const {
   *         return fAmbientSet.find(ambient, matrix, translate);
   *     }
   * ```
   */
  public fun find(
    ambient: AmbientVerticesFactory,
    matrix: SkMatrix,
    translate: SkVector?,
  ): SkSp<SkVertices> {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> add(const SkPath& devPath, const AmbientVerticesFactory& ambient,
   *                           const SkMatrix& matrix, SkVector* translate) {
   *         return fAmbientSet.add(devPath, ambient, matrix, translate);
   *     }
   * ```
   */
  public fun add(
    devPath: SkPath,
    ambient: AmbientVerticesFactory,
    matrix: SkMatrix,
    translate: SkVector?,
  ): SkSp<SkVertices> {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> find(const SpotVerticesFactory& spot, const SkMatrix& matrix,
   *                            SkVector* translate) const {
   *         return fSpotSet.find(spot, matrix, translate);
   *     }
   * ```
   */
  public fun find(
    spot: SpotVerticesFactory,
    matrix: SkMatrix,
    translate: SkVector?,
  ): SkSp<SkVertices> {
    TODO("Implement find")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> add(const SkPath& devPath, const SpotVerticesFactory& spot,
   *                           const SkMatrix& matrix, SkVector* translate) {
   *         return fSpotSet.add(devPath, spot, matrix, translate);
   *     }
   * ```
   */
  public fun add(
    devPath: SkPath,
    spot: SpotVerticesFactory,
    matrix: SkMatrix,
    translate: SkVector?,
  ): SkSp<SkVertices> {
    TODO("Implement add")
  }

  public data class Set<FACTORY> public constructor(
    private var fEntries: Array<org.skia.core.Entry>,
    private var fCount: Int,
    private var fSize: ULong,
    private var fRandom: SkRandom,
  ) {
    public fun size(): ULong {
      TODO("Implement size")
    }

    public fun find(
      factory: FACTORY,
      matrix: SkMatrix,
      translate: SkVector?,
    ): SkSp<SkVertices> {
      TODO("Implement find")
    }

    public fun add(
      path: SkPath,
      factory: FACTORY,
      matrix: SkMatrix,
      translate: SkVector?,
    ): SkSp<SkVertices> {
      TODO("Implement add")
    }

    public open class Entry public constructor(
      public var fFactory: FACTORY,
      public var fVertices: SkSp<SkVertices>,
      public var fMatrix: SkMatrix,
    )
  }
}
