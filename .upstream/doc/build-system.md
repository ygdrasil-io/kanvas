# Build System

Skia ships **two** parallel build systems and keeps both green: GN (the
historical primary, used by Chrome and Android) and Bazel (newer,
preferred for hermetic third-party consumption and for the
[CanvasKit](canvaskit.md) / WASM matrix). Every source file you add
needs entries in both. This document is a map of where the build
metadata lives and how the two systems factor the same source tree.

| Surface | GN | Bazel |
| --- | --- | --- |
| Top entry point | `BUILD.gn` | `BUILD.bazel` |
| Toolchain bootstrap | `.gn`, `gn/BUILDCONFIG.gn`, `gn/toolchain/` | `WORKSPACE.bazel`, `MODULE.bazel`, `bazel/toolchains_*` |
| Per-module file lists | `gn/*.gni` (`core.gni`, `gpu.gni`, …) | per-directory `BUILD.bazel` |
| Configuration knobs | `gn/skia.gni` (`skia_use_*`, `skia_enable_*`) | `bazel/common_config_settings/`, `--define` flags |
| External / vendored deps | `third_party/BUILD.gn` files | `third_party/BUILD.bazel`, `bazel/external/` |
| Override seams | `build_overrides/` (Chrome / Fuchsia replacements) | `MODULE.bazel` overrides |
| Vendored helper repos | `buildtools/` (`clang_format`, `checkdeps`, `reclient_cfgs`) | mirrored under `bazel/` |
| Toolchain definitions | `toolchain/` (legacy bootstrap) | `toolchain/*.bzl` (`mac_toolchain_config.bzl`, `windows_toolchain_config.bzl`, `ios_toolchain_config.bzl`, `linux_amd64_toolchain_config.bzl`, NDK variants) |
| CI infra | `infra/` (Buildbot recipes, swarming) | `bazel/devicesrc`, `bazel/device_specific_configs` |

Both systems read the same source files; the duplication is in *file
lists and feature flags*, not in source code. A typical Skia CL adds
the new `.cpp` to one `*.gni` and one `BUILD.bazel`.

---

## GN — `gn/`, `BUILD.gn`

