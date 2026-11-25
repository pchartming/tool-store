package com.toolstore.installer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads and installs the community Clean Your TV phone app that the
 * official cleanyour.tv site lists under "Community apps". The TV launcher
 * itself still has no official APK; this phone app is the official-site
 * endorsed way to clean a TV and install the launcher without a PC.
 *
 * Safety properties:
 *  - only the fixed repository linked by cleanyour.tv, resolved from its
 *    GitHub Releases API (no hardcoded release number);
 *  - the release's SHA-256, the fixed package name and the compiled-in signer
 *    certificate are checked before the system installer sees the file;
 *  - installation always goes through the visible system installer;
 *  - this path never touches the connected TV.
 */
final class PhoneAppInstaller {

    static final String PACKAGE_NAME = "tv.cleanyour.app";
    static final String DISPLAY_NAME = "Clean Your TV 手机版";
    static final String SIGNER_SHA256 =
            "eaf7af3867bb29ff4bb038854842c93f42ffe4198a087e611cb18f925201cd65";
    static final int MIN_SDK = 28;

    static final String ACTION_RESULT = "com.toolstore.installer.PHONE_INSTALL_RESULT";
    static final String EXTRA_SESSION = "com.toolstore.installer.extra.PHONE_SESSION";

    private static final String RELEASES_API =
            "https://api.github.com/repos/udhaykumarbala/cleanyourtv-app/releases/latest";
    private static final String RELEASE_PATH =
            "/udhaykumarbala/cleanyourtv-app/releases/download/";
    private static final String ASSET_API_PATH =
            "/repos/udhaykumarbala/cleanyourtv-app/releases/assets/";
    private static final String API_ACCEPT = "application/octet-stream";
    private static final long MAX_APK_BYTES = 25L * 1024L * 1024L;
    private static final int MAX_INDEX_BYTES = 1024 * 1024;
    private static final int MAX_HASH_BYTES = 4096;
    private static final long INSTALL_WAIT_MS = TimeUnit.MINUTES.toMillis(4);
    private static final Set<String> API_HOSTS = hosts("api.github.com");
    private static final Set<String> ASSET_HOSTS = hosts("github.com");
    private static final Pattern APK_NAME =
            Pattern.compile("^cleanyourtv-(\\d+(?:\\.\\d+){1,3})\\.apk$");
    private static final Pattern VERSION =
            Pattern.compile("\\d+(?:\\.\\d+){1,3}");
    private static final Pattern HASH_LINE =
            Pattern.compile("(?m)^\\s*([0-9a-fA-F]{64})\\s+(\\S+)\\s*$");

    private static final Object LOCK = new Object();
    private static Waiter waiter;

    private PhoneAppInstaller() {
    }

    private static final class Waiter {
        final int sessionId;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger status = new AtomicInteger(PackageInstaller.STATUS_FAILURE);

        Waiter(int sessionId) {
            this.sessionId = sessionId;
        }
    }

    private static final class VerifiedApk {
        final String versionName;
        final int minSdk;
        final long bytes;

        VerifiedApk(String versionName, int minSdk, long bytes) {
            this.versionName = versionName;
            this.minSdk = minSdk;
            this.bytes = bytes;
        }
    }

    /** Installation state on this phone, never the TV. */
    static JSONObject installed(Context context) {
        JSONObject result = new JSONObject();
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(PACKAGE_NAME, 0);
            result.put("installed", true);
            result.put("versionName", info.versionName == null ? "" : info.versionName.trim());
            result.put("versionCode", Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode);
            ApplicationInfo application = info.applicationInfo;
            result.put("label", application == null
                    ? DISPLAY_NAME : String.valueOf(application.loadLabel(manager)));
        } catch (PackageManager.NameNotFoundException error) {
            try {
                result.put("installed", false);
            } catch (Exception ignored) {
            }
        } catch (Throwable error) {
            try {
                result.put("installed", false);
                result.put("error", String.valueOf(error.getMessage()));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    static Intent launchIntent(Context context) {
        try {
            return context.getPackageManager()
                    .getLaunchIntentForPackage(PACKAGE_NAME);
        } catch (Throwable error) {
            return null;
        }
    }

    static boolean canRequestInstalls(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return true;
        }
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable error) {
            return false;
        }
    }

