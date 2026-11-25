package com.toolstore.installer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads, verifies and installs only fixed catalog APKs over an already
 * authorized ADB connection.
 *
 * Safety properties:
 *  - URLs come from fixed official indexes over allowlisted HTTPS hosts;
 *  - the package, signer pin and optional version assertion are compiled-in;
 *  - the APK is hash-checked and parsed locally before adb sees it;
 *  - an existing app is never uninstalled automatically;
 *  - system packages are never targeted.
 */
final class ApkManager {

    static final class InstallException extends Exception {
        InstallException(String message) {
            super(message);
        }
    }

    private static final AtomicBoolean INSTALL_BUSY = new AtomicBoolean(false);
    private static final Pattern SUCCESS = Pattern.compile("(?i)\\bSuccess\\b");
    private static final Pattern VERSION_NAME = Pattern.compile("versionName=([^\\s]+)");
    private static final long MAX_APK_BYTES = 80L * 1024L * 1024L;

    private final Context context;
    private final AdbClient adb;
    private final AdbClient.Sink sink;
    private final ProgressSink progress;

    ApkManager(Context context, AdbClient.Sink sink, ProgressSink progress) throws Exception {
        this.context = context.getApplicationContext();
        this.sink = sink == null ? new AdbClient.Sink() {
            @Override
            public void line(String text) {
            }
        } : sink;
        this.progress = progress == null ? new ProgressSink() {
            @Override
            public void update(String phase, String flavor, long received, long total) {
            }
        } : progress;
        this.adb = new AdbClient(this.context);
    }

