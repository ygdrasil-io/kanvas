#!/usr/bin/env python3
import contextlib
import importlib.util
import io
import sys
import tempfile
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
    def test_separator_alias_is_reported_without_being_direct_match(self):
        checker = load_checker()

        result = checker.classify_reference("lineargradientrt", {"linear_gradient_rt"}, None)

        self.assertEqual("normalized-alias", result["kind"])
        self.assertEqual("linear_gradient_rt", result["reference"])

    def test_prefix_family_is_reported_as_variant(self):
        checker = load_checker()

        result = checker.classify_reference(
            "clippedbitmapshaders",
            {"clipped-bitmap-shaders-clamp", "clipped-bitmap-shaders-tile"},
            {"clippedbitmapshaders"},
        )

        self.assertEqual("variant-family", result["kind"])

    def test_unmatched_name_is_actionable_missing(self):
        checker = load_checker()

        result = checker.classify_reference(
            "aa_rect_effect",
            {"aarectmodes"},
            {"aa_rect_effect"},
        )

        self.assertEqual("missing", result["kind"])

    def test_cli_fixture_reports_source_aware_headings(self):
        checker = load_checker()

        with tempfile.TemporaryDirectory(prefix="check_missing_gms_") as temp_root:
            root = Path(temp_root)
            ref_dir = root / "reference"
            gm_dir = root / "gm-kotlin"
            cpp_gm_dir = root / "gm-cpp"
            ref_dir.mkdir()
            gm_dir.mkdir()
            cpp_gm_dir.mkdir()

            for name in (
                "linear_gradient_rt",
                "clipped-bitmap-shaders-clamp",
                "clipped-bitmap-shaders-tile",
                "aarectmodes",
            ):
                (ref_dir / f"{name}.png").write_bytes(b"")

            (gm_dir / "LinearGradientRtGm.kt").write_text(
                'object LinearGradientRtGm : DemoGm("lineargradientrt")\n',
                encoding="utf-8",
            )
            (gm_dir / "ClippedBitmapShadersGm.kt").write_text(
                'object ClippedBitmapShadersGm : DemoGm("clippedbitmapshaders")\n',
                encoding="utf-8",
            )
            (gm_dir / "AaRectEffectGm.kt").write_text(
                'object AaRectEffectGm : DemoGm("aa_rect_effect")\n',
                encoding="utf-8",
            )

            (cpp_gm_dir / "fixture.cpp").write_text(
                "\n".join(
                    [
                        "DEF_SIMPLE_GM(clippedbitmapshaders) {}",
                        "DEF_SIMPLE_GM(aa_rect_effect) {}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            stdout = io.StringIO()
            argv = [
                str(SCRIPT_PATH),
                "--cpp-gm-dir",
                str(cpp_gm_dir),
            ]
            with (
                mock.patch.object(checker, "REF_DIR", ref_dir),
                mock.patch.object(checker, "GM_DIR", gm_dir),
                mock.patch.object(sys, "argv", argv),
                contextlib.redirect_stdout(stdout),
            ):
                checker.main()

        output = stdout.getvalue()
        self.assertIn("--- Normalized aliases ---", output)
        self.assertIn("--- Variant families from CPP source evidence ---", output)
        self.assertIn("=== ACTIONABLE missing references ===", output)


if __name__ == "__main__":
    unittest.main()
