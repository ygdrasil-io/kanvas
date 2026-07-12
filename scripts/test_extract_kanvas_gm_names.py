#!/usr/bin/env python3
"""Tests for extract_kanvas_gm_names.py."""

from __future__ import annotations

import tempfile
import textwrap
import unittest
from pathlib import Path

import extract_kanvas_gm_names


CLIPPED_BITMAP_SHADERS_FIXTURE = textwrap.dedent(
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
)


class ExtractKanvasGmNamesTest(unittest.TestCase):
    def test_extract_gm_names_resolves_block_getter_interpolation_and_subclasses(self) -> None:
        with tempfile.TemporaryDirectory(prefix="extract_kanvas_gm_names_") as temp_dir:
            gm_dir = Path(temp_dir)
            (gm_dir / "ClippedBitmapShadersGm.kt").write_text(
                CLIPPED_BITMAP_SHADERS_FIXTURE,
                encoding="utf-8",
            )

            actual = extract_kanvas_gm_names.extract_kanvas_gm_names(gm_dir)

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


if __name__ == "__main__":
    unittest.main()
