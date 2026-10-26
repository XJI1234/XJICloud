"""Linux tests: sudo python3 -m unittest discover -s deploy/tests -v (requires PyYAML).

Runs a copied wizard and --no-systemd installs only inside a disposable directory.
No real service, user, configuration or credentials are modified.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import yaml


class BackendDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="xjicloud-deploy-test-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.deploy = self.root / "deploy"
        self.config = self.deploy / "config"
        self.config.mkdir(parents=True)
        self.script = self.deploy / "deploy-backend.sh"
        source = Path(os.environ.get("DEPLOY_SCRIPT", Path(__file__).resolve().parents[1] / self.script.name))
        self.script.write_text(source.read_text(), encoding="utf-8")

    def run_script(self, *args, inputs="", success=True):
        result = subprocess.run(["bash", str(self.script), *args], input=inputs, text=True,
                                capture_output=True, timeout=15)
        if success:
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        else:
            self.assertNotEqual(0, result.returncode)
        return result

    def wizard_inputs(self, sms):
        return "\n".join([""] * 16 + ['admin"\\secret', *sms]) + "\n"

    def test_disabled_sms_and_admin_password_round_trip(self):
        self.run_script("configure", inputs=self.wizard_inputs(["n"]))
        path = self.config / "application-prod.yml"
        config = yaml.safe_load(path.read_text())
        self.assertFalse(config["xjicloud"]["sms"]["enabled"])
        self.assertEqual('admin"\\secret', config["xjicloud"]["admin"]["default-password"])
        self.assertNotIn("profiles", config["spring"])
        self.assertEqual(0o600, path.stat().st_mode & 0o777)

    def test_enabled_sms_validation_yaml_escaping_and_summary_redaction(self):
        secret = 'test-only"\\secret#:'
        # Reject invalid enabled selection, overlong scheme, duplicate vars, zero and overflow quota.
        sms = ["invalid", "y", "test-only-ak", secret, "赠送签名", "100001",
               "x" * 21, "test-register", "code", "code", "verify_code", "minutes",
               "0", "2147483648", "42"]
        result = self.run_script("configure", inputs=self.wizard_inputs(sms))
        sms_config = yaml.safe_load((self.config / "application-prod.yml").read_text())["xjicloud"]["sms"]
        self.assertTrue(sms_config["enabled"])
        self.assertEqual(secret, sms_config["access-key-secret"])
        self.assertEqual("test-register", sms_config["scheme-name"])
        self.assertEqual("赠送签名", sms_config["sign-name"])
        self.assertEqual("verify_code", sms_config["code-variable"])
        self.assertEqual(42, sms_config["daily-limit"])
        self.assertNotIn("code-secret", sms_config)
        summary = (self.config / "install-summary.txt").read_text()
        for value in (secret, "test-only-ak", 'admin"\\secret'):
            self.assertNotIn(value, summary + result.stdout + result.stderr)

    def test_eof_does_not_write_partial_config(self):
        self.run_script("configure", inputs="", success=False)
        self.assertFalse((self.config / "application-prod.yml").exists())

    def prepare_install(self):
        self.installed = self.root / "installed"
        self.installed.mkdir()
        (self.installed / "application-prod.yml").write_text("old: config\n")
        self.jar_dir = self.root / "services/backend/target"
        self.jar_dir.mkdir(parents=True)
        (self.jar_dir / "xjicloud-backend-1.0.0.jar").write_text("new artifact")
        self.bin_dir = self.root / "bin"
        self.bin_dir.mkdir()
        java = self.bin_dir / "java"
        java.write_text('#!/bin/sh\necho \'openjdk version "17.0.1"\' >&2\n')
        java.chmod(0o755)
        # Avoid creating system users; root owns only disposable files in these tests.
        useradd = self.bin_dir / "useradd"
        useradd.write_text("#!/bin/sh\nexit 0\n")
        useradd.chmod(0o755)
        paths = {"CONFIG_DIR": self.installed, "INSTALL_DIR": self.root / "jar",
                 "DATA_DIR": self.root / "data", "JAVA_BIN": java}
        conf = "\n".join(f'{key}="{value}"' for key, value in paths.items())
        conf += f'\nSERVICE_USER=root\nPATH="{self.bin_dir}:$PATH"\n'
        (self.config / "deploy.conf").write_text(conf)

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_upgrade_preserves_server_config_without_local_config(self):
        self.prepare_install()
        self.run_script("upgrade", "--skip-build", "--no-systemd", "--non-interactive")
        self.assertEqual("old: config\n", (self.installed / "application-prod.yml").read_text())

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_explicit_config_update_backs_up_and_installs(self):
        self.prepare_install()
        (self.config / "application-prod.yml").write_text("new: config\n")
        self.run_script("upgrade", "--update-config", "--skip-build", "--no-systemd", "--non-interactive")
        target = self.installed / "application-prod.yml"
        self.assertEqual("new: config\n", target.read_text())
        backups = list(self.installed.glob("application-prod.yml.bak.*"))
        self.assertEqual(1, len(backups))
        self.assertEqual("old: config\n", backups[0].read_text())
        self.assertEqual(0o640, target.stat().st_mode & 0o777)

    @unittest.skipUnless(os.geteuid() == 0, "temporary install ownership checks require root")
    def test_old_env_requires_migration_before_any_artifact_change(self):
        self.prepare_install()
        (self.installed / "backend.env").write_text("SMS_ENABLED=true\n")
        self.run_script("upgrade", "--skip-build", "--no-systemd", success=False)
        self.assertFalse((self.root / "jar").exists())


if __name__ == "__main__":
    unittest.main()
