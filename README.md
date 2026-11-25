# tool-store 工具市场

一个面向 Android 手机端的精选电视工具市场：手机和电视连接同一个 Wi-Fi，通过局域网 ADB 发现电视、配对授权、下载并校验官方 APK，然后完成安装、卸载与默认桌面管理。

界面是**纯原生 Java + XML**，明亮浅色、手机厂商应用商店风格。没有 WebView，没有 HTML / CSS / JS UI，也没有 Gradle、AndroidX 或 Compose。

> **当前版本：0.3.0（versionCode 13）**
> 产物：`dist/tool-store-0.3.0.apk`，仅 `arm64-v8a`，minSdk 24（Android 7.0），targetSdk 34。
> 构建与 APK 结构验收以实际执行结果为准；未连接电视时应用只做说明与连接引导。

## 已实现

### 工具市场

- 首发收录 **5 个 TV 桌面 + 1 个答题工具**：PlanYourTV、Aerial、Quiet TV、HaloTV、CleanYourTV、题了个题 StudyOnTV。
- 搜索、全部 / 桌面 / 工具筛选、分组推荐、精选卡片、安装状态与特色标签。
- 卡片使用各项目图标；除 StudyOnTV 按上游许可证使用通用占位图标外，其余使用官方启动图标。点击整张卡片或动作按钮进入独立全屏详情页。
- 全屏详情页复刻应用市场结构：顶部图标与徽章、官方截图轮播与页码圆点、应用介绍、特色亮点、安装选项、权限说明、应用信息，底部固定操作栏。StudyOnTV 不打包官方截图，详情页隐藏截图区。
- 已安装应用在详情页底栏有明确的“卸载”按钮；「我的电视」的已安装卡片只保留一个“卸载”入口。卸载默认桌面时不再要求用户先手动切回：会按官方恢复顺序停止守护、撤销通知监听 / 无障碍 / 悬浮窗设置、恢复 TCL / 海信开机项和原桌面，确认 HOME 后才删除应用；HOME 无法确认时在删除前停止。CleanYourTV 需先运行官方 `restore.sh`，或使用手机版 App 内保存的恢复点。
- 除 StudyOnTV 外，图标与截图来自官方仓库 / 官网，打包为本地 PNG / WebP；运行时不联网取图，不引入图片加载库。完整来源与许可边界见 [docs/ARTWORK.md](docs/ARTWORK.md)。
- 直接安装路径：从官方索引动态解析最新版本 → 按官方源顺序 HTTPS 下载（网络失败才切换备用源；哈希、域名、路径或版本校验失败立即停止） → 包名 / 签名证书 / 版本 / 可用 SHA-256 校验 → ADB 安装 → 读回电视上的真实版本。
- 桌面工具可选“设为默认桌面”，默认关闭；只有电视端确认切换后才报告成功，并可在「我的电视」恢复原桌面。
- 工具不参与 HOME：StudyOnTV 安装 / 卸载不改默认桌面，高风险权限逐项引导，不静默授予。
- CleanYourTV 的电视桌面本身没有官方 APK：提供官方电脑脚本，也可以从本页下载并安装官方站点推荐的社区手机版 App。工具市场负责下载、校验与交给系统安装器，清理和电视端桌面安装由该 App 完成。

### 我的电视 / 初始准备

- **无线调试（优先）**：Android 11+ 配对端口 + 6 位配对码 → 配对 → 连接端口 → 电视端授权 → 读取状态。
- **经典网络 ADB**：IP + 5555 端口，明确局域网安全边界。
- **USB 调试引导**：三步勾选清单，完成后引导转无线连接。
- **局域网设备扫描**：同时监听 mDNS `_adb-tls-connect._tcp.` / `_adb._tcp.`，并扫描当前 IPv4 网段的 5555 端口；列出所有已开启 ADB 的候选设备，显示名称 / 地址 / 来源，每行右侧直接提供「连接」按钮。
- 点「连接」即直接连接该设备，不需要先“选择”再点第二次；连接仍会走 ADB 授权，首次无线调试仍需要电视上的配对端口和配对码。未开启 ADB 的设备不会出现在候选里。
- 已连接仪表盘：品牌 / 型号 / Android 版本、传输方式、延迟、默认 HOME、已安装精选应用。
- 刷新、断开、恢复原桌面、按应用卸载；系统桌面和系统应用永不出现在卸载入口。

### 原生 UI

