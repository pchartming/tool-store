# 官方图标与截图素材

工具市场从 0.2.0 起在卡片和全屏详情页使用应用图标与界面截图，替代字母占位图；0.3.0 起按各项目许可证收紧了素材范围。

原则：

- 素材只用于**识别和介绍对应项目**，版权归原作者。
- 除 StudyOnTV 外，素材取自官方公开仓库或官方网站；下载后打包进 APK，不在运行时联网取图。
- 不引入图片加载库、不引入 WebView，也不新增权限。
- StudyOnTV 为 Source Available License — Personal Use Only；上游许可证限制使用其名称、Logo 和品牌形象进行推广。工具市场**不打包**其官方图标/截图，详情页使用通用占位图标并隐藏截图区；也不内嵌、不重新托管、不再分发它的 APK。
- 权利人要求移除时，删除 `res/drawable-nodpi/` 中对应文件并更新 `MediaCatalog` 即可；页面会退回默认图标并隐藏截图区。

## 归一化规则

| 类型 | 规则 | 输出 |
| --- | --- | --- |
| 图标 | 保持正方形与透明背景，最长边 ≤ 256px | PNG，`app_icon_<id>.png` |
| 截图 | 16:9 居中裁切，宽度 960px | WebP q82，`app_shot_<id>_<n>.webp` |

当前素材总量约 590 KB，5 个图标 + 15 张截图。详情页的轮播使用本地 `R.drawable`，不请求网络。

## 来源清单

| 应用 | 图标来源 | 截图来源（3 张） | 官方页面 |
| --- | --- | --- | --- |
| PlanYourTV | PlanYourTV 官方发布的电视端启动图标（192×192） | planyourtv.me：`home-clean.jpg`、`quiz-question.jpg`、`quiz-result.jpg`（1600×900） | <https://planyourtv.me/> |
| Aerial | `pengchujin/aerial-tv` · `docs/brand/icon.png`（256×256） | `site/img/home.jpg`、`focus.jpg`、`folder.jpg`（1600×900） | <https://aerial.qust.me/> |
| Quiet TV | `BeatsAD/quiet-tv` · `app/src/main/res/drawable-nodpi/quiettv_launcher.png`（512×512） | `docs/assets/home-1.3.0.png`、`app-management-1.3.0.png`、`wallpaper-1.3.0.png`（1920×1080） | <https://beatsad.github.io/quiet-tv/> |
| HaloTV | `Null9797/halo-tv` · `website/images/brand/halotv.png`（384×384） | `website/images/showcase/main.jpg`、`control.jpg`、`settings.jpg`（1920×1080） | <https://halo-tv.pages.dev/> |
| CleanYourTV | `Gathin23/cleanyourtv` · `launcher/app/src/main/res/mipmap-xhdpi/ic_launcher.jpg`（192×192，官方仅此密度） | `docs/new-home.jpg`、`app-library.jpg`、`focus-2-up-netflix.png` | <https://cleanyour.tv/> |
| 题了个题 StudyOnTV | 不打包官方图标，使用工具市场通用占位图标 | 不打包截图 | <https://chadyi.github.io/StudyOnTV/> |

完整原始 URL 与权利说明随 APK 打包在 `assets/NOTICE-artwork.txt`；第三方项目的许可证摘要见根目录 [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md)。

## 代码位置

- `src/com/toolstore/installer/MediaCatalog.java`：appId → 图标 / 截图 / 说明 / 来源。
- `src/com/toolstore/installer/RoundedImageView.java`：framework `ImageView` 圆角裁切和细边框。
- `src/com/toolstore/installer/AppDetailPage.java`：Hero、截图轮播、页码圆点、固定底栏。
- `res/drawable-nodpi/`：5 个图标与 15 张截图。

安装、卸载、HOME 切换与权限引导的事实仍来自 `Catalog` / `UiData`，素材层不参与任何安装决策。