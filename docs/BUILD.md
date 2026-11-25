# 构建、签名与验收

本工程是**纯原生 Java + XML** 的 Android 应用，不使用 WebView、JavaScript UI、Gradle、AndroidX 或 Compose。构建脚本直接调用 Android SDK 工具：

```text
aapt2 compile/link → javac → d8 → Python zipfile → zipalign → apksigner → verify-apk.sh
```

默认在本机完成编译、签名和验收。也可以在独立远端构建机上编译，APK 拉回本机后才签名；签名密钥和密码始终只在本机，永远不复制到远端构建机。

## 1. 环境要求

本机（编译、签名、验收）：

- Linux 或 macOS，bash 4+
- JDK 17（`javac`、`jar`、`keytool`）
- Android SDK：
  - `platforms/android-35/android.jar`（或 34 / 36）
  - `build-tools/35.0.0`（或 34.0.0 / 36.0.0，需含 `aapt2`、`d8`、`aapt`、`apksigner`、`zipalign`）
- Python 3（APK 打包）
- `unzip`、`sha256sum`、`rsync`、`ssh`（远程模式）

远程构建机额外需要：

- 免密 `ssh` 登录
- `rsync`、`flock`、`python3`、`javac`、`jar`
- 同一套 Android SDK 和 `javac`

远程机器**不需要** `zip` / `unzip`，APK 由 Python `zipfile` 组装。

路径可通过环境变量覆盖：

```bash
TOOLSTORE_SDK_ROOT=/path/to/Android/Sdk
TOOLSTORE_BUILD_TOOLS=/path/to/Android/Sdk/build-tools/35.0.0
TOOLSTORE_ANDROID_JAR=/path/to/Android/Sdk/platforms/android-35/android.jar
```

## 2. 常用命令

```bash
# 只检查本机工具链，不构建
tools/build.sh --check

# 默认：本机编译 + 本机签名 + 本机验收
tools/build.sh

# 可选：远端编译 + 本机签名 + 本机验收
tools/build.sh --remote --ssh user@host --remote-root ~/toolstore_build --wait 1800

# 保留本次本地或远端工作目录，便于排查
tools/build.sh --keep

# 单独复验一个已经签名的 APK
tools/verify-apk.sh dist/tool-store-0.3.0.apk
```

远端相关参数也可用环境变量设置：

```bash
TOOLSTORE_SSH=user@host
TOOLSTORE_REMOTE_ROOT='~/toolstore_build'
```

## 3. 远端隔离流程（可选）

`tools/build.sh --remote` 使用独立构建目录，**远端根目录完全独立**：

```text
默认根目录:        ~/toolstore_build
每次运行工作区:    ~/toolstore_build/work-<runid>
每次运行产物区:    ~/toolstore_build/result-<runid>
锁文件:            ~/toolstore_build/build.lock
所有权标记:        ~/toolstore_build/.toolstore-build-root
```

流程：

1. 检查本地工具链和远端 `ssh`；
2. 只同步 `AndroidManifest.xml`、`src/`、`res/`、`assets/`、`jniLibs/` 和 `tools/build-inner.sh`；
3. 远端用 `flock` 串行编译，唯一工作区避免并发覆盖；
4. 远端只生成未签名 APK，**不接触任何签名材料**；
5. 拉回未签名 APK，立即删除本次远端 `work-*` / `result-*`；
6. 本机 `zipalign`、`apksigner`、`verify-apk.sh`；
7. 只有全部验收通过，才发布到 `dist/`。

远端同步白名单不包含以下内容，它们不会被复制到构建机：

- `.git/`
- `.build/`、`dist/`
- 签名目录（默认 `$HOME/.config/tool-store/`）
- `docs/`、`prototype/`
- 任何 `*.jks`、`*.keystore`、`*.pass`、`*.pem`

如果指定的远端根已存在、非空且没有所有权标记，脚本会拒绝使用，避免把其他工程目录当作工作区。远端构建结束后根目录只保留所有权标记和锁文件；`--keep` 时才会保留本次工作区和结果。

构建机约束：

- 不在远端修改源码、不提交、不推 Git；
- 不使用 `sudo`、不写 `/usr`、不改系统配置；
- 不与本机其他工程的构建目录共用远端根；
- 不向远端复制密钥、密码或签名后的 APK。

## 4. 签名密钥

首次执行真实构建时自动生成：

