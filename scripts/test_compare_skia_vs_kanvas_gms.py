#!/usr/bin/env python3
"""Tests for compare_skia_vs_kanvas_gms.py."""

from __future__ import annotations

import contextlib
import io
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import compare_skia_vs_kanvas_gms


class CompareSkiaVsKanvasGmsTest(unittest.TestCase):
    def test_extract_skia_gm_names_uses_caller_provided_directory(self) -> None:
        gm_dir = Path("/tmp/skia-gm")

        with mock.patch.object(
            compare_skia_vs_kanvas_gms,
            "extract_cpp_gm_names",
            return_value={"alpha", "beta"},
        ) as extract_mock:
            actual = compare_skia_vs_kanvas_gms.extract_skia_gm_names(gm_dir)

        self.assertEqual(actual, {"alpha", "beta"})
        extract_mock.assert_called_once_with(gm_dir)

    def test_extract_skia_gm_names_filters_unresolved_sentinels(self) -> None:
        gm_dir = Path("/tmp/skia-gm")

        with mock.patch.object(
            compare_skia_vs_kanvas_gms,
            "extract_cpp_gm_names",
            return_value={"alpha", "<unresolved:MysteryGM>"},
        ):
            actual = compare_skia_vs_kanvas_gms.extract_skia_gm_names(gm_dir)

        self.assertEqual(actual, {"alpha"})

    def test_resolve_cpp_gm_dir_uses_documented_environment_variable(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env = {"KANVAS_SKIA_GM_DIR": temp_dir}

            actual = compare_skia_vs_kanvas_gms.resolve_cpp_gm_dir(None, env=env)

        self.assertEqual(actual, Path(temp_dir))

    def test_resolve_cpp_gm_dir_requires_explicit_input_when_no_env_or_default_exists(self) -> None:
        with mock.patch.object(compare_skia_vs_kanvas_gms, "resolve_default_gm_dir", return_value=None):
            with self.assertRaisesRegex(
                ValueError,
                "--cpp-gm-dir or KANVAS_SKIA_GM_DIR",
            ):
                compare_skia_vs_kanvas_gms.resolve_cpp_gm_dir(None, env={})

    def test_main_does_not_list_unresolved_sentinels_as_not_yet_ported(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            cpp_gm_dir = Path(temp_dir)
            stdout = io.StringIO()
            stderr = io.StringIO()

            with (
                mock.patch.object(compare_skia_vs_kanvas_gms, "extract_kanvas_gm_names", return_value=set()),
                mock.patch.object(compare_skia_vs_kanvas_gms, "extract_skia_gm_names", return_value={"alpha"}),
                mock.patch.object(compare_skia_vs_kanvas_gms, "REF_DIR", cpp_gm_dir),
                mock.patch("sys.argv", [str(compare_skia_vs_kanvas_gms.__file__), "--cpp-gm-dir", str(cpp_gm_dir)]),
                contextlib.redirect_stdout(stdout),
                contextlib.redirect_stderr(stderr),
            ):
                compare_skia_vs_kanvas_gms.main()

        output = stdout.getvalue()
        self.assertIn("alpha", output)
        self.assertNotIn("<unresolved:", output)


if __name__ == "__main__":
    unittest.main()
