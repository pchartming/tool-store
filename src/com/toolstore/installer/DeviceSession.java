package com.toolstore.installer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connection, authorization and read-only device inspection.
 *
 * All operations target the serial explicitly built from the validated host
 * and port. No caller-supplied adb arguments are ever accepted.
 */
final class DeviceSession {

    static final class ConnectException extends Exception {
        ConnectException(String message) {
            super(message);
        }
    }

    private static final Pattern COMPONENT = Pattern.compile(
            "([A-Za-z0-9._]+)/([A-Za-z0-9.$_]+)");
    private static final String PREFS = "toolstore_connections";
    private static final String KEY_LAST_TV = "last_tv";
    private static final String KEY_BASELINE_PREFIX = "baseline_home_";
    private static final int WAIT_FOR_STATE_SECONDS = 15;
    private static final int AUTHORIZE_SECONDS = 120;

    private final Context context;
    private final AdbClient adb;
    private final AdbClient.Sink sink;

    DeviceSession(Context context, AdbClient.Sink sink) throws Exception {
        this.context = context.getApplicationContext();
        this.sink = sink == null ? new AdbClient.Sink() {
            @Override
            public void line(String text) {
            }
        } : sink;
        this.adb = new AdbClient(this.context);
    }

    JSONObject pair(String host, int pairPort, String pairCode) throws Exception {
        if (pairCode == null || !pairCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("请填写电视上显示的 6 位无线调试配对码。");
        }
        final String code = pairCode;
        AdbClient.Sink safeSink = new AdbClient.Sink() {
            @Override
            public void line(String text) {
                sink.line(scrub(text, code));
            }
        };

        safeSink.line("正在与 " + host + ":" + pairPort
                + " 配对（配对码不会写入日志）…");
        adb.run(new String[]{"start-server"}, 60, safeSink);
        AdbClient.Result pair = adb.runSanitized(
                new String[]{"pair", host + ":" + pairPort, code},
                "adb pair " + host + ":" + pairPort + " ******",
                90, safeSink);
        String output = scrub(pair.output, code);
        String lower = output.toLowerCase(Locale.US);
        if (!pair.ok() || !lower.contains("successfully paired")) {
            throw new ConnectException("配对失败。请确认配对端口和 6 位配对码来自电视当前打开的"
                    + "“使用配对码配对设备”弹窗，并保持弹窗不关闭。\n" + snippet(output));
        }
        sink.line("配对成功，调试密钥已保存在本机。");

        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("kind", "pair");
        result.put("host", host);
        result.put("pairPort", pairPort);
        return result;
    }

    JSONObject connect(String host, int port, String transport) throws Exception {
        String serial = host + ":" + port;
        sink.line("正在连接 " + serial + "…");
        adb.run(new String[]{"start-server"}, 60, sink);

        AdbClient.Result connected = adb.run(
                new String[]{"connect", serial}, 45, sink);
        String lower = connected.output.toLowerCase(Locale.US);
        if (!(lower.contains("connected to")
                || lower.contains("already connected")
                || lower.contains("already in target state"))) {
            if (lower.contains("failed to authenticate")) {
                throw new ConnectException("连接被电视拒绝。无线调试需要先完成配对；"
                        + "配对码每次都会变化。\n" + snippet(connected.output));
            }
            throw new ConnectException("无法连接电视 " + serial
                    + "。请确认手机和电视在同一局域网、电视已开启无线调试或网络 ADB，"
                    + "并且连接端口填写正确。\n" + snippet(connected.output));
        }

        String state = waitForState(serial, WAIT_FOR_STATE_SECONDS, false);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("kind", "connect");
        result.put("serial", serial);
        result.put("state", state);
        result.put("authorized", "device".equals(state));
        if (!"device".equals(state)) {
            sink.line("电视还没有授权调试。请在电视屏幕上选择“允许”并勾选“始终允许”。");
        }
        return result;
    }

    JSONObject authorize(String host, int port, String transport) throws Exception {
        String serial = host + ":" + port;
        sink.line("等待电视端授权调试…");
        String state = waitForState(serial, AUTHORIZE_SECONDS, true);
        if (!"device".equals(state)) {
            throw new ConnectException("电视没有授权调试。请在电视上点“允许”。"
                    + "如果没有弹窗，请在电视开发者选项里撤销调试授权后重新连接。");
        }
        JSONObject info = inspect(host, port, transport);
        info.put("kind", "authorize");
        saveLastTv(info, transport);
        return info;
    }

