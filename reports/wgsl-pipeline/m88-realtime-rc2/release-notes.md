# M88 RC2 Release Notes

RC2 packages the current realtime renderer evidence for PM and engineering review.

Included:

- generated dashboard support/refusal evidence;
- M84 native timing candidate evidence;
- M85 resource/cache selected ledger evidence;
- M86 fidelity burn-down queue;
- M87 selected runtime-effect live editing;
- RC2 API surface, gate freeze, support/refusal matrix, and PM demo script.

Shader target:

- WGSL is the implementation target for WebGPU.
- SkSL is compatibility wording for Skia APIs such as `SkRuntimeEffect`, not a
  dynamic compiler target.
- Supported runtime effects require registered Kanvas descriptors, Kotlin CPU
  behavior, and parser-validated WGSL GPU modules.

Not included:

- full Skia parity;
- arbitrary Skia/SkSL runtime shader input;
- dynamic SkSL compilation;
- release-grade windowed FPS gate;
- broad display-list replay;
- broad observed WebGPU runtime cache telemetry.

CI/package note:

- `pipelinePmBundle` and checked-in RC validation are headless and must not
  require unpublished Kadre dependency resolution.
- Native Kadre demos are opt-in local evidence and require
  `git submodule update --init --recursive external/poc-koreos` when the
  submodule is not initialized.
