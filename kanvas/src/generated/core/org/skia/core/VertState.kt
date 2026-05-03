package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct VertState {
 *     int f0, f1, f2;
 *
 *     /**
 *      *  Construct a VertState from a vertex count, index array, and index count.
 *      *  If the vertices are unindexed pass nullptr for indices.
 *      */
 *     VertState(int vCount, const uint16_t indices[], int indexCount)
 *             : fIndices(indices) {
 *         fCurrIndex = 0;
 *         if (indices) {
 *             fCount = indexCount;
 *         } else {
 *             fCount = vCount;
 *         }
 *     }
 *
 *     typedef bool (*Proc)(VertState*);
 *
 *     /**
 *      *  Choose an appropriate function to traverse the vertices.
 *      *  @param mode    Specifies the SkCanvas::VertexMode.
 *      */
 *     Proc chooseProc(SkVertices::VertexMode mode);
 *
 * private:
 *     int             fCount;
 *     int             fCurrIndex;
 *     const uint16_t* fIndices;
 *
 *     static bool Triangles(VertState*);
 *     static bool TrianglesX(VertState*);
 *     static bool TriangleStrip(VertState*);
 *     static bool TriangleStripX(VertState*);
 *     static bool TriangleFan(VertState*);
 *     static bool TriangleFanX(VertState*);
 * }
 * ```
 */
public data class VertState public constructor(
  /**
   * C++ original:
   * ```cpp
   * int f0
   * ```
   */
  public var f0: Int,
  /**
   * C++ original:
   * ```cpp
   * int f0, f1
   * ```
   */
  public var f1: Int,
  /**
   * C++ original:
   * ```cpp
   * int f0, f1, f2
   * ```
   */
  public var f2: Int,
  /**
   * C++ original:
   * ```cpp
   * int             fCount
   * ```
   */
  private var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int             fCurrIndex
   * ```
   */
  private var fCurrIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * const uint16_t* fIndices
   * ```
   */
  private val fIndices: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * VertState::Proc VertState::chooseProc(SkVertices::VertexMode mode) {
   *     switch (mode) {
   *         case SkVertices::kTriangles_VertexMode:
   *             return fIndices ? TrianglesX : Triangles;
   *         case SkVertices::kTriangleStrip_VertexMode:
   *             return fIndices ? TriangleStripX : TriangleStrip;
   *         case SkVertices::kTriangleFan_VertexMode:
   *             return fIndices ? TriangleFanX : TriangleFan;
   *         default:
   *             return nullptr;
   *     }
   * }
   * ```
   */
  public fun chooseProc(mode: SkVertices.VertexMode): VertStateProc {
    TODO("Implement chooseProc")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool VertState::Triangles(VertState* state) {
     *     int index = state->fCurrIndex;
     *     if (index + 3 > state->fCount) {
     *         return false;
     *     }
     *     state->f0 = index + 0;
     *     state->f1 = index + 1;
     *     state->f2 = index + 2;
     *     state->fCurrIndex = index + 3;
     *     return true;
     * }
     * ```
     */
    private fun triangles(state: VertState?): Boolean {
      TODO("Implement triangles")
    }

    /**
     * C++ original:
     * ```cpp
     * bool VertState::TrianglesX(VertState* state) {
     *     const uint16_t* indices = state->fIndices;
     *     int index = state->fCurrIndex;
     *     if (index + 3 > state->fCount) {
     *         return false;
     *     }
     *     state->f0 = indices[index + 0];
     *     state->f1 = indices[index + 1];
     *     state->f2 = indices[index + 2];
     *     state->fCurrIndex = index + 3;
     *     return true;
     * }
     * ```
     */
    private fun trianglesX(state: VertState?): Boolean {
      TODO("Implement trianglesX")
    }

    /**
     * C++ original:
     * ```cpp
     * bool VertState::TriangleStrip(VertState* state) {
     *     int index = state->fCurrIndex;
     *     if (index + 3 > state->fCount) {
     *         return false;
     *     }
     *     state->f2 = index + 2;
     *     if (index & 1) {
     *         state->f0 = index + 1;
     *         state->f1 = index + 0;
     *     } else {
     *         state->f0 = index + 0;
     *         state->f1 = index + 1;
     *     }
     *     state->fCurrIndex = index + 1;
     *     return true;
     * }
     * ```
     */
    private fun triangleStrip(state: VertState?): Boolean {
      TODO("Implement triangleStrip")
    }

    /**
     * C++ original:
     * ```cpp
     * bool VertState::TriangleStripX(VertState* state) {
     *     const uint16_t* indices = state->fIndices;
     *     int index = state->fCurrIndex;
     *     if (index + 3 > state->fCount) {
     *         return false;
     *     }
     *     state->f2 = indices[index + 2];
     *     if (index & 1) {
     *         state->f0 = indices[index + 1];
     *         state->f1 = indices[index + 0];
     *     } else {
     *         state->f0 = indices[index + 0];
     *         state->f1 = indices[index + 1];
     *     }
     *     state->fCurrIndex = index + 1;
     *     return true;
     * }
     * ```
     */
    private fun triangleStripX(state: VertState?): Boolean {
      TODO("Implement triangleStripX")
    }

    /**
     * C++ original:
     * ```cpp
     * bool VertState::TriangleFan(VertState* state) {
     *     int index = state->fCurrIndex;
     *     if (index + 3 > state->fCount) {
     *         return false;
     *     }
     *     state->f0 = 0;
     *     state->f1 = index + 1;
     *     state->f2 = index + 2;
     *     state->fCurrIndex = index + 1;
     *     return true;
     * }
     * ```
     */
    private fun triangleFan(state: VertState?): Boolean {
      TODO("Implement triangleFan")
    }

    /**
     * C++ original:
     * ```cpp
     * bool VertState::TriangleFanX(VertState* state) {
     *     const uint16_t* indices = state->fIndices;
     *     int index = state->fCurrIndex;
     *     if (index + 3 > state->fCount) {
     *         return false;
     *     }
     *     state->f0 = indices[0];
     *     state->f1 = indices[index + 1];
     *     state->f2 = indices[index + 2];
     *     state->fCurrIndex = index + 1;
     *     return true;
     * }
     * ```
     */
    private fun triangleFanX(state: VertState?): Boolean {
      TODO("Implement triangleFanX")
    }
  }
}
