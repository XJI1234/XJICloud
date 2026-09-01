#!/usr/bin/env python3
"""XJICloud GPU worker agent."""

import os
import socket
import threading
import time
from pathlib import Path

import requests

from mock_trainer import run_mock_training
from oss_client import download_dataset_images, upload_file

BACKEND_URL = os.environ.get("XJICLOUD_BACKEND_URL", "http://127.0.0.1:8080").rstrip("/")
WORKER_SECRET = os.environ.get("WORKER_SECRET", "change-me-worker-secret-in-production")
WORKER_NAME = os.environ.get("WORKER_NAME", socket.gethostname())
POLL_INTERVAL = float(os.environ.get("WORKER_POLL_INTERVAL", "5"))
HEARTBEAT_INTERVAL = float(os.environ.get("WORKER_HEARTBEAT_INTERVAL", "15"))


class WorkerAgent:
    def __init__(self) -> None:
        self.token: str | None = None
        self.worker_id: str | None = None
        self.session = requests.Session()
        self.running = True

    def wait_for_backend(self) -> None:
        health_url = f"{BACKEND_URL}/actuator/health"
        for attempt in range(60):
            try:
                response = requests.get(health_url, timeout=5)
                if response.status_code == 200:
                    print(f"[worker] backend ready: {BACKEND_URL}")
                    return
            except requests.RequestException:
                pass
            print(f"[worker] waiting for backend ({attempt + 1}/60)...")
            time.sleep(2)
        raise RuntimeError(f"backend unavailable: {BACKEND_URL}")

    def register(self) -> None:
        response = self.session.post(
            f"{BACKEND_URL}/api/v1/worker/register",
            headers={"X-Worker-Secret": WORKER_SECRET},
            json={"name": WORKER_NAME, "gpuInfo": self.detect_gpu_info()},
            timeout=30,
        )
        response.raise_for_status()
        payload = response.json()["data"]
        self.worker_id = payload["workerId"]
        self.token = payload["token"]
        self.session.headers.update({"Authorization": f"Bearer {self.token}"})
        print(f"[worker] registered as {self.worker_id}")

    def detect_gpu_info(self) -> str:
        try:
            import subprocess

            result = subprocess.run(
                ["nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader"],
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )
            if result.returncode == 0 and result.stdout.strip():
                return result.stdout.strip()
        except Exception:
            pass
        return "CPU-only mock worker"

    def heartbeat_loop(self) -> None:
        while self.running:
            try:
                self.session.post(
                    f"{BACKEND_URL}/api/v1/worker/heartbeat",
                    json={"gpuInfo": self.detect_gpu_info()},
                    timeout=15,
                )
            except requests.RequestException as exc:
                print(f"[worker] heartbeat failed: {exc}")
            time.sleep(HEARTBEAT_INTERVAL)

    def report_progress(self, job_id: str, percent: int, stage: str, message: str) -> None:
        self.session.post(
            f"{BACKEND_URL}/api/v1/worker/jobs/{job_id}/progress",
            json={"percent": percent, "stage": stage, "message": message},
            timeout=30,
        ).raise_for_status()

    def complete_job(self, job_id: str, output_oss_key: str) -> None:
        self.session.post(
            f"{BACKEND_URL}/api/v1/worker/jobs/{job_id}/complete",
            json={"outputOssKey": output_oss_key},
            timeout=30,
        ).raise_for_status()

    def fail_job(self, job_id: str, error_message: str) -> None:
        self.session.post(
            f"{BACKEND_URL}/api/v1/worker/jobs/{job_id}/fail",
            json={"errorMessage": error_message},
            timeout=30,
        ).raise_for_status()

    def process_job(self, job: dict) -> None:
        job_id = job["jobId"]
        dataset_dir = Path("/tmp/xjicloud") / job_id / "images"
        output_path = Path("/tmp/xjicloud") / job_id / "output.ply"

        try:
            print(f"[worker] processing job {job_id}")
            download_dataset_images(job.get("images", []), dataset_dir)

            def report(percent: int, stage: str, message: str) -> None:
                print(f"[worker] job {job_id}: {percent}% {stage} - {message}")
                self.report_progress(job_id, percent, stage, message)

            run_mock_training(dataset_dir, output_path, report)
            upload_file(output_path, job["outputUploadUrl"], "application/octet-stream")
            self.complete_job(job_id, job["outputOssKey"])
            print(f"[worker] job {job_id} completed")
        except Exception as exc:
            print(f"[worker] job {job_id} failed: {exc}")
            self.fail_job(job_id, str(exc))

    def poll_loop(self) -> None:
        while self.running:
            try:
                response = self.session.get(f"{BACKEND_URL}/api/v1/worker/jobs/next", timeout=40)
                response.raise_for_status()
                job = response.json().get("data")
                if job:
                    self.process_job(job)
                else:
                    time.sleep(POLL_INTERVAL)
            except requests.RequestException as exc:
                print(f"[worker] poll failed: {exc}")
                time.sleep(POLL_INTERVAL)

    def run(self) -> None:
        self.wait_for_backend()
        self.register()
        heartbeat_thread = threading.Thread(target=self.heartbeat_loop, daemon=True)
        heartbeat_thread.start()
        self.poll_loop()


if __name__ == "__main__":
    WorkerAgent().run()