```text
${TOOLSTORE_KEYSTORE_DIR:-$HOME/.config/tool-store}/tool-store-release.jks    PKCS#12，RSA 4096，别名 toolstore
${TOOLSTORE_KEYSTORE_DIR:-$HOME/.config/tool-store}/tool-store-release.pass   随机生成的本地密码
```

这两个文件：

- 位于仓库外，**不进入 Git 历史**，也不会被同步到构建机；
- 不出现在命令行参数和构建日志中（`keytool` / `apksigner` 通过 `file:` 读取密码）；
- 只在本机用于 `zipalign` 后的 APK 签名。

请自行做离线加密备份，并限制目录和文件权限。不要把密钥转发到聊天工具、公开目录或任何公开仓库。丢失密钥后，已安装该签名的设备无法直接覆盖升级，只能卸载重装。若怀疑密钥泄露，应更换签名密钥并通知用户重新安装。

## 5. 验收门禁

`tools/verify-apk.sh` 会对最终签名 APK 执行以下检查，任一失败都不会发布：

- 包名、`versionCode`、`versionName` 与 `AndroidManifest.xml` 一致；
- `minSdkVersion 24`、`targetSdkVersion 34`，`MainActivity` 是启动入口；
- 权限集合**精确等于** `INTERNET` + `ACCESS_NETWORK_STATE` + `REQUEST_INSTALL_PACKAGES`（最后一项只用于把官方站点推荐的 CleanYourTV 手机版交给系统安装器）；
- `debuggable=false`、`allowBackup=false`、`usesCleartextTraffic=false`、`extractNativeLibs=true`；
- `jniLibs` 中的 59 个 arm64-v8a 库与 APK 内原生库清单完全一致，且包含 `libadb.so`；
- Termux / Android-Tools 运行时许可证与 NOTICE 已打包；
- APK 中不存在 WebView 字节码，也不存在 HTML / JS / CSS / `assets/www`；
- `zipalign -c -p 4` 通过；
- APK Signature Scheme v2 签名有效，签名者恰好一个；
- 输出 SHA-256、签名证书 SHA-256 与文件大小。

当前 APK 的关键信息：

```text
package: com.toolstore.installer
version: 0.3.0 (versionCode 13)
ABI:     arm64-v8a only
minSdk:  24 (Android 7.0)
target:  34 (Android 14)
```

## 6. 产物

```text
dist/tool-store-0.3.0.apk
dist/tool-store-0.3.0.apk.sha256
.build/build-<runid>.log
```

- `dist/` 和 `.build/` 均被 Git 忽略。
- 构建日志不包含配对码、密码、密钥内容。
- 默认构建成功会删除本地临时工作目录；`--keep` 可保留。
- 远端临时目录默认删除；`--keep` 会在终端打印保留路径。

## 7. 退出码

| 退出码 | 含义 |
| --- | --- |
| 0 | 成功 |
| 2 | 参数错误 |
| 3 | 本地预检或源码同步失败 |
| 4 | 远端不可达 / 工具链不完整 / 远端根不受管 |
| 6 | 编译或打包失败 |
| 7 | 拉取未签名 APK 失败 |
| 8 | 签名或 APK 验收失败 |
| 75 | 远端构建锁等待超时 |

## 8. 发布前人工检查

自动验收只证明 APK 的结构、权限、签名和原生库正确，不证明 UI 在真实电视上工作。发布前仍需：

1. 在 Android 7+ arm64 手机上安装，检查首页、搜索、筛选、底部导航和设置页；
2. 用真实电视完成无线调试配对、经典 5555、USB 调试三条路径中的至少两条；
3. 安装一个桌面工具，确认“设为默认桌面”关闭时 HOME 不变，开启时以电视确认结果为准；
4. 卸载默认桌面工具：确认它先自动切回原桌面、恢复系统设置，再删除应用，且没有进入空 HOME；HOME 无法确认时应停在删除之前；
5. 安装 / 卸载 StudyOnTV，确认不自动授予高风险权限、不改 HOME；
6. 打开 CleanYourTV 详情，确认展示官方电脑脚本和官方站点推荐的手机版 App；手机版只从官方页面链接的 GitHub Release（或其同一仓库的 GitHub API 官方资产通道）下载，校验 SHA-256、包名与签名后由系统安装器确认，安装目标始终是手机。确认没有假称“直接安装电视桌面”的按钮。

README 中的当前状态会如实标注本轮是否完成过真机验收。