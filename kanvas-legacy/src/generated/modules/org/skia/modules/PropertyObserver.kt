package org.skia.modules

import LazyHandle
import kotlin.CharArray
import org.skia.foundation.SkRefCnt
import undefined.ColorPropertyHandle

/**
 * C++ original:
 * ```cpp
 * class SK_API PropertyObserver : public SkRefCnt {
 * public:
 *     enum class NodeType {COMPOSITION, LAYER, EFFECT, OTHER};
 *
 *     template <typename T>
 *     using LazyHandle = std::function<std::unique_ptr<T>()>;
 *
 *     virtual void onColorProperty    (const char node_name[],
 *                                      const LazyHandle<ColorPropertyHandle>&);
 *     virtual void onOpacityProperty  (const char node_name[],
 *                                      const LazyHandle<OpacityPropertyHandle>&);
 *     virtual void onTextProperty     (const char node_name[],
 *                                      const LazyHandle<TextPropertyHandle>&);
 *     virtual void onTransformProperty(const char node_name[],
 *                                      const LazyHandle<TransformPropertyHandle>&);
 *     virtual void onEnterNode(const char node_name[], NodeType node_type);
 *     virtual void onLeavingNode(const char node_name[], NodeType node_type);
 * }
 * ```
 */
public open class PropertyObserver : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * void PropertyObserver::onColorProperty(const char[],
   *                                        const LazyHandle<ColorPropertyHandle>&) {}
   * ```
   */
  public open fun onColorProperty(nodeName: CharArray, param1: LazyHandle<ColorPropertyHandle>) {
    TODO("Implement onColorProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void PropertyObserver::onOpacityProperty(const char[],
   *                                          const LazyHandle<OpacityPropertyHandle>&) {}
   * ```
   */
  public open fun onOpacityProperty(nodeName: CharArray, param1: LazyHandle<OpacityPropertyHandle>) {
    TODO("Implement onOpacityProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void PropertyObserver::onTextProperty(const char[],
   *                                       const LazyHandle<TextPropertyHandle>&) {}
   * ```
   */
  public open fun onTextProperty(nodeName: CharArray, param1: LazyHandle<TextPropertyHandle>) {
    TODO("Implement onTextProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void PropertyObserver::onTransformProperty(const char[],
   *                                            const LazyHandle<TransformPropertyHandle>&) {}
   * ```
   */
  public open fun onTransformProperty(nodeName: CharArray, param1: LazyHandle<TransformPropertyHandle>) {
    TODO("Implement onTransformProperty")
  }

  /**
   * C++ original:
   * ```cpp
   * void PropertyObserver::onEnterNode(const char node_name[], NodeType) {}
   * ```
   */
  public open fun onEnterNode(nodeName: CharArray, nodeType: NodeType) {
    TODO("Implement onEnterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * void PropertyObserver::onLeavingNode(const char node_name[], NodeType) {}
   * ```
   */
  public open fun onLeavingNode(nodeName: CharArray, nodeType: NodeType) {
    TODO("Implement onLeavingNode")
  }

  public enum class NodeType {
    COMPOSITION,
    LAYER,
    EFFECT,
    OTHER,
  }
}
