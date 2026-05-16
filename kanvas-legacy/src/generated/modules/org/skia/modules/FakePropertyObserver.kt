package org.skia.modules

import LazyHandle
import kotlin.CharArray
import kotlin.Int
import undefined.ColorPropertyHandle

/**
 * C++ original:
 * ```cpp
 * class FakePropertyObserver : public PropertyObserver {
 * public:
 *     void onOpacityProperty(const char node_name[],
 *                            const LazyHandle<OpacityPropertyHandle>& opacity_handle) override {
 *         opacity_handle_ = opacity_handle();
 *     }
 *
 *     void onTransformProperty(const char node_name[],
 *                              const LazyHandle<TransformPropertyHandle>& transform_handle) override {
 *         transform_handle_ = transform_handle();
 *     }
 *
 *     void onColorProperty(const char node_name[],
 *                          const LazyHandle<ColorPropertyHandle>& color_handle) override {
 *         color_handle_ = color_handle();
 *     }
 *
 *     void onTextProperty(const char node_name[],
 *                         const LazyHandle<TextPropertyHandle>& text_handle) override {
 *         text_handle_ = text_handle();
 *     }
 *
 *     std::unique_ptr<OpacityPropertyHandle> opacity_handle_;
 *     std::unique_ptr<TransformPropertyHandle> transform_handle_;
 *     std::unique_ptr<ColorPropertyHandle> color_handle_;
 *     std::unique_ptr<TextPropertyHandle> text_handle_;
 * }
 * ```
 */
public open class FakePropertyObserver : PropertyObserver() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<OpacityPropertyHandle> opacity_handle_
   * ```
   */
  public var opacityHandle: Int = TODO("Initialize opacityHandle")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<TransformPropertyHandle> transform_handle_
   * ```
   */
  public var transformHandle: Int = TODO("Initialize transformHandle")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ColorPropertyHandle> color_handle_
   * ```
   */
  public var colorHandle: Int = TODO("Initialize colorHandle")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<TextPropertyHandle> text_handle_
   * ```
   */
  public var textHandle: Int = TODO("Initialize textHandle")

  /**
   * C++ original:
   * ```cpp
   * void onOpacityProperty(const char node_name[],
   *                            const LazyHandle<OpacityPropertyHandle>& opacity_handle) override {
   *         opacity_handle_ = opacity_handle();
   *     }
   * ```
   */
  public override fun onOpacityProperty(nodeName: CharArray, opacityHandle: LazyHandle<OpacityPropertyHandle>) {
    TODO("Implement onOpacityProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onTransformProperty(const char node_name[],
   *                              const LazyHandle<TransformPropertyHandle>& transform_handle) override {
   *         transform_handle_ = transform_handle();
   *     }
   * ```
   */
  public override fun onTransformProperty(nodeName: CharArray, transformHandle: LazyHandle<TransformPropertyHandle>) {
    TODO("Implement onTransformProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onColorProperty(const char node_name[],
   *                          const LazyHandle<ColorPropertyHandle>& color_handle) override {
   *         color_handle_ = color_handle();
   *     }
   * ```
   */
  public override fun onColorProperty(nodeName: CharArray, colorHandle: LazyHandle<ColorPropertyHandle>) {
    TODO("Implement onColorProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void onTextProperty(const char node_name[],
   *                         const LazyHandle<TextPropertyHandle>& text_handle) override {
   *         text_handle_ = text_handle();
   *     }
   * ```
   */
  public override fun onTextProperty(nodeName: CharArray, textHandle: LazyHandle<TextPropertyHandle>) {
    TODO("Implement onTextProperty")
  }
}
