"""Static regression tests for the diagnostic-only reduced manifest.

These tests do not claim that a merged APK has been inspected.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
REMOVED_PERMISSIONS = {
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.USE_EXACT_ALARM",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.RECEIVE_BOOT_COMPLETED",
}
REMOVED_COMPONENTS = {
    ".services.JarvisAccessibilityService",
    ".services.JarvisNotificationListenerService",
    ".receivers.BootReceiver",
}


class DiagnosticManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(ROOT / "app/src/diagnostic/AndroidManifest.xml").getroot()

    def test_sensitive_permissions_are_explicitly_removed(self):
        permissions = {
            e.get(ANDROID + "name"): e.get(TOOLS + "node")
            for e in self.root.findall("uses-permission")
        }
        for permission in REMOVED_PERMISSIONS:
            with self.subTest(permission=permission):
                self.assertEqual(permissions.get(permission), "remove")

    def test_sensitive_services_are_explicitly_removed(self):
        app = self.root.find("application")
        self.assertIsNotNone(app)
        components = {
            e.get(ANDROID + "name"): e.get(TOOLS + "node")
            for e in app if e.tag in {"service", "receiver"}
        }
        for component in REMOVED_COMPONENTS:
            with self.subTest(component=component):
                self.assertEqual(components.get(component), "remove")

    def test_normal_manifest_keeps_sensitive_services_for_explicit_consent(self):
        main = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = main.find("application")
        self.assertIsNotNone(app)
        names = {e.get(ANDROID + "name") for e in app if e.tag in {"service", "receiver"}}
        self.assertTrue(REMOVED_COMPONENTS.issubset(names))


if __name__ == "__main__":
    unittest.main()
