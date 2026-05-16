package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.foundation.SkSurfaceProps
import org.skia.gpu.ContextOptions
import org.skia.gpu.ganesh.GrContextOptions
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * struct Src {
 *     using GraphiteTestContext = skiatest::graphite::GraphiteTestContext;
 *
 *     virtual ~Src() {}
 *     [[nodiscard]] virtual Result draw(SkCanvas* canvas, GraphiteTestContext*) const = 0;
 *     virtual SkISize size() const = 0;
 *     virtual Name name() const = 0;
 *     // Called by sinks to modify the default-default surface properties (if applicable).
 *     // Sinks may then further update the value based on other criteria.
 *     virtual void modifySurfaceProps(SkSurfaceProps*) const {}
 *     virtual void modifyGrContextOptions(GrContextOptions*) const  {}
 *     virtual void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions*) const {}
 *     virtual bool veto(SinkFlags) const { return false; }
 *
 *     virtual int pageCount() const { return 1; }
 *     [[nodiscard]] virtual Result draw([[maybe_unused]] int page,
 *                                       SkCanvas* canvas,
 *                                       GraphiteTestContext* graphiteTestContext) const {
 *         return this->draw(canvas, graphiteTestContext);
 *     }
 *     virtual SkISize size([[maybe_unused]] int page) const { return this->size(); }
 *     // Force Tasks using this Src to run on the main thread?
 *     virtual bool serial() const { return false; }
 * }
 * ```
 */
public abstract class Src {
  /**
   * C++ original:
   * ```cpp
   * virtual Result draw(SkCanvas* canvas, GraphiteTestContext*) const = 0
   * ```
   */
  public abstract fun draw(canvas: SkCanvas?, param1: GraphiteTestContext?): Result

  /**
   * C++ original:
   * ```cpp
   * virtual SkISize size() const = 0
   * ```
   */
  public abstract fun size(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual Name name() const = 0
   * ```
   */
  public abstract fun name(): Name

  /**
   * C++ original:
   * ```cpp
   * virtual void modifySurfaceProps(SkSurfaceProps*) const {}
   * ```
   */
  public open fun modifySurfaceProps(param0: SkSurfaceProps?) {
    TODO("Implement modifySurfaceProps")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void modifyGrContextOptions(GrContextOptions*) const  {}
   * ```
   */
  public open fun modifyGrContextOptions(param0: GrContextOptions?) {
    TODO("Implement modifyGrContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions*) const {}
   * ```
   */
  public open fun modifyGraphiteContextOptions(param0: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool veto(SinkFlags) const { return false; }
   * ```
   */
  public open fun veto(param0: SinkFlags): Boolean {
    TODO("Implement veto")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual int pageCount() const { return 1; }
   * ```
   */
  public open fun pageCount(): Int {
    TODO("Implement pageCount")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual Result draw([[maybe_unused]] int page,
   *                                       SkCanvas* canvas,
   *                                       GraphiteTestContext* graphiteTestContext) const {
   *         return this->draw(canvas, graphiteTestContext);
   *     }
   * ```
   */
  public open fun draw(
    page: Int,
    canvas: SkCanvas?,
    graphiteTestContext: GraphiteTestContext?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkISize size([[maybe_unused]] int page) const { return this->size(); }
   * ```
   */
  public open fun size(page: Int): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool serial() const { return false; }
   * ```
   */
  public open fun serial(): Boolean {
    TODO("Implement serial")
  }
}

public typealias SVGSrcINHERITED = Src
