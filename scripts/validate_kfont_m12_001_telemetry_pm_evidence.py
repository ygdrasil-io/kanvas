#!/usr/bin/env python3
import json
import sys
from pathlib import Path


TASK_NAME = "validateKfontM12001TelemetryPmEvidence"
DASHBOARD_PATH = "reports/pure-kotlin-text/font-claim-dashboard.json"
SCHEMA_PATH = "reports/pure-kotlin-text/font-telemetry-schema.json"
FIXTURE_PATH = "reports/pure-kotlin-text/font-telemetry-schema-fixture.json"
ADVISORY_JSON_PATH = "reports/pure-kotlin-text/font-telemetry-pm-bundle.json"
ADVISORY_MD_PATH = "reports/pure-kotlin-text/2026-06-17-kfont-m12-001-telemetry-pm-bundle.md"
BUILD_GRADLE_PATH = "build.gradle.kts"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KFONT-M12-001 telemetry PM evidence validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def project_root() -> Path:
    if len(sys.argv) > 1:
        return Path(sys.argv[1]).resolve()
    return Path(__file__).resolve().parents[1]


def load_json(root: Path, relative_path: str) -> object:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def load_text(root: Path, relative_path: str) -> str:
    path = root / relative_path
    require(path.is_file(), f"missing text file: {relative_path}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    root = project_root()
    dashboard = load_json(root, DASHBOARD_PATH)
    schema = load_json(root, SCHEMA_PATH)
    fixture = load_json(root, FIXTURE_PATH)
    advisory = load_json(root, ADVISORY_JSON_PATH)
    advisory_md = load_text(root, ADVISORY_MD_PATH)
    build_gradle = load_text(root, BUILD_GRADLE_PATH)

    require(isinstance(dashboard, dict), "dashboard root must be an object")
    require(isinstance(schema, dict), "schema root must be an object")
    require(isinstance(fixture, dict), "fixture root must be an object")
    require(isinstance(advisory, dict), "advisory root must be an object")

    surface_rows = dashboard.get("surfaceRows")
    require(isinstance(surface_rows, list), "dashboard surfaceRows must be a list")
    telemetry_row = next((row for row in surface_rows if isinstance(row, dict) and row.get("surfaceId") == "font-telemetry-schema"), None)
    require(isinstance(telemetry_row, dict), "dashboard must contain the font-telemetry-schema row")

    require(advisory.get("artifactId") == "font-telemetry-pm-bundle", "artifactId changed")
    require(advisory.get("ownerTickets") == ["KFONT-M12-001"], "ownerTickets must stay pinned to KFONT-M12-001")
    require(advisory.get("surfaceId") == "font-telemetry-schema", "surfaceId changed")
    require(advisory.get("classification") == "tracked-gap", "classification must remain tracked-gap")
    require(advisory.get("claimPromotionAllowed") is False, "claimPromotionAllowed must remain false")
    require(advisory.get("pmBundleTask") == "pipelinePmBundle", "pmBundleTask must stay pipelinePmBundle")
    require(advisory.get("warningMode") == "advisory", "warningMode must stay advisory")
    require(advisory.get("sourceDashboardPath") == DASHBOARD_PATH, "sourceDashboardPath changed")

    dashboard_dumps = telemetry_row.get("evidence", {}).get("deterministicDumps", [])
    require(ADVISORY_JSON_PATH in dashboard_dumps, "dashboard deterministicDumps must reference font-telemetry-pm-bundle.json")

    gates = telemetry_row.get("requiredEvidenceGates", [])
    require(isinstance(gates, list), "dashboard requiredEvidenceGates must be a list")
    require(
        not any("pipelinePerformanceTrendWarnings or PM bundle evidence" in str(gate) for gate in gates),
        "dashboard still reports the PM bundle ingestion gate as open",
    )
    require(
        any("KFONT-M12-005" in str(gate) and "KFONT-M12-004" not in str(gate) for gate in gates),
        "dashboard must point remaining producer emission at KFONT-M12-005 only",
    )

    schema_domains = schema.get("domains")
    fixture_samples = fixture.get("samples")
    advisory_rows = advisory.get("domainRows")
    require(isinstance(schema_domains, list) and schema_domains, "schema domains must be a non-empty list")
    require(isinstance(fixture_samples, list) and fixture_samples, "fixture samples must be a non-empty list")
    require(isinstance(advisory_rows, list) and advisory_rows, "advisory domainRows must be a non-empty list")

    schema_domain_names = [row.get("domain") for row in schema_domains if isinstance(row, dict)]
    sample_by_domain = {
        sample.get("domain"): sample
        for sample in fixture_samples
        if isinstance(sample, dict) and isinstance(sample.get("domain"), str)
    }
    advisory_domain_names = []
    for row in advisory_rows:
        require(isinstance(row, dict), "each domainRows entry must be an object")
        domain = row.get("domain")
        advisory_domain_names.append(domain)
        require(domain in schema_domain_names, f"domainRows includes unknown domain `{domain}`")
        sample = sample_by_domain.get(domain)
        require(isinstance(sample, dict), f"fixture sample missing for domain `{domain}`")
        require(row.get("fixtureId") == sample.get("fixtureId"), f"fixtureId mismatch for domain `{domain}`")
        require(row.get("sampleCount") == sample.get("sampleCount"), f"sampleCount mismatch for domain `{domain}`")
        require(row.get("measurementPhase") == sample.get("measurementPhase"), f"measurementPhase mismatch for domain `{domain}`")
        require(row.get("cacheState") == sample.get("cacheState"), f"cacheState mismatch for domain `{domain}`")
        metric_names = [metric.get("name") for metric in sample.get("metrics", []) if isinstance(metric, dict)]
        require(row.get("metricNames") == metric_names, f"metricNames mismatch for domain `{domain}`")

    require(advisory_domain_names == schema_domain_names, "domainRows must preserve schema domain order")

    bundle_paths = advisory.get("bundlePaths")
    require(isinstance(bundle_paths, list) and bundle_paths, "bundlePaths must be a non-empty list")
    require("reports/pure-kotlin-text/parser-metrics.json" in bundle_paths, "bundlePaths must include parser-metrics.json")
    require("reports/pure-kotlin-text/scaler-metrics.json" in bundle_paths, "bundlePaths must include scaler-metrics.json")
    require("reports/pure-kotlin-text/glyph-artifact-metrics.json" in bundle_paths, "bundlePaths must include glyph-artifact-metrics.json")
    require("reports/pure-kotlin-text/glyph-cache-metrics.json" in bundle_paths, "bundlePaths must include glyph-cache-metrics.json")
    require("reports/pure-kotlin-text/glyph-atlas-occupancy.json" in bundle_paths, "bundlePaths must include glyph-atlas-occupancy.json")
    for relative_path in bundle_paths:
        require(isinstance(relative_path, str) and relative_path, "bundlePaths entries must be non-empty strings")
        require((root / relative_path).is_file(), f"bundlePaths references a missing checked-in file: {relative_path}")

    remaining_gates = advisory.get("remainingGates")
    require(isinstance(remaining_gates, list) and remaining_gates, "remainingGates must be a non-empty list")
    require(
        any("KFONT-M12-005" in str(gate) and "KFONT-M12-004" not in str(gate) for gate in remaining_gates),
        "remainingGates must point remaining producer emission at KFONT-M12-005 only",
    )
    require(
        all("PM bundle evidence" not in str(gate) for gate in remaining_gates),
        "remainingGates must not keep PM bundle evidence open after this slice",
    )

    require("pipelinePmBundle" in advisory_md, "markdown report must mention pipelinePmBundle")
    require("warning-only" in advisory_md, "markdown report must mention warning-only status")
    require("tracked-gap" in advisory_md, "markdown report must mention tracked-gap classification")
    require("glyph-artifact-metrics.json" in advisory_md, "markdown report must mention glyph-artifact-metrics.json")
    require("glyph-cache-metrics.json" in advisory_md, "markdown report must mention glyph-cache-metrics.json")
    require("glyph-atlas-occupancy.json" in advisory_md, "markdown report must mention glyph-atlas-occupancy.json")
    require("KFONT-M12-005" in advisory_md and "KFONT-M12-004" not in advisory_md.split("Remaining gate", 1)[-1], "markdown report must mention only KFONT-M12-005 as the remaining producer gate")
    require("remains open before `done`" not in advisory_md, "markdown report must not keep KFONT-M12-001 open before done")

    require(f'tasks.register<Exec>("{TASK_NAME}")' in build_gradle, "build.gradle.kts must register validateKfontM12001TelemetryPmEvidence")
    pm_bundle_start = build_gradle.find('tasks.register("pipelinePmBundle")')
    require(pm_bundle_start >= 0, "pipelinePmBundle task is missing")
    pm_bundle_block = build_gradle[pm_bundle_start: pm_bundle_start + 16000]
    require(f'"{TASK_NAME}"' in pm_bundle_block, "pipelinePmBundle must depend on validateKfontM12001TelemetryPmEvidence")
    require("reports/pure-kotlin-text/parser-metrics.json" in pm_bundle_block, "pipelinePmBundle must include parser-metrics.json")
    require("reports/pure-kotlin-text/scaler-metrics.json" in pm_bundle_block, "pipelinePmBundle must include scaler-metrics.json")
    require("reports/pure-kotlin-text/glyph-artifact-metrics.json" in pm_bundle_block, "pipelinePmBundle must include glyph-artifact-metrics.json")
    require("reports/pure-kotlin-text/glyph-cache-metrics.json" in pm_bundle_block, "pipelinePmBundle must include glyph-cache-metrics.json")
    require("reports/pure-kotlin-text/glyph-atlas-occupancy.json" in pm_bundle_block, "pipelinePmBundle must include glyph-atlas-occupancy.json")
    require(ADVISORY_JSON_PATH in pm_bundle_block, "pipelinePmBundle must include font-telemetry-pm-bundle.json")
    require(ADVISORY_MD_PATH in pm_bundle_block, "pipelinePmBundle must include the telemetry PM markdown report")

    print("KFONT-M12-001 telemetry PM evidence validated")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as exc:
        print(exc, file=sys.stderr)
        raise SystemExit(1)
