#!/usr/bin/env python3
"""Local contract test for the ECI one-shot training entry point."""

import json
import os
import subprocess
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


GPU_WORKER_DIR = Path(__file__).resolve().parent
ENTRY_SCRIPT = GPU_WORKER_DIR / "run_once.py"
IMAGE_BYTES = b"local image fixture"


class LocalStorageServer(ThreadingHTTPServer):
    def __init__(self, server_address, request_handler):
        super().__init__(server_address, request_handler)
        self.requests = []


class StorageHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.server.requests.append(("GET", self.path, self.headers.get("Content-Type"), b""))
        if self.path != "/input/001.jpg":
            self.send_error(404)
            return

        self.send_response(200)
        self.send_header("Content-Length", str(len(IMAGE_BYTES)))
        self.end_headers()
        self.wfile.write(IMAGE_BYTES)

    def do_PUT(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.server.requests.append(("PUT", self.path, self.headers.get("Content-Type"), body))
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.server.requests.append(("POST", self.path, self.headers.get("Content-Type"), body))
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, _format, *_args):
        pass


class OneShotTrainingTest(unittest.TestCase):
    def setUp(self):
        self.server = LocalStorageServer(("127.0.0.1", 0), StorageHandler)
        self.server_thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.server_thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.server_thread.join(timeout=2)

    def test_downloads_trains_uploads_and_exits_without_backend_calls(self):
        self.assertTrue(ENTRY_SCRIPT.is_file(), "run_once.py entry script must exist")

        env = os.environ.copy()
        env.update(
            {
                "XJI_INPUT_FILES_JSON": json.dumps(
                    [
                        {
                            "fileName": "001.jpg",
                            "ossKey": "datasets/job-1/images/001.jpg",
                            "downloadUrl": f"{self.base_url}/input/001.jpg",
                        }
                    ]
                ),
                "XJI_OUTPUT_UPLOAD_URL": f"{self.base_url}/output/model.ply",
                "XJICLOUD_BACKEND_URL": f"{self.base_url}/backend",
            }
        )
        process = subprocess.run(
            [sys.executable, str(ENTRY_SCRIPT)],
            cwd=GPU_WORKER_DIR,
            env=env,
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )

        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual(
            ["GET", "PUT"],
            [request[0] for request in self.server.requests],
            "the one-shot entry must only download inputs and upload the result",
        )
        self.assertEqual("/input/001.jpg", self.server.requests[0][1])
        method, path, content_type, body = self.server.requests[1]
        self.assertEqual("PUT", method)
        self.assertEqual("/output/model.ply", path)
        self.assertEqual("application/octet-stream", content_type)
        self.assertTrue(body.startswith(b"ply\nformat ascii 1.0\n"), body[:80])
        self.assertIn(b"0 0 0 255 0 0", body)


if __name__ == "__main__":
    unittest.main()
