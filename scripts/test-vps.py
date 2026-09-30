#!/usr/bin/env python3
"""Offline configuration tests; --compose additionally validates the resolved Compose model."""
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("configure_vps", ROOT / "scripts/configure-vps.py")
CONFIG = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONFIG)
COMPOSE = "--compose" in sys.argv
if COMPOSE:
    sys.argv.remove("--compose")


def values():
    return CONFIG.configuration("orders.example.com", "ghcr.io/example/app@sha256:" + "a" * 64,
                                "owner@example.com", "orders@mail.example.com", "re_test_only")


class ConfigurationTest(unittest.TestCase):
    def test_generated_secrets_are_independent(self):
        first, second = values(), values()
        keys = ("DATABASE_PASSWORD", "POSTGRES_ADMIN_PASSWORD", "APP_AUTH_JWT_SECRET")
        self.assertEqual(len({first[key] for key in keys}), 3)
        for key in keys:
            self.assertRegex(first[key], r"^[a-f0-9]{64}$")
            self.assertNotEqual(first[key], second[key])
        self.assertRegex(first["APP_PLATFORM_TOTP_SECRET"], r"^[A-Z2-7]{32}$")
        self.assertTrue(14 <= len(first["APP_PLATFORM_PASSWORD"]) <= 72)
        self.assertEqual("2587", first["SMTP_PORT"])

    def test_refuses_invalid_public_inputs(self):
        valid = ["orders.example.com", "ghcr.io/example/app@sha256:" + "a" * 64,
                 "owner@example.com", "orders@example.com", "re_test_only"]
        for index, invalid in ((0, "https://orders.example.com"), (0, "localhost"),
                               (0, "orders.example.com/path"), (0, "1.2.3.4"),
                               (1, "ghcr.io/example/app:latest"), (2, "bad email"),
                               (3, "x@example.com\nINJECT=true"), (4, "placeholder")):
            with self.subTest(value=invalid):
                args = valid.copy()
                args[index] = invalid
                with self.assertRaises(ValueError):
                    CONFIG.configuration(*args)

    def test_private_file_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            CONFIG.write_configuration(path, values())
            original = path.read_bytes()
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            with self.assertRaises(FileExistsError):
                CONFIG.write_configuration(path, values())
            self.assertEqual(original, path.read_bytes())

    def test_refuses_symlink_and_multiline_injection(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "target"
            link = Path(directory) / ".env"
            link.symlink_to(target)
            with self.assertRaises(FileExistsError):
                CONFIG.write_configuration(link, values())
            self.assertFalse(target.exists())
            with self.assertRaises(ValueError):
                CONFIG.write_configuration(target, {"KEY": "value\nINJECT=true"})
            self.assertFalse(target.exists())

    def test_destructive_smoke_test_refuses_non_ci_host(self):
        environment = {key: value for key, value in os.environ.items() if key != "GITHUB_ACTIONS"}
        result = subprocess.run(["bash", str(ROOT / "scripts/smoke-vps.sh")], env=environment,
                                capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn("never on the live server", result.stderr)

    def test_wrapper_requires_private_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            environment = dict(os.environ, VPS_ENV_FILE=str(Path(directory) / "absent"))
            result = subprocess.run(["bash", str(ROOT / "scripts/vps.sh"), "ps"], env=environment,
                                    capture_output=True, text=True)
            self.assertEqual(1, result.returncode)
            self.assertIn("Missing private configuration", result.stderr)

    @unittest.skipUnless(COMPOSE, "Pass --compose when the Docker Compose CLI is available")
    def test_compose_model(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            configuration = values()
            CONFIG.write_configuration(path, configuration)
            environment = {key: value for key, value in os.environ.items()
                           if key not in configuration and not key.startswith("COMPOSE_")}
            result = subprocess.run(["docker", "compose", "--env-file", str(path), "-f",
                                     str(ROOT / "deploy/vps/compose.yml"), "config", "--format", "json"],
                                    env=environment, check=True, capture_output=True, text=True)
            model = json.loads(result.stdout)
            app, db, proxy = (model["services"][key] for key in ("app", "database", "proxy"))
            self.assertNotIn("build", app)
            self.assertNotIn("ports", app)
            self.assertNotIn("ports", db)
            self.assertTrue(model["networks"]["database"]["internal"])
            self.assertNotIn("web", db["networks"])
            self.assertNotIn("database", proxy["networks"])
            self.assertEqual({"80", "443"}, {str(port["published"]) for port in proxy["ports"]})
            self.assertEqual("prod", app["environment"]["SPRING_PROFILES_ACTIVE"])
            self.assertEqual("restaurant_app", app["environment"]["DATABASE_USERNAME"])
            self.assertNotIn("POSTGRES_ADMIN_PASSWORD", app["environment"])
            self.assertIn("-Xmx512m", app["environment"]["JAVA_TOOL_OPTIONS"])
            self.assertEqual("5", str(app["environment"]["DATABASE_POOL_SIZE"]))
            self.assertEqual(1536 * 1024 * 1024, sum(int(s["mem_limit"]) for s in (app, db, proxy)))
            self.assertTrue(all(s["logging"]["driver"] == "local" for s in (app, db, proxy)))
            self.assertEqual("https://orders.example.com", app["environment"]["APP_CUSTOMER_BASE_URL"])


if __name__ == "__main__":
    unittest.main()