- 单 Activity + XML 布局 + 系统控件；底部两栏「工具市场 / 我的电视」。
- 全屏详情页是 `MainActivity` 内的覆盖页面层，返回时保留市场滚动位置；安装进度和结果使用框架 `Dialog` 底部面板。
- 自绘 LineIconView / FlowLayout 与 framework ImageView 圆角裁切，避免引入图标库或图片加载库。
- 浅色原生界面，不依赖 Material / AndroidX / Compose。
- 所有耗时操作在 `ExecutorService` 执行，结果切回主线程；锁屏保持常亮，浅色状态栏 / 导航栏。

## 收录应用

| 项目 | 类型 | 包名 | 可选版本 | 手机直装 | 说明 |
| --- | --- | --- | --- | --- | --- |
| PlanYourTV | 桌面 | `com.planyourtv.tv` | 标准版 / Guard | ✅ | 儿童桌面、答题与时间管理；官网 `version.txt` + 固定签名 |
| Aerial | 桌面 | `app.orchard.tv` / `.lite` | 标准版 / Guard / Lite | ✅ | 去广告、tvOS 风格；官方站点优先，GitHub 备用 |
| Quiet TV | 桌面 | `home.quiet.tv` | 标准版 / 4.x 兼容版 | ✅ | 官方 `latest.json` 解析；官方更新镜像优先，兼容版单独标注 |
| HaloTV | 桌面 | `app.halotv.launcher` / `.lite` | 标准版 / Guard / Lite | ✅ | Aerial 分支，可与 Aerial 共存；官方站点优先，GitHub 备用 |
| CleanYourTV | 桌面 | `com.gathin.tvhome` | 官方脚本 / 手机版 App | ✅ 手机版 App | 电视桌面无官方 APK；官方站点推荐的社区手机版可完成清理与桌面安装；卸载前先恢复系统项 |
| 题了个题 StudyOnTV | 工具 | `com.studyontv.tv` | TV 标准版 / TV 本地版 | ✅ + 权限引导 | 答题工具，不是桌面，不改 HOME；蒲公英官方更新 / Gitee / GitHub 动态选最新 |

版本号由官方索引动态解析，不在代码中写死。CleanYourTV 的电视桌面没有官方二进制，这是项目本身的限制；工具市场不伪造电视端一键安装，而是安装官方站点推荐的社区手机版 App 来完成整件事。

下载源策略：

- **StudyOnTV**：同时读取蒲公英官方更新通道（应用自身的更新接口）、作者维护的 Gitee 镜像与 GitHub 官方 Release，只提供版本最新的源。同版本时按 Gitee → 蒲公英 → GitHub 排序。Gitee 当前停留在 0.3.36，而蒲公英与 GitHub 均为 0.5.6，因此国内网络下优先走蒲公英，不会为了速度装旧版。TV 本地版（LocalOnly）只从 Gitee / GitHub 获取，不使用标准版的更新通道。
- **Quiet TV**：官方 `latest.json` 仍是唯一版本来源；APK 按官方更新器公布的主备顺序走 `ghfast.top` → `gh-proxy.com` → `v4.gh-proxy.org` → GitHub 原生地址，并使用索引中的 SHA-256 校验。镜像只改变下载通道，不能改变安装包内容。
- **CleanYourTV**：官方仓库明确不发布 TV launcher 二进制。cleanyour.tv 的 Community apps 页面推荐独立的社区手机版 App（`tv.cleanyour.app`，MIT，作者 udhaykumarbala）。工具市场只从该官方页面链接的 GitHub Release 动态解析最新版，校验 SHA-256、包名与固定签名后交给系统安装器，安装到手机而不是电视。GitHub 发布页域名在部分移动网络不可达时，会改用同一 GitHub API 的官方资产通道（`api.github.com`，仍只重定向到 GitHub 官方资产 CDN），校验规则不变。
- **其他项目**：调研中没有找到作者维护的官方 Gitee 仓库，因此使用各自官方站点 / 官方索引 / GitHub Release；不使用未授权的社区镜像或第三方加速站。
- 所有项目都只允许固定官方主机与路径前缀；网络失败可以切换备用官方源，但 SHA-256、域名、路径、包名、签名证书或版本校验失败时立即停止，不会“换个源再试试”。
- 下载中断会按 HTTP Range 从断点续传，最多尝试 4 次；续传前后都会重新计算哈希，完整性失败绝不重试或切换镜像。

