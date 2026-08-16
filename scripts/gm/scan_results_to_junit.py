#!/usr/bin/env python3
"""Convert scanner timeout records into explicit terminal JUnit rows."""

import argparse
import pathlib
import xml.etree.ElementTree as ET


def _timeout_records(scan_path):
    records = []
    for line in pathlib.Path(scan_path).read_text(encoding="utf-8").splitlines():
        fields = line.split("|", 3)
        if len(fields) != 4 or fields[0] != "TIMEOUT":
            continue
        index, name, elapsed_ms = fields[1:]
        records.append((int(index), name, int(elapsed_ms)))
    return records


def write_timeout_junit(scan_path, output_path):
    records = _timeout_records(scan_path)
    suite = ET.Element(
        "testsuite",
        {
            "name": "org.graphiks.kanvas.skia.SkiaGmScannerTimeouts",
            "tests": str(len(records)),
            "skipped": "0",
            "failures": str(len(records)),
            "errors": "0",
        },
    )
    for index, name, elapsed_ms in records:
        testcase = ET.SubElement(
            suite,
            "testcase",
            {
                "name": name,
                "classname": "org.graphiks.kanvas.skia.SkiaGmRunner",
                "time": f"{elapsed_ms / 1000:.3f}",
                "sourceRegistration": name,
                "registryIndex": str(index),
            },
        )
        failure = ET.SubElement(
            testcase,
            "failure",
            {
                "message": f"terminal timeout while scanning GM '{name}' after {elapsed_ms}ms",
                "type": "terminal-timeout",
            },
        )
        failure.text = f"Scanner TIMEOUT|{index}|{name}|{elapsed_ms}"

    output = pathlib.Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    tree = ET.ElementTree(suite)
    ET.indent(tree, space="  ")
    tree.write(output, encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scan", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    write_timeout_junit(args.scan, args.output)


if __name__ == "__main__":
    main()
