package org.skia.tests

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct PrecompileSettings {
 *     PrecompileSettings(const skgpu::graphite::PaintOptions& paintOptions,
 *                        skgpu::graphite::DrawTypeFlags drawTypeFlags,
 *                        const skgpu::graphite::RenderPassProperties& renderPassProps,
 *                        bool analyticClipping = false)
 *            : fPaintOptions(paintOptions)
 *            , fDrawTypeFlags(drawTypeFlags)
 *            , fRenderPassProps({ &renderPassProps, 1 })
 *            , fAnalyticClipping(analyticClipping) {}
 *
 *     PrecompileSettings(const skgpu::graphite::PaintOptions& paintOptions,
 *                        skgpu::graphite::DrawTypeFlags drawTypeFlags,
 *                        SkSpan<const skgpu::graphite::RenderPassProperties> renderPassProps,
 *                        bool analyticClipping = false)
 *             : fPaintOptions(paintOptions)
 *             , fDrawTypeFlags(drawTypeFlags)
 *             , fRenderPassProps(renderPassProps)
 *             , fAnalyticClipping(analyticClipping) {}
 *
 *     skgpu::graphite::PaintOptions fPaintOptions;
 *     skgpu::graphite::DrawTypeFlags fDrawTypeFlags = skgpu::graphite::DrawTypeFlags::kNone;
 *     SkSpan<const skgpu::graphite::RenderPassProperties> fRenderPassProps;
 *     bool fAnalyticClipping = false;
 * }
 * ```
 */
public data class PrecompileSettings public constructor(
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::PaintOptions fPaintOptions
   * ```
   */
  public var fPaintOptions: Int,
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::DrawTypeFlags fDrawTypeFlags
   * ```
   */
  public var fDrawTypeFlags: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const skgpu::graphite::RenderPassProperties> fRenderPassProps
   * ```
   */
  public var fRenderPassProps: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fAnalyticClipping = false
   * ```
   */
  public var fAnalyticClipping: Boolean,
)