> StudyOnTV 采用 Source Available License — Personal Use Only。官方站点是 <https://chadyi.github.io/StudyOnTV/>，官方备用下载页为 <https://www.pgyer.com/StudyOnTVTV>。工具市场只解析并下载官方渠道的发布，不内嵌、不重新托管 APK；按上游许可证也不打包其官方图标或截图，详情页使用通用占位图标。蒲公英分发的 0.5.6 TV 标准版与 GitHub Release 的 APK 已核对为同一文件（SHA-256 `df5463…0153`）。

## 技术架构

```text
MainActivity + native XML
  ├─ AppDetailPage        全屏详情、截图轮播、版本 / 安装 / 卸载 / HOME 操作
  ├─ MediaCatalog         官方图标与截图映射（仅展示层）
  ├─ ConnectSheet         配对 / 网络 ADB / USB 引导 / 扫描
  ├─ UiData + Catalog     固定精选目录、展示文案与安全边界
  ├─ ApkManager           下载后校验、ADB 安装、读回验收
  ├─ PhoneAppInstaller    官方推荐的 CleanYourTV 手机版：下载、校验、系统安装
  ├─ ApkResolver          官方版本索引 → URL / SHA-256 / 证书
  ├─ HttpDownloader       HTTPS、主机白名单、大小上限、SHA-256
  ├─ DeviceSession        配对 / 授权 / 巡检 / 设置 HOME / 卸载
  ├─ AdbClient            内置 Termux adb 运行时启动器
  └─ LanScanner           mDNS + 私有 IPv4 端口扫描
```

关键实现事实：

- 入口只有 `MainActivity`，`usesCleartextTraffic=false`。权限为 `INTERNET` + `ACCESS_NETWORK_STATE` + `REQUEST_INSTALL_PACKAGES`；最后一项只用于把官方页面推荐的 CleanYourTV 手机版交给系统安装器，安装确认始终由系统弹出，应用不会静默安装。
- 内置 59 个 `arm64-v8a` ADB 运行时库（Termux android-tools），许可证随 APK 打包。
- 下载只允许精确官方主机；GitHub 重定向只允许官方资产 CDN，Gitee 官方镜像只允许 `gitee.com` 及官方附件域名 `foruda.gitee.com`，蒲公英只允许其安装跳转路径与官方 CDN，Quiet TV 只允许其官方更新器公布的 GitHub 代理主机。主机、路径前缀、大小上限、SHA-256（官方提供时）、包名、签名证书、版本都会校验。
- 下载支持断点续传：连接中断时用 HTTP Range 从已接收位置继续，最多 4 次；每次续传都会重新校验已有分片，哈希或来源不匹配立即停止。
- 安装完成后必须从电视读回包名和版本，`ADB Success` 不算最终成功。
- CleanYourTV 手机版只安装到手机本地：从官方页面链接的 GitHub Release 动态解析，校验 SHA-256、包名 `tv.cleanyour.app` 与固定签名，再通过系统 `PackageInstaller` 交给用户确认；不会推送到电视，也不会重新分发社区构建的 TV launcher。
- HOME 切换结果以电视端 `cmd package resolve-activity` / 用户选择结果为准；未确认时如实提示用户手动选择。
- 配对码只用于一次配对调用，日志会脱敏，不持久化。
- 卸载目标永远来自固定目录且必须已安装；系统桌面、系统应用永远不会成为目标。
- 卸载默认桌面前自动执行官方恢复顺序，只有电视确认 HOME 已切回原桌面才删除应用；HOME 无法确认时失败关闭，不让主页留空。删除后还会重新读取通知监听、无障碍、屏保设置并多次重试清理，残留时明确告警。

## 构建

无需 Gradle，默认在本机编译和签名：

```bash
# 工具链检查
tools/build.sh --check

# 默认：本机编译 + 本机签名 + 验收
tools/build.sh

# 可选：远端编译，拉回本机签名（需要免密 SSH）
tools/build.sh --remote --ssh user@host --remote-root ~/toolstore_build

# 单独复验一个 APK
tools/verify-apk.sh dist/tool-store-0.3.0.apk
```

远端构建只拿源码白名单，永远不接触签名材料；APK 拉回后由本机 `zipalign` + `apksigner` 签名。远端主机、独立根目录、SDK 路径均可用 `--ssh`、`--remote-root`、`--wait` 或环境变量覆盖。

首次真实构建会在仓库外生成签名材料：

```text
${TOOLSTORE_KEYSTORE_DIR:-$HOME/.config/tool-store}/tool-store-release.jks
${TOOLSTORE_KEYSTORE_DIR:-$HOME/.config/tool-store}/tool-store-release.pass
```

密钥和密码**不进入 Git 历史**，也不会同步到远端构建机。请自行做离线加密备份；丢失密钥后，已安装该签名的设备无法直接覆盖升级，只能卸载重装。

