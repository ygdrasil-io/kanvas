package org.skia.core

import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkBitmap
import Args as Args_
import undefined.Args as UndefinedArgs

/**
 * C++ original:
 * ```cpp
 * class DebugStageBuilder {
 * public:
 *     DebugStageBuilder() = default;
 *     DebugStageBuilder(const DebugStageBuilder&) = delete;
 *     DebugStageBuilder(DebugStageBuilder&&) = delete;
 *     DebugStageBuilder& operator=(const DebugStageBuilder&) = delete;
 *     DebugStageBuilder& operator=(DebugStageBuilder&&) = delete;
 *
 *     template <typename... Args>
 *     DebugStageBuilder& add(const SkBitmap& panel, SkRasterPipelineOp op, Args... args) {
 *         std::vector<SkBitmap> panels;
 *         std::vector<SkRasterPipelineOp> ops;
 *
 *         add_next(panels, ops, panel, op, args...);
 *         fDebugStages.push_back({panels, ops});
 *         return *this;
 *     }
 *
 *     DebugStageBuilder& add() {
 *         std::vector<SkBitmap> panels;
 *         std::vector<SkRasterPipelineOp> ops;
 *         fDebugStages.push_back({panels, ops});
 *         return *this;
 *     }
 *
 *     std::vector<DebugStage> build() { return fDebugStages; }
 *
 * private:
 *     std::vector<DebugStage> fDebugStages;
 *
 *     static void add_next(std::vector<SkBitmap>& v, std::vector<SkRasterPipelineOp>& ops) {}
 *
 *     template <typename... Args>
 *     static void add_next(std::vector<SkBitmap>& panels,
 *                          std::vector<SkRasterPipelineOp>& ops,
 *                          const SkBitmap& panel,
 *                          SkRasterPipelineOp op,
 *                          Args... args) {
 *         panels.emplace_back(panel);
 *         ops.emplace_back(op);
 *         add_next(panels, ops, args...);
 *     }
 * }
 * ```
 */
public data class DebugStageBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<DebugStage> fDebugStages
   * ```
   */
  private var fDebugStages: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * DebugStageBuilder& operator=(const DebugStageBuilder&) = delete
   * ```
   */
  public fun assign(param0: DebugStageBuilder) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * DebugStageBuilder& operator=(DebugStageBuilder&&) = delete
   * ```
   */
  public fun <Args> add(
    panel: SkBitmap,
    op: SkRasterPipelineOp,
    args: UndefinedArgs,
  ): DebugStageBuilder {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename... Args>
   *     DebugStageBuilder& add(const SkBitmap& panel, SkRasterPipelineOp op, Args... args) {
   *         std::vector<SkBitmap> panels;
   *         std::vector<SkRasterPipelineOp> ops;
   *
   *         add_next(panels, ops, panel, op, args...);
   *         fDebugStages.push_back({panels, ops});
   *         return *this;
   *     }
   * ```
   */
  public fun add(): DebugStageBuilder {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * DebugStageBuilder& add() {
   *         std::vector<SkBitmap> panels;
   *         std::vector<SkRasterPipelineOp> ops;
   *         fDebugStages.push_back({panels, ops});
   *         return *this;
   *     }
   * ```
   */
  public fun build(): Int {
    TODO("Implement build")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void add_next(std::vector<SkBitmap>& v, std::vector<SkRasterPipelineOp>& ops) {}
     * ```
     */
    private fun addNext(v: List<SkBitmap>, ops: List<SkRasterPipelineOp>) {
      TODO("Implement addNext")
    }

    /**
     * C++ original:
     * ```cpp
     *     template <typename... Args>
     *     static void add_next(std::vector<SkBitmap>& panels,
     *                          std::vector<SkRasterPipelineOp>& ops,
     *                          const SkBitmap& panel,
     *                          SkRasterPipelineOp op,
     *                          Args... args) {
     *         panels.emplace_back(panel);
     *         ops.emplace_back(op);
     *         add_next(panels, ops, args...);
     *     }
     * ```
     */
    private fun <Args> addNext(
      panels: List<SkBitmap>,
      ops: List<SkRasterPipelineOp>,
      panel: SkBitmap,
      op: SkRasterPipelineOp,
      args: Args_,
    ) {
      TODO("Implement addNext")
    }
  }
}
