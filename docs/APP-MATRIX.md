# 精选电视应用：安装 / 卸载能力矩阵

> 调研时间：2026-11-23。版本、下载文件名和索引会变化，应用内应从官方 Release / `latest.json` / `version.txt` 动态读取，不要写死本文件的版本号。

## 一句话结论

工具市场当前收录 **5 个 Android TV 桌面 + 1 个答题工具**。桌面和工具都通过局域网 ADB 安装，都不需要 root；但两者的边界不同：

- **桌面**会参与默认 HOME 的竞争，安装后可选“设为默认桌面”；卸载时由工具市场按官方顺序自动切回原桌面并确认（CleanYourTV 仍需先在电脑运行官方 `restore.sh`）。
- **工具**（题了个题）**不改变默认 HOME**，通常需要用户在电视上按需开启悬浮窗、使用情况访问等权限；安装器负责装包、引导与验收，不替用户静默开启高风险权限。

| 项目 | 类型 | 包名 | 可用版本 | 最低系统 | 官方下载 | Gitee | 手机一键装 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PlanYourTV | 桌面 | `com.planyourtv.tv` | 标准版 / Guard | Android 5.0+ | [planyourtv.me/download](https://planyourtv.me/download/) | ❌ 无官方 Gitee | ✅ |
| CleanYourTV | 桌面 | `com.gathin.tvhome` | 设备配置列表（无官方 APK） | 桌面 Android 8.0+ | [cleanyour.tv](https://cleanyour.tv) | ❌ 未确认 | ✅ 手机版 App（官方站点推荐） |
| Aerial | 桌面 | `app.orchard.tv` / `.lite` | 标准版 / Guard / Lite | 5.0+；Lite 4.0–4.4 | [aerial.qust.me](https://aerial.qust.me) | ❌ 未确认 | ✅ |
| Quiet TV | 桌面 | `home.quiet.tv` | 标准版 / 4.x 兼容版 | 5.0+；兼容版 4.0–4.4 | [beatsad.github.io/quiet-tv](https://beatsad.github.io/quiet-tv/) | ❌ 未确认 | ✅ |
| HaloTV | 桌面 | `app.halotv.launcher` / `.lite` | 标准版 / Guard / Lite | 5.0+；Lite 4.0+ | [halo-tv.pages.dev](https://halo-tv.pages.dev/) | ❌ 未确认 | ✅ |
| 题了个题 StudyOnTV | 工具 | `com.studyontv.tv` | TV 标准版 / TV 本地版 | Android 5.0+ | [官网](https://chadyi.github.io/StudyOnTV/) · [GitHub Release](https://github.com/chadyi/StudyOnTV/releases/latest) · [蒲公英](https://www.pgyer.com/StudyOnTVTV) | ✅ [chadyi/StudyOnTV](https://gitee.com/chadyi/StudyOnTV/releases) | ✅ + 权限引导 |

> 目前确认的**作者维护 Gitee 镜像**只有 StudyOnTV。PlanYourTV 的 `liurumbo/planyourtv` 在仓库描述中明确写着 “community source snapshot (private mirror)”，属于社区镜像，不是作者官方发布渠道，工具市场不使用。Gitee 镜像还可能滞后于 GitHub 最新 Release，应用必须动态比对版本与资产，不能直接假设镜像最新。

---

## 1. PlanYourTV

- 仓库：<https://planyourtv.me/>
- 官网下载：<https://planyourtv.me/download/>
- 官方 Gitee：无。社区快照 `liurumbo/planyourtv` 不是作者发布渠道，不采用。
- 包名：`com.planyourtv.tv`
- 类型：儿童时间管理桌面
- 版本：标准版 / Guard（固件拒绝桌面类 APK 时换 Guard）
- 最低系统：Android 5.0（API 21）
- 说明：以官网 `version.txt` 与固定 APK 文件名为唯一官方来源；没有官方 Gitee 镜像。

### 安装

官方安装器不只做 `adb install`，还会做权限、通知监听、默认桌面和验收。核心步骤：

```bash
adb connect <TV_IP>:5555
adb install -r -i com.planyourtv.tv planyourtv-standard.apk
adb shell pm grant com.planyourtv.tv android.permission.WRITE_SECURE_SETTINGS
adb shell cmd notification allow_listener com.planyourtv.tv/app.orchard.tv.guard.RevivalListener
adb shell appops set com.planyourtv.tv SYSTEM_ALERT_WINDOW allow
adb shell am start -n com.planyourtv.tv/app.orchard.tv.MainActivity
adb shell cmd package compile -m speed -f com.planyourtv.tv
```

安装器还会按机型处理使用统计、通知监听、默认桌面 / HOME role、海信属性、小米开机组件等。**通用的“装 APK”只能算完成第一步**。

### 卸载 / 恢复

- 先恢复默认桌面、移除 PlanYourTV 的无障碍守护和通知监听，再卸载。
- `adb shell pm uninstall com.planyourtv.tv` 会删除规则、题库、壁纸、照片和一次性恢复码。
- 官方安装器提供还原 / `--uninstall` 路径；不要只卸载 APK 而把 Home 留在空状态。
- 不要动电视自带桌面和其他第三方桌面。

### UI 必须体现

- 版本选择：标准版 / Guard，并说明 Guard 的使用场景。
- 下载源顺序：官网（唯一官方源）；不要用社区 Gitee 镜像冒充官方。
- 安装后验收：Home 键、前台应用、无障碍服务是否真的生效。
- 卸载警告：数据删除 + 恢复码失效 + 自动切回原桌面并恢复系统设置。

---

## 2. CleanYourTV

- 仓库：<https://github.com/Gathin23/cleanyourtv>
- 官网：<https://cleanyour.tv>
- 包名：`com.gathin.tvhome`
- 最低系统：当前 launcher APK 要求 Android 8.0（API 26）+；不同电视的清理列表兼容性不同。
- 关键事实：**官方 Release 不提供二进制 APK。** 发布说明原话是 “No binaries, as always: build the launcher with `launcher/build-and-install.sh`.”

### 官方安装

需要一台与电视同局域网的电脑，并准备：

- Python 3
- JDK 17–22
- Android SDK Platform 34 + Build Tools 34.x
- 可执行命令的 Agent（豆包电脑版 / Claude Code / Codex 等）

官方流程会：

1. 备份已安装应用和恢复状态；
2. 用 `pm disable-user` 停用广告、遥测、推荐和不需要的预装应用；
3. 编译并安装 `com.gathin.tvhome`，切换默认 HOME；
4. 用 `tools/restore.sh` 恢复被停用的应用和原始桌面。

### 为什么不能直接下载官方电视桌面 APK

- 官方没有可下载的 launcher APK；
- 完整价值来自“按机型清理”，不是单独一个桌面 APK；
- 在电视上现场编译 Android APK 不现实；
- 如果工具市场自带一份社区构建的 TV launcher，只能算“装桌面”，不能算“CleanYourTV 完整安装”，还要处理签名、版本、许可证和厂商风险。

所以工具市场不内置、不重新分发 TV launcher；手机端的完整体验交给官方页面推荐的社区 App（见下）。

### 手机版 App（官方站点推荐）

cleanyour.tv 的 [Community apps](https://cleanyour.tv/app/android) 页面现在推荐一个独立维护的手机端项目
[udhaykumarbala/cleanyourtv-app](https://github.com/udhaykumarbala/cleanyourtv-app)（MIT，2.4k+ stars）。
它把 launcher、清理列表和进度页内置进一个手机 APK（`tv.cleanyour.app`，Android 9+），在手机上通过局域网完成清理，并在支持的电视上安装 CleanYourTV 桌面。官方页面明确说明它“made and maintained separately”，问题应报给它自己的仓库。

工具市场 0.3.0 起内置这条路径：

- 版本只从该仓库的 GitHub Releases API 动态解析；安装包优先走同一 API 的资产通道（`api.github.com` + `Accept: application/octet-stream`），失败时回退到 cleanyour.tv 官方页面链接的 Release 地址。两条路线都只允许 GitHub 官方主机，并且只重定向到 GitHub 官方资产 CDN；
- 校验 Release 提供的 SHA-256、包名 `tv.cleanyour.app` 与固定的签名证书；
- 通过系统 `PackageInstaller` 安装到手机，必须由用户在系统弹窗中确认，不会静默安装；
- 这条路径只安装手机 App，不直接操作电视，也不把社区构建的 TV launcher 当作官方 APK 分发。

其它限制：

- Fire TV、TCL 中国版、Android 7 及更旧设备不能完整支持；
- Android 8+ 的兼容设备可以清理并安装桌面；
- 所有更改都保留恢复路径（App 内的恢复点）。

### 卸载 / 恢复

- 只卸载 `com.gathin.tvhome` **不会恢复**被停用的系统应用，也不会自动装回原桌面。
- 必须先在电脑上运行官方 `tools/restore.sh`，恢复清理列表和默认 HOME；
- 再卸载 launcher；卸载会删除桌面数据。

### UI 必须体现

- 卡片上直接标记“需电脑”，不要放一个会失败的“安装”按钮；
- 详情页给三步引导：准备电脑环境 → 复制官方 Agent 指令 → 完成后回来检测安装状态；
- 已安装时，卸载按钮必须先让用户确认“已执行恢复脚本”；
- 文案不要暗示“去广告可以一键完成”。

---

## 3. Aerial

- 仓库：<https://github.com/pengchujin/aerial-tv>
- 官网：<https://aerial.qust.me>
- 官网安装说明：<https://aerial.qust.me/agent.md>
- 包名：
  - 标准版 / Guard：`app.orchard.tv`
  - Lite：`app.orchard.tv.lite`
- 版本：标准版 / Guard / Lite
- 最低系统：Android 5.0+；Lite 面向 Android 4.0–4.4（测试版）
- 类型：tvOS 风格轻桌面，去除广告与推荐，不卸载、不停用系统桌面

### 安装

官方脚本 `tools/install.sh` 会下载 APK、安装、授予权限、设置 Home、开启守护并验收。手动核心命令：

```bash
adb install -r aerial-standard.apk
adb shell pm grant app.orchard.tv android.permission.WRITE_SECURE_SETTINGS
adb shell cmd notification allow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener
adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW allow
adb shell am start -n app.orchard.tv/.MainActivity
```

Lite：

```bash
adb install -r aerial-lite.apk
adb shell am start -n app.orchard.tv.lite/.HomeActivity
```

### 卸载 / 恢复

- 官方 `tools/restore.sh`：先停用守护、撤销通知 / 无障碍 / AppOps，再恢复默认主页；加 `--uninstall` 才卸载 APK。
- `app.orchard.tv` **也可能是旧版 HaloTV**。不能只看包名就断定是 Aerial。
- 卸载前应读取实际应用名和版本并在界面展示；如果无法确认，要求用户到电视上核对。

### UI 必须体现

- 版本选择：标准版 / Guard / Lite，推荐逻辑按 Android 版本和安装失败原因。
- 下载：官网固定文件优先，GitHub Release 备用。
- 卸载时显示“检测到的应用名与版本”，并提示包名共用风险。
- 不要自动卸载 `app.orchard.tv` 来“解决冲突”。

---

## 4. Quiet TV

- 仓库：<https://github.com/BeatsAD/quiet-tv>
- 安装页：<https://beatsad.github.io/quiet-tv/>
- 最新版索引：<https://beatsad.github.io/quiet-tv/latest.json>
- Agent 入口：<https://beatsad.github.io/quiet-tv/agent.md>
- 包名：`home.quiet.tv`
- 类型：极简、可配置、安静无推荐的电视桌面
- 版本：标准版（Android 5.0+）/ 4.x 兼容版（Android 4.0–4.4）
- 注意：官方索引当前可能把新版标为 `prerelease: true`，界面应显示“测试版”，不要让用户误以为是稳定版。

### 版本选择

不要自己拼接文件名。读 `latest.json`：

- 选 `packageName == "home.quiet.tv"`；
- 按电视 Android / ABI / 签名 / 构建号筛选；
- 使用 `releases[].assets[].url`、`bytes`、`sha256`；
- 安装后把 APK 拉回电视或重新读取版本 / 签名，不能只看 `Success`。

### 下载通道

Quiet TV 官方更新器在 `docs/UPDATES.md` 公布固定主备顺序：`ghfast.top` → `gh-proxy.com` → `v4.gh-proxy.org` → GitHub 原生地址。工具市场只把官方附件 `url` 换成这些官方镜像前缀，仍以 `latest.json` 的 `sha256` 逐字节校验；镜像超时才切换下一源，哈希不匹配立即停止，不再尝试其他镜像。不使用其他第三方加速站。

官方安装分 `trial` 和 `takeover` 两步：先试用基础导航，再配置 HOME 和必要的 ADB；不同固件有不同接管路线。手机安装器可以完成基础的 APK 安装 + 默认桌面选择，但复杂机型的完整接管应交给官方电脑 Agent。

### 安装

```bash
adb install -r --user 0 QuietTV.apk
adb shell am start --user 0 -n home.quiet.tv/.HomeActivity
```

### 卸载 / 恢复

- 先切回原桌面，保留 / 恢复原桌面状态（工具市场 0.2.4 起自动执行并确认）；
- `adb shell pm uninstall home.quiet.tv` 会删除桌面数据；
- 官方恢复入口会尽量只撤销有记录的修改，不猜系统属性；
- 某些固件上的 `com.coocaa.quiettv` 是厂商预装形态，不要把它当普通用户安装包直接卸载。

### UI 必须体现

- “测试版”标记，并说明官方索引是唯一版本来源；官方 GH 代理镜像只作为下载通道；
- 标准版 / 兼容版选择；
- 复杂接管失败时，提供“复制官方电脑接管指令”的出口；
- 卸载时区分 `home.quiet.tv` 和厂商预装 `com.coocaa.quiettv`。

---

## 5. HaloTV

- 仓库：<https://github.com/Null9797/halo-tv>
- 官网：<https://halo-tv.pages.dev/>
- 安装说明：<https://github.com/Null9797/halo-tv/blob/main/INSTALL.md>
- 包名：
  - 标准版 / Guard：`app.halotv.launcher`
  - Lite：`app.halotv.launcher.lite`
- 版本：标准版 / Guard / Lite
- 最低系统：标准版 / Guard Android 5.0+；Lite Android 4.0+
- 类型：Aerial 的独立包名分支，可与 Aerial 共存
- 说明：旧版 HaloTV 可能使用 `app.orchard.tv`。

### 安装

```bash
adb install -r HaloTV-3.5.4-standard.apk
adb shell am start -n app.halotv.launcher/app.orchard.tv.MainActivity
```

Lite：

```bash
adb install -r HaloTV-3.5.4-lite.apk
adb shell am start -n app.halotv.launcher.lite/app.orchard.tv.lite.HomeActivity
```

注意：

- 标准版与 Guard 共用包名，二选一；
- Lite 独立包名；
- 独立版**不继承旧版权限**，无障碍、通知、自启动、悬浮窗要重新确认；
- 不要用上游 Aerial 的 `tools/install.sh` 修 HaloTV；官方提供自己的诊断脚本；
- 当前独立版不支持从旧 `app.orchard.tv` 直接升级 / 导入数据。

### 卸载 / 恢复

```bash
adb shell pm uninstall app.halotv.launcher
adb shell pm uninstall app.halotv.launcher.lite
```

- 先切回其他默认桌面、停止守护，再卸载；
- 不要盲目卸载 `app.orchard.tv`，它可能是 Aerial 或旧版 HaloTV；
- 卸载会删除独立版私有数据。

### UI 必须体现

- 独立条目，不能和 Aerial 合并；
- 版本选择 + “权限不会继承”的提醒；
- 卸载时明确包名，不显示“卸载 HaloTV”却操作 `app.orchard.tv`；
- 失败时引导使用 HaloTV 自己的诊断工具，而不是 Aerial 脚本。

---

## 6. 题了个题 StudyOnTV

- 官网：<https://chadyi.github.io/StudyOnTV/>（GitHub Pages，只链接 GitHub Release 与文档，不托管 APK）
- 仓库：<https://github.com/chadyi/StudyOnTV>
- 最新 Release：<https://github.com/chadyi/StudyOnTV/releases/latest>
- Gitee 镜像：<https://gitee.com/chadyi/StudyOnTV/releases>（当前 0.3.36，滞后）
- 蒲公英官方更新：<https://www.pgyer.com/StudyOnTVTV>（TV）/ <https://www.pgyer.com/StudyOnTVMobile>（手机 / 平板）。应用自身的更新器走蒲公英公共更新接口；工具市场读取同一接口，只取最新版本，不使用账号 API 密钥。
- 一致性：蒲公英最新 TV 标准版 0.5.6 与 GitHub Release TV 标准版已核对为同一文件（3,545,198 字节，SHA-256 `df5463ea047f86d15843e640f9af165d12cee9f509352ca869390c07a37d0153`，包名与签名证书一致）。
- TV 包名：`com.studyontv.tv`
- 手机 / 平板包名：`com.studyontv.mobile`（独立包，不与 TV 版互换）
- 类型：**答题 / 屏幕时间工具，不是桌面**
- 版本：TV 标准版；TV 本地版（EU LocalOnly）
- 最低系统：Android 5.0+（API 21）；调研时最新 0.5.6（2026-11-24）的 targetSdk 为 34
- 许可：**Source Available License — Personal Use Only**，版权 2024 chadyi；不是 OSI 开源许可证

### 两个 TV 变体

| 变体 | 资产示例 | 说明 |
| --- | --- | --- |
| TV 标准版 | `StudyOnTV-Android-TV-<版本>.apk` | 推荐；可使用在线 AI、语音和更新等联网能力 |
| TV 本地版 | `StudyOnTV-Android-TV-EU-LocalOnly-<版本>.apk` | 零公网 / 隐私优先；核心规则仍在本地运行，在线能力不可用 |

两个变体使用同一个包名，切换变体属于覆盖安装，需要核对签名与版本；不要同时安装。

### 它与桌面的关系

- 不申请 HOME / launcher role，**不会替换默认桌面**。
- 安装、卸载都不应读取或修改默认 HOME 为 PlanYourTV / Aerial / 系统桌面。
- 它通过悬浮窗、使用情况访问等能力判断前台应用和计时；部分机型需要额外开启无障碍。
- 设备管理员只是可选的“防卸载”增强，安装器**不得自动开启**；卸载前必须先取消。

### 核心能力

- 答题解锁电视时间，家长扫码进入局域网管理面板；
- 家长面板地址形如 `http://<电视IP>:8099/`，路径 `/admin` 打开同一面板；需要首次设置的管理密码；
- 题库、规则、使用记录保存在被控设备，本地 SQLite；没有项目自建的云端账号体系；
- 支持单选、多选、判断、填空、主观题、公式、语音等；在线 AI / 语音属于可选联网能力；
- 家长可设置每日上限、答题解锁时长、时段、星级奖励、加练、错题优先等。

**不要把“家长面板”做成工具市场上的账号服务。** 工具市场只负责安装和权限引导；管理入口在电视本地局域网。

### 权限说明

| 权限 | 作用 | 安装器策略 |
| --- | --- | --- |
| 显示在其他应用上层 / 悬浮窗 | 到时间显示学习页、解锁提醒；部分电视只能用 AppOps 授权 | 引导用户在电视上开启；可提供 `appops` 命令，但不静默授权 |
| 使用情况访问 | 识别前台应用、准确计时、拦截系统设置 | 跳转系统设置，由用户确认 |
| 无障碍服务 | 增强应用切换识别与显示学习页 | **可选**，默认不开启；系统无入口时允许跳过 |
| 自启动 / 后台运行 | 重启后恢复计时与家长规则 | 厂商设置项，给出引导，不保证所有机型 |
| 设备管理员 | 增强防卸载 | **可选且高风险**；安装器绝不自动开启，卸载前必须取消 |

部分机型精简了悬浮窗入口，可用：

```bash
adb shell appops set com.studyontv.tv SYSTEM_ALERT_WINDOW allow
adb shell am start -a android.settings.USAGE_ACCESS_SETTINGS
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
```

ADB 能打开入口，不等于可以替用户决定授权。UI 必须把“已打开设置页”“用户已授权”和“未授权”区分开。

### 安装

```bash
adb connect <TV_IP>:5555
adb install -r StudyOnTV-Android-TV-<版本>.apk
adb shell monkey -p com.studyontv.tv -c android.intent.category.LAUNCHER 1
```

安装完成后：

1. 首次打开，让用户在电视上设置管理密码；
2. 展示权限引导，用户按需在电视上开启；
3. 提示用同一局域网的手机扫码或访问 `http://<电视IP>:8099/`；
4. 验收包名、版本、启动前台；**不验收 HOME 变化**，因为不应有变化。

### 卸载 / 恢复

```bash
adb shell dpm remove-active-admin com.studyontv.tv/<AdminReceiver>  # 若用户已启用防卸载
adb shell pm uninstall com.studyontv.tv
```

- 若启用了设备管理员，必须先由用户取消，否则卸载会失败；
- 卸载会永久删除题库、规则、使用记录和加练池；
- 卸载**不会改变默认 HOME**；不要顺手恢复 / 设置任何桌面；
- 手机 / 平板版 `com.studyontv.mobile` 是另一个包，不能拿 TV 安装器卸载它。

### UI 必须体现

- 卡片明确“工具”而不是“桌面”，并强调“不改默认 HOME”；
- 版本选择：TV 标准版 / TV 本地版，解释联网与本地差异；
- 来源：蒲公英官方更新、Gitee 官方镜像与 GitHub 官方 Release 动态比对；只选最新版本，同版本时按 Gitee → 蒲公英 → GitHub 排序。Gitee 滞后时使用蒲公英或 GitHub，不自动降级；TV 本地版跳过蒲公英，保持零公网语义；
- 许可证提示：仅个人使用，不是 OSI 开源；工具市场不内嵌、不再分发 APK；
- 权限引导逐项展示，标注“必需 / 可选”，设备管理员单独警示；
- 安装成功页显示默认 HOME“未更改”，并提供打开应用 / 权限引导；
- 卸载确认：数据删除 + 设备管理员先取消 + HOME 不变。

---

## 通用安装与卸载规则

### 应用类型

每个条目必须显式声明 `category`：

- `launcher`：桌面，可参与 HOME；安装选项可出现“设为默认桌面”；卸载需要先恢复 HOME。
- `tool`：普通应用工具，不参与 HOME；安装选项不出现 HOME；卸载不应触碰 HOME。

未来收录新工具时，优先复用 `tool` 模型，并补充：

- `installable`：是否有可供手机安装的官方 APK；
- `needsPc`：是否需要电脑 / Agent 完成；
- `permissionGuide`：用户在电视上需要开启的权限；
- `permissionCommands`：可复制的 ADB 辅助命令；
- `uninstallWarning`：删除的数据、设备管理员、常驻服务等；
- `licenseNote`：尤其是“源码可见 / 仅个人使用”这类非 OSI 许可。

### 下载源

1. 作者维护的官方 Gitee 镜像（目前只有 StudyOnTV；必须与其他渠道比对版本，镜像滞后时不得装旧版）；
2. 应用自身的官方更新通道（StudyOnTV 标准版使用蒲公英；Quiet TV 使用官方更新器公布的 GitHub 代理镜像；必须与应用作者公开的接口 / 顺序一致）；
3. 项目官方站 / 官方索引（PlanYourTV、Aerial、HaloTV、Quiet TV）；
4. GitHub 官方 Release / API；
5. 官方备用入口（如 Aerial / HaloTV 官网或 GitHub 备用、StudyOnTV 蒲公英页面）；
6. 全部失败：停止并显示原始错误，不安装旧缓存、不猜文件名。

不要把社区镜像或第三方加速站当作官方源；下载器按固定官方主机和路径前缀白名单执行。

下载后必须做：

- SHA-256 校验（官方提供时）；
- 包名 / 版本 / 签名核对；
- 安装前检查 Android 版本、存储空间、同包名签名冲突；
- 安装后重新读取版本，并检查默认桌面 / 前台应用 / 权限状态。

### ADB 连接

- 默认 `IP:5555`，但 Android 11+ 可能只提供无线调试配对，需要 `adb pair`；不要假设所有电视都是 5555。
- `unauthorized` 时让用户在电视弹窗选“始终允许”，不要反复重连。
- 不要把 5555 暴露到公网。
- 局域网 ADB 不走下载代理。
- 下载代理只影响 HTTP 下载；开发环境的代理配置不是电视连接方式。

### 桌面共存

- 可以同时安装多个第三方桌面；
- 只有一个默认 HOME；
- 不要同时开启多个 Home Guard；
- 不得卸载、停用电视自带桌面或系统应用；
- 卸载默认桌面之前，工具市场会自动切回原桌面并读回确认；CleanYourTV 例外，仍需先运行官方 `restore.sh`。

### 卸载顺序

桌面：

```text
停用该桌面的守护 / 通知监听 / 无障碍
        ↓
恢复被它修改的屏保 / 开机应用 / 厂商 HOME 设置
        ↓
把默认 HOME 切回电视原桌面（原桌面被停用时先 pm enable）
        ↓
确认 HOME 已恢复
        ↓
卸载目标包（数据会删除）
        ↓
读回默认 HOME 和包状态，确认没有留下空桌面
```

工具市场 0.2.4 起把上述顺序放在一次“卸载”操作内自动执行；任一步无法确认时在删除应用前停止。

工具：

```text
确认没有启用设备管理员（如启用，先由用户取消）
        ↓
停止服务 / 悬浮窗 / 通知监听（按应用能力）
        ↓
卸载目标包（数据会删除）
        ↓
读回默认 HOME，确认它没有被改变
```

CleanYourTV 额外一步：**先运行官方 `restore.sh` 恢复被停用的系统应用，再卸载 launcher。**

### 许可与上架

- PlanYourTV / CleanYourTV / Aerial / Quiet TV / HaloTV 分别遵循各自仓库许可证；上架页应链接原仓库。
- StudyOnTV 为 **Source Available License — Personal Use Only**：个人非商业使用可下载、编译、安装、修改，但不得再分发衍生作品、不得商用。工具市场不得内嵌或重新托管其 APK，也不打包其官方图标 / 截图，只能链接并从官方 Release / Gitee / 蒲公英官方更新通道下载。
- 未来收录新工具，先确认：官方二进制是否存在、是否允许第三方分发、是否需要设备管理员 / 无障碍等高风险权限。

---

## 参考链接

- PlanYourTV：<https://planyourtv.me/> · <https://planyourtv.me/download/>
- CleanYourTV：<https://github.com/Gathin23/cleanyourtv> · <https://cleanyour.tv>
- Aerial：<https://github.com/pengchujin/aerial-tv> · <https://aerial.qust.me>
- Quiet TV：<https://github.com/BeatsAD/quiet-tv> · <https://beatsad.github.io/quiet-tv/latest.json> · <https://github.com/BeatsAD/quiet-tv/blob/main/docs/UPDATES.md>
- HaloTV：<https://github.com/Null9797/halo-tv> · <https://halo-tv.pages.dev>
- 题了个题 StudyOnTV：<https://chadyi.github.io/StudyOnTV/> · <https://github.com/chadyi/StudyOnTV> · <https://gitee.com/chadyi/StudyOnTV/releases> · <https://www.pgyer.com/StudyOnTVTV>
- 社区 CleanYourTV 手机端：<https://github.com/udhaykumarbala/cleanyourtv-app>