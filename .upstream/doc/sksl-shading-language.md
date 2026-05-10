# SkSL Shading Language

SkSL ("Skia Shading Language") is the GLSL-like, statically typed shader
language that Skia uses internally to express every programmable stage
of its rendering pipelines. Source lives in
`include/sksl/`, `src/sksl/`, and `src/sksl/codegen/` — a complete
front-end (lexer, parser, IR, analysis, transforms) plus several
back-ends that lower the same IR to GLSL, GLSL ES, SPIR-V, Metal SL,
WGSL, HLSL, or to Skia's own raster-pipeline byte stages for the CPU.
SkSL is the only shader source the higher Skia layers ever produce —
Ganesh, Graphite, every `SkRuntimeEffect`, the geometry tessellators,
and the GPU mesh path all hand SkSL to the same `SkSL::Compiler`, which
emits whatever shader text the active GPU backend wants.

If you have ever written a `SkRuntimeEffect`, the source string handed
to `SkRuntimeEffect::MakeForShader()` is SkSL. Internally that exact
same compiler is also the one that compiles Skia's hundreds of
fragment-program permutations for Vulkan, Metal, OpenGL, Dawn, WebGPU,
and the raster CPU back-end.

For the public, end-user API on top of this compiler see
[Runtime Effects](runtime-effects.md). For the GPU back-ends that
consume the generated shader text see [Ganesh Backend](ganesh-backend.md)
and [Graphite Backend](graphite-backend.md). The CPU raster-pipeline
target is covered in [CPU Rendering Pipeline](cpu-rendering-pipeline.md).
The `skslc` and `sksl-minify` command-line tools are part of
[Developer Tools](developer-tools.md).

---

## Pipeline at a glance

```
                                                 ┌──── GLSL / GLSL ES (OpenGL / WebGL)
                                                 │
  ┌──────────┐  ┌──────┐  ┌──────┐  ┌────────┐  │── SPIR-V (Vulkan, optionally → HLSL via SPIRV-Cross)
  │  .sksl   │─►│ Lex  │─►│Parse │─►│  IR    │──┤── Metal SL (iOS / macOS)
  │  source  │  │      │  │      │  │ (AST)  │  │── WGSL (Dawn / WebGPU)
  └──────────┘  └──────┘  └──────┘  └───┬────┘  │── HLSL (Direct3D)
                                        │       │── Pipeline-stage GLSL/Metal (per-effect snippet
                                        │       │      embedded into a larger GPU program)
                                        ▼       │── Raster Pipeline byte stages (CPU)
                                  ┌──────────┐  │── PipelineStage (snippet for Skia GPU programs)
                                  │ Analysis │  │
                                  └─────┬────┘  │
                                        ▼       │
                                  ┌──────────┐  │
                                  │Transform │──┘
                                  └──────────┘
                                  (constant fold, inline,
                                   dead-code elim,
                                   private-symbol rename, …)
```

Every stage operates on the same IR; back-ends are interchangeable. The
front end is shared with the bundled built-in modules
(`sksl_gpu.sksl`, `sksl_frag.sksl`, `sksl_vert.sksl`,
`sksl_compute.sksl`, `sksl_graphite_*`, `sksl_rt_shader.sksl`, …) which
are pre-parsed at startup and merged into the symbol table of the
program being compiled.

---

## `ProgramKind` — what is being compiled

`src/sksl/SkSLProgramKind.h` enumerates every flavour of program SkSL
recognises. The kind selects which built-in module is loaded, which
intrinsics are visible, and which back-ends are legal:

| `ProgramKind`                  | Purpose                                                                |
| ------------------------------ | ---------------------------------------------------------------------- |
| `kVertex`                      | Ganesh vertex shader                                                   |
| `kFragment`                    | Ganesh fragment shader                                                 |
| `kCompute`                     | Compute shader (Graphite / atlas builders)                             |
| `kGraphiteVertex`              | Graphite vertex shader                                                 |
| `kGraphiteFragment`            | Graphite fragment shader                                               |
| `kRuntimeShader`               | `SkRuntimeEffect::MakeForShader` — produces an `SkShader`              |
| `kRuntimeColorFilter`          | `SkRuntimeEffect::MakeForColorFilter` — produces an `SkColorFilter`    |
| `kRuntimeBlender`              | `SkRuntimeEffect::MakeForBlender` — produces an `SkBlender`            |
| `kPrivateRuntimeShader`        | Like `kRuntimeShader` with public-API restrictions lifted (Skia-only)  |
| `kPrivateRuntimeColorFilter`   | Same, for color filters                                                |
| `kPrivateRuntimeBlender`       | Same, for blenders                                                     |
| `kMeshVertex`                  | Vertex stage of an `SkMesh` custom mesh                                |
| `kMeshFragment`                | Fragment stage of an `SkMesh` custom mesh                              |

