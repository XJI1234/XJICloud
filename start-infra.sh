#!/usr/bin/env bash
cd "$(dirname "$0")/deploy"
docker compose up redis minio minio-init -d
docker compose ps
