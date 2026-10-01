#!/usr/bin/env python3
"""Offline configuration tests; --compose additionally validates the resolved Compose model."""
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tarfile
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
    def test_compose_supports_backup_restart_options(self):
        result = subprocess.run(["docker", "compose", "up", "--help"], check=True,
                                capture_output=True, text=True)
        for option in ("--no-deps", "--no-recreate", "--no-build", "--pull", "--wait", "--wait-timeout"):
            self.assertIn(option, result.stdout)

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


class BackupTest(unittest.TestCase):
    RESTART = ["up", "-d", "--no-deps", "--no-recreate", "--no-build", "--pull", "never",
               "--wait", "--wait-timeout", "300", "app"]

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.output = self.directory / "backup"
        self.log = self.directory / "commands.jsonl"
        private_config = self.directory / ".env"
        CONFIG.write_configuration(private_config, values())
        executable = self.directory / "docker"
        fixture = ROOT / "scripts/test-fixtures/docker-backup.py"
        executable.write_text(f"#!{sys.executable}\n" + fixture.read_text())
        executable.chmod(0o700)
        self.environment = dict(os.environ, PATH=f"{self.directory}{os.pathsep}{os.environ['PATH']}",
                                VPS_ENV_FILE=str(private_config), BACKUP_TEST_LOG=str(self.log),
                                BACKUP_TEST_RUNNING="true", BACKUP_TEST_FAILURE="")

    def backup(self, failure="", running=True):
        self.environment.update(BACKUP_TEST_FAILURE=failure,
                                BACKUP_TEST_RUNNING="true" if running else "false")
        return subprocess.run(["bash", str(ROOT / "scripts/backup-vps.sh"), str(self.output)],
                              env=self.environment, capture_output=True, text=True, timeout=30)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_success_restarts_existing_app_and_validates_archives(self):
        result = self.backup()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Backup created", result.stdout)
        commands = self.commands()
        self.assertEqual(["stop", "app"], commands[1])
        self.assertEqual(self.RESTART, commands[-1])
        self.assertEqual(1, commands.count(self.RESTART))
        self.assertEqual(b"test-database-dump", (self.output / "database.dump").read_bytes())
        with tarfile.open(self.output / "photos.tar.gz") as archive:
            self.assertEqual(b"test-photo", archive.extractfile("menu-images/test-photo").read())
        self.assertEqual(0o700, stat.S_IMODE(self.output.stat().st_mode))
        self.assertEqual(0o600, stat.S_IMODE((self.output / "database.dump").stat().st_mode))
        self.assertEqual([], list(self.output.glob("*.partial")))

    def test_failed_dump_still_restarts_app_and_preserves_failure(self):
        result = self.backup(failure="dump")
        self.assertEqual(23, result.returncode, result.stderr)
        self.assertEqual(self.RESTART, self.commands()[-1])
        self.assertNotIn("Backup created", result.stdout)
        self.assertIn("Backup/restart failed", result.stderr)
        self.assertFalse((self.output / "database.dump").exists())

    def test_failed_archive_still_restarts_app(self):
        result = self.backup(failure="archive")
        self.assertEqual(24, result.returncode, result.stderr)
        self.assertEqual(self.RESTART, self.commands()[-1])
        self.assertFalse((self.output / "photos.tar.gz").exists())

    def test_restart_failure_is_reported_as_failure(self):
        result = self.backup(failure="restart")
        self.assertEqual(1, result.returncode)
        self.assertEqual(self.RESTART, self.commands()[-1])
        self.assertIn("App restart failed", result.stderr)
        self.assertNotIn("Backup created", result.stdout)
        self.assertTrue((self.output / "database.dump").exists())

    def test_invalid_dump_is_not_promoted_and_app_restarts(self):
        result = self.backup(failure="validate-dump")
        self.assertEqual(25, result.returncode, result.stderr)
        self.assertEqual(self.RESTART, self.commands()[-1])
        self.assertFalse((self.output / "database.dump").exists())

    def test_invalid_archive_is_not_promoted_and_app_restarts(self):
        result = self.backup(failure="invalid-archive")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.RESTART, self.commands()[-1])
        self.assertFalse((self.output / "photos.tar.gz").exists())

    def test_previously_stopped_app_is_not_started(self):
        result = self.backup(running=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn(["stop", "app"], self.commands())
        self.assertNotIn(self.RESTART, self.commands())

    def test_existing_backup_is_not_overwritten_or_app_stopped(self):
        self.output.mkdir()
        result = self.backup()
        self.assertEqual(1, result.returncode)
        self.assertIn("Refusing to overwrite", result.stderr)
        self.assertEqual([], self.commands())


if __name__ == "__main__":
    unittest.main()
