#!/bin/bash
set -euo pipefail

echo "[entrypoint] XJICloud COLMAP local run starting..."

if ! command -v colmap &>/dev/null; then
    echo "[entrypoint] ERROR: colmap not found in image PATH" >&2
    exit 1
fi

echo "[entrypoint] COLMAP CLI: $(command -v colmap)"

INPUT_ZIP="${INPUT_ZIP:-/input/south-building.zip}"
EXTRACT_DIR="${EXTRACT_DIR:-/tmp/colmap_input}"
OUTPUT_ROOT="${OUTPUT_ROOT:-/output}"
OUTPUT_PATH="${OUTPUT_PATH:-${OUTPUT_ROOT}/model.ply}"
EXPORT_PLY="${EXPORT_PLY:-0}"

# 0 = 自动/每张图可变相机参数；1 = 强制所有图使用单相机（更适合同一相机拍摄的严格场景）
COLMAP_SINGLE_CAMERA="${COLMAP_SINGLE_CAMERA:-0}"

# 没有 GPU 时也能跑：建议先把 COLMAP_USE_GPU=0
COLMAP_USE_GPU="${COLMAP_USE_GPU:-0}"

DATABASE_PATH="${OUTPUT_ROOT}/database.db"
SPARSE_DIR="${OUTPUT_ROOT}/sparse"

mkdir -p "${OUTPUT_ROOT}"

if [[ ! -f "${INPUT_ZIP}" ]]; then
    echo "[entrypoint] ERROR: INPUT_ZIP not found: ${INPUT_ZIP}" >&2
    exit 1
fi

rm -rf "${EXTRACT_DIR}"
mkdir -p "${EXTRACT_DIR}"
unzip -q "${INPUT_ZIP}" -d "${EXTRACT_DIR}"

IMAGE_ROOT="${EXTRACT_DIR}"
if [[ -d "${EXTRACT_DIR}/images" ]]; then
    IMAGE_ROOT="${EXTRACT_DIR}/images"
fi

image_count="$(find "${IMAGE_ROOT}" -type f | wc -l | tr -d ' ')"
if [[ "${image_count}" = "0" ]]; then
    echo "[entrypoint] ERROR: no extracted files found in ${IMAGE_ROOT}" >&2
    exit 1
fi

echo "[entrypoint] zip=${INPUT_ZIP}"
echo "[entrypoint] image_root=${IMAGE_ROOT}"
echo "[entrypoint] output_root=${OUTPUT_ROOT}"
echo "[entrypoint] sparse_dir=${SPARSE_DIR}"
echo "[entrypoint] image_count=${image_count}"

# 1) 特征提取
echo "[entrypoint] Step 1/3: feature_extractor"
colmap feature_extractor \
    --database_path "${DATABASE_PATH}" \
    --image_path "${IMAGE_ROOT}" \
    --ImageReader.single_camera "${COLMAP_SINGLE_CAMERA}" \
    --FeatureExtraction.use_gpu "${COLMAP_USE_GPU}"

# 2) 特征匹配（全局穷举）
echo "[entrypoint] Step 2/3: exhaustive_matcher"
colmap exhaustive_matcher \
    --database_path "${DATABASE_PATH}" \
    --FeatureMatching.use_gpu "${COLMAP_USE_GPU}"

# 3) 稀疏重建（生成 /output/sparse/0 等）
echo "[entrypoint] Step 3/3: mapper (sparse reconstruction)"
colmap mapper \
    --database_path "${DATABASE_PATH}" \
    --image_path "${IMAGE_ROOT}" \
    --output_path "${SPARSE_DIR}" \
    --Mapper.ba_use_gpu 0

if [[ ! -d "${SPARSE_DIR}/0" ]]; then
    echo "[entrypoint] ERROR: sparse model not produced at ${SPARSE_DIR}/0" >&2
    exit 1
fi

if [[ "${EXPORT_PLY}" = "1" ]]; then
    echo "[entrypoint] Optional export: model_converter (sparse point cloud)"
    colmap model_converter \
        --input_path "${SPARSE_DIR}/0" \
        --output_path "${OUTPUT_PATH}" \
        --output_type PLY
    echo "[entrypoint] ply=${OUTPUT_PATH}"
fi

echo "[entrypoint] done: ${SPARSE_DIR}/0"
