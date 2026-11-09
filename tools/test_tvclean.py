import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("tvclean.py")
SPEC = importlib.util.spec_from_file_location("tvclean", MODULE_PATH)
assert SPEC and SPEC.loader
tvclean = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tvclean)


class FakeAdb:
    def __init__(self):
        self.enabled = {"com.example.enabled", "com.example.home"}
        self.disabled = {"com.example.already-off"}
        self.calls = []

    def package_set(self, disabled):
        return set(self.disabled if disabled else self.enabled | self.disabled)

    def home(self):
        return "com.example.home/.MainActivity"

    def shell(self, *args, check=True):
        self.calls.append(args)
        return "ok"


class CleanupToolTest(unittest.TestCase):
    def test_parses_comments_and_deduplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "debloat.txt"
            path.write_text(
                "# model\ncom.example.enabled\ncom.example.enabled  # duplicate\n",
                encoding="utf-8",
            )
            self.assertEqual(["com.example.enabled"], tvclean.read_packages(path))

    def test_rejects_unsafe_package_name(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "debloat.txt"
            path.write_text("com.example.good; reboot\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                tvclean.read_packages(path)

    def test_records_exact_pre_change_state(self):
        adb = FakeAdb()
        with tempfile.TemporaryDirectory() as directory:
            list_path = Path(directory) / "debloat.txt"
            states, record = tvclean.build_record(
                adb,
                list_path,
                ["com.example.enabled", "com.example.already-off", "com.example.missing"],
            )
        self.assertEqual(
            {
                "com.example.enabled": "enabled",
                "com.example.already-off": "disabled",
                "com.example.missing": "absent",
            },
            states,
        )
        self.assertEqual("com.example.home/.MainActivity", record["home"])

    def test_protects_home_and_core_packages(self):
        self.assertIsNotNone(tvclean.protected_reason("app.cozytv", False, None))
        self.assertIsNotNone(
            tvclean.protected_reason(
                "com.example.home",
                False,
                "com.example.home/.MainActivity",
            ),
        )
        self.assertIsNone(
            tvclean.protected_reason(
                "com.example.video",
                False,
                "com.example.home/.MainActivity",
            ),
        )


if __name__ == "__main__":
    unittest.main()
