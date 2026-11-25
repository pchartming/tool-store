# tool-store 工具市场

一个面向 Android 手机端的精选电视工具市场：手机和电视连接同一个 Wi-Fi，通过局域网 ADB 发现电视、配对授权、下载并校验官方 APK，然后完成安装、卸载与默认桌面管理。

界面是纯原生 Java + XML，不使用 WebView、Gradle、AndroidX 或 Compose。

## 下载

已签名的发布 APK 和 SHA-256 校验文件放在 Releases：

- GitHub: <https://github.com/pchartming/tool-store/releases/latest>
- Gitee: <https://gitee.com/pchartming/tool-store/releases/latest>

发布包仅支持 `arm64-v8a`，minSdk 24（Android 7.0），targetSdk 34。

## 源码说明

公开仓库只保留运行所需的最小源码、资源和原生运行时：

- `src/` — Java 源码
- `res/` — 原生 XML 布局与图片资源
- `assets/` — 图标、第三方许可与运行时通知
- `jniLibs/` — arm64-v8a ADB 运行时
- `AndroidManifest.xml`

内部设计文档、构建与发布脚本、签名材料和构建产物不在公开仓库中。发布包由维护者在私有流程中构建、签名并验收；本仓库不包含构建脚本，源码仅供审计与参考。

## 安全边界

- 局域网 ADB 直连电视，不把 5555 端口暴露到公网，也不做端口转发。
- 不卸载、不停用电视自带桌面和系统应用。
- 下载只允许项目固定的官方主机；主机、路径、SHA-256、包名或签名证书不符时失败关闭。
- 不内嵌、不重新托管第三方 APK。
- 签名私钥和密码不进入公开仓库；发布 APK 只通过 Releases 分发。

## 许可证

本项目源代码采用 [MIT License](LICENSE)。收录的第三方应用、图标、截图、ADB 运行时分别归其作者所有，详见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) 和 [assets/NOTICE-artwork.txt](assets/NOTICE-artwork.txt)。

StudyOnTV 采用 Source Available License — Personal Use Only；本项目只链接并从其官方渠道下载安装包，不打包其官方图标或截图。