    static void openInstallSettings(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Resolves, downloads, verifies and hands the APK to the system installer.
     * Blocks while the user decides in the system dialog, so callers run it on
     * a background executor.
     */
    static JSONObject install(Context context, AdbClient.Sink sink, ProgressSink progress)
            throws Exception {
        final Context app = context.getApplicationContext();
        final AdbClient.Sink log = sink == null ? new AdbClient.Sink() {
            @Override
            public void line(String text) {
            }
        } : sink;
        final ProgressSink updates = progress == null ? new ProgressSink() {
            @Override
            public void update(String phase, String flavor, long received, long total) {
            }
        } : progress;

        if (Build.VERSION.SDK_INT < MIN_SDK) {
            throw new IOException(DISPLAY_NAME + " 需要 Android 9 或更高版本；当前手机是 Android "
                    + Build.VERSION.RELEASE + "。可以改用电安装指引。");
        }
        if (!canRequestInstalls(app)) {
            throw new IOException("还没有允许“工具市场”安装应用。请先在系统设置中允许安装未知应用，再重试。");
        }

        JSONObject already = installed(app);
        if (already.optBoolean("installed", false)) {
            already.put("ok", true);
            already.put("alreadyInstalled", true);
            log.line("手机上已经有 " + DISPLAY_NAME + "。");
            return already;
        }

        log.line("正在读取 " + DISPLAY_NAME + " 的官方 Release…");
        JSONObject release = fetchLatestRelease();
        String version = normalizeVersion(release.optString("tag_name"));
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            throw new IOException("官方 Release 没有列出安装包，已停止。");
        }
        JSONObject apkAsset = findApkAsset(assets);
        if (apkAsset == null) {
            throw new IOException("官方 Release 中没有找到手机版安装包，已停止。");
        }
        String url = apkAsset.optString("browser_download_url");
        requireAssetUrl(url);
        long assetId = apkAsset.optLong("id", 0);
        long declaredSize = apkAsset.optLong("size", 0);
        if (declaredSize <= 0 || declaredSize > MAX_APK_BYTES) {
            throw new IOException("官方安装包大小异常，已停止。");
        }
        String sha = releaseSha(assets, apkAsset);
        log.line("官方最新版：" + (version.isEmpty() ? "未知" : version)
                + " · " + (declaredSize / 1024L / 1024L) + " MB");

        File directory = new File(app.getCacheDir(), "toolstore-phone");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建下载缓存目录。");
        }
        File target = new File(directory, "cleanyourtv-phone.apk");
        File part = new File(directory, "cleanyourtv-phone.apk.part");
        try {
            HttpDownloader.Artifact artifact;
            String apiUrl = assetId > 0
                    ? "https://api.github.com" + ASSET_API_PATH + assetId : "";
            if (!apiUrl.isEmpty()) {
                try {
                    // api.github.com just answered the release query, so it is
                    // the most reliable official route when the mobile network
                    // blocks github.com. It redirects to the same official
                    // release CDN; the pinned SHA-256 still decides.
                    artifact = HttpDownloader.download(apiUrl, API_HOSTS, ASSET_API_PATH,
                            target, part, MAX_APK_BYTES, sha, updates, "phone", API_ACCEPT);
                } catch (HttpDownloader.IntegrityException error) {
                    throw error;
                } catch (IOException apiError) {
                    log.line("GitHub API 下载通道中断，改用发布页地址重试…");
                    artifact = HttpDownloader.download(url, ASSET_HOSTS,
                            RELEASE_PATH, target, part, MAX_APK_BYTES, sha, updates, "phone");
                }
            } else {
                artifact = HttpDownloader.download(url, ASSET_HOSTS,
                        RELEASE_PATH, target, part, MAX_APK_BYTES, sha, updates, "phone");
            }
            updates.update("verify", "phone", artifact.bytes, artifact.bytes);
            log.line("正在校验包名、签名证书与 SHA-256…");
            VerifiedApk verified = verifyApk(app, target, version);
            log.line("✓ 安装包校验通过：" + verified.versionName + "（SHA-256 "
                    + shortSha(artifact.sha256) + "）");
            updates.update("install", "phone", 0, 0);
            log.line("正在交给系统安装器，请在弹窗中确认安装…");

            JSONObject result = commit(app, target, verified);
            updates.update("done", "phone", artifact.bytes, artifact.bytes);
            result.put("ok", true);
            result.put("versionName", verified.versionName);
            result.put("source", "官方站点推荐的 GitHub Release");
            result.put("sha256", artifact.sha256);
            result.put("bytes", artifact.bytes);
            return result;
        } finally {
            HttpDownloader.delete(part);
            HttpDownloader.delete(target);
        }
    }

    private static JSONObject fetchLatestRelease() throws IOException {
        String body = HttpDownloader.fetchText(RELEASES_API, API_HOSTS, MAX_INDEX_BYTES);
        JSONObject release;
        try {
            release = new JSONObject(body);
        } catch (Exception error) {
            throw new IOException("GitHub Release 索引格式异常，已停止。");
        }
        if (release.optBoolean("draft", false)) {
            throw new IOException("官方 Release 还是草稿，已停止。");
        }
        return release;
    }

    private static JSONObject findApkAsset(JSONArray assets) {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            if (APK_NAME.matcher(asset.optString("name", "")).matches()) {
                return asset;
            }
        }
        return null;
    }

    private static String releaseSha(JSONArray assets, JSONObject apkAsset) throws IOException {
        try {
            String sha = HttpDownloader.normalizeSha256(apkAsset.optString("digest", ""));
            if (!sha.isEmpty()) {
                return sha;
            }
        } catch (IOException ignored) {
            // Older releases may not include the API digest; fall back to the
            // official .sha256 asset below.
        }
        String apkName = apkAsset.optString("name", "");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !(apkName + ".sha256").equals(asset.optString("name", ""))) {
                continue;
            }
            String hashUrl = asset.optString("browser_download_url");
            requireAssetUrl(hashUrl);
            String text = HttpDownloader.fetchText(hashUrl, ASSET_HOSTS, MAX_HASH_BYTES);
            Matcher matcher = HASH_LINE.matcher(text);
            while (matcher.find()) {
                if (apkName.equals(matcher.group(2))) {
                    return HttpDownloader.normalizeSha256(matcher.group(1));
                }
            }
            break;
        }
        throw new IOException("官方 Release 没有提供有效的 SHA-256，已停止。");
    }

    private static VerifiedApk verifyApk(Context context, File apk, String expectedVersion)
            throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo info;
        try {
            info = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        } catch (Throwable error) {
            throw new IOException("下载的安装包无法解析，可能已损坏。请重试。");
        }
        if (info == null || info.applicationInfo == null) {
            throw new IOException("下载的安装包无法解析，可能已损坏。请重试。");
        }
        if (!PACKAGE_NAME.equals(info.packageName)) {
            throw new IOException("安装包包名与官方目录不一致，已停止。");
        }
        int minSdk = info.applicationInfo.minSdkVersion;
        if (minSdk > Build.VERSION.SDK_INT) {
            throw new IOException("这个安装包要求 Android API " + minSdk
                    + "，当前手机只有 API " + Build.VERSION.SDK_INT + "。");
        }
        Signature[] signers = signatures(info);
        if (signers == null || signers.length != 1) {
            throw new IOException("安装包签名数量异常，已停止。");
        }
        String signer;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            signer = HttpDownloader.hex(digest.digest(signers[0].toByteArray()));
        } catch (Exception error) {
            throw new IOException("无法校验安装包签名，已停止。");
        }
        if (!SIGNER_SHA256.equals(signer)) {
            throw new IOException("安装包签名证书与已核对的官方证书不一致，已停止。");
        }
        String versionName = info.versionName == null ? "" : info.versionName.trim();
        String expected = normalizeVersion(expectedVersion);
        if (!expected.isEmpty() && !expected.equals(normalizeVersion(versionName))) {
            throw new IOException("官方 Release 标注的版本（" + expectedVersion
                    + "）与安装包版本（" + versionName + "）不一致，已停止。");
        }
        return new VerifiedApk(versionName, minSdk, apk.length());
    }

    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            SigningInfo signing = info.signingInfo;
            return signing == null ? null : signing.getApkContentsSigners();
        }
        return info.signatures;
    }

    private static JSONObject commit(Context context, File apk, VerifiedApk verified)
            throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(PACKAGE_NAME);
        params.setSize(apk.length());
        final int sessionId;
        try {
            sessionId = installer.createSession(params);
        } catch (SecurityException error) {
            throw new IOException("系统拒绝了安装会话。请确认已经允许“工具市场”安装未知应用。");
        } catch (Throwable error) {
            throw new IOException("系统安装器不可用，无法创建安装会话：" + safe(error));
        }

        Waiter wait = new Waiter(sessionId);
        synchronized (LOCK) {
            waiter = wait;
        }
        PackageInstaller.Session session = null;
        boolean committed = false;
        try {
            session = installer.openSession(sessionId);
            OutputStream output = session.openWrite("base.apk", 0, apk.length());
            try {
                InputStream input = new FileInputStream(apk);
                try {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    session.fsync(output);
                } finally {
                    input.close();
                }
            } finally {
                output.close();
            }

            Intent callback = new Intent(context, PhoneInstallReceiver.class)
                    .setAction(ACTION_RESULT)
                    .putExtra(EXTRA_SESSION, sessionId);
            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= android.app.PendingIntent.FLAG_MUTABLE;
            }
            android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(
                    context, sessionId, callback, flags);
            session.commit(pending.getIntentSender());
            committed = true;

            boolean received;
            try {
                received = wait.latch.await(INSTALL_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                received = false;
            }
            if (!received) {
                throw new IOException("等待系统安装确认超时。如果已经取消安装，可以直接重试。");
            }
            int status = wait.status.get();
            if (status != PackageInstaller.STATUS_SUCCESS) {
                throw new IOException(installFailure(status));
            }

            JSONObject result = installed(context);
            for (int i = 0; i < 12 && !result.optBoolean("installed", false); i++) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
                result = installed(context);
            }
            if (!result.optBoolean("installed", false)) {
                throw new IOException("系统报告安装成功，但没有在手机上找到 "
                        + PACKAGE_NAME + "。请打开系统设置确认。");
            }
            result.put("minSdk", verified.minSdk);
            return result;
        } finally {
            synchronized (LOCK) {
                if (waiter == wait) {
                    waiter = null;
                }
            }
            if (session != null) {
                if (!committed) {
                    try {
                        session.abandon();
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    session.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Called from {@link PhoneInstallReceiver} for final session statuses. */
    static void onSessionResult(int sessionId, int status) {
        Waiter current;
        synchronized (LOCK) {
            current = waiter;
        }
        if (current == null) {
            return;
        }
        if (sessionId >= 0 && sessionId != current.sessionId) {
            return;
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            return;
        }
        current.status.set(status);
        current.latch.countDown();
    }

    private static String installFailure(int status) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "安装被取消，手机上的应用没有变化。";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "系统阻止了这次安装。请在系统设置中允许“工具市场”安装未知应用，"
                        + "并检查手机的安全策略。";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "手机上已有签名或包名冲突的同名应用。本应用不会自动卸载它；"
                        + "请先确认来源后手动处理。";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "这个安装包与当前手机系统不兼容。";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "系统安装器认为安装包或安装会话无效。请重试。";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "手机剩余空间不足，安装没有完成。";
            default:
                return "系统安装器没有完成安装（状态 " + status + "）。没有改动任何应用。";
        }
    }

    private static void requireAssetUrl(String url) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IOException("官方 Release 缺少下载地址，已停止。");
        }
        try {
            java.net.URL parsed = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(parsed.getProtocol())
                    || !"github.com".equalsIgnoreCase(parsed.getHost())
                    || !parsed.getPath().startsWith(RELEASE_PATH)) {
                throw new IOException("官方 Release 指向了非预期下载地址，已停止。");
            }
        } catch (java.net.MalformedURLException error) {
            throw new IOException("官方 Release 下载地址格式异常，已停止。");
        }
    }

    private static String normalizeVersion(String text) {
        Matcher matcher = VERSION.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : "";
    }

    private static String shortSha(String value) {
        return value == null ? "" : value.substring(0, Math.min(12, value.length())) + "…";
    }

    private static String safe(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? String.valueOf(error) : message.trim();
    }

    private static Set<String> hosts(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }
}