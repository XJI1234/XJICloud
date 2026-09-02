#!/usr/bin/env python3
"""Generate internal GET/PUT presigned URLs for 3DGS manual Shell flow."""

from __future__ import annotations

from datetime import timedelta
from pathlib import Path

import oss2

ROOT = Path(__file__).resolve().parents[1]
BUILDING = "新文科楼"
INPUT_KEY = f"3dgs/raw/{BUILDING}.zip"
OUTPUT_KEY = f"3dgs/exports/{BUILDING}.ply"
EXPIRE_SEC = 60 * 60 * 24


def load_env() -> dict[str, str]:
    env: dict[str, str] = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#") or "=" not in s:
            continue
        k, v = s.split("=", 1)
        env[k] = v.strip().strip("'").strip('"')
    return env


def main() -> None:
    env = load_env()
    for key in ("ECI_OSS_ACCESS_KEY", "ECI_OSS_SECRET_KEY", "ECI_OSS_BUCKET"):
        if not env.get(key):
            raise SystemExit(f"missing {key} in {ROOT / '.env'}")

    auth = oss2.Auth(env["ECI_OSS_ACCESS_KEY"], env["ECI_OSS_SECRET_KEY"])
    bucket = oss2.Bucket(
        auth,
        "https://oss-cn-hangzhou-internal.aliyuncs.com",
        env["ECI_OSS_BUCKET"],
    )

    get_url = bucket.sign_url("GET", INPUT_KEY, EXPIRE_SEC)
    put_url = bucket.sign_url(
        "PUT",
        OUTPUT_KEY,
        EXPIRE_SEC,
        headers={"Content-Type": "application/octet-stream"},
    )

    print("=== 复制以下两行到记事本，进容器后用（24h 有效） ===")
    print("PRESIGN_GET=" + get_url)
    print("PRESIGN_PUT=" + put_url)
    print(f"=== expires in {timedelta(seconds=EXPIRE_SEC)} ===")


if __name__ == "__main__":
    main()