    JSONObject inspect(String host, int port, String transport) throws Exception {
        String serial = host + ":" + port;
        long started = System.currentTimeMillis();
        String state = stateOf(serial);
        if (!"device".equals(state)) {
            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("kind", "device");
            result.put("authorized", false);
            result.put("state", state);
            return result;
        }

        String props = adbText(serial, "shell", "getprop");
        String manufacturer = prop(props, "ro.product.manufacturer");
        String brand = prop(props, "ro.product.brand");
        String model = prop(props, "ro.product.model");
        String release = prop(props, "ro.build.version.release");
        int sdk = intValue(prop(props, "ro.build.version.sdk"));

        String homeComponent = resolveHome(serial);
        String homePackage = packageOf(homeComponent);
        String homeAppId = Catalog.appIdForPackage(homePackage);
        String baselinePackage = packageOf(readBaseline(serial));
        String stockLabel;
        if (homeAppId.isEmpty()) {
            stockLabel = homePackage.isEmpty() ? "系统桌面" : homePackage;
        } else if (!baselinePackage.isEmpty()
                && !Catalog.appIdForPackage(baselinePackage).equals(homeAppId)) {
            stockLabel = baselinePackage.contains(".")
                    ? baselinePackage : "系统桌面";
        } else {
            stockLabel = "电视原桌面";
        }

        String packageText = adbText(serial, "shell", "pm", "list", "packages");
        Set<String> installedPackages = parsePackages(packageText);
        JSONArray installed = new JSONArray();
        JSONObject knownApps = new JSONObject();

        for (String packageName : Catalog.packageNames()) {
            if (!installedPackages.contains(packageName)) {
                continue;
            }
            String version = readVersion(serial, packageName);
            String appId = Catalog.appIdForPackage(packageName);
            JSONObject entry = new JSONObject();
            entry.put("packageName", packageName);
            entry.put("versionName", version);
            entry.put("appId", appId);
            entry.put("variantId", Catalog.variantIdForPackage(appId, packageName));
            installed.put(entry);
            if (!appId.isEmpty()) {
                knownApps.put(appId, entry);
            }
        }

        String latency = Math.max(1L, System.currentTimeMillis() - started) + " ms";
        String label = friendlyBrand(manufacturer, brand, model);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("kind", "device");
        result.put("authorized", true);
        result.put("state", "device");
        result.put("serial", serial);
        result.put("label", label);
        result.put("brand", clean(manufacturer));
        result.put("model", clean(model).isEmpty() ? "Android TV" : clean(model));
        result.put("android", clean(release).isEmpty() ? "未知" : clean(release));
        result.put("sdk", sdk);
        result.put("latency", latency);
        result.put("transport", "classic".equals(transport) ? "classic" : "wireless");
        result.put("defaultHomePackage", homePackage);
        result.put("defaultHomeComponent", homeComponent);
        result.put("defaultHomeApp", homeAppId.isEmpty() ? "stock" : homeAppId);
        result.put("stockLabel", stockLabel);
        result.put("installed", installed);
        result.put("knownApps", knownApps);
        saveLastTv(result, transport);
        return result;
    }

    JSONObject disconnect(String host, int port) throws Exception {
        String serial = host + ":" + port;
        adb.run(new String[]{"disconnect", serial}, 30, sink);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("kind", "disconnect");
        result.put("serial", serial);
        return result;
    }

    JSONObject setHome(String appId, String variantId, String host, int port) throws Exception {
        String serial = host + ":" + port;
        Target target = requireInstalledTarget(appId, variantId, serial);
        Catalog.App app = target.app;
        if ("tool".equals(app.category)) {
            throw new IllegalArgumentException("工具应用不会接管电视桌面。");
        }
        String packageName = target.packageName;
        String current = resolveHome(serial);
        String currentPackage = packageOf(current);
        if (currentPackage.equals(packageName)) {
            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("kind", "setHome");
            result.put("alreadyDefault", true);
            result.put("home", current);
            result.put("appId", appId);
            result.put("variantId", target.variantId);
            result.put("packageName", packageName);
            return result;
        }

        String component = findHomeComponent(serial, packageName);
        if (component.isEmpty() && target.homeComponent != null
                && packageName.equals(packageOf(target.homeComponent))) {
            component = target.homeComponent;
        }
        if (component.isEmpty()) {
            throw new ConnectException("电视没有报告可用于桌面的 " + packageName
                    + " 组件。请先安装该应用，或在电视上按主页键选择。");
        }
        rememberBaseline(serial, current);
        sink.line("正在把默认桌面切换为 " + app.name + "…");
        adb.run(withSerial(serial, "shell", "cmd", "package",
                "set-home-activity", component), 30, sink);
        sleep(1200L);
        String after = resolveHome(serial);
        String afterPackage = packageOf(after);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("kind", "setHome");
        result.put("confirmed", packageName.equals(afterPackage));
        result.put("home", after);
        result.put("appId", appId);
        result.put("variantId", target.variantId);
        result.put("packageName", packageName);
        if (!packageName.equals(afterPackage)) {
            result.put("warning", "系统没有立即确认默认桌面。请在电视上按主页键并选择 " + app.name + "。");
        } else {
            sink.line("✓ 默认桌面已切换为 " + app.name);
        }
        return result;
    }

