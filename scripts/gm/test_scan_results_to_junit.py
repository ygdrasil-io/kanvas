import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

from scripts.gm.scan_results_to_junit import write_timeout_junit


class ScanResultsToJunitTest(unittest.TestCase):
    def test_writes_only_timeout_rows_as_terminal_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            scan = root / "scan.txt"
            output = root / "timeouts.xml"
            scan.write_text(
                "PASS|1|safe|10\nTIMEOUT|350|jpg-color-cube|30000\n"
                "FAIL|451|drawregion|4|error\nTIMEOUT|451|drawregion|30000\n",
                encoding="utf-8",
            )

            write_timeout_junit(scan, output)

            suite = ElementTree.parse(output).getroot()
            self.assertEqual("testsuite", suite.tag)
            self.assertEqual("2", suite.get("tests"))
            self.assertEqual("2", suite.get("failures"))
            self.assertEqual(
                ["jpg-color-cube", "drawregion"],
                [testcase.get("name") for testcase in suite],
            )
            self.assertEqual("terminal-timeout", suite[0][0].get("type"))


if __name__ == "__main__":
    unittest.main()
