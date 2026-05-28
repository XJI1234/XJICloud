# Exit on any error
$ErrorActionPreference = "Stop"

# Resolve the script's directory and change to it
Set-Location -Path (Split-Path -Parent $MyInvocation.MyCommand.Path)

# Use rsproxy by default for local wasm builds when no Rust mirror is configured.
if (-not $env:RUSTUP_DIST_SERVER) {
    $env:RUSTUP_DIST_SERVER = "https://rsproxy.cn"
}

if (-not $env:RUSTUP_UPDATE_ROOT) {
    $env:RUSTUP_UPDATE_ROOT = "https://rsproxy.cn/rustup"
}

if (-not $env:CARGO_REGISTRIES_CRATES_IO_PROTOCOL) {
    $env:CARGO_REGISTRIES_CRATES_IO_PROTOCOL = "sparse"
}

if (-not $env:CARGO_REGISTRIES_CRATES_IO_INDEX) {
    $env:CARGO_REGISTRIES_CRATES_IO_INDEX = "sparse+https://rsproxy.cn/index/"
}

# Check if rustup is installed
if (-not (Get-Command rustup -ErrorAction SilentlyContinue)) {
    Write-Host "Rust tool 'rustup' not found! Please install Rust to build."
    Write-Host "Visit: https://www.rust-lang.org/tools/install"
    exit 1
}

# Ensure wasm32-unknown-unknown target is installed
rustup target add wasm32-unknown-unknown

# Check if wasm-pack is installed, and install if not
if (-not (Get-Command wasm-pack -ErrorAction SilentlyContinue)) {
    cargo install wasm-pack
}

# Change directory and build using wasm-pack with SIMD enabled
Set-Location -Path "./spark-worker-rs"
$env:RUSTFLAGS = "-C target-feature=+simd128,+bulk-memory"
wasm-pack build --target web --release

# Change directory and build using wasm-pack with SIMD enabled
Set-Location -Path "../spark-rs"
$env:RUSTFLAGS = "-C target-feature=+simd128,+bulk-memory"
wasm-pack build --target web --release