    JSONObject restoreHome(String host, int port) throws Exception {
        String serial = host + ":" + port;
        String baseline = readBaseline(serial);
        JSONObject result = new JSONObject();
        result.put("kind", "restoreHome");
        if (baseline.isEmpty() || packageOf(baseline).equals(packageOf(resolveHome(serial)))) {
            sink.line("没有可自动恢复的原桌面记录，正在打开电视的桌面选择器…");
            adb.run(withSerial(serial, "shell", "am", "start",
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.HOME"), 30, sink);
            result.put("ok", true);
            result.put("openedChooser", true);
            result.put("message", "请在电视上选择原来的桌面。");
            return result;
        }

        String baselinePackage = packageOf(baseline);
        if (!readPackages(serial).contains(baselinePackage)) {
            throw new ConnectException("安装前记录的桌面应用已不在电视上，无法自动切回。"
                    + "请按主页键手动选择。");
        }
        sink.line("正在切回安装前的桌面…");
        adb.run(withSerial(serial, "shell", "cmd", "package",
                "set-home-activity", baseline), 30, sink);
        sleep(1200L);
        String after = resolveHome(serial);
        boolean confirmed = baselinePackage.equals(packageOf(after));
        result.put("ok", true);
        result.put("confirmed", confirmed);
        result.put("home", after);
        result.put("label", Catalog.labelForPackage(baselinePackage));
        if (!confirmed) {
            result.put("warning", "系统没有立即确认切换。请按主页键在电视上选择原桌面。");
        }
        return result;
    }

    JSONObject launch(String appId, String variantId, String host, int port) throws Exception {
        String serial = host + ":" + port;
        Target target = requireInstalledTarget(appId, variantId, serial);
        Catalog.App app = target.app;
        String packageName = target.packageName;
        sink.line("正在电视上打开 " + app.name + "…");
        AdbClient.Result result = adb.run(withSerial(serial,
                "shell", "monkey", "-p", packageName,
                "-c", "android.intent.category.LAUNCHER", "1"), 30, sink);
        String lower = result.output.toLowerCase(Locale.US);
        boolean started = result.ok()
                && (lower.contains("events injected") || lower.contains("switch"))
                && !lower.contains("no activities found");
        if (!started) {
            throw new ConnectException("电视没有启动 " + app.name + "。"
                    + "请确认电视已安装该应用并且没有被系统冻结。\n" + snippet(result.output));
        }
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("kind", "launch");
        json.put("appId", appId);
        json.put("variantId", target.variantId);
        json.put("packageName", packageName);
        return json;
    }

    JSONObject uninstall(String appId, String variantId, String host, int port) throws Exception {
        Catalog.App app = Catalog.app(appId);
        if (app == null) {
            throw new IllegalArgumentException("未知应用。");
        }
        String serial = host + ":" + port;
        Set<String> installed = readPackages(serial);
        Target target = target(app, variantId, serial, installed);
        String packageName = target.packageName;
        boolean isTool = "tool".equals(app.category);
        if (!installed.contains(packageName)) {
            // The package may already be gone while a launcher guard left
            // notification-listener or screensaver entries behind. Clean only
            // settings that name this exact package; never touch HOME here.
            String cleanupWarning = isTool
                    ? "" : cleanupResidualSettings(serial, packageName);
            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("kind", "uninstall");
            result.put("alreadyAbsent", true);
            result.put("settingsClean", cleanupWarning.isEmpty());
            result.put("appId", appId);
            result.put("variantId", target.variantId);
            result.put("packageName", packageName);
            if (!cleanupWarning.isEmpty()) {
                result.put("warning", cleanupWarning);
            }
            return result;
        }
        boolean wasDefault = packageName.equals(packageOf(resolveHome(serial)));
        JSONObject restore = null;
        if (!isTool) {
            restore = restoreLauncherSettings(serial, app, packageName, wasDefault);
        }
        if (isTool) {
            sink.line("正在卸载 " + app.name + "；题库、规则和使用记录会一起删除，"
                    + "默认桌面不会被改动。");
        } else {
            sink.line("正在卸载 " + app.name + "（只移除这个第三方应用及其数据）…");
        }
        AdbClient.Result result = adb.run(withSerial(serial,
                "shell", "pm", "uninstall", packageName), 180, sink);
        String lower = result.output.toLowerCase(Locale.US);
        boolean removed = lower.contains("success")
                || lower.contains("unknown package");
        if (!result.ok() || !removed) {
            throw new ConnectException("卸载没有成功。请检查电视是否限制了卸载。\n"
                    + snippet(result.output));
        }
        // The guard can re-register itself between cleanup and force-stop, and
        // some TVs keep reporting a package for a moment after pm uninstall.
        // Clean the exact-package settings first, then allow one stale package
        // list read before declaring failure.
        String cleanupWarning = "";
        if (!isTool) {
            cleanupWarning = cleanupResidualSettings(serial, packageName);
        }
        Set<String> remaining = readPackages(serial);
        if (remaining.contains(packageName)) {
            sleep(800L);
            remaining = readPackages(serial);
        }
        if (remaining.contains(packageName)) {
            throw new ConnectException("ADB 报告卸载完成，但电视上仍然能列出 "
                    + packageName + "。请刷新状态后再确认。");
        }
        String homeAfter = resolveHome(serial);
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("kind", "uninstall");
        json.put("appId", appId);
        json.put("variantId", target.variantId);
        json.put("packageName", packageName);
        json.put("removed", true);
        json.put("wasInstalled", true);
        json.put("wasDefault", wasDefault);
        json.put("systemRestored", restore != null);
        json.put("settingsClean", cleanupWarning.isEmpty());
        json.put("home", homeAfter);
        if (restore != null) {
            json.put("homeSwitched", restore.optBoolean("homeSwitched", false));
            json.put("homeConfirmed", restore.optBoolean("homeConfirmed", false));
            json.put("homeLabel", restore.optString("homeLabel", ""));
            json.put("reEnabledHome", restore.optBoolean("reEnabledHome", false));
        }
        if (!cleanupWarning.isEmpty()) {
            json.put("warning", cleanupWarning);
        }
        if (wasDefault && (homeAfter.isEmpty()
                || packageName.equals(packageOf(homeAfter)))) {
            openHomeChooser(serial);
            String warning = "应用已卸载，但电视没有报告可用的默认桌面。"
                    + "已打开电视的桌面选择器，请按主页键选择电视原桌面。";
            String existing = json.optString("warning", "");
            json.put("warning", existing.isEmpty() ? warning : existing + "\n" + warning);
        }
        return json;
    }

    /**
     * Applies the official restore order before removing a launcher: stop its
     * guard, revoke notification / accessibility / overlay settings, restore
     * vendor boot hooks, then give Home back to the recorded stock launcher.
     * The package is only uninstalled after Home has been confirmed.
     */
    private JSONObject restoreLauncherSettings(String serial, Catalog.App app,
                                               String packageName, boolean wasDefault)
            throws Exception {
        sink.line("正在按官方恢复流程撤销 " + app.name + " 的系统改动…");

        quiet(serial, "am", "broadcast", "-a", packageName + ".CONFIG", "-n",
                packageName + "/app.orchard.tv.guard.ConfigReceiver",
                "--ez", "keep_adb", "false", "--ez", "own_screensaver", "false");
        quiet(serial, "cmd", "notification", "disallow_listener",
                packageName + "/app.orchard.tv.guard.RevivalListener");
        removeFromSettingList(serial, "secure",
                "enabled_notification_listeners", packageName);
        quiet(serial, "pm", "revoke", packageName,
                "android.permission.WRITE_SECURE_SETTINGS");

        String[] appOps = new String[]{
                "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES",
                "GET_USAGE_STATS", "android:get_usage_stats",
                "APP_AUTO_START", "APP_ASSOC_START", "AUTO_START", "ASSOC_START",
                "ACCESS_RESTRICTED_SETTINGS"
        };
        for (String op : appOps) {
            quiet(serial, "appops", "set", packageName, op, "default");
        }
        quiet(serial, "am", "force-stop", packageName);
        restoreScreensaver(serial, packageName);
        removeFromSettingList(serial, "secure",
                "enabled_accessibility_services", packageName);
        restoreVendorBootSettings(serial, packageName);
        quiet(serial, "cmd", "role", "remove-role-holder", "--user", "0",
                "android.app.role.HOME", packageName);

        JSONObject restore = new JSONObject();
        restore.put("ok", true);
        restore.put("guardCleaned", true);
        if (!wasDefault) {
            restore.put("homeSwitched", false);
            restore.put("homeConfirmed", true);
            restore.put("home", resolveHome(serial));
            restore.put("homeLabel", "");
            return restore;
        }

        String target = stockHomeComponent(serial, packageName);
        if (target.isEmpty()) {
            throw new ConnectException("这台电视上找不到可用的原桌面，已停止卸载，避免主页留空。"
                    + "请先在电视上切回系统桌面，再回来重试。");
        }
        String targetPackage = packageOf(target);
        boolean reEnabled = false;
        if (readDisabledPackages(serial).contains(targetPackage)) {
            sink.line("原桌面 " + targetPackage + " 当前被停用，正在按官方流程重新启用…");
            quiet(serial, "pm", "enable", targetPackage);
            sleep(600L);
            reEnabled = !readDisabledPackages(serial).contains(targetPackage);
            if (!reEnabled) {
                sink.line("! 电视没有确认重新启用 " + targetPackage + "，仍会尝试验证切换。");
            }
        }
        String label = homeLabel(targetPackage);
        sink.line("正在切回原桌面 " + label + "…");
        adb.run(withSerial(serial, "shell", "cmd", "package",
                "set-home-activity", target), 30, sink);
        sleep(1500L);
        String after = resolveHome(serial);
        boolean confirmed = !after.isEmpty()
                && targetPackage.equals(packageOf(after));
        restore.put("homeSwitched", true);
        restore.put("homeConfirmed", confirmed);
        restore.put("home", after);
        restore.put("homeLabel", label);
        restore.put("reEnabledHome", reEnabled);
        if (!confirmed) {
            throw new ConnectException("已尝试切回原桌面 " + label
                    + "，但电视没有确认。为避免主页留空，本次卸载已停止。"
                    + "请按电视主页键选择原桌面后再重试。");
        }
        sink.line("✓ 默认桌面已切回 " + label + "，系统设置已恢复");
        return restore;
    }

    private String stockHomeComponent(String serial, String packageName) {
        String baseline = readBaseline(serial);
        String baselinePackage = packageOf(baseline);
        if (!baselinePackage.isEmpty()
                && !baselinePackage.equals(packageName)
                && readPackages(serial).contains(baselinePackage)) {
            return baseline;
        }
        String output = adbText(serial, "shell", "cmd", "package",
                "query-activities", "--brief",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.HOME");
        String fallback = "";
        Matcher matcher = COMPONENT.matcher(output);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.equals(packageName)
                    || candidate.endsWith("ResolverActivity")
                    || !Catalog.appIdForPackage(candidate).isEmpty()) {
                continue;
            }
            String clazz = matcher.group(2);
            String component = candidate + "/"
                    + (clazz.startsWith(".") ? candidate + clazz : clazz);
            if ("com.mitv.tvhome".equals(candidate)) {
                return component;
            }
            if (fallback.isEmpty()) {
                fallback = component;
            }
        }
        return fallback;
    }

    private String homeLabel(String packageName) {
        String label = Catalog.labelForPackage(packageName);
        return label == null || label.isEmpty() || label.equals(packageName)
                ? "电视原桌面" : label;
    }

    private void quiet(String serial, String... command) {
        try {
            adb.run(withSerial(serial, command), 25, null);
        } catch (Throwable ignored) {
        }
    }

    private String settingGet(String serial, String namespace, String key) {
        String value = adbText(serial, "shell", "settings", "get", namespace, key);
        value = value == null ? "" : value.trim();
        return "null".equals(value) ? "" : value;
    }

    private void removeFromSettingList(String serial, String namespace, String key,
                                       String packageName) {
        String current = settingGet(serial, namespace, key);
        if (current.isEmpty()) {
            return;
        }
        StringBuilder kept = new StringBuilder();
        boolean removed = false;
        for (String part : current.split(":")) {
            String value = part == null ? "" : part.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.startsWith(packageName + "/")) {
                removed = true;
                continue;
            }
            if (kept.length() > 0) {
                kept.append(':');
            }
            kept.append(value);
        }
        if (!removed) {
            return;
        }
        if (kept.length() == 0) {
            quiet(serial, "settings", "delete", namespace, key);
        } else {
            quiet(serial, "settings", "put", namespace, key, kept.toString());
        }
    }