    JSONObject install(String appId, String variantId, String host, int port,
                       boolean setHome) throws Exception {
        if (!INSTALL_BUSY.compareAndSet(false, true)) {
            throw new InstallException("已有安装任务在进行，请等它完成后再试。");
        }
        File target = null;
        try {
            Catalog.App app = Catalog.app(appId);
            if (app == null) {
                throw new IllegalArgumentException("未知应用。");
            }
            String requestedVariant = variantId == null ? "" : variantId.trim();
            Catalog.Variant variant = app.variants.get(requestedVariant);
            if (variant == null || app.variants.isEmpty()) {
                throw new InstallException(app.variants.isEmpty()
                        ? "这个应用没有可直接安装的 APK，请按详情页的电脑指引操作。"
                        : "请选择一个明确的安装版本后重试。");
            }
            String serial = host + ":" + port;
            String state = stateOf(serial);
            if (!"device".equals(state)) {
                throw new InstallException("电视尚未授权调试，不能安装。请先完成连接并允许授权。");
            }
            int deviceSdk = readDeviceSdk(serial);

            sink.line("正在读取 " + app.name + " 的官方版本与校验信息…");
            ApkResolver.Resolved resolved = ApkResolver.resolve(app, variant);
            if (!resolved.note.isEmpty()) {
                sink.line(resolved.note);
            }
            if (resolved.sources.isEmpty()) {
                throw new InstallException("没有找到可用的官方下载源。");
            }

            File directory = new File(context.getCacheDir(), "toolstore-downloads");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new InstallException("无法创建下载缓存目录。");
            }
            String safeName = app.id + "-" + variant.id + ".apk";
            target = new File(directory, safeName);
            File part = new File(directory, safeName + ".part");
            long maxBytes = Math.min(MAX_APK_BYTES, variant.maxBytes);

            HttpDownloader.Artifact artifact = null;
            VerifiedApk verified = null;
            ApkResolver.Source usedSource = null;
            IOException lastNetworkError = null;
            for (int index = 0; index < resolved.sources.size(); index++) {
                ApkResolver.Source source = resolved.sources.get(index);
                boolean last = index == resolved.sources.size() - 1;
                if (index > 0) {
                    sink.line("正在切换到备用官方源：" + source.label + "…");
                }
                sink.line("下载源：" + source.label
                        + (source.version.isEmpty() ? "" : " · " + source.version));
                progress.update("download", variant.id, 0, 0);
                try {
                    artifact = HttpDownloader.download(
                            source.url, source.hosts, source.requiredPathPrefix,
                            target, part, maxBytes, source.sha256, progress, variant.id);
                    progress.update("verify", variant.id, artifact.bytes, artifact.bytes);
                    sink.line("正在校验包名、签名证书与系统版本…");
                    verified = verifyApk(target, app, resolved, variant, source, deviceSdk);
                    usedSource = source;
                    break;
                } catch (HttpDownloader.IntegrityException integrity) {
                    // Hash, host, path or size rejection: do not hide it by
                    // silently switching mirrors.
                    throw new InstallException(integrity.getMessage());
                } catch (IOException network) {
                    lastNetworkError = network;
                    HttpDownloader.delete(target);
                    if (last) {
                        throw new InstallException("所有官方下载源都暂时不可用。"
                                + safe(lastNetworkError));
                    }
                    sink.line("! " + source.label + " 下载失败：" + safe(network)
                            + "，将尝试下一个官方源。");
                }
            }
            if (artifact == null || verified == null || usedSource == null) {
                throw new InstallException("所有官方下载源都暂时不可用。"
                        + safe(lastNetworkError));
            }
            if (usedSource.sha256.isEmpty()) {
                sink.line("此官方源未提供独立哈希；已核对包名、版本和固定签名证书。");
            }

            sink.line("✓ 安装包校验通过：" + verified.packageName + " "
                    + verified.versionName + "（SHA-256 "
                    + shortSha(artifact.sha256) + "）");
            progress.update("install", variant.id, 0, 0);

            String previousVersion = readInstalledVersion(serial, verified.packageName);
            if (!previousVersion.isEmpty()) {
                sink.line("电视上已有版本 " + previousVersion
                        + "，将覆盖安装并保留应用数据；不会先卸载。");
            }

            sink.line("正在把安装包推送到电视并安装，可能需要几分钟，请保持本应用在前台…");
            AdbClient.Result result = adb.run(
                    new String[]{"-s", serial, "install", "-r", target.getAbsolutePath()},
                    600, sink);
            if (!result.ok() || !SUCCESS.matcher(result.output).find()) {
                throw new InstallException(installFailure(result));
            }

            String installedVersion = readInstalledVersion(serial, verified.packageName);
            if (installedVersion.isEmpty()) {
                throw new InstallException("ADB 报告安装成功，但没有在电视上找到 "
                        + verified.packageName + "。请刷新电视状态后再确认。");
            }
            progress.update("done", variant.id, artifact.bytes, artifact.bytes);
            sink.line("✓ " + app.name + " " + installedVersion + " 已安装到电视");

            JSONObject home = null;
            JSONObject resultJson = new JSONObject();
            resultJson.put("ok", true);
            resultJson.put("kind", "install");
            resultJson.put("appId", app.id);
            resultJson.put("variantId", variant.id);
            resultJson.put("packageName", verified.packageName);
            resultJson.put("versionName", installedVersion);
            resultJson.put("previousVersion", previousVersion);
            resultJson.put("sha256", artifact.sha256);
            resultJson.put("hashPinned", !usedSource.sha256.isEmpty());
            resultJson.put("source", usedSource.label);
            resultJson.put("sourceVersion", usedSource.version);
            resultJson.put("bytes", artifact.bytes);
            resultJson.put("installed", true);

            if (setHome && variant.homeComponent != null) {
                try {
                    home = new DeviceSession(context, sink).setHome(
                            app.id, variant.id, host, port);
                    resultJson.put("home", home);
                } catch (Throwable error) {
                    String warning = "应用已安装，但默认桌面没有切换：" + safe(error);
                    resultJson.put("homeWarning", warning);
                    sink.line("! " + warning);
                }
            } else if (variant.homeComponent != null) {
                sink.line("按你的选择保留当前默认桌面。");
                resultJson.put("homeKept", true);
            } else {
                sink.line("这是工具应用，不会更改默认桌面。");
                resultJson.put("homeKept", true);
            }
            sink.line("安装完成。可在“我的电视”中查看已安装状态和权限引导。");
            return resultJson;
        } finally {
            HttpDownloader.delete(target);
            INSTALL_BUSY.set(false);
        }
    }

    static boolean isBusy() {
        return INSTALL_BUSY.get();
    }

    static void cleanup(Context context) {
        File directory = new File(context.getCacheDir(), "toolstore-downloads");
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                HttpDownloader.delete(file);
            }
        }
    }

    private VerifiedApk verifyApk(File apk, Catalog.App app, ApkResolver.Resolved resolved,
                                  Catalog.Variant variant, ApkResolver.Source source,
                                  int deviceSdk) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo info;
        try {
            info = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        } catch (Throwable error) {
            throw new InstallException("下载的安装包无法解析，可能已损坏。请重试。");
        }
        if (info == null || info.applicationInfo == null) {
            throw new InstallException("下载的安装包无法解析，可能已损坏。请重试。");
        }
        if (!variant.packageName.equals(info.packageName)
                || !resolved.packageName.equals(info.packageName)) {
            throw new InstallException("安装包包名与官方目录不一致，已停止。不会安装到电视。");
        }
        ApplicationInfo application = info.applicationInfo;
        int minSdk = application.minSdkVersion;
        if (deviceSdk > 0 && minSdk > deviceSdk) {
            throw new InstallException("这个安装包要求 Android API " + minSdk
                    + "，电视只有 API " + deviceSdk + "。请选择兼容版本。");
        }

        Signature[] signers = signatures(info);
        if (signers == null || signers.length != 1) {
            throw new InstallException("安装包签名数量异常，已停止。");
        }
        String signer;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            signer = HttpDownloader.hex(digest.digest(signers[0].toByteArray()));
        } catch (Exception error) {
            throw new InstallException("无法校验安装包签名，已停止。");
        }
        if (!resolved.signerSha256.equals(signer)) {
            throw new InstallException("安装包签名证书与已核对的官方证书不一致，已停止。");
        }
        String versionName = info.versionName == null ? "" : info.versionName.trim();
        if (source.requireVersion) {
            // Some official indexes state the exact version for this asset.
            // Require it to match, so a stale CDN or swapped file cannot pass.
            String expected = normalizeVersion(source.version);
            String actual = normalizeVersion(versionName);
            if (expected.isEmpty() || !expected.equals(actual)) {
                throw new InstallException(source.label + " 标注的版本（"
                        + (source.version.isEmpty() ? "未知" : source.version)
                        + "）与安装包版本（" + (versionName.isEmpty() ? "未知" : versionName)
                        + "）不一致，官方源可能正在更新。请稍后重试。");
            }
        }
        return new VerifiedApk(info.packageName, versionName);
    }

    private static String normalizeVersion(String value) {
        Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+){1,3}")
                .matcher(value == null ? "" : value.trim());
        return matcher.find() ? matcher.group() : "";
    }

    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            SigningInfo signing = info.signingInfo;
            return signing == null ? null : signing.getApkContentsSigners();
        }
        return info.signatures;
    }

    private String installFailure(AdbClient.Result result) {
        String output = result.output == null ? "" : result.output;
        String lower = output.toLowerCase(Locale.US);
        if (result.timedOut) {
            return "安装命令超时，结果未确认。请刷新电视状态；如果仍显示旧版本，"
                    + "请保持电视亮屏后重试。";
        }
        if (lower.contains("install_failed_update_incompatible")
                || lower.contains("inconsistent_certificates")) {
            return "电视上的同名应用签名不同，系统拒绝了覆盖安装。"
                    + "本应用不会自动卸载它；请在电视上确认该应用来源后手动处理。";
        }
        if (lower.contains("install_failed_version_downgrade")) {
            return "电视上已有更高版本，系统拒绝降级。请先确认是否真的需要降级。";
        }
        if (lower.contains("install_failed_user_restricted")) {
            return "电视限制了应用安装。请在电视的开发者选项或安全设置中允许调试安装，"
                    + "然后重试。";
        }
        if (lower.contains("install_failed_verification_failure")) {
            return "电视安装校验拒绝了这个安装包。请检查电视的安全设置后重试。";
        }
        if (lower.contains("no space") || lower.contains("insufficient_storage")) {
            return "电视剩余空间不足，安装没有完成。";
        }
        String snippet = output.trim();
        if (snippet.length() > 500) {
            snippet = snippet.substring(0, 500) + " …";
        }
        return "安装没有成功。" + (snippet.isEmpty() ? "" : "\n" + snippet);
    }

    private String stateOf(String serial) {
        AdbClient.Result state = adb.run(
                new String[]{"-s", serial, "get-state"}, 10, null);
        for (String line : state.text().split("\\r?\\n")) {
            if ("device".equals(line.trim())) {
                return "device";
            }
        }
        return "unknown";
    }

    private int readDeviceSdk(String serial) {
        String value = adb.run(new String[]{"-s", serial,
                "shell", "getprop", "ro.build.version.sdk"}, 15, null).text().trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String readInstalledVersion(String serial, String packageName) {
        String dump = adb.run(new String[]{"-s", serial,
                "shell", "dumpsys", "package", packageName}, 30, null).text();
        if (!dump.contains("package:")) {
            return "";
        }
        Matcher matcher = VERSION_NAME.matcher(dump);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 16 ? sha : sha.substring(0, 16) + "…";
    }

    private static String safe(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        return message.trim();
    }

    private static final class VerifiedApk {
        final String packageName;
        final String versionName;

        VerifiedApk(String packageName, String versionName) {
            this.packageName = packageName;
            this.versionName = versionName;
        }
    }
}