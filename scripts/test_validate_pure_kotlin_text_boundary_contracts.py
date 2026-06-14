#!/usr/bin/env python3
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_pure_kotlin_text_boundary_contracts.py"

sys.dont_write_bytecode = True


def load_validator():
    if not VALIDATOR_PATH.is_file():
        raise AssertionError("missing validator script: scripts/validate_pure_kotlin_text_boundary_contracts.py")
    spec = importlib.util.spec_from_file_location("validate_pure_kotlin_text_boundary_contracts", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load validator module spec")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class PureKotlinTextBoundaryContractsTest(unittest.TestCase):
    def test_manifest_covers_spec_00_boundaries_without_support_claims(self) -> None:
        validator = load_validator()
        manifest = validator.load_manifest(PROJECT_ROOT)
        validator.validate_manifest(PROJECT_ROOT, manifest)

        self.assertEqual(
            [
                "schemaVersion",
                "manifestId",
                "sourceSpec",
                "artifactType",
                "supportClaim",
                "nonClaims",
                "validationCommand",
                "targetPackageRoots",
                "contractSymbols",
            ],
            list(manifest.keys()),
        )
        self.assertEqual("audit-coordination-artifact-no-support-claim", manifest["supportClaim"])

        package_roots = [row["packageRoot"] for row in manifest["targetPackageRoots"]]
        self.assertEqual(
            [
                "org.graphiks.kanvas.font",
                "org.graphiks.kanvas.font.scaler",
                "org.graphiks.kanvas.glyph",
                "org.graphiks.kanvas.glyph.gpu",
                "org.graphiks.kanvas.gpu.renderer.commands",
                "org.graphiks.kanvas.gpu.renderer.text",
                "org.graphiks.kanvas.text.paragraph",
                "org.graphiks.kanvas.text.shaping",
            ],
            sorted(package_roots),
        )

        symbol_ids = [row["id"] for row in manifest["contractSymbols"]]
        self.assertEqual(sorted(symbol_ids), symbol_ids)
        self.assertEqual(
            {
                "font-source-id",
                "gpu-glyph-run-descriptor",
                "gpu-text-artifact-generation",
                "gpu-text-artifact-id",
                "gpu-text-artifact-reference",
                "gpu-text-diagnostic-codes",
                "gpu-text-route-diagnostics",
                "glyph-strike-key",
                "normalized-draw-command-draw-text-run",
                "typeface-id",
            },
            set(symbol_ids),
        )

    def test_validator_rejects_missing_required_contract_symbol(self) -> None:
        validator = load_validator()
        manifest = validator.load_manifest(PROJECT_ROOT)
        modified = dict(manifest)
        modified["contractSymbols"] = [
            row for row in manifest["contractSymbols"] if row["symbol"] != "FontSourceID"
        ]

        with self.assertRaises(validator.ValidationError) as missing:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("missing required contract symbols", str(missing.exception))

    def test_validator_rejects_forbidden_pure_text_import(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory(prefix="pkt_boundary_contracts_") as temp_root:
            root = Path(temp_root)
            bad_source = root / "font" / "text" / "src" / "main" / "kotlin" / "org" / "graphiks" / "kanvas" / "text" / "shaping" / "Bad.kt"
            bad_source.parent.mkdir(parents=True)
            bad_source.write_text(
                "\n".join(
                    [
                        "package org.graphiks.kanvas.text.shaping",
                        "",
                        "import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand",
                        "",
                        "class Bad",
                        "",
                    ]
                ),
                encoding="utf-8",
            )

            with self.assertRaises(validator.ValidationError) as forbidden:
                validator.validate_import_boundaries(root)
        self.assertIn("pure Kotlin text boundary import", str(forbidden.exception))

    def test_validator_rejects_forbidden_gpu_renderer_font_parser_import(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory(prefix="pkt_boundary_contracts_") as temp_root:
            root = Path(temp_root)
            bad_source = root / "gpu-renderer" / "src" / "main" / "kotlin" / "org" / "graphiks" / "kanvas" / "gpu" / "renderer" / "text" / "Bad.kt"
            bad_source.parent.mkdir(parents=True)
            bad_source.write_text(
                "\n".join(
                    [
                        "package org.graphiks.kanvas.gpu.renderer.text",
                        "",
                        "import org.graphiks.kanvas.font.scaler.FontScaler",
                        "",
                        "class Bad",
                        "",
                    ]
                ),
                encoding="utf-8",
            )

            with self.assertRaises(validator.ValidationError) as forbidden:
                validator.validate_import_boundaries(root)
        self.assertIn("GPU renderer text boundary import", str(forbidden.exception))

    def test_scanner_ignores_comments_and_triple_quoted_strings(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory(prefix="pkt_boundary_contracts_") as temp_root:
            root = Path(temp_root)
            source = root / "font" / "text" / "src" / "main" / "kotlin" / "org" / "graphiks" / "kanvas" / "text" / "shaping" / "Safe.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "\n".join(
                    [
                        "package org.graphiks.kanvas.text.shaping // package comment",
                        "",
                        "// import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand",
                        "/*",
                        "import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand",
                        "*/",
                        "val sample = \"\"\"",
                        "import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic",
                        "package org.graphiks.kanvas.gpu.renderer.commands",
                        "\"\"\"",
                        "class Safe",
                        "",
                    ]
                ),
                encoding="utf-8",
            )

            declarations = [
                (line_number, kind, target)
                for _, line_number, kind, target in validator.scan_kotlin_import_targets(
                    root,
                    "font/text/src/main/kotlin",
                )
            ]

        self.assertEqual([(1, "package", "org.graphiks.kanvas.text.shaping")], declarations)

    def test_scanner_ignores_nested_block_comments(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory(prefix="pkt_boundary_contracts_") as temp_root:
            root = Path(temp_root)
            source = root / "font" / "text" / "src" / "main" / "kotlin" / "org" / "graphiks" / "kanvas" / "text" / "shaping" / "NestedSafe.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "\n".join(
                    [
                        "package org.graphiks.kanvas.text.shaping",
                        "",
                        "/*",
                        "outer comment start",
                        "/* inner comment */",
                        "import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand",
                        "*/",
                        "class NestedSafe",
                        "",
                    ]
                ),
                encoding="utf-8",
            )

            declarations = [
                (line_number, kind, target)
                for _, line_number, kind, target in validator.scan_kotlin_import_targets(
                    root,
                    "font/text/src/main/kotlin",
                )
            ]
            validator.validate_import_boundaries(root)

        self.assertEqual([(1, "package", "org.graphiks.kanvas.text.shaping")], declarations)

    def test_import_parser_strips_package_comments_and_import_aliases(self) -> None:
        validator = load_validator()

        self.assertEqual(
            ("package", "org.graphiks.kanvas.text.shaping"),
            validator.import_or_package_target("package org.graphiks.kanvas.text.shaping // trailing comment"),
        )
        self.assertEqual(
            ("import", "org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand"),
            validator.import_or_package_target(
                "import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand as DrawText"
            ),
        )
        self.assertEqual(
            ("import", "org.graphiks.kanvas.gpu.renderer.commands.*"),
            validator.import_or_package_target("import org.graphiks.kanvas.gpu.renderer.commands.*"),
        )

    def test_target_matching_requires_exact_package_segments(self) -> None:
        validator = load_validator()

        self.assertTrue(
            validator.target_matches_prefix(
                "org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand",
                "org.graphiks.kanvas.gpu.renderer",
            )
        )
        self.assertTrue(
            validator.target_matches_prefix(
                "org.graphiks.kanvas.gpu.renderer.*",
                "org.graphiks.kanvas.gpu.renderer",
            )
        )
        self.assertFalse(
            validator.target_matches_prefix(
                "org.graphiks.kanvas.gpu.rendererish.commands.NormalizedDrawCommand",
                "org.graphiks.kanvas.gpu.renderer",
            )
        )
        self.assertFalse(validator.target_matches_prefix("skiax.Canvas", "skia"))

    def test_nested_owner_symbol_must_be_declared_under_parent_symbol(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory(prefix="pkt_boundary_contracts_") as temp_root:
            root = Path(temp_root)
            source = root / "Owner.kt"
            source.write_text(
                "\n".join(
                    [
                        "package org.graphiks.kanvas.gpu.renderer.commands",
                        "",
                        "sealed interface NormalizedDrawCommand",
                        "",
                        "data class DrawTextRun(val id: Int)",
                        "",
                    ]
                ),
                encoding="utf-8",
            )
            row = {
                "symbol": "NormalizedDrawCommand.DrawTextRun",
                "ownerFile": "Owner.kt",
            }

            with self.assertRaises(validator.ValidationError) as missing_nested:
                validator.validate_owner_file_contains_symbol(root, row)

        self.assertIn("nested symbol", str(missing_nested.exception))

    def test_existing_path_guard_rejects_relative_traversal_outside_project_root(self) -> None:
        validator = load_validator()

        with self.assertRaises(validator.ValidationError) as traversal:
            validator.require_existing_path(PROJECT_ROOT, "../../../RTK.md", "escape")
        self.assertIn("stay under project root", str(traversal.exception))

    def test_validator_rejects_hidden_support_claims(self) -> None:
        validator = load_validator()
        manifest = validator.load_manifest(PROJECT_ROOT)
        modified = dict(manifest)
        package_rows = [dict(row) for row in manifest["targetPackageRoots"]]
        package_rows[0]["boundaryRole"] = (
            f"{package_rows[0]['boundaryRole']} This is a support claim for the target boundary."
        )
        modified["targetPackageRoots"] = package_rows

        with self.assertRaises(validator.ValidationError) as support_claim:
            validator.validate_manifest(PROJECT_ROOT, modified)
        self.assertIn("support claim wording is forbidden", str(support_claim.exception))
        self.assertIn("targetPackageRoots.0.boundaryRole", str(support_claim.exception))


if __name__ == "__main__":
    unittest.main()
