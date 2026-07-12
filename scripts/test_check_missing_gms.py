#!/usr/bin/env python3
import contextlib
import importlib.util
import io
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest import mock


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = PROJECT_ROOT / "scripts" / "check_missing_gms.py"

sys.dont_write_bytecode = True


def load_checker():
    if not SCRIPT_PATH.is_file():
        raise AssertionError("missing checker script: scripts/check_missing_gms.py")
    spec = importlib.util.spec_from_file_location("check_missing_gms", SCRIPT_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load checker module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class CheckMissingGmsClassificationTest(unittest.TestCase):
    def test_extract_kotlin_gm_names_sees_block_getter_subclass_variants(self):
        checker = load_checker()

        with tempfile.TemporaryDirectory(prefix="check_missing_gms_block_getter_") as temp_root:
            gm_dir = Path(temp_root)
            (gm_dir / "ClippedBitmapShadersGm.kt").write_text(
                textwrap.dedent(
                    """\
                    package org.graphiks.kanvas.skia.gm.path

                    import org.graphiks.kanvas.paint.TileMode
                    import org.graphiks.kanvas.skia.SkiaGm

                    open class ClippedBitmapShadersBase(
                        private val mode: TileMode,
                        private val hq: Boolean,
                    ) : SkiaGm {
                        override val name: String
                            get() {
                                val descriptor = when (mode) {
                                    TileMode.REPEAT -> "tile"
                                    TileMode.MIRROR -> "mirror"
                                    TileMode.CLAMP -> "clamp"
                                    TileMode.DECAL -> "decal"
                                }
                                return if (hq) "clipped-bitmap-shaders-$descriptor-hq" else "clipped-bitmap-shaders-$descriptor"
                            }
                    }

                    class ClippedBitmapShadersTileGm : ClippedBitmapShadersBase(TileMode.REPEAT, false)
                    class ClippedBitmapShadersMirrorGm : ClippedBitmapShadersBase(TileMode.MIRROR, false)
                    class ClippedBitmapShadersClampGm : ClippedBitmapShadersBase(TileMode.CLAMP, false)
                    class ClippedBitmapShadersTileHqGm : ClippedBitmapShadersBase(TileMode.REPEAT, true)
                    class ClippedBitmapShadersMirrorHqGm : ClippedBitmapShadersBase(TileMode.MIRROR, true)
                    class ClippedBitmapShadersClampHqGm : ClippedBitmapShadersBase(TileMode.CLAMP, true)
                    """
                ),
                encoding="utf-8",
            )

            with mock.patch.object(checker, "GM_DIR", gm_dir):
                actual = checker.extract_kotlin_gm_names()

        self.assertTrue(
            {
                "clipped-bitmap-shaders-tile",
                "clipped-bitmap-shaders-mirror",
                "clipped-bitmap-shaders-clamp",
                "clipped-bitmap-shaders-tile-hq",
                "clipped-bitmap-shaders-mirror-hq",
                "clipped-bitmap-shaders-clamp-hq",
            }.issubset(actual)
        )

    def test_separator_alias_is_reported_without_being_direct_match(self):
        checker = load_checker()

        result = checker.classify_reference("lineargradientrt", {"linear_gradient_rt"}, None)

        self.assertEqual("normalized-alias", result["kind"])
        self.assertEqual("linear_gradient_rt", result["reference"])

    def test_explicit_cpp_family_evidence_is_retained(self):
        checker = load_checker()

        result = checker.classify_reference(
            "clippedbitmapshaders",
            {"clipped-bitmap-shaders-clamp", "clipped-bitmap-shaders-tile"},
            {
                "clipped-bitmap-shaders-clamp",
                "clipped-bitmap-shaders-mirror",
                "clipped-bitmap-shaders-tile",
            },
        )

        self.assertEqual("variant-family", result["kind"])
        self.assertEqual(
            ["clipped-bitmap-shaders-clamp", "clipped-bitmap-shaders-tile"],
            result["references"],
        )

    def test_explicit_cpp_family_evidence_accepts_underscored_variants(self):
        checker = load_checker()

        result = checker.classify_reference(
            "anisotropic_image_scale",
            {
                "anisotropic_image_scale_aniso",
                "anisotropic_image_scale_linear",
                "anisotropic_image_scale_mip",
            },
            {
                "anisotropic_image_scale_aniso",
                "anisotropic_image_scale_linear",
                "anisotropic_image_scale_mip",
            },
        )

        self.assertEqual("variant-family", result["kind"])
        self.assertEqual(
            [
                "anisotropic_image_scale_aniso",
                "anisotropic_image_scale_linear",
                "anisotropic_image_scale_mip",
            ],
            result["references"],
        )

    def test_generic_cpp_buckets_do_not_classify_references(self):
        checker = load_checker()

        cases = (
            ("all", {"all_bitmap_configs", "all_variants_8888"}),
            ("circle", {"circle_sizes"}),
            ("color", {"color_cube_rt", "colorwheel"}),
        )
        for gm_name, references in cases:
            with self.subTest(gm_name=gm_name):
                result = checker.classify_reference(gm_name, references, {gm_name})
                self.assertEqual("missing", result["kind"])
                self.assertEqual([], result["references"])

    def test_unmatched_name_is_actionable_missing(self):
        checker = load_checker()

        result = checker.classify_reference(
            "aa_rect_effect",
            {"aarectmodes"},
            {"aa_rect_effect"},
        )

        self.assertEqual("missing", result["kind"])

    def test_cli_no_argument_mode_preserves_legacy_matched_semantics(self):
        checker = load_checker()

        with fixture_dirs() as fixture:
            output = run_checker(
                checker,
                ref_dir=fixture["ref_dir"],
                gm_dir=fixture["gm_dir"],
            )

        self.assertIn("Matched: 8 (6 direct + 2 parameterized)", output)
        self.assertIn("source-evidence: unavailable", output)
        self.assertIn("--- Parameterized variants of existing GMs ---", output)
        self.assertIn("  nested_aa.png  <- from nested", output)
        self.assertIn("  nested_bw.png  <- from nested", output)
        self.assertIn("=== REFERENCE PNGs WITHOUT Kotlin GM (3) ===", output)
        self.assertIn("  clipped-bitmap-shaders-clamp.png", output)
        self.assertIn("  clipped-bitmap-shaders-tile.png", output)
        self.assertIn("  linear_gradient_rt.png", output)
        self.assertIn("=== GM names WITHOUT reference PNG (5) ===", output)
        self.assertIn("  all.png", output)
        self.assertIn("  anisotropic_image_scale.png", output)
        self.assertIn("  clippedbitmapshaders.png", output)
        self.assertIn("  lineargradientrt.png", output)
        self.assertIn("  missing_gm.png", output)
        self.assertNotIn("--- Normalized aliases ---", output)
        self.assertNotIn("--- Variant families from CPP source evidence ---", output)

    def test_cli_with_cpp_gm_dir_separates_source_evidence_and_suppresses_generic_buckets(self):
        checker = load_checker()

        with fixture_dirs() as fixture:
            output = run_checker(
                checker,
                ref_dir=fixture["ref_dir"],
                gm_dir=fixture["gm_dir"],
                cpp_gm_dir=fixture["cpp_gm_dir"],
            )

        self.assertIn("Matched: 8 (6 direct + 2 parameterized)", output)
        self.assertIn("source-evidence: cpp-gm-dir=", output)
        self.assertIn("--- Normalized aliases ---", output)
        self.assertIn("  lineargradientrt.png  <- alias linear_gradient_rt.png", output)
        self.assertIn("--- Variant families from CPP source evidence ---", output)
        self.assertIn(
            "  anisotropic_image_scale.png  <- variants "
            "anisotropic_image_scale_aniso.png, anisotropic_image_scale_linear.png, "
            "anisotropic_image_scale_mip.png",
            output,
        )
        self.assertIn(
            "  clippedbitmapshaders.png  <- variants "
            "clipped-bitmap-shaders-clamp.png, clipped-bitmap-shaders-tile.png",
            output,
        )
        self.assertNotIn("  all.png  <- variants", output)
        self.assertIn("=== GM names WITHOUT reference PNG (2) ===", output)
        self.assertIn("  all.png", output)
        self.assertIn("  missing_gm.png", output)

    def test_cli_section_ordering_keeps_diagnostics_before_actionable_missing(self):
        checker = load_checker()

        with fixture_dirs() as fixture:
            output = run_checker(
                checker,
                ref_dir=fixture["ref_dir"],
                gm_dir=fixture["gm_dir"],
                cpp_gm_dir=fixture["cpp_gm_dir"],
            )

        section_positions = [
            output.index("source-evidence: cpp-gm-dir="),
            output.index("--- Normalized aliases ---"),
            output.index("--- Variant families from CPP source evidence ---"),
            output.index("=== GM names WITHOUT reference PNG (2) ==="),
        ]
        self.assertEqual(section_positions, sorted(section_positions))

    def test_cli_matches_explicit_reference_name_aliases_without_false_inference(self):
        checker = load_checker()

        with explicit_alias_fixture_dirs() as fixture:
            output = run_checker(
                checker,
                ref_dir=fixture["ref_dir"],
                gm_dir=fixture["gm_dir"],
            )

        self.assertIn("Matched: 2 (2 direct + 0 parameterized)", output)
        self.assertIn("=== REFERENCE PNGs WITHOUT Kotlin GM (1) ===", output)
        self.assertIn("  convex-lineonly-paths-noalias.png", output)
        self.assertIn(
            "bitmaptiled_fractional_horizontal_manual.png "
            "(base 'bitmaptiled_fractional_horizontal' also missing)",
            output,
        )
        self.assertIn("=== GM names WITHOUT reference PNG (1) ===", output)
        gm_names_section = output.split("=== GM names WITHOUT reference PNG (1) ===", 1)[1]
        self.assertIn("  convex_lineonly_paths_noalias.png", gm_names_section)
        self.assertNotIn("  convex-lineonly-paths.png", output)
        self.assertNotIn("  convex-lineonly-paths-stroke-and-fill.png", output)
        self.assertNotIn("  convex_lineonly_paths.png", gm_names_section)
        self.assertNotIn("  convex_lineonly_paths_stroke_and_fill.png", gm_names_section)
        self.assertNotIn("  bitmaptiled_fractional_horizontal_manual.png", gm_names_section)


@contextlib.contextmanager
def fixture_dirs():
    with tempfile.TemporaryDirectory(prefix="check_missing_gms_") as temp_root:
        root = Path(temp_root)
        ref_dir = root / "reference"
        gm_dir = root / "gm-kotlin"
        cpp_gm_dir = root / "gm-cpp"
        ref_dir.mkdir()
        gm_dir.mkdir()
        cpp_gm_dir.mkdir()

        for name in (
            "direct",
            "nested_aa",
            "nested_bw",
            "linear_gradient_rt",
            "anisotropic_image_scale_aniso",
            "anisotropic_image_scale_linear",
            "anisotropic_image_scale_mip",
            "clipped-bitmap-shaders-clamp",
            "clipped-bitmap-shaders-tile",
            "all_bitmap_configs",
            "all_variants_8888",
        ):
            (ref_dir / f"{name}.png").write_bytes(b"")

        for filename, gm_name in (
            ("DirectGm.kt", "direct"),
            ("NestedGm.kt", "nested"),
            ("AnisotropicImageScaleAnisoGm.kt", "anisotropic_image_scale_aniso"),
            ("AnisotropicImageScaleLinearGm.kt", "anisotropic_image_scale_linear"),
            ("AnisotropicImageScaleMipGm.kt", "anisotropic_image_scale_mip"),
            ("LinearGradientRtGm.kt", "lineargradientrt"),
            ("AnisotropicImageScaleGm.kt", "anisotropic_image_scale"),
            ("ClippedBitmapShadersGm.kt", "clippedbitmapshaders"),
            ("AllGm.kt", "all"),
            ("AllBitmapConfigsGm.kt", "all_bitmap_configs"),
            ("AllVariants8888Gm.kt", "all_variants_8888"),
            ("MissingGm.kt", "missing_gm"),
        ):
            (gm_dir / filename).write_text(
                f'object {filename.removesuffix(".kt")} : DemoGm("{gm_name}")\n',
                encoding="utf-8",
            )

        (cpp_gm_dir / "fixture.cpp").write_text(
            "\n".join(
                [
                    "DEF_SIMPLE_GM(anisotropic_image_scale_aniso) {}",
                    "DEF_SIMPLE_GM(anisotropic_image_scale_linear) {}",
                    "DEF_SIMPLE_GM(anisotropic_image_scale_mip) {}",
                    "DEF_SIMPLE_GM(clipped_bitmap_shaders_clamp) {}",
                    "DEF_SIMPLE_GM(clipped_bitmap_shaders_mirror) {}",
                    "DEF_SIMPLE_GM(clipped_bitmap_shaders_tile) {}",
                    "DEF_SIMPLE_GM(missing_gm) {}",
                ]
            )
            + "\n",
            encoding="utf-8",
        )

        yield {
            "ref_dir": ref_dir,
            "gm_dir": gm_dir,
            "cpp_gm_dir": cpp_gm_dir,
        }


@contextlib.contextmanager
def explicit_alias_fixture_dirs():
    with tempfile.TemporaryDirectory(prefix="check_missing_gms_reference_alias_") as temp_root:
        root = Path(temp_root)
        ref_dir = root / "reference"
        gm_dir = root / "gm-kotlin"
        ref_dir.mkdir()
        gm_dir.mkdir()

        for name in (
            "convex-lineonly-paths",
            "convex-lineonly-paths-stroke-and-fill",
            "convex-lineonly-paths-noalias",
            "bitmaptiled_fractional_horizontal_manual",
        ):
            (ref_dir / f"{name}.png").write_bytes(b"")

        (gm_dir / "ConvexLineOnlyPathsGm.kt").write_text(
            textwrap.dedent(
                """\
                package org.graphiks.kanvas.skia.gm.path

                import org.graphiks.kanvas.skia.SkiaGm

                class ConvexLineOnlyPathsGm : SkiaGm {
                    override val name = "convex_lineonly_paths"
                    override val referenceName = "convex-lineonly-paths"
                }

                class ConvexLineOnlyPathsStrokeAndFillGm : SkiaGm {
                    override val name = "convex_lineonly_paths_stroke_and_fill"
                    override val referenceName = "convex-lineonly-paths-stroke-and-fill"
                }

                class ConvexLineOnlyPathsNoAliasGm : SkiaGm {
                    override val name = "convex_lineonly_paths_noalias"
                }

                class BitmapTiledFractionalHorizontalManualGm : SkiaGm {
                    override val name = "bitmaptiled_fractional_horizontal_manual"
                }
                """
            ),
            encoding="utf-8",
        )

        yield {
            "ref_dir": ref_dir,
            "gm_dir": gm_dir,
        }


def run_checker(checker, ref_dir: Path, gm_dir: Path, cpp_gm_dir: Path | None = None) -> str:
    stdout = io.StringIO()
    argv = [str(SCRIPT_PATH)]
    if cpp_gm_dir is not None:
        argv.extend(["--cpp-gm-dir", str(cpp_gm_dir)])
    with (
        mock.patch.object(checker, "REF_DIR", ref_dir),
        mock.patch.object(checker, "GM_DIR", gm_dir),
        mock.patch.object(sys, "argv", argv),
        contextlib.redirect_stdout(stdout),
    ):
        checker.main()
    return stdout.getvalue()


if __name__ == "__main__":
    unittest.main()
