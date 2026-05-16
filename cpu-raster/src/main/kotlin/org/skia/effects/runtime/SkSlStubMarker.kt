package org.skia.effects.runtime

/**
 * R-final.S **STUB.SKSL** marker — documents the gap between
 * upstream Skia's full SkSL parser/IR/codegen pipeline (~30k LOC under
 * `src/sksl/`) and the dispatch-based runtime-effect surface that
 * `:kanvas-skia` ships.
 *
 * **What ships today** : [SkRuntimeEffect.MakeForShader] /
 * [SkRuntimeEffect.MakeForColorFilter] / [SkRuntimeEffect.MakeForBlender]
 * parse the *signature* of the SkSL source (top-level `uniform` /
 * `child` declarations + `main(...)` entry-point arity), then look
 * up a hand-ported [SkRuntimeImpl] in [SkRuntimeEffectDispatch]
 * keyed by canonical SkSL hash. Effects that ship with `:kanvas-skia`
 * (gradients, color cubes, intrinsics, image-filter shaders — see
 * [SkRuntimeEffectDispatch] callers) work end-to-end.
 *
 * **What does not ship** : compiling *arbitrary* SkSL source (e.g.
 * `gm/rippleshader.cpp`'s custom shader). For unknown SkSL the
 * factories return
 * `Result(null, "SkSL not registered: <hash>. Add an entry to SkRuntimeEffectDispatch.")` —
 * the failure path is visible (the DM driver logs the missing hash
 * and skips the GM gracefully).
 *
 * **Why no full parser port** : porting the Skia SkSL parser /
 * type-checker / SPIR-V/Metal/SkVM emitter is several tens of
 * thousands of lines of intricate compiler code with significant
 * test surface — out of scope for the pure-JVM `:kanvas-skia`
 * module. Consumers that need *generic* SkSL should bind Skija /
 * Skia native via JNI and route the `MakeForShader` calls through
 * the native parser. See
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * §STUB.SKSL.
 *
 * This file is intentionally **only documentation** — no symbols.
 * Use it as the canonical anchor when you need to point at the
 * SkSL gap from elsewhere in the tree.
 */
@Suppress("unused")
internal const val SKSL_STUB_MARKER: String = "STUB.SKSL: see API_FINALIZATION_PLAN.md"
