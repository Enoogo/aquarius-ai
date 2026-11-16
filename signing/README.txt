Aquarius AI 发布签名（自 2.3.0 起使用）
=====================================

文件      : aquarius-release.jks
别名      : aquarius
Store 口令: aquarius2026
Key 口令  : aquarius2026
算法      : RSA 4096 / SHA384withRSA，有效期 10000 天

签名命令（build-tools 37.0.0）：

  zipalign -P 16 -f 4 app-release-unsigned.apk aligned.apk
  apksigner sign --ks signing/aquarius-release.jks --ks-key-alias aquarius \
      --ks-pass pass:aquarius2026 --key-pass pass:aquarius2026 \
      --min-sdk-version 21 --v1-signing-enabled true \
      --v2-signing-enabled true --v3-signing-enabled true \
      --out Aquarius-AI.apk aligned.apk
  apksigner verify --verbose Aquarius-AI.apk
  zipalign -c -P 16 4 Aquarius-AI.apk

注意：
- 2.2.0 及更早版本的 APK 由另一把私钥签名，无法直接覆盖安装 2.3.0，
  需要先卸载旧版本（会清空本地会话数据）。
- 后续版本请沿用本 keystore，否则同样无法覆盖安装。
- keystore 与口令属于敏感信息，如需公开仓库，请把 signing/ 从仓库中移除。
