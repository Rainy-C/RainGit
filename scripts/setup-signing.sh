#!/usr/bin/env bash
set -euo pipefail
umask 077
command -v openssl >/dev/null || { echo '请先安装 openssl'; exit 1; }
command -v gh >/dev/null || { echo '请先安装 gh 并登录'; exit 1; }
REPO="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
DIR="$HOME/.config/raingit/signing"
KEYSTORE="$DIR/raingit-release.p12"
PASSFILE="$DIR/password"
mkdir -p "$DIR"
if [[ ! -f "$KEYSTORE" || ! -f "$PASSFILE" ]]; then
  if [[ -f "$KEYSTORE" || -f "$PASSFILE" ]]; then
    echo '发现不完整的旧签名配置，请备份并检查 ~/.config/raingit/signing'; exit 1
  fi
  PASSWORD="$(openssl rand -hex 24)"
  SIGNING_TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$SIGNING_TMP_DIR"' EXIT
  openssl req -new -x509 -newkey rsa:3072 -nodes -days 36500 -sha256 -subj '/CN=RainGit/O=RainGit' -keyout "$SIGNING_TMP_DIR/private.pem" -out "$SIGNING_TMP_DIR/cert.pem" >/dev/null 2>&1
  openssl pkcs12 -export -inkey "$SIGNING_TMP_DIR/private.pem" -in "$SIGNING_TMP_DIR/cert.pem" -name raingit -out "$KEYSTORE" -passout "pass:$PASSWORD"
  printf '%s' "$PASSWORD" > "$PASSFILE"
  rm -rf "$SIGNING_TMP_DIR"
  trap - EXIT
else
  PASSWORD="$(cat "$PASSFILE")"
fi
ENVFILE="$(mktemp)"
trap 'rm -f "$ENVFILE"' EXIT
{
  printf 'RAINGIT_KEYSTORE_BASE64=%s\n' "$(base64 < "$KEYSTORE" | tr -d '\n')"
  printf 'RAINGIT_STORE_PASSWORD=%s\n' "$PASSWORD"
  printf 'RAINGIT_KEY_ALIAS=raingit\n'
  printf 'RAINGIT_KEY_PASSWORD=%s\n' "$PASSWORD"
} > "$ENVFILE"
gh secret set -R "$REPO" -f "$ENVFILE"
echo "固定 Release 签名已配置：$REPO"
echo "私钥仅备份于：$KEYSTORE"
echo '请妥善备份该目录，否则以后无法使用原签名更新 APK。'
