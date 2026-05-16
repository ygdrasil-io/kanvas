package org.skia.effects

import kotlin.Boolean
import org.skia.core.SkStageRec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkTableColorFilter final : public SkColorFilterBase {
 * public:
 *     SkTableColorFilter(sk_sp<SkColorTable> table) : fTable(table) {
 *         SkASSERT(fTable);
 *     }
 *
 *     SkColorFilterBase::Type type() const override { return SkColorFilterBase::Type::kTable; }
 *
 *     bool appendStages(const SkStageRec& rec, bool shaderIsOpaque) const override;
 *
 *     void flatten(SkWriteBuffer& buffer) const override;
 *
 *     const SkBitmap& bitmap() const { return fTable->bitmap(); }
 *
 * private:
 *     friend void ::SkRegisterTableColorFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkTableColorFilter)
 *
 *     sk_sp<SkColorTable> fTable;
 * }
 * ```
 */
public class SkTableColorFilter : SkColorFilterBase() {
  /**
   * C++ original:
   * ```cpp
   * void SkTableColorFilter::flatten(SkWriteBuffer& buffer) const {
   *     fTable->flatten(buffer);
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkBitmap& bitmap() const { return fTable->bitmap(); }
   * ```
   */
  public fun bitmap(): SkBitmap {
    TODO("Implement bitmap")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkTableColorFilter::appendStages(const SkStageRec& rec, bool shaderIsOpaque) const {
   *     SkRasterPipeline* p = rec.fPipeline;
   *     if (!shaderIsOpaque) {
   *         p->append(SkRasterPipelineOp::unpremul);
   *     }
   *
   *     SkRasterPipelineContexts::TablesCtx* tables =
   *             rec.fAlloc->make<SkRasterPipelineContexts::TablesCtx>();
   *     tables->a = fTable->alphaTable();
   *     tables->r = fTable->redTable();
   *     tables->g = fTable->greenTable();
   *     tables->b = fTable->blueTable();
   *     p->append(SkRasterPipelineOp::byte_tables, tables);
   *
   *     bool definitelyOpaque = shaderIsOpaque && tables->a[0xff] == 0xff;
   *     if (!definitelyOpaque) {
   *         p->append(SkRasterPipelineOp::premul);
   *     }
   *     return true;
   * }
   * ```
   */
  public override fun appendStages(rec: SkStageRec, shaderIsOpaque: Boolean): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkTableColorFilter::CreateProc(SkReadBuffer& buffer) {
   *     return SkColorFilters::Table(SkColorTable::Deserialize(buffer));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
