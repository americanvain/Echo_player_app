# 构建与发布

## 环境

- JDK 17
- Android SDK：platform 35、build-tools 35.0.0（`sdkmanager "platforms;android-35" "build-tools;35.0.0"`）
- 不需要 Android Studio，命令行即可

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest      # 单元测试（切句、导入、DTO 解析）
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

## Release 签名

`app/build.gradle.kts` 从环境变量读密钥；没有就退回 debug 签名（仍能安装，只是不能覆盖升级正式签名的包）：

```bash
export ECHO_KEYSTORE=/path/to/echo_release.jks
export ECHO_KEYSTORE_PASSWORD=...
export ECHO_KEY_ALIAS=echoplayer
export ECHO_KEY_PASSWORD=...
./gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

生成密钥：

```bash
keytool -genkeypair -v -keystore echo_release.jks -alias echoplayer -keyalg RSA -keysize 2048 -validity 10000
```

**密钥不要进仓库**（`.gitignore` 已排除 `*.jks` / `*.keystore`）。换密钥意味着老用户必须卸载重装。

## GitHub Actions

`.github/workflows/android.yml`：
- push / PR 到 `master`：跑单元测试 + 打 debug 包（作为 artifact）。
- 推 `v*` tag：打 release 包并附到 GitHub Release。需要在仓库 Secrets 里配
  `ECHO_KEYSTORE_BASE64`（`base64 -w0 echo_release.jks`）、`ECHO_KEYSTORE_PASSWORD`、`ECHO_KEY_ALIAS`、`ECHO_KEY_PASSWORD`。
  没配就用 debug 签名。

## 手动发布

```bash
./gradlew :app:assembleRelease
gh release create v0.1.0 app/build/outputs/apk/release/app-release.apk --title "v0.1.0" --notes-file CHANGELOG.md
```