The "private" runtime kinds expose Skia-internal extensions (e.g.
arbitrary sampler bindings, narrower-than-public type allowances) and
are never reachable from public headers.

`SkSL::Version` (`include/sksl/SkSLVersion.h`) is an orthogonal axis
that picks between `k100` (GLSL 1.10 / GLSL ES 1.00 / WebGL 1) and
`k300` (GLSL 3.30 / GLSL ES 3.00 / WebGL 2). It governs which
intrinsics, types and control-flow constructs the front end will accept.

---

## The compiler entry point — `SkSL::Compiler`

The class lives in `src/sksl/SkSLCompiler.h`. A single `Compiler`
instance is reused per thread (it owns the parsed built-in modules and
a memory pool):

- `convertProgram(ProgramKind, std::string text, ProgramSettings)`
  parses `text`, builds the IR, runs all analysis and transformation
  passes, and returns a `std::unique_ptr<SkSL::Program>`. Errors are
  routed through an `ErrorReporter` (`src/sksl/SkSLErrorReporter.h`)
  and accumulated on the compiler.
- `errorCount()`, `errorText()`, `resetErrors()` — diagnostic surface.
- `moduleForProgramKind(ProgramKind)` — fetches the pre-parsed built-in
  module that supplies built-in symbols and intrinsics for that kind.
- A handful of well-known builtin slot numbers are exposed as
  constants: `SK_FRAGCOLOR_BUILTIN`, `SK_FRAGCOORD_BUILTIN`,
  `SK_POSITION_BUILTIN`, `SK_VERTEXID_BUILTIN`,
  `SK_GLOBALINVOCATIONID_BUILTIN`, …
- `GetRTAdjustVector(SkISize, bool flipY)` computes the float4 needed
  to map Skia device coords to the GPU's normalized device coords; it
  is used to populate `sk_RTAdjust` when a vertex shader needs to flip
  Y or rescale.

`ProgramSettings` (`src/sksl/SkSLProgramSettings.h`) is the knob bag
passed alongside the source — it controls inlining, optimization,
debug-trace insertion, ES2-restriction enforcement, RT-flip handling,
and a long tail of backend-specific options.

The output `SkSL::Program` is a self-contained IR tree plus a symbol
table. To get shader text out of it you hand the program to a
back-end-specific helper (see "Code generation" below).

---

## IR — the intermediate representation

`src/sksl/ir/` (~80 small files) defines every node of the SkSL IR.
Two top-level categories:

- **Expression nodes** — `BinaryExpression`, `Constructor*` (compound,
  matrix, splat, scalar-cast, …), `ChildCall`, `FunctionCall`,
  `IndexExpression`, `Swizzle`, `TernaryExpression`, `Literal`,
  `VariableReference`, `FieldAccess`, …
- **Statement nodes** — `Block`, `IfStatement`, `ForStatement`,
  `DoStatement`, `SwitchStatement`, `ReturnStatement`,
  `BreakStatement`, `ContinueStatement`, `DiscardStatement`,
  `ExpressionStatement`, plus `VarDeclaration`.

Top-level `ProgramElement`s are `FunctionDefinition`,
`FunctionPrototype`, `GlobalVarDeclaration`, `InterfaceBlock`,
`StructDefinition`, `Extension`, and `ModifiersDeclaration`. All
nodes carry a `SkSL::Position` so diagnostics can point back into the
source. Memory is bump-allocated through `Pool` /
`SkSLMemoryPool.h`, freed wholesale when the `Program` is destroyed.

The lexer (`SkSLLexer.{h,cpp}`, `lex/`) and parser
(`SkSLParser.{h,cpp}`) are hand-written. The parser produces IR
directly — there is no separate AST stage — and folds constants on the
fly via `SkSLConstantFolder`.

---

## Analysis passes — `src/sksl/analysis/`