    /**
     * Runs after the package is gone and keeps checking the actual settings,
     * not just the command return code. A guard can re-register between the
     * pre-uninstall cleanup and the package disappearing, so retry a few
     * times and report any setting that still names the removed package.
     */
    private String cleanupResidualSettings(String serial, String packageName) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            quiet(serial, "cmd", "notification", "disallow_listener",
                    packageName + "/app.orchard.tv.guard.RevivalListener");
            removeFromSettingList(serial, "secure",
                    "enabled_notification_listeners", packageName);
            removeFromSettingList(serial, "secure",
                    "enabled_accessibility_services", packageName);
            restoreScreensaver(serial, packageName);

            boolean listenerLeft = settingHasPackage(serial, "secure",
                    "enabled_notification_listeners", packageName);
            boolean accessibilityLeft = settingHasPackage(serial, "secure",
                    "enabled_accessibility_services", packageName);
            boolean screensaverLeft = settingHasPackage(serial, "secure",
                    "screensaver_components", packageName)
                    || settingHasPackage(serial, "secure",
                    "screensaver_default_component", packageName);
            if (!listenerLeft && !accessibilityLeft && !screensaverLeft) {
                if (attempt > 1) {
                    sink.line("✓ 卸载后的通知监听、无障碍与屏保设置已清理");
                }
                return "";
            }
            if (attempt < 3) {
                sleep(400L);
            }
        }

        List<String> left = new ArrayList<String>();
        if (settingHasPackage(serial, "secure",
                "enabled_notification_listeners", packageName)) {
            left.add("通知监听");
        }
        if (settingHasPackage(serial, "secure",
                "enabled_accessibility_services", packageName)) {
            left.add("无障碍服务");
        }
        if (settingHasPackage(serial, "secure",
                "screensaver_components", packageName)
                || settingHasPackage(serial, "secure",
                "screensaver_default_component", packageName)) {
            left.add("屏保组件");
        }
        StringBuilder names = new StringBuilder();
        for (String name : left) {
            if (names.length() > 0) {
                names.append("、");
            }
            names.append(name);
        }
        sink.line("! 卸载后仍有系统设置指向 " + packageName
                + "，已多次尝试清理。");
        return "应用已卸载，但电视仍保留" + names + "的旧引用。"
                + "请重启电视后重新检查，或在电视系统设置中关闭这些项。";
    }

    private boolean settingHasPackage(String serial, String namespace, String key,
                                      String packageName) {
        String current = settingGet(serial, namespace, key);
        for (String part : current.split(":")) {
            if (part.trim().startsWith(packageName + "/")) {
                return true;
            }
        }
        return false;
    }

    private void restoreScreensaver(String serial, String packageName) {
        String components = settingGet(serial, "secure", "screensaver_components");
        String original = settingGet(serial, "secure", "screensaver_default_component");
        boolean defaultIsApp = original.trim().startsWith(packageName + "/");
        if (settingHasPackage(serial, "secure", "screensaver_components", packageName)) {
            String kept = removePackageFromList(components, packageName);
            if (!original.isEmpty() && !defaultIsApp) {
                quiet(serial, "settings", "put", "secure",
                        "screensaver_components", original);
            } else if (kept.isEmpty()) {
                quiet(serial, "settings", "delete", "secure",
                        "screensaver_components");
            } else {
                quiet(serial, "settings", "put", "secure",
                        "screensaver_components", kept);
            }
        }
        if (defaultIsApp) {
            quiet(serial, "settings", "delete", "secure",
                    "screensaver_default_component");
        }
    }

    private String removePackageFromList(String current, String packageName) {
        StringBuilder kept = new StringBuilder();
        for (String part : current.split(":")) {
            String value = part == null ? "" : part.trim();
            if (value.isEmpty() || value.startsWith(packageName + "/")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(':');
            }
            kept.append(value);
        }
        return kept.toString();
    }

    private void restoreVendorBootSettings(String serial, String packageName) {
        String bootComponent = settingGet(serial, "global",
                "aerial_boot_app_componentname");
        if (!bootComponent.isEmpty()) {
            String bootMode = settingGet(serial, "global", "aerial_boot_app_mode");
            quiet(serial, "settings", "put", "system",
                    "boot_app_componentname", bootComponent);
            if (!bootMode.isEmpty()) {
                quiet(serial, "settings", "put", "system",
                        "boot_app_mode", bootMode);
            }
            quiet(serial, "settings", "delete", "global",
                    "aerial_boot_app_componentname");
            quiet(serial, "settings", "delete", "global",
                    "aerial_boot_app_mode");
            sink.line("✓ 已恢复 TCL 开机应用设置");
        } else if (settingGet(serial, "system", "boot_app_componentname")
                .startsWith(packageName + "/")) {
            quiet(serial, "settings", "put", "system",
                    "boot_app_componentname", "''");
        }

        String vtHome = settingGet(serial, "global", "aerial_vt_home_pkg");
        if (!vtHome.isEmpty()) {
            quiet(serial, "setprop", "persist.vt.use_home_pkg", vtHome);
            quiet(serial, "settings", "delete", "global", "aerial_vt_home_pkg");
            sink.line("✓ 已恢复海信开机桌面设置");
        } else if (packageName.equals(adbText(serial, "shell", "getprop",
                "persist.vt.use_home_pkg"))) {
            if (readPackages(serial).contains("com.jamdeo.tv.vod")) {
                quiet(serial, "setprop", "persist.vt.use_home_pkg",
                        "com.jamdeo.tv.vod");
            }
        }
    }

    private Set<String> readDisabledPackages(String serial) {
        return parsePackages(adbText(serial, "shell", "pm", "list", "packages", "-d"));
    }

    private void openHomeChooser(String serial) {
        quiet(serial, "am", "start", "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.HOME");
    }

    private Target requireInstalledTarget(String appId, String variantId, String serial)
            throws Exception {
        Catalog.App app = Catalog.app(appId);
        if (app == null) {
            throw new IllegalArgumentException("未知应用。");
        }
        Set<String> installed = readPackages(serial);
        Target target = target(app, variantId, serial, installed);
        if (!installed.contains(target.packageName)) {
            throw new ConnectException("电视上没有安装 " + app.name + "。");
        }
        return target;
    }

    private Target target(Catalog.App app, String variantId, String serial,
                          Set<String> installed) throws Exception {
        String packageName = "";
        if (variantId != null && !variantId.trim().isEmpty()) {
            Catalog.Variant requested = app.variant(variantId.trim());
            if (requested == null) {
                throw new IllegalArgumentException("未知的版本档。");
            }
            packageName = requested.packageName;
        } else {
            String currentPackage = packageOf(resolveHome(serial));
            if (app.id.equals(Catalog.appIdForPackage(currentPackage))) {
                packageName = currentPackage;
            }
            if (packageName.isEmpty() && installed.contains(app.packageName)) {
                packageName = app.packageName;
            }
            if (packageName.isEmpty()) {
                List<String> candidates = new ArrayList<String>();
                for (Catalog.Variant variant : app.variants.values()) {
                    if (installed.contains(variant.packageName)
                            && !candidates.contains(variant.packageName)) {
                        candidates.add(variant.packageName);
                    }
                }
                if (!candidates.isEmpty()) {
                    packageName = candidates.get(0);
                    if (candidates.size() > 1) {
                        sink.line("! 电视上检测到 " + candidates.size()
                                + " 个 " + app.name + " 包，将操作 " + packageName
                                + "。如需操作另一个版本，请先在电视上确认应用。");
                    }
                }
            }
            if (packageName.isEmpty()) {
                packageName = app.packageName;
            }
        }
        String resolvedVariant = Catalog.variantIdForPackage(app.id, packageName);
        Catalog.Variant variant = resolvedVariant.isEmpty()
                ? null : app.variant(resolvedVariant);
        return new Target(app, variant, packageName);
    }

    private static final class Target {
        final Catalog.App app;
        final Catalog.Variant variant;
        final String packageName;
        final String variantId;
        final String homeComponent;

        Target(Catalog.App app, Catalog.Variant variant, String packageName) {
            this.app = app;
            this.variant = variant;
            this.packageName = packageName;
            this.variantId = variant == null ? "" : variant.id;
            this.homeComponent = variant == null ? null : variant.homeComponent;
        }
    }

    JSONObject lastTv() {
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String value = preferences.getString(KEY_LAST_TV, "");
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            return new JSONObject(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String resolveHomeFromOutput(String output) {
        return parseComponent(output);
    }

    private void saveLastTv(JSONObject info, String transport) {
        try {
            JSONObject saved = new JSONObject();
            saved.put("ip", info.optString("serial", "").split(":")[0]);
            String serial = info.optString("serial", "");
            int colon = serial.lastIndexOf(':');
            if (colon > 0) {
                saved.put("ip", serial.substring(0, colon));
                saved.put("port", intValue(serial.substring(colon + 1)));
            }
            saved.put("label", info.optString("label", "电视"));
            saved.put("brand", info.optString("brand", ""));
            saved.put("model", info.optString("model", ""));
            saved.put("android", info.optString("android", ""));
            saved.put("sdk", info.optInt("sdk", 0));
            saved.put("latency", info.optString("latency", ""));
            saved.put("transport", "classic".equals(transport) ? "classic" : "wireless");
            saved.put("defaultHomePackage", info.optString("defaultHomePackage", ""));
            saved.put("defaultHomeApp", info.optString("defaultHomeApp", "stock"));
            saved.put("stockLabel", info.optString("stockLabel", "系统桌面"));
            saved.put("lastSeen", System.currentTimeMillis());
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LAST_TV, saved.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private void rememberBaseline(String serial, String currentHome) {
        if (currentHome == null || currentHome.isEmpty()) {
            return;
        }
        String packageName = packageOf(currentHome);
        if (Catalog.appIdForPackage(packageName).isEmpty() || "stock".equals(packageName)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(baselineKey(serial), currentHome).apply();
        }
    }

    private String readBaseline(String serial) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(baselineKey(serial), "");
    }

    private static String baselineKey(String serial) {
        return KEY_BASELINE_PREFIX + serial.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String waitForState(String serial, int seconds, boolean verbose) throws Exception {
        long deadline = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        boolean warnedUnauthorized = false;
        boolean sawUnauthorized = false;
        long lastLog = 0;
        int ticks = 0;
        while (System.currentTimeMillis() < deadline) {
            String state = stateOf(serial);
            if ("device".equals(state)) {
                sink.line("已连接：" + serial + "（电视已授权调试）");
                return state;
            }
            if ("unauthorized".equals(state)) {
                sawUnauthorized = true;
                if (!warnedUnauthorized) {
                    sink.line("等待电视授权：请在电视屏幕上选择“允许 USB 调试”"
                            + "并勾选“始终允许”。");
                    warnedUnauthorized = true;
                }
                if (!verbose) {
                    return state;
                }
            } else if ("offline".equals(state) || "connecting".equals(state)) {
                if (verbose && System.currentTimeMillis() - lastLog > 15000L) {
                    sink.line("电视正在连接，仍在等待…");
                    lastLog = System.currentTimeMillis();
                }
            } else if (verbose && ticks > 0 && ticks % 15 == 0) {
                sink.line("仍在等待电视响应…");
            }
            ticks++;
            sleep(1000L);
        }
        return sawUnauthorized ? "unauthorized" : stateOf(serial);
    }

    private String stateOf(String serial) {
        AdbClient.Result state = adb.run(
                new String[]{"-s", serial, "get-state"}, 10, null);
        for (String line : state.text().split("\\r?\\n")) {
            String value = line.trim().toLowerCase(Locale.US);
            if ("device".equals(value)
                    || "unauthorized".equals(value)
                    || "offline".equals(value)
                    || "connecting".equals(value)) {
                return value;
            }
        }
        String text = state.text().toLowerCase(Locale.US);
        if (text.contains("unauthorized")) {
            return "unauthorized";
        }
        if (text.contains("offline")) {
            return "offline";
        }
        return "unknown";
    }

    private String resolveHome(String serial) {
        String output = adbText(serial, "shell", "cmd", "package",
                "resolve-activity", "--brief",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.HOME");
        return parseComponent(output);
    }

    private String findHomeComponent(String serial, String packageName) {
        String output = adbText(serial, "shell", "cmd", "package",
                "query-activities", "--brief",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.HOME");
        String component = componentForPackage(output, packageName);
        if (!component.isEmpty()) {
            return component;
        }
        for (Catalog.App app : Catalog.apps()) {
            if (!app.packageName.equals(packageName)) {
                continue;
            }
            for (Catalog.Variant variant : app.variants.values()) {
                if (packageName.equals(variant.packageName) && variant.homeComponent != null) {
                    return variant.homeComponent;
                }
            }
        }
        return "";
    }

    private Set<String> readPackages(String serial) {
        return parsePackages(adbText(serial, "shell", "pm", "list", "packages"));
    }

    private String readVersion(String serial, String packageName) {
        String output = adbText(serial, "shell", "dumpsys", "package", packageName);
        Matcher matcher = Pattern.compile("versionName=([^\\s]+)").matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String adbText(String serial, String... command) {
        return adb.run(withSerial(serial, command), 30, null).text();
    }

    private static String[] withSerial(String serial, String... command) {
        String[] args = new String[command.length + 2];
        args[0] = "-s";
        args[1] = serial;
        System.arraycopy(command, 0, args, 2, command.length);
        return args;
    }

    private static Set<String> parsePackages(String text) {
        Set<String> packages = new HashSet<String>();
        for (String line : (text == null ? "" : text).split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith("package:")) {
                packages.add(value.substring("package:".length()).trim());
            }
        }
        return packages;
    }

    private static String prop(String props, String name) {
        for (String line : (props == null ? "" : props).split("\\r?\\n")) {
            int bracket = line.indexOf('[');
            int close = line.indexOf(']', bracket + 1);
            if (bracket <= 0 || close <= bracket) {
                continue;
            }
            String key = line.substring(0, bracket).trim();
            if (!name.equals(key)) {
                continue;
            }
            int valueStart = line.indexOf('[', close + 1);
            int valueEnd = line.indexOf(']', valueStart + 1);
            if (valueStart >= 0 && valueEnd > valueStart) {
                return line.substring(valueStart + 1, valueEnd).trim();
            }
        }
        return "";
    }

    private static String parseComponent(String output) {
        String text = output == null ? "" : output.trim();
        if (text.isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("resolveractivity") || lower.contains("no activities found")) {
            return "";
        }
        Matcher matcher = COMPONENT.matcher(text);
        while (matcher.find()) {
            String packageName = matcher.group(1);
            String clazz = matcher.group(2);
            if (packageName.endsWith("ResolverActivity")) {
                continue;
            }
            return packageName + "/" + (clazz.startsWith(".") ? packageName + clazz : clazz);
        }
        return "";
    }

    private static String componentForPackage(String output, String packageName) {
        Matcher matcher = COMPONENT.matcher(output == null ? "" : output);
        while (matcher.find()) {
            if (!packageName.equals(matcher.group(1))) {
                continue;
            }
            String clazz = matcher.group(2);
            return packageName + "/" + (clazz.startsWith(".") ? packageName + clazz : clazz);
        }
        return "";
    }

    private static String packageOf(String component) {
        if (component == null) {
            return "";
        }
        int slash = component.indexOf('/');
        return slash > 0 ? component.substring(0, slash) : "";
    }

    private static String friendlyBrand(String manufacturer, String brand, String model) {
        String value = (clean(manufacturer) + " " + clean(brand)).toLowerCase(Locale.US);
        if (value.contains("xiaomi") || value.contains("redmi")) {
            return "小米电视";
        }
        if (value.contains("sony")) {
            return "索尼电视";
        }
        if (value.contains("tcl") || value.contains("雷鸟")) {
            return "TCL 电视";
        }
        if (value.contains("hisense") || value.contains("海信")) {
            return "海信电视";
        }
        if (value.contains("skyworth") || value.contains("创维")) {
            return "创维电视";
        }
        if (value.contains("changhong") || value.contains("长虹")) {
            return "长虹电视";
        }
        if (value.contains("huawei") || value.contains("honor")) {
            return "智慧屏";
        }
        String cleanModel = clean(model);
        if (!cleanModel.isEmpty()) {
            return cleanModel;
        }
        String cleanManufacturer = clean(manufacturer);
        return cleanManufacturer.isEmpty() ? "电视" : cleanManufacturer;
    }

    private static int intValue(String value) {
        try {
            return Integer.parseInt(clean(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String scrub(String text, String code) {
        if (text == null) {
            return "";
        }
        if (code == null || !code.matches("\\d{6}")) {
            return text;
        }
        return text.replace(code, "******");
    }

    private static String snippet(String text) {
        String value = clean(text);
        if (value.length() > 500) {
            value = value.substring(0, 500) + " …";
        }
        return value;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}