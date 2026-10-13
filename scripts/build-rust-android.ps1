# Build the Rust core into an Android .so (Windows). Usage:
#   $env:ANDROID_NDK_HOME = "$env:LOCALAPPDATA\Android\Sdk\ndk\27.1.12297006"
#   .\scripts\build-rust-android.ps1 -Abi arm64
# ASCII only on purpose: Windows PowerShell 5.1 reads BOM-less files in the ANSI code page.
param([ValidateSet("arm64", "x86_64", "all")][string]$Abi = "arm64")
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Ndk = $env:ANDROID_NDK_HOME
if (-not $Ndk) { throw "Set ANDROID_NDK_HOME to the NDK 27.1.12297006 directory" }
$Tc = Join-Path $Ndk "toolchains\llvm\prebuilt\windows-x86_64\bin"
if (-not (Test-Path $Tc)) { throw "NDK toolchain not found: $Tc" }
# Keep the 16KB page-size flag from rust/.cargo/config.toml (RUSTFLAGS would replace it)
Remove-Item Env:RUSTFLAGS -ErrorAction SilentlyContinue
Remove-Item Env:CARGO_ENCODED_RUSTFLAGS -ErrorAction SilentlyContinue

function Build-Abi([string]$Target, [string]$AbiDir, [string]$Prefix) {
  $clang = Join-Path $Tc "$Prefix-linux-android26-clang.cmd"
  $lower = $Target -replace '-', '_'
  $upper = $lower.ToUpper()
  Set-Item "Env:CARGO_TARGET_${upper}_LINKER" $clang
  Set-Item "Env:CC_$lower" $clang
  Set-Item "Env:AR_$lower" (Join-Path $Tc "llvm-ar.exe")
  Push-Location (Join-Path $Root "rust")
  try {
    cargo build --release --locked -p wipi_android --target $Target
    if ($LASTEXITCODE -ne 0) { throw "cargo build failed: $Target" }
  } finally { Pop-Location }
  $dest = Join-Path $Root "android\app\src\main\jniLibs\$AbiDir"
  New-Item -ItemType Directory -Force $dest | Out-Null
  Copy-Item (Join-Path $Root "rust\target\$Target\release\libwipi_android.so") $dest -Force
  Write-Host "== $AbiDir LOAD alignment (expect Align 0x4000)"
  & (Join-Path $Tc "llvm-readelf.exe") -lW (Join-Path $dest "libwipi_android.so") | Select-String "LOAD"
}

if ($Abi -in @("arm64", "all"))  { Build-Abi "aarch64-linux-android" "arm64-v8a" "aarch64" }
if ($Abi -in @("x86_64", "all")) { Build-Abi "x86_64-linux-android" "x86_64" "x86_64" }