Each file under `analysis/` answers one structural question about a
program; they are pure inspection passes (no rewriting) used both by
the optimizer and by error checking:

- `SkSLCanExitWithoutReturningValue` — control-flow check for missing
  `return` in non-`void` functions
- `SkSLCheckProgramStructure` — recursion / nesting / size limits
- `SkSLCheckSymbolTableCorrectness` — sanity-check the symbol stack
- `SkSLFinalizationChecks` — last-mile validation before codegen
- `SkSLGetLoopControlFlowInfo`, `SkSLGetLoopUnrollInfo` — analyses for
  ES2-style "must be unrollable" loops
- `SkSLGetReturnComplexity` — how many return paths a function has
- `SkSLHasSideEffects`, `SkSLIsConstantExpression`,
  `SkSLIsTrivialExpression`, `SkSLIsSameExpressionTree`,
  `SkSLIsDynamicallyUniformExpression` — predicates over expressions
- `SkSLReturnsInputAlpha` — used by paint optimizers to detect
  alpha-preserving filters
- `SkSLProgramUsage` — reference counting of every symbol reachable
  from the program root; used by dead-code elimination
- `SkSLSpecialization` — identifies constant-argument call sites that
  can be specialized
- `SkSLProgramVisitor.h` — generic visitor base used by the rest of the
  passes

---

## Transforms — `src/sksl/transform/`

Transforms mutate the IR in place. The driver lives in
`SkSLTransform.{h,cpp}`; individual passes include:

- `SkSLEliminateDeadFunctions`,
  `SkSLEliminateDeadGlobalVariables`,
  `SkSLEliminateDeadLocalVariables`,
  `SkSLEliminateUnreachableCode`,
  `SkSLEliminateEmptyStatements`,
  `SkSLEliminateUnnecessaryBraces` — DCE and shape cleanup
- `SkSLFindAndDeclareBuiltinFunctions`,
  `SkSLFindAndDeclareBuiltinStructs`,
  `SkSLFindAndDeclareBuiltinVariables` — pull only the built-ins
  actually referenced into the final program
- `SkSLAddConstToVarModifiers` — promote variables that turn out to
  be constant
- `SkSLReplaceConstVarsWithLiterals` — propagate literal values
- `SkSLReplaceSplatCastsWithSwizzles`,
  `SkSLRewriteIndexedSwizzle` — backend-friendly rewrites
- `SkSLHoistSwitchVarDeclarationsAtTopLevel` — work around backend
  restrictions on `switch` scoping
- `SkSLRenamePrivateSymbols` — mangle private identifiers so they will
  not collide once embedded in a larger GPU program

`SkSLInliner.{h,cpp}` is the function inliner; it runs alongside the
transforms. `SkSLConstantFolder.{h,cpp}` is invoked both during parsing
and from transforms for constant propagation.

---

## Code generation — `src/sksl/codegen/`

Every back-end is a small file that walks an `SkSL::Program` and emits
its target representation. They share `SkSLCodeGenerator.h` /
`SkSLCodeGenTypes.h`.

| Backend                            | Header                                | Output                                    | Used by                                          |
| ---------------------------------- | ------------------------------------- | ----------------------------------------- | ------------------------------------------------ |
| GLSL / GLSL ES                     | `SkSLGLSLCodeGenerator.h`             | text                                      | OpenGL / WebGL via Ganesh                        |
| SPIR-V                             | `SkSLSPIRVCodeGenerator.h`            | `std::vector<uint32_t>` binary            | Vulkan; also Dawn (WebGPU) when targeting SPV    |
| SPIR-V → HLSL                      | `SkSLSPIRVtoHLSL.h`                   | HLSL (via SPIRV-Cross)                    | Direct3D legacy path                             |
| HLSL                               | `SkSLHLSLCodeGenerator.h`             | text                                      | Direct3D backend                                 |
| Metal SL                           | `SkSLMetalCodeGenerator.h`            | text                                      | Metal backend                                    |
| WGSL                               | `SkSLWGSLCodeGenerator.h`             | text                                      | Dawn / WebGPU backend                            |
| Pipeline-stage snippet             | `SkSLPipelineStageCodeGenerator.h`    | GLSL/Metal/WGSL fragment                  | Embeds an effect into a larger GPU program       |
| Raster Pipeline                    | `SkSLRasterPipelineCodeGenerator.h` + `SkSLRasterPipelineBuilder.h` | byte stages | CPU runtime effects, see [CPU Rendering Pipeline](cpu-rendering-pipeline.md) |

