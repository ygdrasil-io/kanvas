# OpenGL / GLES Backend

This document covers Skia's OpenGL / OpenGL-ES / WebGL backend, which is
exposed only through Ganesh — the Graphite renderer
([Graphite Backend](graphite-backend.md)) was designed around modern
explicit-API graphics (Vulkan, Metal, Dawn) and deliberately does **not**
target GL. So everything here lives under
`skia-main/src/gpu/ganesh/gl/` and `skia-main/include/gpu/ganesh/gl/`.

For the Ganesh architecture and how a backend plugs into the
`GrGpu` / `GrCaps` / `GrOpsRenderPass` abstraction, see
[Ganesh Backend](ganesh-backend.md). For where the GLSL that Skia emits
comes from, see [SkSL Shading Language](sksl-shading-language.md), and
the parallel intro at [GPU Overview](gpu-overview.md).

## Map

| Area | Location |
|------|----------|
| Public GL types & interface | `skia-main/include/gpu/ganesh/gl/` |
| `GrGLInterface` (function pointers) | `include/gpu/ganesh/gl/GrGLInterface.h` |
| Direct-context entry point | `include/gpu/ganesh/gl/GrGLDirectContext.h` |
| Backend-surface accessors | `include/gpu/ganesh/gl/GrGLBackendSurface.h` |
| Per-platform `MakeNativeInterface` headers | `include/gpu/ganesh/gl/{egl,glx,win,mac,ios,epoxy}/` |
| Core Ganesh GL implementation | `skia-main/src/gpu/ganesh/gl/` |
| GL `GrGpu` subclass | `src/gpu/ganesh/gl/GrGLGpu.{h,cpp}` |
| Capabilities / extension probing | `src/gpu/ganesh/gl/GrGLCaps.{h,cpp}`, `GrGLExtensions.{h,cpp}` |
| Interface assemblers (autogen) | `src/gpu/ganesh/gl/GrGLAssemble{GL,GLES,WebGL}InterfaceAutogen.cpp` |
| Program / shader cache | `src/gpu/ganesh/gl/GrGLProgram*.{h,cpp}`, `GrGLGpuProgramCache.cpp` |
| Render pass / FBO | `src/gpu/ganesh/gl/GrGLOpsRenderPass.{h,cpp}`, `GrGLRenderTarget.{h,cpp}` |
| GLSL shader builders | `src/gpu/ganesh/glsl/GrGLSL*` |
| Test contexts (offscreen) | `tools/gpu/gl/` |

## `GrGLInterface` — the function-pointer table

Skia does not link against `libGL` directly. Instead a client supplies a
`GrGLInterface`, a struct of `GrGLFuncPtr`s for every GL entry point
Skia might call (`GrGLInterface.h`). Each interface declares:

- `fStandard` — `kGL_GrGLStandard` / `kGLES_GrGLStandard` / `kWebGL_GrGLStandard`
- `fExtensions` — a `GrGLExtensions` set produced by querying `GL_EXTENSIONS`
- `fFunctions` — every GL entry point as a typed function pointer

`validate()` checks that all the function pointers required by the
declared GL version + extensions are non-null; otherwise context creation
fails. Convenience builders — `GrGLMakeNativeInterface()` and the
per-API `GrGLMakeAssembledInterface(...)` — populate the table by calling
back into a client-supplied `GrGLGetProc` resolver. The "Assembled"
variants live in `GrGLAssembleGLInterfaceAutogen.cpp`,
`GrGLAssembleGLESInterfaceAutogen.cpp`, and
`GrGLAssembleWebGLInterfaceAutogen.cpp` — they are mechanically generated
from a single source of truth and select which functions to bind based on
the standard, version, and extension list of the live context.

### Per-platform native interfaces

Under `include/gpu/ganesh/gl/{egl,glx,win,mac,ios,epoxy}/` Skia ships
small helpers that resolve `GrGLFuncPtr`s using the right platform loader:

- `egl/GrGLMakeEGLInterface.h` — `eglGetProcAddress` (Android, ChromeOS, embedded)
- `glx/GrGLMakeGLXInterface.h` — `glXGetProcAddressARB` (X11/Linux desktop)
- `win/GrGLMakeWinInterface.h` — `wglGetProcAddress` + `GetProcAddress` on `opengl32.dll`
- `mac/GrGLMakeMacInterface.h` — `dlsym` of the OpenGL framework
- `ios/GrGLMakeIOSInterface.h` — `dlsym` of OpenGLES.framework
- `epoxy/` — uses `libepoxy` to resolve

If none is appropriate, a client provides its own `GrGLGetProc` lambda
and calls the assembled-interface builder directly. The
`GrGLMakeNativeInterface_none.cpp` translation unit makes
`GrGLMakeNativeInterface()` return `nullptr` for builds that supply
their own.

## `GrGLDirectContext` — creating the context

`include/gpu/ganesh/gl/GrGLDirectContext.h` is the GL entry point that
mirrors `GrDirectContext`. The typical sequence is:

