import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/build-ultra.yml").read_text(encoding="utf-8")
EXPORTER = (ROOT / "Exporter.java").read_text(encoding="utf-8")
GAME = (ROOT / "Game.java").read_text(encoding="utf-8")
VIDEO = (ROOT / "VideoLayer.java").read_text(encoding="utf-8")


class UltraBuildContractTests(unittest.TestCase):
    def test_ultra_is_a_separate_api21_build_without_changing_normal_workflow(self):
        normal = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
        self.assertIn("--min-sdk-version 21 --target-sdk-version 28", WORKFLOW)
        self.assertIn("--min-sdk-version 21 --target-sdk-version 33", WORKFLOW)
        self.assertGreaterEqual(WORKFLOW.count("--min-api 21"), 2)
        self.assertIn("motor-v14-ultra-api21-27", WORKFLOW)
        self.assertIn("--min-sdk-version 24 --target-sdk-version 28", normal)
        self.assertIn("--min-sdk-version 26 --target-sdk-version 33", normal)

    def test_build_checks_both_minimum_and_target_sdk(self):
        for marker in (
            "sdkVersion:'21'",
            "targetSdkVersion:'28'",
            "targetSdkVersion:'33'",
            "apksigner\" verify --verbose motor-v14-ultra.apk",
            "zipalign\" -f 4 base-ultra.apk",
        ):
            self.assertIn(marker, WORKFLOW)

    def test_v14_engine_features_and_legacy_migration_are_still_in_source(self):
        for feature in (
            '"Image"', '"Button"', '"ImageButton"', '"Label"', '"Color"',
            '"Sound"', '"AnimatedSprite"', '"Video"', '"Camera2D"', '"Group"',
            '"Anchor"', '"Grab"', '"Collision"', '"Limiter"',
        ):
            self.assertIn(feature, GAME)
        self.assertIn("convertLegacySave", GAME)
        self.assertIn("assetOrders", GAME)
        self.assertIn("seekTo(when, MediaPlayer.SEEK_CLOSEST)", VIDEO)
        self.assertRegex(VIDEO, r"SDK_INT\s*>=\s*26[\s\S]{0,180}seekTo\(when, MediaPlayer\.SEEK_CLOSEST\)")

    def test_exporter_streams_template_and_limits_image_decode_peak(self):
        self.assertIn('c.getAssets().open("template.apk")', EXPORTER)
        self.assertNotIn("new ByteArrayInputStream(tpl)", EXPORTER)
        self.assertIn("BitmapFactory.decodeFile(source.getPath(), bounds)", EXPORTER)
        self.assertIn("decode.inSampleSize", EXPORTER)
        self.assertIn("converted.length() < source.length()", EXPORTER)
        self.assertIn("finally {\n                    if (converted != null) converted.delete();", EXPORTER)

    def test_manifest_placeholder_width_contract(self):
        values = {}
        for name in ("PKG_PH", "PKG_OLD", "LABEL_PH", "VER_PH", "VER_OLD"):
            match = re.search(r"static final String " + name + r' = "([^"\\]*)";', EXPORTER)
            self.assertIsNotNone(match, name)
            values[name] = match.group(1)
        manifest = (ROOT / "PlayerManifest.xml").read_text(encoding="utf-8")
        for key in ("PKG_PH", "LABEL_PH", "VER_PH"):
            self.assertIn(values[key], manifest)
        self.assertIn(values["PKG_OLD"], EXPORTER)
        self.assertEqual(len(values["LABEL_PH"]), 32)
        self.assertGreater(len(values["PKG_PH"]), len(values["PKG_OLD"]))


if __name__ == "__main__":
    unittest.main()