Two validators live next to the generators:
`SkSLSPIRVValidator.{h,cpp}` (wraps `spirv-tools` when available) and
`SkSLWGSLValidator.{h,cpp}` (wraps Tint). `SkSLNativeShader.h` is the
small struct (`std::string` + `std::vector<uint32_t>`) that callers
hand around when they don't yet know whether they want text or
SPIR-V binary out.

The pipeline-stage generator is what makes Skia's effect composition
work: a single user `SkRuntimeEffect` is emitted as a *snippet* — its
entry point is renamed, its private symbols are mangled (see
`SkSLMangler.{h,cpp}`), and its sample-children calls are rewritten so
the parent shader graph can splice it in.

---

## Built-in modules — the bundled `.sksl` files

The compiler is bootstrapped with a stack of built-in modules that
declare every intrinsic and every Skia-specific builtin variable. They
ship as plain `.sksl` text under `src/sksl/`:

| File                       | Provides                                                          |
| -------------------------- | ----------------------------------------------------------------- |
| `sksl_shared.sksl`         | Types and intrinsics common to every program kind                 |
| `sksl_gpu.sksl`            | GPU-only intrinsics shared by vertex / fragment / compute / mesh  |
| `sksl_vert.sksl`           | Built-ins for Ganesh vertex programs                              |
| `sksl_frag.sksl`           | Built-ins for Ganesh fragment programs                            |
| `sksl_compute.sksl`        | Built-ins for compute programs                                    |
| `sksl_graphite_vert.sksl`  | Graphite vertex built-ins                                         |
| `sksl_graphite_frag.sksl`  | Graphite fragment built-ins                                       |
| `sksl_rt_shader.sksl`      | Runtime-effect-only built-ins (`half4 main(float2 coord)`, etc.)  |
| `sksl_public.sksl`         | The cut-down surface visible from public runtime effects          |

These modules are loaded by `SkSLModuleLoader` /
`SkSLGraphiteModules` and parsed into `SkSL::Module` objects (see
`SkSLModule.{h,cpp}`). Two embedding strategies coexist:

- `SkSLModuleDataDefault.cpp` — the modules are baked into the binary
  as C++ string literals (release / shipping builds).
- `SkSLModuleDataFile.cpp` — the modules are loaded from disk at
  startup (developer builds and `skslc`).

Pre-compiled blobs of these modules live under `src/sksl/generated/`
for faster startup.

---

## CLI tools

### `skslc`

`skslc` is the standalone compiler driver. It accepts a `.sksl` source
file plus a target — for example `glsl`, `spirv`, `metal`, `wgsl`,
`hlsl`, or `stage` (pipeline-stage snippet) — and writes the generated
shader to stdout or to a file. It is the tool of choice for inspecting
exactly what the front end produces, for regenerating the bundled
module blobs, and for round-tripping a runtime effect through every
back-end during debugging. See [Developer Tools](developer-tools.md).

### `sksl-minify`

`sksl-minify` parses an SkSL program and re-emits it as the smallest
syntactically valid SkSL it can produce — strips comments and
whitespace, renames identifiers, and folds trivially constant
expressions. Skia uses it on the source of internal runtime effects
that get embedded into the binary as string literals (notably the
ones in `src/effects/`), keeping shipped binary size down.

---

## Cross-references

- [Runtime Effects](runtime-effects.md) — the public-facing API
  (`SkRuntimeEffect`, `SkRuntimeShaderBuilder`, …) that exposes SkSL
  to applications.
- [Ganesh Backend](ganesh-backend.md) — consumer of the GLSL, SPIR-V,
  and Metal/WGSL outputs, plus the pipeline-stage snippets.
- [Graphite Backend](graphite-backend.md) — consumer of the
  `kGraphiteFragment` / `kGraphiteVertex` outputs and the compute
  generator.
- [Developer Tools](developer-tools.md) — `skslc`, `sksl-minify`, and
  the SkSL debug-trace UI.
- [CPU Rendering Pipeline](cpu-rendering-pipeline.md) — the raster-
  pipeline back-end that lets a runtime effect run on the CPU with no
  GPU at all.