详细流程、环境要求、验收门禁、退出码与故障处理见 **[docs/BUILD.md](docs/BUILD.md)**。

构建产物：

```text
dist/tool-store-0.3.0.apk
dist/tool-store-0.3.0.apk.sha256
.build/build-<runid>.log
```

安装到手机测试：

```bash
adb install -r dist/tool-store-0.3.0.apk
```

如果手机上装过另一把签名密钥构建的旧版本，需要先卸载旧版本；卸载会清除该应用数据。

## 安全边界

- 局域网 ADB 直连电视，**绝不把 5555 端口暴露到公网**，也不做端口转发。
- 不卸载、不停用电视自带桌面和系统应用。
- 默认桌面只做用户明确选择的操作；未确认就报“未接管”，不假装成功。
- 设备管理员、无障碍、悬浮窗、使用情况访问等权限由用户在电视上手动决定，安装器不静默开启。
- 下载失败、索引异常、主机不在白名单、大小异常、证书不符时失败关闭，不使用旧缓存。
- 不把配对码、签名密码、家长密码或恢复码写入日志。
- 签名密钥和密码只存在于本机（默认 `~/.config/tool-store/`），不进入 Git 历史，也不同步到远端构建机；请离线备份并限制文件权限。

## 设计文档与原型

- [docs/BUILD.md](docs/BUILD.md) — 原生构建、可选远端隔离、签名与验收
- [docs/UI-DESIGN.md](docs/UI-DESIGN.md) — 信息架构、卡片系统、状态与原生实现说明
- [docs/ARTWORK.md](docs/ARTWORK.md) — 图标 / 截图来源、处理规则与权利说明
- [docs/APP-MATRIX.md](docs/APP-MATRIX.md) — 六个项目的包名、来源、安装 / 卸载与许可风险
- [prototype/index.html](prototype/index.html) — 交互原型，仅用于设计评审；HTML / JS 不会被打进 APK

原型可以直接双击打开，或在 `prototype/` 下执行 `python3 -m http.server 8000`。原型是交流设计用的浏览器演示，不是应用运行界面。

## 已知限制

- 打包的 ADB 运行时只有 `arm64-v8a`；应用 minSdk 24。不支持 32 位手机或 Android 6 及以下。
- 官方索引或 Release 变更时，应用会失败关闭并提示稍后重试，不会回退到来源不明的 APK。
- 只有 StudyOnTV 找到了作者维护的官方 Gitee 镜像和蒲公英官方更新通道，而且 Gitee 镜像当前可能滞后；其他项目没有官方 Gitee，不能把社区镜像当作官方源。应用会动态比较版本，最新版优先，速度其次；镜像落后时如实提示，不会自动降级。
- GitHub 在部分国内网络下仍可能很慢或超时；StudyOnTV 标准版使用其自身的蒲公英更新通道，Quiet TV 使用其官方更新器公布的 GitHub 代理镜像，其他项目仍以官方站点为主。没有引入项目未公布的第三方加速或代理，避免把 APK 下载交给不可信中间人。CleanYourTV 手机版只有 GitHub Release 这一官方页面公布的渠道，网络不通时只能稍后重试或改用电脑脚本。
- CleanYourTV 的电视桌面没有官方 APK：可以用官方电脑脚本，或安装官方站点推荐的社区手机版 App。工具市场不重新分发社区构建的 TV launcher；卸载和恢复仍按官方脚本或该 App 的恢复点进行。
- 本轮只完成编译、签名、静态验收，尚未在真实电视上验证无线配对、HOME 接管、待机和卸载恢复；真机发布验收清单见 `docs/BUILD.md`。
- 站长收录目前是内置固定目录；远程审核目录是后续扩展方向。

## 网络与代理

开发机访问外网或拉取官方索引时，可按自己的环境配置 HTTPS 代理；构建本身只需要本地 SDK。应用内的 ADB 局域网连接永远直连电视，不经过代理，也不做端口转发。

## 许可证

本项目源代码采用 [MIT License](LICENSE)。收录的第三方应用、图标、截图、ADB 运行时等分别归其作者所有，详见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) 和 [assets/NOTICE-artwork.txt](assets/NOTICE-artwork.txt)。

本项目与所列第三方项目无隶属关系；不内嵌、不重新托管、不再分发其 APK。StudyOnTV 采用 Source Available License — Personal Use Only，本项目只链接并从其官方渠道下载安装包，不打包其官方图标或截图。