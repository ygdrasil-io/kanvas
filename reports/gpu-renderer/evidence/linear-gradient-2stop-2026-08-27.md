# Bounded two-stop linear-gradient evidence

The prepared-material WGSL route accepts only SRGB, two-stop, clamp linear
gradients with finite bounded affine local matrices. Colors are packed as
linear-premultiplied RGBA and the generated WGSL interpolates those packed
values directly.

`GPUPreparedMaterialProgramTest` supplies the CPU reference sample, including
the sRGB-to-linear conversion, premultiplication, and the `lenSq < 1e-12`
degenerate-axis branch used by WGSL. It verifies the 576-byte v2 ABI and
parser reflection, and verifies the generated WGSL does not apply a second
transfer conversion. `GPUWgpu4kPreparedVerticesNativeSmokeTest` then renders
the non-trivial two-stop fixture through `GPUPreparedMaterialProgram` on the
headless wgpu4k path: one submit/readback, 36 compared channels, and exact
zero diff. This does not use the 592-byte CorePrimitive gradient ABI.
`GPUMaterialMapperTest` proves the accepted route and the closed refusal
diagnostics.

No GM was regenerated. `linear_gradient` uses six stops; `fillrect_gradient`
mixes one-, two-, three-, four-, five-, and six-stop linear gradients with
radial gradients; `gradient_matrix` mixes supported two-stop linear gradients
with radial gradients. Surface evidence records the actual terminal code and
operation count for every target: `linear_gradient` has 101 operations and
refuses as `unsupported.material.mapping.linear_gradient_stop_count`, while
`fillrect_gradient` (19) and `gradient_matrix` (18) refuse as
`unsupported.material.source_unimplemented`. This task does not reinterpret a
terminal refusal as a successful GM render and does not lower a threshold.

The route, diagnostics, no-diff ruling, and ABI statistics are recorded in the
adjacent JSON artifacts.