1. Make the platform's GL context current.
2. Build a `sk_sp<const GrGLInterface>` (native or assembled).
3. `GrDirectContexts::MakeGL(interface, options)` — returns a
   `sk_sp<GrDirectContext>` that internally owns a `GrGLGpu`.

From there the rest of the Ganesh API
([Ganesh Backend](ganesh-backend.md)) is identical to other backends:
make a `SkSurface` from a render target, draw to its `SkCanvas`, call
`flushAndSubmit()`. The GL context must remain current on Skia's calling
thread for every Ganesh entry point.

## `GrGLGpu` — the work horse

`src/gpu/ganesh/gl/GrGLGpu.{h,cpp}` is the GL implementation of `GrGpu`.
It holds the `GrGLContext` (interface + caps + GLSL generation
parameters), a tracked GL state cache (so redundant `glBindBuffer`,
`glBindTexture`, `glScissor` calls are filtered out), the program cache
(`GrGLGpuProgramCache.cpp`), a finish-callback queue
(`GrGLFinishCallbacks.{h,cpp}`), and per-resource subclasses:

- `GrGLBuffer` — VBO / IBO / UBO wrapper
- `GrGLTexture`, `GrGLTextureRenderTarget`, `GrGLRenderTarget`
- `GrGLAttachment` — depth/stencil renderbuffer
- `GrGLOpsRenderPass` — issues draw calls inside an FBO
- `GrGLSemaphore` — ARB_sync fence wrapper used for cross-context sync

Draw submission goes through `GrGLOpsRenderPass`, which binds the FBO,
sets viewport/scissor, and dispatches `glDrawArrays` /
`glDrawElements` / their instanced and indirect variants.

## Capabilities and quirks — `GrGLCaps`

`src/gpu/ganesh/gl/GrGLCaps.cpp` is one of the largest files in the
backend: it walks the version, extension, vendor and renderer strings
and derives every feature flag and workaround Ganesh consults later
(MSAA limits, supported texture formats and swizzles, presence of
`ARB_invalidate_subdata`, instanced rendering, framebuffer fetch, ES vs
desktop sized internal formats, integer textures, half-float color
attachments, etc.). Driver bug workarounds are encoded as boolean fields
on `GrGLCaps` and consulted at draw time. New devices typically need a
patch here rather than in `GrGLGpu`.

## GLSL emission

Shaders are generated from `GrFragmentProcessor` trees and the
[SkSL](sksl-shading-language.md) stages of the pipeline. The translation
to GLSL flavours that the GL driver expects (`#version 100 es`,
`#version 300 es`, `#version 410`, WebGL2) is done by
`SkSLToGLSL` plus the helpers in `src/gpu/ganesh/glsl/`:

- `GrGLSLProgramBuilder` — orchestrates building vertex + fragment + GP
  + FP shaders for one program
- `GrGLSLShaderBuilder` / `GrGLSLFragmentShaderBuilder` /
  `GrGLSLVertexGeoBuilder` — string-builder helpers
- `GrGLSLUniformHandler`, `GrGLSLProgramDataManager` — register and bind
  uniforms
- `GrGLSLBlend`, `GrGLSLColorSpaceXformHelper` — reusable snippets

The compiled program (a `GrGLProgram`) is cached keyed on a stage-key
hash, so subsequent draws with the same processor topology hit the cache.

## Backend-surface interop

`include/gpu/ganesh/gl/GrGLBackendSurface.h` and
`GrGLTypes.h` give clients ways to wrap an existing GL texture or FBO as
a `GrBackendTexture` / `GrBackendRenderTarget`, then promote it to an
`SkSurface` via the corresponding factory in
[Surface & Output](surface-and-output.md). `GrGLTextureInfo` carries
`{target, id, format}`. On Android, `AHardwareBufferGL.cpp` adapts
`AHardwareBuffer` to `EGLImage` and then to a GL texture so Skia can
share buffers with the platform compositor — see
[Android Integration](android-integration.md).

## Test contexts

The offscreen test contexts in `tools/gpu/gl/` (per-platform
subdirectories: `command_buffer/`, `egl/`, `glx/`, `iOS/`, `mac/`,
`win/`, plus a `null/`) build small "windowless" GL contexts used by
GMs, unit tests, and `nanobench` to exercise the backend without a real
window system. They produce a `GrGLInterface` against the chosen
platform GL and hand it to `GrDirectContexts::MakeGL`.

## Where to look next

- [Ganesh Backend](ganesh-backend.md) — overall Ganesh architecture
- [GPU Overview](gpu-overview.md) — how Ganesh and Graphite compare
- [SkSL Shading Language](sksl-shading-language.md) — the source SkSL
  that gets translated into the GLSL flavour this backend emits
- [Surface & Output](surface-and-output.md) — wrapping a GL FBO as an
  `SkSurface`
- [Android Integration](android-integration.md) — `AHardwareBuffer` ↔
  GL texture interop
