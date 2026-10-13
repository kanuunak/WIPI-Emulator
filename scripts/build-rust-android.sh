#!/usr/bin/env bash
# Rust 코어 → Android .so (macOS / Linux). Windows는 build-rust-android.ps1 사용.
# 사용: ANDROID_NDK_HOME=<NDK 27.1.12297006 경로> scripts/build-rust-android.sh [arm64|x86_64|all]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME에 NDK 27.1.12297006 경로를 지정하세요}"
case "$(uname -s)" in
  Darwin) HOST=darwin-x86_64 ;;   # Apple Silicon도 darwin-x86_64 디렉터리를 쓴다
  Linux)  HOST=linux-x86_64 ;;
  *) echo "이 스크립트는 macOS/Linux 전용입니다. Windows는 build-rust-android.ps1"; exit 1 ;;
esac
TC="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
[ -d "$TC" ] || { echo "NDK 툴체인 없음: $TC"; exit 1; }
unset RUSTFLAGS CARGO_ENCODED_RUSTFLAGS   # config.toml의 16KB 정렬 플래그 보존

build() {  # $1 rust 타깃, $2 ABI 디렉터리, $3 clang 접두어
  local target="$1" abi="$2" clang="$TC/$3-linux-android26-clang"
  local upper; upper="$(echo "${target//-/_}" | tr '[:lower:]' '[:upper:]')"
  export "CARGO_TARGET_${upper}_LINKER=$clang"
  export "CC_${target//-/_}=$clang"
  export "AR_${target//-/_}=$TC/llvm-ar"
  (cd "$ROOT/rust" && cargo build --release --locked -p wipi_android --target "$target")
  mkdir -p "$ROOT/android/app/src/main/jniLibs/$abi"
  cp "$ROOT/rust/target/$target/release/libwipi_android.so" "$ROOT/android/app/src/main/jniLibs/$abi/"
  echo "== $abi LOAD 정렬 (Align 0x4000 이어야 함)"
  "$TC/llvm-readelf" -lW "$ROOT/android/app/src/main/jniLibs/$abi/libwipi_android.so" | grep LOAD
}

case "${1:-arm64}" in
  arm64)  build aarch64-linux-android arm64-v8a aarch64 ;;
  x86_64) build x86_64-linux-android x86_64 x86_64 ;;
  all)    build aarch64-linux-android arm64-v8a aarch64; build x86_64-linux-android x86_64 x86_64 ;;
  *) echo "인자: arm64 | x86_64 | all"; exit 1 ;;
esac
