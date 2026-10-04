import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import integration_training_dispatch as driver


class TrainingDispatchScriptTests(unittest.TestCase):
    def test_files_in_returns_archived_image_group(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "b.png").write_bytes(b"b")
            (root / "a.jpg").write_bytes(b"a")
            (root / "notes.txt").write_bytes(b"ignore")

            files = driver.files_in(root)

        self.assertEqual([item["archivedName"] for item in files], ["0001.jpg", "0002.png"])
        self.assertEqual([item["originalName"] for item in files], ["a.jpg", "b.png"])

    def test_submit_uploads_all_images_manifest_and_completes_backend_job(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "a.jpg").write_bytes(b"a")
            (root / "b.jpg").write_bytes(b"bb")
            responses = iter([
                {"data": {"id": "project-1"}},
                {"data": {
                    "jobId": "job-1",
                    "manifestUploadUrl": "http://oss/manifest",
                    "uploads": [
                        {"archivedName": "0001.jpg", "uploadUrl": "http://oss/1"},
                        {"archivedName": "0002.jpg", "uploadUrl": "http://oss/2"},
                    ],
                }},
                {"data": {"status": "QUEUED"}},
            ])
            calls = []

            def fake_request(base, method, path, **kwargs):
                calls.append((base, method, path, kwargs))
                if kwargs.get("url"):
                    return None
                return next(responses)

            with patch.object(driver, "request", side_effect=fake_request):
                project_id, job_id = driver.submit("http://backend", "token", root, "新文科楼")

        self.assertEqual((project_id, job_id), ("project-1", "job-1"))
        self.assertEqual(
            [call[2] for call in calls if call[1] == "PUT"],
            ["", "", ""],
        )
        payloads = [call[3]["payload"] for call in calls if call[1] == "POST" and call[3].get("payload")]
        self.assertEqual(len(payloads[1]["files"]), 2)
        self.assertEqual(
            [call[2] for call in calls if call[1] == "POST"][-1],
            "/api/v1/projects/project-1/datasets/job-1/complete",
        )


if __name__ == "__main__":
    unittest.main()
