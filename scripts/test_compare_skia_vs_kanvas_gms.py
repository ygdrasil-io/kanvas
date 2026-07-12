#!/usr/bin/env python3
"""Tests for compare_skia_vs_kanvas_gms.py."""

from __future__ import annotations

import subprocess
import unittest
from unittest import mock

import compare_skia_vs_kanvas_gms


class CompareSkiaVsKanvasGmsTest(unittest.TestCase):
    def test_extract_skia_gm_names_passes_explicit_gm_dir_to_extractor(self) -> None:
        completed = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="alpha\nbeta\n",
            stderr="",
        )

        with mock.patch("subprocess.run", return_value=completed) as run_mock:
            actual = compare_skia_vs_kanvas_gms.extract_skia_gm_names()

        self.assertEqual(actual, {"alpha", "beta"})
        run_mock.assert_called_once()
        command = run_mock.call_args.args[0]
        self.assertIn("--gm-dir", command)
        self.assertIn(str(compare_skia_vs_kanvas_gms.SKIA_GM_DIR), command)


if __name__ == "__main__":
    unittest.main()