GN ([Generate Ninja](https://gn.googlesource.com/gn/)) is meta-build:
running `gn gen out/Release` consumes `BUILD.gn` plus arguments
(`is_debug`, `skia_use_vulkan`, …) and emits a Ninja graph that the
actual `ninja` binary executes.

The top-level `BUILD.gn` does little more than wire `*.gni` source
lists into a handful of public targets:

```gn
import("gn/codec.gni")
import("gn/pathops.gni")
import("gn/rust.gni")
import("gn/shared_sources.gni")
import("gn/skia.gni")
import("gn/toolchain/wasm.gni")
…
import("third_party/dawn/args.gni")

config("skia_public") {
  include_dirs = [ "." ]
  defines = [ "SK_CODEC_DECODES_BMP", "SK_CODEC_DECODES_WBMP" ]
  …
  if (is_component_build) defines += [ "SKIA_DLL" ]
}
```

The `gn/*.gni` files are flat lists of source files grouped by
subsystem — `core.gni`, `gpu.gni`, `graphite.gni`, `pathops.gni`,
`pdf.gni`, `ports.gni`, `sksl.gni`, `effects.gni`,
`effects_imagefilters.gni`, `gm.gni`, `tests.gni`, `bench.gni`,
`fuzz.gni`, `xml.gni`, `xps.gni`, `svg.gni`, `utils.gni`, `opts.gni`,
`rust.gni`, plus the unifying `skia.gni` (which is also where most
`skia_use_*` / `skia_enable_*` GN args are declared).

Helpers under `gn/` are Python scripts invoked by GN actions —
`compile_sksl_tests.py` (for `gn/sksl_tests.gni` codegen),
`find_msvc.py`, `find_xcode_sysroot.py`, `embed_resources.py`,
`copy_git_directory.py`. The `gn/toolchain/` subdirectory holds the
target-platform toolchain definitions (Linux, Mac, Windows, iOS,
Android NDK, WASM via Emscripten).

`build_overrides/` exists so that an embedder (Chrome, Fuchsia) can
swap out the entire Vulkan / SPIR-V / partition_alloc / ANGLE / build
configurations by pointing GN at a different file: `angle.gni`,
`build.gni`, `partition_alloc.gni`, `spirv_tools.gni`,
`vulkan_headers.gni`, `vulkan_tools.gni`.

`buildtools/` is the vendored DEPS-style dependency for Chromium-style
helpers — `clang_format`, `checkdeps`, `reclient_cfgs`, plus
`deps_revisions.gni` pinning their git hashes.

## Bazel — `BUILD.bazel`, `bazel/`

The top-level `BUILD.bazel` is intentionally small: it declares the
`license` target and a forest of `alias` rules pointing at the real
targets defined per directory:

```bazel
alias(name = "core",        actual = "//src/core:core",      visibility = ["//visibility:public"])
alias(name = "pathops",     actual = "//src/pathops:pathops", visibility = ["//visibility:public"])
alias(name = "ganesh_gl",   actual = "//src/gpu/ganesh/gl:ganesh_gl", …)
```

The actual rules live in per-directory `BUILD.bazel` files
(`src/core/BUILD.bazel`, `src/gpu/ganesh/BUILD.bazel`,
`modules/skottie/BUILD.bazel`, etc.). They use Skia's own thin macro
layer on top of `cc_library` defined in `bazel/`:

- `bazel/cpp_modules.bzl` — `skia_cc_library`, `skia_objc_library`.
- `bazel/flags.bzl`, `bazel/common_config_settings/` — feature flags
  (`use_vulkan`, `use_metal`, `enable_skottie`) exposed as
  `--define=…`.
- `bazel/external/` — wrappers around vendored third-party libs that
  paper over their native build systems (see
  [Third-Party Dependencies](third-party-deps.md)).
- `bazel/cipd_deps.bzl`, `bazel/cipd_install.bzl`,
  `bazel/download_config_files.bzl`, `bazel/gcs_mirror.bzl` — hermetic
  download of toolchains, NDKs, SDKs from CIPD / GCS mirrors.
- `bazel/exporter/`, `bazel/exporter_tool/` — generate the GN
  equivalent from Bazel rules (used to enforce that the two stay in
  sync).
- `bazel/deps.json`, `bazel/deps_parser/` — single source of truth for
  third-party version pins shared by both build systems.

`MODULE.bazel` plus the `WORKSPACE.bazel` (for the legacy path)
configure rule sets and toolchains; `bazel/Makefile` provides the
project-blessed entry points (`make all`, `make rules` …) so people
don't have to remember the long `bazelisk build //…` invocations.

## Toolchains — `toolchain/`

The `toolchain/` directory is Bazel's hermetic toolchain wiring:
`mac_toolchain_config.bzl`, `windows_toolchain_config.bzl`,
`ios_toolchain_config.bzl`, `linux_amd64_toolchain_config.bzl`,
`ndk_linux_arm64_toolchain_config.bzl`, plus `download_*_toolchain.bzl`
recipes that fetch a pinned Clang / NDK / Xcode-stub bundle on first
build. The `*_trampolines/` subdirectories (`linux_trampolines`,
`mac_trampolines`, `windows_trampolines`, `ios_trampolines`,
`android_trampolines`) wrap the downloaded compilers so they can be
launched in Bazel's sandbox.

`clang_layering_check.bzl` enforces include-graph layering as a build
step.

## How a feature lands in both

Adding a new `.cpp` to `src/foo/`:

1. List it in `src/foo/BUILD.bazel` under the appropriate
   `skia_cc_library`.
2. Add it to the matching `gn/foo.gni` (or to
   `src/foo/BUILD.gn` if the sub-target is local).
3. If it depends on a new third-party library, add a wrapper in both
   `third_party/foo/BUILD.gn` and `third_party/foo/BUILD.bazel` (see
   [Third-Party Dependencies](third-party-deps.md)).
4. If it gates on a new feature, declare the flag in `gn/skia.gni`
   (`declare_args { skia_enable_foo = false }`) **and** in
   `bazel/common_config_settings/`.

CI runs both build systems on every CL, so a missing entry on either
side is caught immediately.

## See also

- [Third-Party Dependencies](third-party-deps.md) — what the build pulls in.
- [Testing & Quality](testing-and-quality.md) — `dm`, `nanobench`, and `fuzz` build targets.
- [CanvasKit](canvaskit.md) — the WASM build matrix that exercises both GN and Bazel.
- [Platform Ports](platform-ports.md) — what the per-platform GN/Bazel toolchains link against.
