#!/usr/bin/env python3
"""Submit a dataset through backend and observe the dispatch lifecycle."""

import argparse
import json
import os
import time
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path


CONTENT_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
}


def request(base, method, path, *, token=None, api_key=None, payload=None, url=None, content_type=None):
    body = payload if isinstance(payload, bytes) else (
        json.dumps(payload, separators=(",", ":")).encode() if payload is not None else None
    )
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if api_key:
        headers["X-Api-Key"] = api_key
    if payload is not None and not isinstance(payload, bytes):
        headers["Content-Type"] = "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    with urllib.request.urlopen(
        urllib.request.Request(url or base + path, data=body, headers=headers, method=method),
        timeout=30,
    ) as response:
        raw = response.read()
    return json.loads(raw.decode()) if raw else None


def api(base, method, path, **kwargs):
    response = request(base, method, path, **kwargs)
    return response["data"]


def files_in(dataset_dir):
    files = []
    for path in sorted(Path(dataset_dir).rglob("*")):
        if not path.is_file() or path.suffix.lower() not in CONTENT_TYPES:
            continue
        files.append({
            "path": path,
            "archivedName": f"{len(files) + 1:04d}{path.suffix.lower()}",
            "originalName": path.name,
            "contentType": CONTENT_TYPES[path.suffix.lower()],
            "data": path.read_bytes(),
        })
    if not files:
        raise RuntimeError("dataset directory has no supported images")
    return files


def login(backend):
    token = os.environ.get("TRAINING_TEST_TOKEN", "").removeprefix("Bearer ").strip()
    if token:
        return token
    response = api(
        backend,
        "POST",
        "/api/v1/auth/login",
        payload={
            "username": os.environ["TRAINING_TEST_USERNAME"],
            "password": os.environ["TRAINING_TEST_PASSWORD"],
        },
    )
    return response["accessToken"]


def submit(backend, token, dataset_dir, building):
    files = files_in(dataset_dir)
    project = api(
        backend,
        "POST",
        "/api/v1/projects",
        token=token,
        payload={
            "name": f"{building} training {datetime.now(timezone.utc).isoformat()}",
            "description": "training dispatch integration",
        },
    )
    project_id = project["id"]
    ticket = api(
        backend,
        "POST",
        f"/api/v1/projects/{project_id}/datasets",
        token=token,
        payload={
            "name": building,
            "files": [
                {
                    "archivedName": item["archivedName"],
                    "originalName": item["originalName"],
                    "contentType": item["contentType"],
                    "sizeBytes": len(item["data"]),
                }
                for item in files
            ],
        },
    )
    uploads = {item["archivedName"]: item["uploadUrl"] for item in ticket["uploads"]}
    for item in files:
        request(
            backend,
            "PUT",
            "",
            url=uploads[item["archivedName"]],
            payload=item["data"],
            content_type=item["contentType"],
        )
    manifest = {
        "version": 1,
        "imageCount": len(files),
        "files": [
            {
                "archivedName": item["archivedName"],
                "originalName": item["originalName"],
                "contentType": item["contentType"],
                "sizeBytes": len(item["data"]),
            }
            for item in files
        ],
    }
    request(
        backend,
        "PUT",
        "",
        url=ticket["manifestUploadUrl"],
        payload=json.dumps(manifest, separators=(",", ":")).encode(),
        content_type="application/json",
    )
    completed = api(
        backend,
        "POST",
        f"/api/v1/projects/{project_id}/datasets/{ticket['jobId']}/complete",
        token=token,
    )
    print(f"projectId={project_id}")
    print(f"jobId={ticket['jobId']} businessStatus={completed['status']}")
    return project_id, ticket["jobId"]


def observe(backend, eci, token, api_key, job_id, expected_image, timeout):
    task_id = None
    last = None
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        job = api(backend, "GET", f"/api/v1/jobs/{job_id}", token=token)
        business_status = job["status"]
        tasks = api(eci, "GET", "/api/v1/eci/tasks", api_key=api_key)
        task = next((item for item in tasks if item["name"] == f"training-{job_id[:8]}-1"), None)
        eci_status = instance_status = "PENDING"
        if task:
            task_id = task["id"]
            task = api(eci, "GET", f"/api/v1/eci/tasks/{task_id}", api_key=api_key)
            instance_status = "PENDING"
            if task.get("instanceId"):
                instance = api(
                    eci, "GET", f"/api/v1/eci/instances/{task['instanceId']}", api_key=api_key
                )
                instance_status = instance["status"]
            eci_status = task["status"]
            if last is None:
                assert task["image"] == expected_image
                assert task["command"] == ["/opt/3dgs/docker/entrypoint.sh"]
                assert task["cpu"] == 16.0 and task["memory"] == 60.0 and task["gpu"] == 1
                assert task["instanceType"] == "ecs.gn7i-c16g1.4xlarge"
                assert task["shmSize"] == "8Gi" and task["restartPolicy"] == "Never"
                assert task["maxRuntimeSec"] == 21600
                assert task["env"]["BUILDING"] == f"job-{job_id}"
        state = (business_status, eci_status, instance_status)
        if state != last:
            print(
                f"jobId={job_id} businessStatus={business_status} "
                f"eciTaskId={task_id or 'PENDING'} eciStatus={eci_status} "
                f"instanceStatus={instance_status}"
            )
            last = state
        if business_status == "COMPLETED" and eci_status == "SUCCEEDED" and instance_status == "TERMINATED":
            return
        time.sleep(5)
    raise TimeoutError("training dispatch observation timed out")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("mock", "cloud"), required=True)
    args = parser.parse_args()
    backend = os.environ.get("TRAINING_TEST_BACKEND_BASE", "http://127.0.0.1:8080")
    eci = os.environ.get("TRAINING_TEST_ECI_BASE", "http://127.0.0.1:8081")
    dataset_dir = os.environ["TRAINING_TEST_DATASET_DIR"]
    building = os.environ.get("TRAINING_TEST_BUILDING", "新文科楼")
    expected_image = (
        os.environ.get("TRAINING_TEST_EXPECTED_IMAGE", "").strip()
        or os.environ["XJICLOUD_ECI_MANAGER_TRAINING_IMAGE"]
    )
    api_key = os.environ["ECI_API_KEY"]
    token = login(backend)
    _, job_id = submit(backend, token, dataset_dir, building)
    observe(
        backend,
        eci,
        token,
        api_key,
        job_id,
        expected_image,
        float(os.environ.get("TRAINING_TEST_TIMEOUT_SEC", "1800")),
    )
    print(f"training completed: jobId={job_id}")


if __name__ == "__main__":
    main()
