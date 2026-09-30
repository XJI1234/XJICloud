#!/usr/bin/env python3
"""容器内：GET 原图 → prepare/colmap/train → PUT ply。镜像不认识 XJI_*，必须包这一层。"""
from __future__ import annotations

import json
import os
import pathlib
import re
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = pathlib.Path(os.environ.get("ROOT_DIR", "/data"))
BUILDING = os.environ.get("BUILDING", "新文科楼")
SCRIPTS = pathlib.Path("/opt/3dgs/scripts")


def log(msg: str) -> None:
    print(f"[3dgs-bootstrap] {msg}", flush=True)


def log_shm() -> None:
    """创建时已挂 Memory EmptyDir 到 /dev/shm；这里只打 df 便于真云验收。"""
    df = subprocess.run(["df", "-h", "/dev/shm"], capture_output=True, text=True)
    if df.stdout:
        log(df.stdout.strip())
    elif df.stderr:
        log("/dev/shm: " + df.stderr.strip())


def run(script: str, extra: list[str] | None = None) -> None:
    cmd = ["bash", str(SCRIPTS / script), BUILDING] + (extra or [])
    env = os.environ.copy()
    env["ROOT_DIR"] = str(ROOT)
    log("RUN " + " ".join(cmd))
    subprocess.check_call(cmd, env=env)


def download(url: str, dest: pathlib.Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    log(f"GET {dest}")
    urllib.request.urlretrieve(url, dest)


def ingest() -> None:
    raw = os.environ.get("XJI_INPUT_FILES_JSON") or ""
    if not raw:
        raise SystemExit("XJI_INPUT_FILES_JSON 为空")
    items = json.loads(raw)
    ROOT.mkdir(parents=True, exist_ok=True)
    for i, item in enumerate(items, start=1):
        name = item.get("fileName") or f"input_{i}"
        url = item.get("downloadUrl")
        if not url:
            raise SystemExit(f"缺少 downloadUrl: {item}")
        if name.lower().endswith(".zip"):
            zpath = ROOT / name
            download(url, zpath)
            with zipfile.ZipFile(zpath) as zf:
                zf.extractall(ROOT)
            zpath.unlink(missing_ok=True)
        else:
            download(url, ROOT / BUILDING / name)


def ensure_export_ply() -> pathlib.Path:
    """Mirror manual shell §6.5: use exports/ if present, else copy latest results ply."""
    export_path = ROOT / "exports" / f"{BUILDING}.ply"
    if export_path.is_file() and export_path.stat().st_size > 0:
        log(f"export ply ready: {export_path} ({export_path.stat().st_size} bytes)")
        return export_path

    ply_dir = ROOT / "results" / BUILDING / "ply"
    if not ply_dir.is_dir():
        raise SystemExit(f"no ply directory: {ply_dir}")

    candidates = list(ply_dir.glob("point_cloud_*.ply"))
    if not candidates:
        raise SystemExit(f"no point_cloud_*.ply under {ply_dir}")

    def step_num(path: pathlib.Path) -> int:
        match = re.search(r"point_cloud_(\d+)\.ply$", path.name)
        return int(match.group(1)) if match else -1

    best = max(candidates, key=step_num)
    export_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(best, export_path)
    log(f"copied {best} -> {export_path} ({export_path.stat().st_size} bytes)")
    return export_path


def put_ply(local: pathlib.Path) -> None:
    url = os.environ.get("XJI_OUTPUT_UPLOAD_URL")
    if not url:
        raise SystemExit("XJI_OUTPUT_UPLOAD_URL 为空")
    if not local.is_file():
        raise SystemExit(f"没有产出物: {local}")
    log(f"PUT {local} ({local.stat().st_size} bytes)")
    req = urllib.request.Request(
        url,
        data=local.read_bytes(),
        method="PUT",
        headers={"Content-Type": "application/octet-stream"},
    )
    with urllib.request.urlopen(req, timeout=600) as resp:
        log(f"PUT status={getattr(resp, 'status', '?')}")


def main() -> int:
    log_shm()
    ingest()
    src = ROOT / BUILDING
    if not src.is_dir():
        raise SystemExit(f"原图目录不存在: {src}（zip 内应为 {BUILDING}/image*.JPG）")
    run("prepare_building.sh", [str(src)])
    run("run_colmap_sparse.sh")

    train_rc = 0
    try:
        run("run_gsplat_train.sh")
    except subprocess.CalledProcessError as exc:
        train_rc = exc.returncode
        log(f"train exit={train_rc}, trying ply fallback")

    export_path = ensure_export_ply()
    put_ply(export_path)
    if train_rc != 0:
        log(f"warning: train exited {train_rc} but ply uploaded from {export_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        log(f"script failed exit={exc.returncode}")
        raise
