package com.toolstore.installer;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presentation copy and brand details for the fixed catalog. Installation
 * facts (package names, URLs, signer pins) remain in Catalog/ApkResolver; this
 * class only decides how an entry is explained to the user.
 */
final class UiData {

    static final int TONE_NONE = 0;
    static final int TONE_INFO = 1;
    static final int TONE_WARN = 2;
    static final int TONE_RED = 3;
    static final int TONE_GREEN = 4;

    static final class Variant {
        final String id;
        final String label;
        final String note;

        Variant(String id, String label, String note) {
            this.id = id;
            this.label = label;
            this.note = note;
        }
    }

    static final class Permission {
        final String title;
        final String desc;
        final boolean optional;
        final boolean highRisk;

        Permission(String title, String desc, boolean optional, boolean highRisk) {
            this.title = title;
            this.desc = desc;
            this.optional = optional;
            this.highRisk = highRisk;
        }
    }

    static final class App {
        final Catalog.App catalog;
        final String tagline;
        final String desc;
        final String letter;
        final int color;
        final String[] features;
        final String sourceShort;
        final String minAndroid;
        final String licenseNote;
        final String cardNote;
        final int noteTone;
        final boolean isNew;
        final boolean testBuild;
        final boolean needsPc;
        final Variant[] variants;
        final List<Permission> permissions;
        final String guide;

        App(Catalog.App catalog, String tagline, String desc, String letter, int color,
            String[] features, String sourceShort, String minAndroid, String licenseNote,
            String cardNote, int noteTone, boolean isNew, boolean testBuild, boolean needsPc,
            Variant[] variants, List<Permission> permissions, String guide) {
            this.catalog = catalog;
            this.tagline = tagline;
            this.desc = desc;
            this.letter = letter;
            this.color = color;
            this.features = features;
            this.sourceShort = sourceShort;
            this.minAndroid = minAndroid;
            this.licenseNote = licenseNote;
            this.cardNote = cardNote;
            this.noteTone = noteTone;
            this.isNew = isNew;
            this.testBuild = testBuild;
            this.needsPc = needsPc;
            this.variants = variants;
            this.permissions = permissions;
            this.guide = guide;
        }

        String id() {
            return catalog.id;
        }

        String name() {
            return catalog.name;
        }

        String category() {
            return catalog.category;
        }

        boolean isTool() {
            return "tool".equals(catalog.category);
        }

        String packageName() {
            return catalog.packageName;
        }

        Variant defaultVariant() {
            return variants.length == 0 ? null : variants[0];
        }

        Variant variant(String id) {
            for (Variant variant : variants) {
                if (variant.id.equals(id)) {
                    return variant;
                }
            }
            return defaultVariant();
        }
    }

    private static final Map<String, App> APPS;

