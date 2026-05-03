package org.skia.core

import kotlin.Array
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkPMColor

/**
 * C++ original:
 * ```cpp
 * class BitmapProcShaderContext : public SkShaderBase::Context {
 * public:
 *     BitmapProcShaderContext(const SkShaderBase& shader, const SkShaderBase::ContextRec& rec,
 *                             SkBitmapProcState* state)
 *         : INHERITED(shader, rec)
 *         , fState(state)
 *         , fFlags(0)
 *     {
 *         if (fState->fPixmap.isOpaque() && (255 == this->getPaintAlpha())) {
 *             fFlags |= SkShaderBase::kOpaqueAlpha_Flag;
 *         }
 *     }
 *
 *     uint32_t getFlags() const override { return fFlags; }
 *
 *     void shadeSpan(int x, int y, SkPMColor dstC[], int count) override {
 *         const SkBitmapProcState& state = *fState;
 *         if (state.getShaderProc32()) {
 *             state.getShaderProc32()(&state, x, y, dstC, count);
 *             return;
 *         }
 *
 *         const int BUF_MAX = 128;
 *         uint32_t buffer[BUF_MAX];
 *         SkBitmapProcState::MatrixProc   mproc = state.getMatrixProc();
 *         SkBitmapProcState::SampleProc32 sproc = state.getSampleProc32();
 *         const int max = state.maxCountForBufferSize(sizeof(buffer[0]) * BUF_MAX);
 *
 *         SkASSERT(state.fPixmap.addr());
 *
 *         for (;;) {
 *             int n = std::min(count, max);
 *             SkASSERT(n > 0 && n < BUF_MAX*2);
 *             mproc(state, buffer, n, x, y);
 *             sproc(state, buffer, n, dstC);
 *
 *             if ((count -= n) == 0) {
 *                 break;
 *             }
 *             SkASSERT(count > 0);
 *             x += n;
 *             dstC += n;
 *         }
 *     }
 *
 * private:
 *     SkBitmapProcState*  fState;
 *     uint32_t            fFlags;
 *
 *     using INHERITED = SkShaderBase::Context;
 * }
 * ```
 */
public open class BitmapProcShaderContext public constructor(
  shader: SkShaderBase,
  rec: SkShaderBase.ContextRec,
  state: SkBitmapProcState?,
) : SkShaderBase.Context(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkBitmapProcState*  fState
   * ```
   */
  private var fState: SkBitmapProcState? = TODO("Initialize fState")

  /**
   * C++ original:
   * ```cpp
   * uint32_t            fFlags
   * ```
   */
  private var fFlags: UInt = TODO("Initialize fFlags")

  /**
   * C++ original:
   * ```cpp
   * uint32_t getFlags() const override { return fFlags; }
   * ```
   */
  public override fun getFlags(): UInt {
    TODO("Implement getFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * void shadeSpan(int x, int y, SkPMColor dstC[], int count) override {
   *         const SkBitmapProcState& state = *fState;
   *         if (state.getShaderProc32()) {
   *             state.getShaderProc32()(&state, x, y, dstC, count);
   *             return;
   *         }
   *
   *         const int BUF_MAX = 128;
   *         uint32_t buffer[BUF_MAX];
   *         SkBitmapProcState::MatrixProc   mproc = state.getMatrixProc();
   *         SkBitmapProcState::SampleProc32 sproc = state.getSampleProc32();
   *         const int max = state.maxCountForBufferSize(sizeof(buffer[0]) * BUF_MAX);
   *
   *         SkASSERT(state.fPixmap.addr());
   *
   *         for (;;) {
   *             int n = std::min(count, max);
   *             SkASSERT(n > 0 && n < BUF_MAX*2);
   *             mproc(state, buffer, n, x, y);
   *             sproc(state, buffer, n, dstC);
   *
   *             if ((count -= n) == 0) {
   *                 break;
   *             }
   *             SkASSERT(count > 0);
   *             x += n;
   *             dstC += n;
   *         }
   *     }
   * ```
   */
  public override fun shadeSpan(
    x: Int,
    y: Int,
    dstC: Array<SkPMColor>,
    count: Int,
  ) {
    TODO("Implement shadeSpan")
  }
}
