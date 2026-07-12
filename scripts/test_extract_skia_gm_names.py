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

PATTERN_FIXTURE = textwrap.dedent(
    """\
    class RuntimeShaderGM : public GM {
    public:
        RuntimeShaderGM(const char* name) : fName(name) {}
        SkString getName() const override { return fName; }
    protected:
        SkString fName;
    };

    class SimpleRT : public RuntimeShaderGM {
    public:
        SimpleRT() : RuntimeShaderGM("runtime_shader") {}
    };
    DEF_GM(return new SimpleRT;)

    class ThresholdRT : public RuntimeShaderGM {
    public:
        ThresholdRT() : RuntimeShaderGM("threshold_rt") {}
    };
    DEF_GM(return new ThresholdRT;)

    class SpiralRT : public RuntimeShaderGM {
    public:
        SpiralRT() : RuntimeShaderGM("spiral_rt") {}
    };
    DEF_GM(return new SpiralRT;)

    class UnsharpRT : public RuntimeShaderGM {
    public:
        UnsharpRT() : RuntimeShaderGM("unsharp_rt") {}
    };
    DEF_GM(return new UnsharpRT;)

    class ColorCubeRT : public RuntimeShaderGM {
    public:
        ColorCubeRT() : RuntimeShaderGM("color_cube_rt") {}
    };
    DEF_GM(return new ColorCubeRT;)

    class ColorCubeColorFilterRT : public RuntimeShaderGM {
    public:
        ColorCubeColorFilterRT() : RuntimeShaderGM("color_cube_cf_rt") {}
    };
    DEF_GM(return new ColorCubeColorFilterRT;)

    class ClippedBitmapShadersGM : public GM {
    public:
        ClippedBitmapShadersGM(SkTileMode mode, bool hq=false) : fMode(mode), fHQ(hq) {}
    protected:
        SkTileMode fMode;
        bool fHQ;

        SkString getName() const override {
            SkString descriptor;
            switch (fMode) {
                case SkTileMode::kRepeat:
                    descriptor = "tile";
                    break;
                case SkTileMode::kMirror:
                    descriptor = "mirror";
                    break;
                case SkTileMode::kClamp:
                    descriptor = "clamp";
                    break;
                case SkTileMode::kDecal:
                    descriptor = "decal";
                    break;
            }
            descriptor.prepend("clipped-bitmap-shaders-");
            if (fHQ) {
                descriptor.append("-hq");
            }
            return descriptor;
        }
    };
    DEF_GM(return new ClippedBitmapShadersGM(SkTileMode::kRepeat);)
    DEF_GM(return new ClippedBitmapShadersGM(SkTileMode::kMirror);)
    DEF_GM(return new ClippedBitmapShadersGM(SkTileMode::kClamp);)
    DEF_GM(return new ClippedBitmapShadersGM(SkTileMode::kRepeat, true);)
    DEF_GM(return new ClippedBitmapShadersGM(SkTileMode::kMirror, true);)
    DEF_GM(return new ClippedBitmapShadersGM(SkTileMode::kClamp, true);)

    class AnisotropicGM : public GM {
    public:
        enum class Mode { kLinear, kMip, kAniso };

        AnisotropicGM(Mode mode) : fMode(mode) {}

    protected:
        Mode fMode;

        SkString getName() const override {
            SkString name("anisotropic_image_scale_");
            switch (fMode) {
                case Mode::kLinear:
                    name += "linear";
                    break;
                case Mode::kMip:
                    name += "mip";
                    break;
                case Mode::kAniso:
                    name += "aniso";
                    break;
            }
            return name;
        }
    };
    DEF_GM(return new AnisotropicGM(AnisotropicGM::Mode::kLinear);)
    DEF_GM(return new AnisotropicGM(AnisotropicGM::Mode::kMip);)
    DEF_GM(return new AnisotropicGM(AnisotropicGM::Mode::kAniso);)

    class ClipSuperRRect : public RuntimeShaderGM {
    public:
        ClipSuperRRect(const char* name, float power) : RuntimeShaderGM(name) {}
    };
    DEF_GM(return new ClipSuperRRect("clip_super_rrect_pow2", 2);)
    // DEF_GM(return new ClipSuperRRect("clip_super_rrect_pow3", 3);)
    DEF_GM(return new ClipSuperRRect("clip_super_rrect_pow3.5", 3.5);)
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

    def test_summary_keeps_using_fallback_wording(self) -> None:
        gm_dir = self.make_gm_dir()
        (gm_dir / "unresolved.cpp").write_text(
            textwrap.dedent(
                """\
                class MysteryGM : public GM {};

                DEF_GM(return new MysteryGM;)
                """
            ),
            encoding="utf-8",
        )

        completed = subprocess.run(
            [sys.executable, str(SCRIPT), "--gm-dir", str(gm_dir)],
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertIn("Unresolved (1 entries", completed.stdout)

    def test_extract_gm_names_resolves_real_world_constructor_and_switch_patterns(self) -> None:
        gm_dir = self.make_gm_dir()
        (gm_dir / "patterns.cpp").write_text(PATTERN_FIXTURE, encoding="utf-8")

        actual = extract_skia_gm_names.extract_gm_names(gm_dir)

        self.assertTrue(
            {
                "runtime_shader",
                "threshold_rt",
                "spiral_rt",
                "unsharp_rt",
                "color_cube_rt",
                "color_cube_cf_rt",
                "clipped-bitmap-shaders-tile",
                "clipped-bitmap-shaders-mirror",
                "clipped-bitmap-shaders-clamp",
                "clipped-bitmap-shaders-tile-hq",
                "clipped-bitmap-shaders-mirror-hq",
                "clipped-bitmap-shaders-clamp-hq",
                "anisotropic_image_scale_linear",
                "anisotropic_image_scale_mip",
                "anisotropic_image_scale_aniso",
                "clip_super_rrect_pow2",
                "clip_super_rrect_pow3.5",
            }.issubset(actual)
        )
        self.assertNotIn("clip_super_rrect_pow3", actual)

    def test_extract_gm_names_api_and_names_output_exclude_unresolved_sentinels(self) -> None:
        gm_dir = self.make_gm_dir()
        (gm_dir / "unresolved_only.cpp").write_text(
            textwrap.dedent(
                """\
                class MysteryGM : public GM {};
                DEF_GM(return new MysteryGM;)
                """
            ),
            encoding="utf-8",
        )

        actual = extract_skia_gm_names.extract_gm_names(gm_dir)
        self.assertEqual(actual, {"baz-gm", "foo_bar"})

        completed = subprocess.run(
            [sys.executable, str(SCRIPT), "--gm-dir", str(gm_dir), "--names"],
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertEqual(completed.stdout.splitlines(), ["baz-gm", "foo_bar"])


if __name__ == "__main__":
    unittest.main()