    static {
        Map<String, App> map = new LinkedHashMap<String, App>();

        map.put(Catalog.PLAN_YOUR_TV, new App(
                Catalog.app(Catalog.PLAN_YOUR_TV),
                "儿童桌面 · 答题 · 时间管理",
                "专业的儿童电视桌面，把观看时间变成答题奖励，家长规则一目了然。",
                "P", Color.parseColor("#2F6BFF"),
                new String[]{"儿童桌面", "答题系统", "时间管理"},
                "官方站点", "Android 5.0+", "开源 · 以官方仓库为准",
                "安装后按电视端引导设置家长规则；不会停用系统桌面。", TONE_INFO,
                false, false, false,
                new Variant[]{
                        new Variant("standard", "标准版", "大多数电视"),
                        new Variant("guard", "Guard", "固件拒绝桌面时")
                },
                Collections.<Permission>emptyList(), ""));

        map.put(Catalog.AERIAL, new App(
                Catalog.app(Catalog.AERIAL),
                "去广告 · 保留系统桌面",
                "tvOS 风格轻桌面，去掉广告与推荐；不卸载、不停用电视原来的系统桌面。",
                "A", Color.parseColor("#7350F2"),
                new String[]{"去广告", "tvOS 风格", "不停用系统桌面"},
                "官方站点 / GitHub", "Android 5.0+", "开源 · 以官方仓库为准",
                "不会卸载或停用系统桌面；Lite 适合较老的电视。", TONE_INFO,
                false, false, false,
                new Variant[]{
                        new Variant("standard", "标准版", "大多数电视"),
                        new Variant("guard", "Guard", "接管失败时"),
                        new Variant("lite", "Lite", "Android 4.x 老电视")
                },
                Collections.<Permission>emptyList(), ""));

        map.put(Catalog.QUIET_TV, new App(
                Catalog.app(Catalog.QUIET_TV),
                "极简安静 · 可配置",
                "关闭推荐流与打扰，只留下干净、安静、可以按自己习惯配置的电视首页。",
                "Q", Color.parseColor("#C2810A"),
                new String[]{"安静无推荐", "可配置", "轻量"},
                "官方索引 / 官方更新镜像 / GitHub", "Android 5.0+", "开源 · 以官方仓库为准",
                "当前公开版按测试版标注；部分固件需要手动接管 HOME。", TONE_WARN,
                false, true, false,
                new Variant[]{
                        new Variant("standard", "标准版", "Android 5+ / 推荐"),
                        new Variant("legacy14", "4.x 兼容版", "Android 4.0–4.4")
                },
                Collections.<Permission>emptyList(), ""));

        map.put(Catalog.HALO_TV, new App(
                Catalog.app(Catalog.HALO_TV),
                "独立包名 · 可与 Aerial 共存",
                "Aerial 的独立包名分支，权限与数据互不影响，可与 Aerial 同时安装、各自管理。",
                "H", Color.parseColor("#0E93B5"),
                new String[]{"独立包名", "与 Aerial 共存", "轻量"},
                "官方站点 / GitHub", "Android 5.0+", "开源 · 以官方仓库为准",
                "只用于独立包名版本，不会覆盖 Aerial 的数据。", TONE_INFO,
                false, false, false,
                new Variant[]{
                        new Variant("standard", "标准版", "大多数电视"),
                        new Variant("guard", "Guard", "接管失败时"),
                        new Variant("lite", "Lite", "Android 4+ / 轻量")
                },
                Collections.<Permission>emptyList(), ""));

        map.put(Catalog.CLEAN_YOUR_TV, new App(
                Catalog.app(Catalog.CLEAN_YOUR_TV),
                "精简快速 · 手机版可一键安装",
                "精简启动器加系统清理，启动更快；官方站点推荐社区手机版 App，"
                        + "也可以按官方脚本在电脑上构建。卸载前必须先恢复系统项。",
                "C", Color.parseColor("#0F9A67"),
                new String[]{"精简桌面", "响应更快", "系统清理", "手机版 App"},
                "官方脚本 / 手机版 App", "Android 8.0+（电视）· 9+（手机版）",
                "开源 · 以官方仓库为准",
                "官方不发布电视桌面 APK；推荐官方站点链接的社区手机版 App，"
                        + "也可用电脑脚本构建。", TONE_INFO,
                false, false, true,
                new Variant[0], Collections.<Permission>emptyList(),
                "官方仓库明确不提供电视桌面二进制。\n\n"
                        + "手机版（推荐）：从本页下载官方站点推荐的 Clean Your TV 手机版，"
                        + "装到手机上后连接同一 Wi-Fi 的电视，按它的清理计划操作。\n\n"
                        + "电脑版：准备 Python、JDK 与 Android SDK，按官方说明运行 "
                        + "launcher/build-and-install.sh。\n\n"
                        + "卸载前务必先运行官方 restore.sh 或使用手机版的恢复点，"
                        + "恢复被清理的系统项和原桌面。"));

        List<Permission> studyPermissions = new ArrayList<Permission>();
        studyPermissions.add(new Permission("显示在其他应用上层",
                "用于答题层与解锁提醒；可在电视系统设置中随时关闭。", false, false));
        studyPermissions.add(new Permission("使用情况访问",
                "读取前台应用和使用时长，是计时与解锁规则的基础。", false, false));
        studyPermissions.add(new Permission("无障碍服务",
                "部分机型用于增强前台识别；不开启也能使用核心功能。", true, false));
        studyPermissions.add(new Permission("开机自启动 / 后台运行",
                "重启电视后恢复计时与家长规则；可在电视上关闭。", true, false));
        studyPermissions.add(new Permission("设备管理员",
                "仅用于加强防卸载。安装器不会自动开启；卸载前必须先取消。", true, true));

        map.put(Catalog.STUDY_ON_TV, new App(
                Catalog.app(Catalog.STUDY_ON_TV),
                "答题换电视时间",
                "先学习，后娱乐：孩子答题解锁电视时间，家长扫码管理题库与规则。",
                "题", Color.parseColor("#D9542B"),
                new String[]{"答题解锁", "家长面板", "本地运行"},
                "官方更新 / Gitee / GitHub", "Android 5.0+", "源码可见 · 仅个人使用",
                "不是桌面，不会更改默认 HOME；权限可在电视上按需开启。", TONE_INFO,
                true, false, false,
                new Variant[]{
                        new Variant("standard", "TV 标准版", "推荐 · 在线 AI / 语音"),
                        new Variant("localonly", "TV 本地版", "零公网 · 隐私优先")
                },
                Collections.unmodifiableList(studyPermissions), ""));

        APPS = Collections.unmodifiableMap(map);
    }

    private UiData() {
    }

    static List<App> apps() {
        return new ArrayList<App>(APPS.values());
    }

    static App app(String id) {
        return APPS.get(id == null ? "" : id.trim());
    }

    static String officialSite(String appId) {
        if (Catalog.PLAN_YOUR_TV.equals(appId)) {
            return "https://planyourtv.me/";
        }
        if (Catalog.AERIAL.equals(appId)) {
            return "https://aerial.qust.me";
        }
        if (Catalog.QUIET_TV.equals(appId)) {
            return "https://beatsad.github.io/quiet-tv/";
        }
        if (Catalog.HALO_TV.equals(appId)) {
            return "https://halo-tv.pages.dev";
        }
        if (Catalog.CLEAN_YOUR_TV.equals(appId)) {
            return "https://cleanyour.tv";
        }
        if (Catalog.STUDY_ON_TV.equals(appId)) {
            return "https://chadyi.github.io/StudyOnTV/";
        }
        return "";
    }
}