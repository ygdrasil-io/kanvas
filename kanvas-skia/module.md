# Module kanvas-skia

Foundation layer of the `kanvas-skia` port — abstract device/canvas/paint/path types ported from Skia's C++ core. The module keeps a clean architectural boundary : `:kanvas-skia/src/main` has no dependency on a concrete rasterizer (CPU or GPU). The CPU backend lives in `:cpu-raster`, the WebGPU backend in `:gpu-raster`.

This is the "core / abstractions" module : consumers depend on it for type signatures, then layer one (or both) of the concrete raster modules on top.

The namespace mirrors Skia upstream : `org.skia.core`, `org.skia.foundation`, `org.skia.canvas`, etc.

# Package org.skia.core

Canvas, device, and core drawing primitives.

# Package org.skia.foundation

`SkBitmap`, `SkPaint`, `SkPath`, `SkPathBuilder`, `SkColorSpace`, and other foundation types referenced by every drawing API.

# Package org.skia.canvas

Canvas-level helpers and surface APIs.
