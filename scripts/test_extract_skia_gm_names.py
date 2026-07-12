#!/usr/bin/env python3
"""Tests for extract_skia_gm_names.py."""

from __future__ import annotations

import pathlib
import subprocess
import sys
import tempfile
import textwrap
import unittest

import extract_skia_gm_names


SCRIPT = pathlib.Path(__file__).with_name("extract_skia_gm_names.py")


CPP_FIXTURE = textwrap.dedent(
    """\
    DEF_SIMPLE_GM(foo_bar, canvas, 10, 10) {
        canvas->clear(SK_ColorWHITE);
    }

    class BazGM : public GM {
    protected:
        SkString getName() const override { return SkString("baz-gm"); }
    };

    DEF_GM(return new BazGM;)
    """
)


class ExtractSkiaGmNamesTest(unittest.TestCase):
    def make_gm_dir(self) -> pathlib.Path:
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        gm_dir = pathlib.Path(temp_dir.name)
        (gm_dir / "fixture.cpp").write_text(CPP_FIXTURE, encoding="utf-8")
        return gm_dir

    def test_extract_gm_names_reads_names_from_caller_provided_cpp_directory(self) -> None:
        gm_dir = self.make_gm_dir()

        actual = extract_skia_gm_names.extract_gm_names(gm_dir)

        self.assertEqual(actual, {"foo_bar", "baz-gm"})

    def test_cli_accepts_gm_dir_with_names(self) -> None:
        gm_dir = self.make_gm_dir()

        completed = subprocess.run(
            [sys.executable, str(SCRIPT), "--gm-dir", str(gm_dir), "--names"],
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertEqual(completed.stdout.splitlines(), ["baz-gm", "foo_bar"])


if __name__ == "__main__":
    unittest.main()
