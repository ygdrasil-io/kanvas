# Bounded two-stop linear-gradient evidence

The prepared-material WGSL route accepts only SRGB, two-stop, clamp linear
gradients with finite bounded affine local matrices. Colors are packed as
linear-premultiplied RGBA and the generated WGSL interpolates those packed
values directly.

`GPUPreparedMaterialProgramTest` supplies the CPU reference sample, verifies
the 576-byte v2 ABI and parser reflection, and verifies the generated WGSL
does not apply a second transfer conversion. `GPUMaterialMapperTest` proves
the accepted route and the closed refusal diagnostics.

No GM was regenerated. `linear_gradient` uses six stops; `fillrect_gradient`
mixes one-, two-, three-, four-, five-, and six-stop linear gradients with
radial gradients; `gradient_matrix` mixes supported two-stop linear gradients
with radial gradients. Each targeted `SkiaGmRunner` invocation terminates at
the existing product surface with `unsupported.material.source_unimplemented`.
This task does not reinterpret that generic product-surface terminal as a
successful native render and does not lower a threshold.

The route, diagnostics, no-diff ruling, and ABI statistics are recorded in the
adjacent JSON artifacts.
