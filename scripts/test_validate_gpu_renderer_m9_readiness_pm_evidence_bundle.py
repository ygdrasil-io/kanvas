#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_gpu_renderer_m9_readiness_pm_evidence_bundle as validator


DASHBOARD_LINES = [
    "readiness-dashboard id=m9-gpu-renderer-readiness row=gpu-renderer.readiness classification=PolicyGated rows=5 readinessDelta=0.0 releaseBlocking=false productRouteActivated=false",
    "readiness-row area=correctness state=evidence-present classification=PolicyGated source=gpuEvidenceVerification readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
    "readiness-row area=activation state=policy-gated classification=PolicyGated source=headless-offscreen-only readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
    "readiness-row area=performance state=candidate-nonblocking classification=PolicyGated source=m9-frame-gate-policy readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
    "readiness-row area=cache state=observed-and-reporting classification=PolicyGated source=m9-cache-source-map readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
    "readiness-row area=release state=non-release-blocking classification=PolicyGated source=m9-frame-gate-policy readinessDelta=0.0 releaseBlocking=false productRouteActivated=false reportingOnly=true",
    "pm:gpu-renderer.readiness classification=PolicyGated correctness=evidence-present activation=policy-gated performance=candidate-nonblocking cache=observed-and-reporting release=non-release-blocking readinessDelta=0.0 releaseBlocking=false",
    "nonclaim:no-readiness-delta no-release-blocking-gate no-product-activation no-correctness-from-performance no-cache-derived-as-observed no-dashboard-promotion",
]


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def refresh_dashboard_hash(output_dir: Path) -> None:
    summary_path = output_dir / validator.SUMMARY_ARTIFACT
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    summary["artifacts"][validator.DASHBOARD_LINES_ARTIFACT] = validator.sha256_file(
        output_dir / validator.DASHBOARD_LINES_ARTIFACT,
    )
    write_json(summary_path, summary)


def write_fixture_bundle(root: Path) -> Path:
    output_dir = root / "gpu-renderer/build/reports/gpu-renderer-m9-readiness-pm-evidence"
    output_dir.mkdir(parents=True, exist_ok=True)
    lines_path = output_dir / validator.DASHBOARD_LINES_ARTIFACT
    lines_path.write_text("\n".join(DASHBOARD_LINES) + "\n", encoding="utf-8")
    summary = {
        "dashboardId": "m9-gpu-renderer-readiness",
        "evidenceRow": "gpu-renderer.readiness",
        "classification": "PolicyGated",
        "readinessDelta": 0.0,
        "releaseBlocking": False,
        "productRouteActivated": False,
        "rows": [
            {"area": "correctness", "state": "evidence-present", "source": "gpuEvidenceVerification"},
            {"area": "activation", "state": "policy-gated", "source": "headless-offscreen-only"},
            {"area": "performance", "state": "candidate-nonblocking", "source": "m9-frame-gate-policy"},
            {"area": "cache", "state": "observed-and-reporting", "source": "m9-cache-source-map"},
            {"area": "release", "state": "non-release-blocking", "source": "m9-frame-gate-policy"},
        ],
        "artifacts": {
            validator.DASHBOARD_LINES_ARTIFACT: validator.sha256_file(lines_path),
        },
        "nonClaims": [
            "No readiness delta.",
            "No release-blocking gate.",
            "No product activation.",
            "No correctness support inferred from performance evidence.",
            "No derived cache telemetry counted as observed.",
            "No dashboard row promotes readiness.",
        ],
    }
    write_json(output_dir / validator.SUMMARY_ARTIFACT, summary)
    _, entry = validator.validate_output(output_dir)
    write_json(output_dir / validator.OUTPUT_MANIFEST_ENTRY, entry)
    return output_dir


class GpuRendererM9ReadinessPmEvidenceValidatorTest(unittest.TestCase):
    def test_validate_output_accepts_policy_gated_dashboard_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))

            summary, entry = validator.validate_output(output_dir)

        self.assertEqual("gpu-renderer.readiness", summary["evidenceRow"])
        self.assertEqual("PolicyGated", summary["classification"])
        self.assertEqual(0.0, summary["readinessDelta"])
        self.assertFalse(summary["releaseBlocking"])
        self.assertFalse(summary["productRouteActivated"])
        self.assertEqual("gpuRendererM9ReadinessPmEvidence", entry["key"])
        self.assertEqual("PolicyGated", entry["status"])
        self.assertFalse(entry["releaseBlocking"])
        self.assertEqual(
            "rtk ./gradlew --no-daemon :gpu-renderer:gpuRendererM9ReadinessPmEvidenceBundle",
            entry["pmPackageCommand"],
        )
        self.assertFalse(hasattr(validator, "inject_pm_bundle"))

    def test_validate_output_rejects_readiness_or_release_movement(self) -> None:
        unsafe_updates = [
            ("readinessDelta", 0.5, "readinessDelta must remain 0.0"),
            ("releaseBlocking", True, "releaseBlocking must remain false"),
            ("productRouteActivated", True, "productRouteActivated must remain false"),
        ]
        for field, value, message in unsafe_updates:
            with self.subTest(field=field):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    summary_path = output_dir / validator.SUMMARY_ARTIFACT
                    summary = json.loads(summary_path.read_text(encoding="utf-8"))
                    summary[field] = value
                    write_json(summary_path, summary)

                    with self.assertRaisesRegex(validator.ValidationError, message):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_any_nonzero_dashboard_line_readiness_delta(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            lines_path = output_dir / validator.DASHBOARD_LINES_ARTIFACT
            lines_path.write_text(
                lines_path.read_text(encoding="utf-8").replace("readinessDelta=0.0", "readinessDelta=2.0", 1),
                encoding="utf-8",
            )
            refresh_dashboard_hash(output_dir)

            with self.assertRaisesRegex(validator.ValidationError, "dashboard line readinessDelta must remain 0.0"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_any_dashboard_line_release_or_product_movement(self) -> None:
        unsafe_updates = [
            ("releaseBlocking=false", "releaseBlocking=true", "dashboard line releaseBlocking must remain false"),
            (
                "productRouteActivated=false",
                "productRouteActivated=true",
                "dashboard line productRouteActivated must remain false",
            ),
        ]
        for old, new, message in unsafe_updates:
            with self.subTest(field=old):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    lines_path = output_dir / validator.DASHBOARD_LINES_ARTIFACT
                    lines_path.write_text(
                        lines_path.read_text(encoding="utf-8").replace(old, new, 1),
                        encoding="utf-8",
                    )
                    refresh_dashboard_hash(output_dir)

                    with self.assertRaisesRegex(validator.ValidationError, message):
                        validator.validate_output(output_dir)

if __name__ == "__main__":
    unittest.main()
