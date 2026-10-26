"""Configuration editor regression tests; no external services or real credentials."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch

import yaml

SPEC = importlib.util.spec_from_file_location("backend_config", Path(__file__).resolve().parents[1] / "backend_config.py")
config = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(config)


class BackendConfigTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.template = Path(__file__).resolve().parents[1] / "config/application-prod.yml.example"
        self.valid = yaml.safe_load(self.template.read_text())
        self.valid["spring"]["datasource"]["password"] = 'db"\\password: #测试'
        self.valid["xjicloud"]["admin"]["default-password"] = "test-only-admin-password"
        self.valid["xjicloud"]["jwt"]["secret"] = "test-only-jwt-secret-at-least-32-characters"
        self.valid["xjicloud"]["worker"]["shared-secret"] = "test-only-worker-secret-at-least-24-characters"

    def test_first_config_round_trips_strings_numbers_booleans_and_permissions(self):
        asked = []
        def ask(path, value, added, hidden):
            asked.append((path, added, hidden))
            return {"spring.datasource.password": 'db"\\password: #测试',
                    "xjicloud.admin.default-password": "test-only-admin-password"}.get(path, "")
        candidate = config.review_config(config.load_yaml(self.template), {}, ask=ask, report=lambda _: None)
        target = self.root / "application-prod.yml"
        config.save_config(target, candidate)
        loaded = config.load_yaml(target)
        self.assertEqual(candidate, loaded)
        self.assertEqual('db"\\password: #测试', loaded["spring"]["datasource"]["password"])
        self.assertIs(type(loaded["server"]["port"]), int)
        self.assertIs(type(loaded["xjicloud"]["sms"]["enabled"]), bool)
        self.assertEqual(0o600, target.stat().st_mode & 0o777)
        self.assertGreaterEqual(len(loaded["xjicloud"]["jwt"]["secret"]), 32)
        self.assertTrue(all(added for _, added, _ in asked))

    def test_upgrade_reviews_every_field_preserves_secrets_unknown_values_and_new_keys(self):
        old = copy.deepcopy(self.valid)
        del old["xjicloud"]["sms"]
        old["custom"] = {"switch": True, "members": ["one", "two"]}
        seen = {}
        def ask(path, value, added, hidden):
            seen[path] = (value, added, hidden)
            return "9090" if path == "server.port" else ""
        candidate = config.review_config(config.load_yaml(self.template), old, ask=ask, report=lambda _: None)
        self.assertEqual(9090, candidate["server"]["port"])
        self.assertEqual(old["custom"], candidate["custom"])
        self.assertEqual(old["xjicloud"]["jwt"], candidate["xjicloud"]["jwt"])
        self.assertTrue(seen["xjicloud.sms.enabled"][1])
        self.assertFalse(seen["spring.datasource.password"][1])
        self.assertTrue(seen["spring.datasource.password"][2])
        self.assertIn("custom.members", seen)

    def test_unchanged_upgrade_does_not_rotate_keys(self):
        candidate = config.review_config(config.load_yaml(self.template), self.valid,
                                         ask=lambda *args: "", report=lambda _: None)
        self.assertEqual(self.valid, candidate)

    def test_invalid_new_values_reprompt_and_cross_field_rules_apply(self):
        responses = iter(["abc", "0", "65536", "9090"])
        errors = []
        candidate = config.review_config(config.load_yaml(self.template), self.valid,
            ask=lambda path, *args: next(responses) if path == "server.port" else "", report=errors.append)
        self.assertEqual(9090, candidate["server"]["port"])
        self.assertEqual(3, len(errors))
        for path, value in {
            "spring.datasource.url": "jdbc:h2:mem:wrong",
            "spring.data.redis.host": "https://localhost",
            "xjicloud.oss.endpoint": "ftp://localhost",
            "xjicloud.cors.allowed-origins": "https://example.com/api",
            "xjicloud.storage.root": "relative/path",
            "spring.jpa.hibernate.ddl-auto": "create-drop",
            "xjicloud.jwt.secret": "short",
            "xjicloud.admin.default-password": "中" * 25,
            "xjicloud.sms.daily-limit": True,
            "spring.servlet.multipart.max-request-size": "1MB",
        }.items():
            with self.subTest(path=path):
                bad = copy.deepcopy(self.valid)
                config.set_value(bad, path, value)
                with self.assertRaises(config.ConfigError):
                    config.validate_config(bad)

    def test_malformed_duplicate_and_non_mapping_yaml_are_rejected_without_echoing_secrets(self):
        for content in ('secret: [never-print-this', 'server:\n  port: 8080\n  port: 9090\n',
                        '- item\n', 'x: &x\n  child: *x\n'):
            path = self.root / "bad.yml"
            path.write_text(content)
            with self.assertRaises(config.ConfigError) as error:
                config.load_yaml(path)
            self.assertNotIn("never-print-this", str(error.exception))

    def test_legacy_prod_profile_is_migrated_without_losing_other_values(self):
        old = copy.deepcopy(self.valid)
        old["spring"]["profiles"] = {"active": "prod"}
        candidate = config.review_config(config.load_yaml(self.template), old,
                                         ask=lambda *args: "", report=lambda _: None)
        self.assertNotIn("profiles", candidate["spring"])
        self.assertEqual(self.valid, candidate)

    def test_failed_validation_leaves_original_file_intact(self):
        target = self.root / "application-prod.yml"
        config.save_config(target, self.valid)
        original = target.read_bytes()
        bad = copy.deepcopy(self.valid)
        bad["server"]["port"] = "8080\nxjicloud: hacked"
        with self.assertRaises(config.ConfigError):
            config.save_config(target, bad)
        self.assertEqual(original, target.read_bytes())

    def test_optional_connection_checks_authenticate_redis_and_hide_network_failures(self):
        data = copy.deepcopy(self.valid)
        data["spring"]["data"]["redis"]["password"] = "test-only-redis-secret"
        connection = MagicMock()
        connection.__enter__.return_value = connection
        connection.makefile.return_value.readline.side_effect = [b"+OK\r\n", b"+PONG\r\n"]
        with patch.object(config.socket, "create_connection", return_value=connection), \
                patch.object(config.shutil, "which", return_value=None), patch("builtins.print") as output:
            config.check_connections(data)
        sent = [call.args[0] for call in connection.sendall.call_args_list]
        self.assertIn(b"AUTH", sent[0])
        self.assertIn(b"PING", sent[1])
        self.assertNotIn("test-only-redis-secret", str(output.call_args_list))
        with patch.object(config.socket, "create_connection", side_effect=OSError("never-print-this")):
            with self.assertRaises(config.ConfigError) as error:
                config.check_connections(data)
        self.assertNotIn("never-print-this", str(error.exception))

    def test_optional_postgres_check_uses_secret_environment_and_fails_closed(self):
        connection = MagicMock()
        with patch.object(config.socket, "create_connection", return_value=connection), \
                patch.object(config.shutil, "which", return_value="/usr/bin/psql"), \
                patch.object(config.subprocess, "run", return_value=MagicMock(returncode=1, stdout=b"")) as process:
            with self.assertRaises(config.ConfigError):
                config.check_connections(self.valid)
        self.assertEqual(self.valid["spring"]["datasource"]["password"], process.call_args.kwargs["env"]["PGPASSWORD"])
        self.assertNotIn(self.valid["spring"]["datasource"]["password"], str(process.call_args.args[0]))

    def test_storage_normalization_rejects_root_aliases_and_system_directories(self):
        for value in ("/", "//", "/data/..", "/tmp/subdirectory/../..", "/etc", "/home"):
            with self.subTest(value=value):
                bad = copy.deepcopy(self.valid)
                bad["xjicloud"]["storage"]["root"] = value
                with self.assertRaises(config.ConfigError):
                    config.validate_config(bad)

    def test_yaml_boolean_numeric_and_date_looking_strings_stay_strings(self):
        target = self.root / "application-prod.yml"
        for password in ("true", "00123", "null", "on", "2026-10-26", 'quotes"\\: #中文\t'):
            data = copy.deepcopy(self.valid)
            data["spring"]["datasource"]["password"] = password
            config.save_config(target, data)
            loaded = config.load_yaml(target)
            self.assertIs(type(loaded["spring"]["datasource"]["password"]), str)
            self.assertEqual(password, loaded["spring"]["datasource"]["password"])
        self.assertTrue(all((p.stat().st_mode & 0o777) == 0o600 for p in self.root.glob("*.bak.*")))

    def test_optional_legacy_null_password_is_kept_without_rewriting(self):
        old = copy.deepcopy(self.valid)
        old["spring"]["data"]["redis"]["password"] = None
        candidate = config.review_config(config.load_yaml(self.template), old,
                                         ask=lambda *args: "", report=lambda _: None)
        self.assertEqual(old, candidate)

    def test_cyclic_storage_symlinks_are_reported_as_configuration_errors(self):
        first, second = self.root / "first", self.root / "second"
        first.symlink_to(second)
        second.symlink_to(first)
        bad = copy.deepcopy(self.valid)
        bad["xjicloud"]["storage"]["root"] = str(first)
        with self.assertRaises(config.ConfigError):
            config.validate_config(bad)


if __name__ == "__main__":
    unittest.main()
