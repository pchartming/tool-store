package com.toolstore.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed catalog. The native UI may select an app or variant by id, but it
 * cannot supply a package name, download URL or shell command. Every entry
 * below is deliberate and validated again after download.
 */
final class Catalog {

    static final class Variant {
        final String id;
        final String label;
        final String packageName;
        final String homeComponent;
        final String source;
        final long maxBytes;

        Variant(String id, String label, String packageName, String homeComponent,
                String source, long maxBytes) {
            this.id = id;
            this.label = label;
            this.packageName = packageName;
            this.homeComponent = homeComponent;
            this.source = source;
            this.maxBytes = maxBytes;
        }
    }

    static final class App {
        final String id;
        final String name;
        final String category;
        final String source;
        final Map<String, Variant> variants;
        final String packageName;

        App(String id, String name, String category, String source,
            String packageName, Variant... variants) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.source = source;
            this.packageName = packageName;
            Map<String, Variant> map = new LinkedHashMap<String, Variant>();
            for (Variant variant : variants) {
                map.put(variant.id, variant);
            }
            this.variants = Collections.unmodifiableMap(map);
        }

        Variant variant(String id) {
            Variant variant = variants.get(id);
            if (variant != null) {
                return variant;
            }
            if (!variants.isEmpty()) {
                return variants.values().iterator().next();
            }
            return null;
        }
    }

    static final String PLAN_YOUR_TV = "planyourtv";
    static final String AERIAL = "aerial";
    static final String QUIET_TV = "quiettv";
    static final String HALO_TV = "halotv";
    static final String CLEAN_YOUR_TV = "cleanyourtv";
    static final String STUDY_ON_TV = "studyontv";

    private static final Map<String, App> APPS;

    static {
        Map<String, App> apps = new LinkedHashMap<String, App>();

        apps.put(AERIAL, new App(
                AERIAL, "Aerial", "launcher", "官方站点 / GitHub",
                "app.orchard.tv",
                new Variant("standard", "标准版", "app.orchard.tv",
                        "app.orchard.tv/app.orchard.tv.MainActivity",
                        "https://aerial.qust.me/download/", 40L * 1024L * 1024L),
                new Variant("guard", "Guard", "app.orchard.tv",
                        "app.orchard.tv/app.orchard.tv.MainActivity",
                        "https://aerial.qust.me/download/", 40L * 1024L * 1024L),
                new Variant("lite", "Lite", "app.orchard.tv.lite",
                        "app.orchard.tv.lite/app.orchard.tv.lite.HomeActivity",
                        "https://aerial.qust.me/download/", 40L * 1024L * 1024L)));

        apps.put(PLAN_YOUR_TV, new App(
                PLAN_YOUR_TV, "PlanYourTV", "launcher", "PlanYourTV 官网",
                "com.planyourtv.tv",
                new Variant("standard", "标准版", "com.planyourtv.tv",
                        "com.planyourtv.tv/app.orchard.tv.MainActivity",
                        "https://planyourtv.me/download/", 80L * 1024L * 1024L),
                new Variant("guard", "Guard", "com.planyourtv.tv",
                        "com.planyourtv.tv/app.orchard.tv.MainActivity",
                        "https://planyourtv.me/download/", 80L * 1024L * 1024L)));

        apps.put(QUIET_TV, new App(
                QUIET_TV, "Quiet TV", "launcher", "官方索引 / 官方更新镜像 / GitHub",
                "home.quiet.tv",
                new Variant("standard", "标准版", "home.quiet.tv",
                        "home.quiet.tv/home.quiet.tv.HomeActivity",
                        "https://beatsad.github.io/quiet-tv/latest.json",
                        40L * 1024L * 1024L),
                new Variant("legacy14", "4.x 兼容版", "home.quiet.tv",
                        "home.quiet.tv/home.quiet.tv.HomeActivity",
                        "https://beatsad.github.io/quiet-tv/latest.json",
                        40L * 1024L * 1024L)));

        apps.put(HALO_TV, new App(
                HALO_TV, "HaloTV", "launcher", "官方站点 / GitHub",
                "app.halotv.launcher",
                new Variant("standard", "标准版", "app.halotv.launcher",
                        "app.halotv.launcher/app.orchard.tv.MainActivity",
                        "https://halo-tv.pages.dev/", 40L * 1024L * 1024L),
                new Variant("guard", "Guard", "app.halotv.launcher",
                        "app.halotv.launcher/app.orchard.tv.MainActivity",
                        "https://halo-tv.pages.dev/", 40L * 1024L * 1024L),
                new Variant("lite", "Lite", "app.halotv.launcher.lite",
                        "app.halotv.launcher.lite/app.orchard.tv.lite.HomeActivity",
                        "https://halo-tv.pages.dev/", 40L * 1024L * 1024L)));

        apps.put(CLEAN_YOUR_TV, new App(
                CLEAN_YOUR_TV, "CleanYourTV", "launcher", "官方脚本 / 手机版 App",
                "com.gathin.tvhome"));

        apps.put(STUDY_ON_TV, new App(
                STUDY_ON_TV, "StudyOnTV", "tool", "官方更新 / Gitee / GitHub",
                "com.studyontv.tv",
                new Variant("standard", "TV 标准版", "com.studyontv.tv",
                        null,
                        "https://gitee.com/chadyi/StudyOnTV/releases",
                        30L * 1024L * 1024L),
                new Variant("localonly", "TV 本地版", "com.studyontv.tv",
                        null,
                        "https://gitee.com/chadyi/StudyOnTV/releases",
                        30L * 1024L * 1024L)));

        APPS = Collections.unmodifiableMap(apps);
    }

    private Catalog() {
    }

    static App app(String id) {
        return APPS.get(id == null ? "" : id.trim());
    }

    static Iterable<App> apps() {
        return APPS.values();
    }

    static String appIdForPackage(String packageName) {
        String pkg = packageName == null ? "" : packageName.trim();
        if (pkg.isEmpty()) {
            return "";
        }
        for (App app : APPS.values()) {
            if (pkg.equals(app.packageName)) {
                return app.id;
            }
            for (Variant variant : app.variants.values()) {
                if (pkg.equals(variant.packageName)) {
                    return app.id;
                }
            }
        }
        return "";
    }

    static String variantIdForPackage(String appId, String packageName) {
        App app = app(appId);
        if (app == null || packageName == null) {
            return "";
        }
        for (Variant variant : app.variants.values()) {
            if (packageName.equals(variant.packageName)) {
                return variant.id;
            }
        }
        return "";
    }

    static List<String> packageNames() {
        List<String> packages = new ArrayList<String>();
        for (App app : APPS.values()) {
            if (!packages.contains(app.packageName)) {
                packages.add(app.packageName);
            }
            for (Variant variant : app.variants.values()) {
                if (!packages.contains(variant.packageName)) {
                    packages.add(variant.packageName);
                }
            }
        }
        return packages;
    }

    static List<String> installablePackageNames() {
        List<String> packages = new ArrayList<String>();
        for (App app : APPS.values()) {
            for (Variant variant : app.variants.values()) {
                if (variant.homeComponent == null && !STUDY_ON_TV.equals(app.id)) {
                    continue;
                }
                if (!packages.contains(variant.packageName)) {
                    packages.add(variant.packageName);
                }
            }
        }
        return packages;
    }

    static String labelForPackage(String packageName) {
        String appId = appIdForPackage(packageName);
        App app = app(appId);
        if (app != null) {
            return app.name;
        }
        return packageName == null ? "" : packageName;
    }
}