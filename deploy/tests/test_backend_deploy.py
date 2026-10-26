"""Linux shell/editor integration tests; all installs stay inside disposable directories."""
import copy
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import unittest

import yaml
from test_backend_config import config


class BackendDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="xjicloud-deploy-test-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.deploy = self.root / "deploy"
        self.config = self.deploy / "config"
        self.config.mkdir(parents=True)
        source = Path(__file__).resolve().parents[1]
        self.script = self.deploy / "deploy-backend.sh"
        for name in ("deploy-backend.sh", "backend_config.py"):
            (self.deploy / name).write_text((source / name).read_text(), encoding="utf-8")
        shutil.copyfile(source / "config/application-prod.yml.example", self.config / "application-prod.yml.example")
        self.defaults = config.load_yaml(self.config / "application-prod.yml.example")
        self.valid = copy.deepcopy(self.defaults)
        config.set_value(self.valid, "spring.datasource.password", 'db"\\password: #测试')
        config.set_value(self.valid, "xjicloud.admin.default-password", "test-only-admin-password")
        config.set_value(self.valid, "xjicloud.jwt.secret", "test-only-jwt-secret-at-least-32-characters")
        config.set_value(self.valid, "xjicloud.worker.shared-secret", "test-only-worker-secret-at-least-24-characters")
        config.set_value(self.valid, "xjicloud.storage.root", str(self.root / "data"))

    def run_script(self, *args, inputs="", success=True):
        result = subprocess.run(["bash", str(self.script), *args], input=inputs, text=True,
                                capture_output=True, timeout=20)
        if success:
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        else:
            self.assertNotEqual(0, result.returncode)
        return result

    def answers(self, existing, overrides=None, confirm=""):
        overrides = overrides or {}
        candidate = config.merge_defaults(self.defaults, existing)
        values = []
        for path, _ in config.fields(candidate):
            answer = overrides.get(path, "")
            values.extend(answer if isinstance(answer, list) else [answer])
        return "\n".join(values + [confirm]) + "\n"

    def write(self, path, value):
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False))

    def test_first_deployment_yaml_round_trip_and_secret_redaction(self):
        result = self.run_script("configure", inputs=self.answers({}, {
            "spring.datasource.password": 'db"\\password: #测试',
            "xjicloud.admin.default-password": "test-only-admin-password",
            "xjicloud.storage.root": str(self.root / "data"),
            "server.port": ["oops", "0", "65536", "9090"]}))
        path = self.config / "application-prod.yml"
        loaded = config.load_yaml(path)
        config.validate_config(loaded)
        self.assertEqual('db"\\password: #测试', loaded["spring"]["datasource"]["password"])
        self.assertEqual(9090, loaded["server"]["port"])
        self.assertIs(type(loaded["xjicloud"]["sms"]["enabled"]), bool)
        self.assertNotIn("profiles", loaded["spring"])
        self.assertEqual(0o600, path.stat().st_mode & 0o777)
        for value in ('db"\\password: #测试', "test-only-admin-password", loaded["xjicloud"]["jwt"]["secret"]):
            self.assertNotIn(value, result.stdout + result.stderr)

    def test_configure_cancel_or_eof_preserves_original_file(self):
        path = self.config / "application-prod.yml"
        self.write(path, self.valid)
        before = path.read_bytes()
        self.run_script("configure", inputs=self.answers(self.valid, {"server.port": "9090"}, confirm="n"), success=False)
        self.assertEqual(before, path.read_bytes())
        self.run_script("configure", inputs="", success=False)
        self.assertEqual(before, path.read_bytes())
        self.assertFalse(list(self.config.glob("application-prod.yml.bak.*")))

    def test_enabled_sms_prompts_missing_credentials_validates_variables_and_hides_secrets(self):
        self.run_script("configure", inputs=self.answers({}, {
            "spring.datasource.password": "test-only-database-password",
            "xjicloud.admin.default-password": "test-only-admin-password",
            "xjicloud.sms.enabled": "true",
            "xjicloud.sms.access-key-id": "test-only-ak",
            "xjicloud.sms.access-key-secret": 'test-only"\\secret#:',
            "xjicloud.sms.sign-name": "赠送签名",
            "xjicloud.sms.template-code": "100001",
            "xjicloud.sms.scheme-name": ["x" * 21, "test-register"],
            "xjicloud.sms.minutes-variable": ["code", "min"],
            "xjicloud.sms.daily-limit": ["0", "2147483648", "42"],
        }))
        sms = config.load_yaml(self.config / "application-prod.yml")["xjicloud"]["sms"]
        self.assertTrue(sms["enabled"])
        self.assertEqual("100001", sms["template-code"])
        self.assertIs(type(sms["template-code"]), str)
        self.assertEqual("test-register", sms["scheme-name"])
        self.assertEqual(42, sms["daily-limit"])

    def prepare_install(self):
        self.installed = self.root / "installed"
        self.installed.mkdir()
        self.write(self.installed / "application-prod.yml", self.valid)
        (self.installed / "application-prod.yml").chmod(0o640)
        jar_dir = self.root / "services/backend/target"
        jar_dir.mkdir(parents=True)
        (jar_dir / "xjicloud-backend-1.0.0.jar").write_text("new artifact")
        self.install_dir = self.root / "jar"
        self.install_dir.mkdir()
        self.old_jar = self.install_dir / "xjicloud-backend-1.0.0.jar"
        self.old_jar.write_text("old artifact")
        self.bin_dir = self.root / "bin"
        self.bin_dir.mkdir()
        java = self.bin_dir / "java"
        java.write_text('#!/bin/sh\necho \'openjdk version "17.0.1"\' >&2\n')
        java.chmod(0o755)
        useradd = self.bin_dir / "useradd"
        useradd.write_text("#!/bin/sh\nexit 0\n")
        useradd.chmod(0o755)
        paths = {"CONFIG_DIR": self.installed, "INSTALL_DIR": self.install_dir, "JAVA_BIN": java}
        conf = "\n".join(f'{key}="{value}"' for key, value in paths.items())
        conf += f'\nSERVICE_USER=root\nPATH="{self.bin_dir}:$PATH"\n'
        (self.config / "deploy.conf").write_text(conf)

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_noninteractive_upgrade_keeps_installed_yaml_without_local_file(self):
        self.prepare_install()
        target = self.installed / "application-prod.yml"
        before = target.read_bytes()
        self.run_script("upgrade", "--skip-build", "--no-systemd", "--non-interactive")
        self.assertEqual(before, target.read_bytes())
        self.assertEqual("new artifact", self.old_jar.read_text())

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_interactive_upgrade_reviews_installed_values_prompts_new_sms_and_backs_up(self):
        self.prepare_install()
        old = copy.deepcopy(self.valid)
        del old["xjicloud"]["sms"]
        old["custom"] = {"label": "keep-this-value"}
        target = self.installed / "application-prod.yml"
        self.write(target, old)
        before = target.read_bytes()
        # Stale repository YAML must never replace the installed secrets during interactive upgrade.
        stale = copy.deepcopy(self.valid)
        stale["xjicloud"]["jwt"]["secret"] = "stale-jwt-secret-at-least-32-characters"
        self.write(self.config / "application-prod.yml", stale)
        result = self.run_script("upgrade", "--skip-build", "--no-systemd", inputs=self.answers(old, {"server.port": "9090"}))
        loaded = config.load_yaml(target)
        self.assertEqual(9090, loaded["server"]["port"])
        self.assertEqual(old["xjicloud"]["jwt"], loaded["xjicloud"]["jwt"])
        self.assertEqual(old["custom"], loaded["custom"])
        self.assertIn("[新增] xjicloud.sms.enabled", result.stdout)
        self.assertIn("[原配置] server.port = 8080", result.stdout)
        self.assertEqual(before, next(self.installed.glob("application-prod.yml.bak.*")).read_bytes())
        self.assertEqual(0o640, target.stat().st_mode & 0o777)
        self.assertNotIn(old["xjicloud"]["jwt"]["secret"], result.stdout + result.stderr)

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_upgrade_keep_all_preserves_bytes_and_keys_without_config_backup(self):
        self.prepare_install()
        target = self.installed / "application-prod.yml"
        before = target.read_bytes()
        result = self.run_script("upgrade", "--skip-build", "--no-systemd", inputs=self.answers(self.valid))
        self.assertEqual(before, target.read_bytes())
        self.assertIn("未修改，沿用原配置", result.stdout)
        self.assertFalse(list(self.installed.glob("application-prod.yml.bak.*")))

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_cancelled_upgrade_changes_neither_yaml_nor_jar(self):
        self.prepare_install()
        target = self.installed / "application-prod.yml"
        before = target.read_bytes()
        self.run_script("upgrade", "--skip-build", "--no-systemd", inputs=self.answers(self.valid, confirm="n"), success=False)
        self.assertEqual(before, target.read_bytes())
        self.assertEqual("old artifact", self.old_jar.read_text())

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_invalid_noninteractive_config_rejected_before_artifact_change(self):
        self.prepare_install()
        bad = copy.deepcopy(self.valid)
        bad["server"]["port"] = 70000
        self.write(self.config / "application-prod.yml", bad)
        self.run_script("upgrade", "--update-config", "--skip-build", "--no-systemd", "--non-interactive", success=False)
        self.assertEqual("old artifact", self.old_jar.read_text())
        self.assertEqual(self.valid, config.load_yaml(self.installed / "application-prod.yml"))

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_old_env_requires_migration_before_any_artifact_change(self):
        self.prepare_install()
        (self.installed / "backend.env").write_text("SMS_ENABLED=true\n")
        result = self.run_script("upgrade", "--skip-build", "--no-systemd", "--non-interactive", success=False)
        self.assertIn("旧 backend.env", result.stderr)
        self.assertEqual("old artifact", self.old_jar.read_text())

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_one_click_first_install_generates_and_installs_valid_yaml(self):
        self.prepare_install()
        (self.installed / "application-prod.yml").unlink()
        self.old_jar.unlink()
        self.run_script("--skip-build", "--no-systemd", inputs=self.answers({}, {
            "spring.datasource.password": "test-only-database-password",
            "xjicloud.admin.default-password": "test-only-admin-password",
            "xjicloud.storage.root": str(self.root / "data")}))
        local = config.load_yaml(self.config / "application-prod.yml")
        installed = config.load_yaml(self.installed / "application-prod.yml")
        self.assertEqual(local, installed)
        self.assertEqual("new artifact", self.old_jar.read_text())
        self.assertEqual(0o640, (self.installed / "application-prod.yml").stat().st_mode & 0o777)

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_explicit_noninteractive_update_applies_valid_local_config_and_backs_up(self):
        self.prepare_install()
        changed = copy.deepcopy(self.valid)
        changed["server"]["port"] = 9090
        self.write(self.config / "application-prod.yml", changed)
        self.run_script("upgrade", "--update-config", "--skip-build", "--no-systemd", "--non-interactive")
        self.assertEqual(changed, config.load_yaml(self.installed / "application-prod.yml"))
        self.assertEqual(self.valid, config.load_yaml(next(self.installed.glob("application-prod.yml.bak.*"))))

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_connection_failure_rejects_upgrade_before_any_artifact_change(self):
        self.prepare_install()
        with socket.socket() as unused:
            unused.bind(("127.0.0.1", 0))
            port = unused.getsockname()[1]
        candidate = copy.deepcopy(self.valid)
        candidate["spring"]["datasource"]["url"] = f"jdbc:postgresql://127.0.0.1:{port}/unused"
        self.write(self.config / "application-prod.yml", candidate)
        result = self.run_script("upgrade", "--update-config", "--skip-build", "--no-systemd",
                                 "--non-interactive", "--check-connections", success=False)
        self.assertIn("PostgreSQL", result.stderr)
        self.assertEqual("old artifact", self.old_jar.read_text())
        self.assertEqual(self.valid, config.load_yaml(self.installed / "application-prod.yml"))


if __name__ == "__main__":
    unittest.main()
