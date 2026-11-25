package com.toolstore.installer;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a catalog entry to the latest official APK sources. Every version
 * is discovered from a live official index at install time; no release number
 * is compiled in. Source order is project-specific: an author-maintained Gitee
 * mirror or official updater when one exists, otherwise the official site,
 * then GitHub fallback.
 */
final class ApkResolver {

    static final class Source {
        final String label;
        final String url;
        final String sha256;
        final String version;
        final String requiredPathPrefix;
        final Set<String> hosts;
        final boolean requireVersion;

        Source(String label, String url, String sha256, String version,
               String requiredPathPrefix, Set<String> hosts, boolean requireVersion) {
            this.label = label;
            this.url = url;
            this.sha256 = sha256;
            this.version = version;
            this.requiredPathPrefix = requiredPathPrefix;
            this.hosts = hosts;
            this.requireVersion = requireVersion;
        }
    }

    static final class Resolved {
        final String packageName;
        final String signerSha256;
        final List<Source> sources;
        final String note;

        Resolved(String packageName, String signerSha256, List<Source> sources, String note) {
            this.packageName = packageName;
            this.signerSha256 = signerSha256;
            this.sources = Collections.unmodifiableList(sources);
            this.note = note == null ? "" : note;
        }

        Source primary() {
            return sources.get(0);
        }
    }

    static final String PLAN_CERT =
            "ff0d5ef112be8677ac9f9f3dba39bed6c34f24e506baf059ef8729b552702474";
    static final String AERIAL_CERT =
            "e08962dceaab534210f4725ecb71e3d0906d60dc9da9f2c5fa3b1759874d6685";
    static final String QUIET_CERT =
            "ff3108659c8cf2188e613de82daf32ba679321d85295295d3c42e041a3978d39";
    static final String HALO_CERT =
            "ed28a19770dac5f926e7adf27224f05dd07d1a6dd54af89fc663bbcf102c41dd";
    static final String STUDY_CERT =
            "5c800ca4b199287b0deef76dde26544fa416254fe62007567e516eb1fe7ae724";

    private static final Set<String> PLAN_HOSTS =
            hosts("planyourtv.me", "www.planyourtv.me");
    private static final Set<String> AERIAL_HOSTS = hosts("aerial.qust.me");
    private static final Set<String> HALO_HOSTS = hosts("halo-tv.pages.dev");
    private static final Set<String> GITHUB_API_HOSTS = hosts("api.github.com");
    private static final Set<String> GITHUB_ASSET_HOSTS = hosts("github.com");
    private static final Set<String> GITEE_HOSTS =
            hosts("gitee.com", "foruda.gitee.com");
    private static final Set<String> QUIET_HOSTS = hosts("beatsad.github.io");
    // Quiet TV's own updater downloads through these GitHub proxies first and
    // keeps github.com as the last source. The list and order are published in
    // the project's docs/UPDATES.md; the official SHA-256 still decides whether
    // a mirror is accepted.
    private static final String[] QUIET_MIRRORS =
            {"ghfast.top", "gh-proxy.com", "v4.gh-proxy.org"};
    private static final String QUIET_MIRROR_PATH =
            "/https://github.com/BeatsAD/quiet-tv/releases/download/";

    private static final String STUDY_GITEE_RELEASES =
            "https://gitee.com/api/v5/repos/chadyi/StudyOnTV/releases?per_page=30";
    private static final String STUDY_GITEE_PATH =
            "/chadyi/StudyOnTV/releases/download/";
    // StudyOnTV's own updater uses Pgyer's public in-app update endpoint
    // (app key + current version, no account API key). The SDK key below is
    // the public constant embedded by the official app, not a user secret.
    // Only the version-less query "0.0.0" is sent, so no release is pinned.
    private static final Set<String> PGYER_API_HOSTS = hosts("www.pgyer.com");
    private static final Set<String> PGYER_HOSTS =
            hosts("www.pgyer.com", "pgyer-apps-cfw.appmeme.com", "oss.pgyer.com");
    private static final String PGYER_CHECK_URL =
            "https://www.pgyer.com/apiv1/update/check";
    private static final String PGYER_CHECK_PATH = "/apiv1/update/check";
    private static final String PGYER_INSTALL_PATH = "/app/installUpdate/";
    private static final String PGYER_TV_AGKEY = "5c78c116f305ded2cd7f34edb69a70ec";
    private static final String PGYER_PUBLIC_SDK_KEY = "305092bc73c180b55c26012a94809131";
    private static final Pattern VERSION = Pattern.compile("\\d+(?:\\.\\d+){1,3}");
    private static final Pattern HASH_LINE =
            Pattern.compile("(?m)^\\s*([0-9a-fA-F]{64})\\s+(\\S+)\\s*$");
    private static final int MAX_INDEX_BYTES = 2 * 1024 * 1024;

    private ApkResolver() {
    }

    static Resolved resolve(Catalog.App app, Catalog.Variant variant) throws IOException {
        if (Catalog.PLAN_YOUR_TV.equals(app.id)) {
            return single(variant.packageName, PLAN_CERT, planYourTv(variant));
        }
        if (Catalog.AERIAL.equals(app.id)) {
            return aerial(variant);
        }
        if (Catalog.HALO_TV.equals(app.id)) {
            return haloTv(variant);
        }
        if (Catalog.STUDY_ON_TV.equals(app.id)) {
            return studyOnTv(variant);
        }
        if (Catalog.QUIET_TV.equals(app.id)) {
            return quietTv(variant);
        }
        if (Catalog.CLEAN_YOUR_TV.equals(app.id)) {
            throw new IOException("CleanYourTV 需要电脑按官方脚本构建和恢复，"
                    + "本应用不下载来源不明的 APK。请使用详情页的电脑安装指引。");
        }
        throw new IOException("这个应用暂不支持手机直接安装。");
    }

    private static Resolved single(String packageName, String cert, Source source) {
        List<Source> sources = new ArrayList<Source>();
        sources.add(source);
        return new Resolved(packageName, cert, sources, "");
    }

    private static Source planYourTv(Catalog.Variant variant) throws IOException {
        String base = "https://planyourtv.me/download/";
        String body = HttpDownloader.fetchText(base + "version.txt", PLAN_HOSTS, 256);
        String version = normalizeVersion(body);
        if (!VERSION.matcher(version).matches()) {
            throw new IOException("官网版本文件格式异常，已停止。请稍后重试。");
        }
        boolean guard = "guard".equals(variant.id);
        String url = base + (guard ? "planyourtv-guard.apk" : "planyourtv-standard.apk");
        return new Source("PlanYourTV 官网", url, "", version,
                "/download/", PLAN_HOSTS, true);
    }

    /**
     * Aerial publishes a live version.txt and stable APK names on its official
     * Chinese site. The GitHub "latest download" URL is kept as a dynamic
     * fallback, so neither path contains a compiled-in version.
     */
    private static Resolved aerial(Catalog.Variant variant) throws IOException {
        List<Source> sources = new ArrayList<Source>();
        IOException siteError = null;
        try {
            String body = HttpDownloader.fetchText(
                    "https://aerial.qust.me/download/version.txt", AERIAL_HOSTS, 256);
            String version = normalizeVersion(body);
            if (!VERSION.matcher(version).matches()) {
                throw new IOException("Aerial 官网版本文件格式异常。");
            }
            sources.add(new Source("Aerial 官网",
                    "https://aerial.qust.me/download/aerial-" + variant.id + ".apk",
                    "", version, "/download/", AERIAL_HOSTS,
                    !"lite".equals(variant.id)));
            sources.add(new Source("GitHub 官方备用",
                    "https://github.com/pengchujin/aerial-tv/releases/latest/download/aerial-"
                            + variant.id + ".apk",
                    "", "", "/pengchujin/aerial-tv/releases/latest/download/",
                    GITHUB_ASSET_HOSTS, false));
        } catch (IOException error) {
            siteError = error;
        }

        if (sources.isEmpty()) {
            try {
                sources.add(githubRelease(variant,
                        "https://api.github.com/repos/pengchujin/aerial-tv/releases/latest",
                        "/pengchujin/aerial-tv/releases/download/",
                        "aerial", !"lite".equals(variant.id)));
            } catch (IOException githubError) {
                throw new IOException("Aerial 官网与 GitHub 官方源都暂时不可用。"
                        + shortError(siteError, githubError));
            }
        }
        return new Resolved(variant.packageName, AERIAL_CERT, sources, "");
    }

    /**
     * HaloTV's official site lists versioned APK links. The page is parsed at
     * install time, so the newest published version is used without a pin.
     */
    private static Resolved haloTv(Catalog.Variant variant) throws IOException {
        List<Source> sources = new ArrayList<Source>();
        IOException siteError = null;
        try {
            String page = HttpDownloader.fetchText(
                    "https://halo-tv.pages.dev/", HALO_HOSTS, MAX_INDEX_BYTES);
            Pattern official = Pattern.compile(
                    "(?:https://halo-tv\\.pages\\.dev)?(/updates/HaloTV-(\\d+(?:\\.\\d+){1,3})-"
                            + Pattern.quote(variant.id) + "\\.apk)");
            Matcher match = official.matcher(page);
            String version = "";
            String officialPath = "";
            while (match.find()) {
                String candidate = match.group(2);
                if (version.isEmpty() || compareVersions(candidate, version) > 0) {
                    version = candidate;
                    officialPath = match.group(1);
                }
            }
            if (version.isEmpty()) {
                throw new IOException("HaloTV 官网没有找到适配这个版本的安装包。");
            }
            sources.add(new Source("HaloTV 官网",
                    "https://halo-tv.pages.dev" + officialPath, "", version,
                    "/updates/", HALO_HOSTS, true));

            Pattern github = Pattern.compile(
                    "(https://github\\.com/Null9797/halo-tv/releases/download/[^\\s\"'<>]+/HaloTV-"
                            + Pattern.quote(version) + "-" + Pattern.quote(variant.id) + "\\.apk)");
            Matcher githubMatch = github.matcher(page);
            if (githubMatch.find()) {
                sources.add(new Source("GitHub 官方备用", githubMatch.group(1), "",
                        version, "/Null9797/halo-tv/releases/download/",
                        GITHUB_ASSET_HOSTS, true));
            }
        } catch (IOException error) {
            siteError = error;
        }

        if (sources.isEmpty()) {
            try {
                sources.add(githubRelease(variant,
                        "https://api.github.com/repos/Null9797/halo-tv/releases/latest",
                        "/Null9797/halo-tv/releases/download/",
                        "halotv", true));
            } catch (IOException githubError) {
                throw new IOException("HaloTV 官网与 GitHub 官方源都暂时不可用。"
                        + shortError(siteError, githubError));
            }
        }
        return new Resolved(variant.packageName, HALO_CERT, sources, "");
    }

    /**
     * StudyOnTV publishes on GitHub, an official Gitee mirror and the
     * author's official Pgyer update channel (the one the app's own updater
     * uses). All three indexes are read on every install. Only the newest
     * version is offered; among equal versions Gitee is preferred, then the
     * Pgyer updater, then GitHub. A mirror can never cause a downgrade.
     */
    private static Resolved studyOnTv(Catalog.Variant variant) throws IOException {
        boolean pgyerSupported = "standard".equals(variant.id);
        Source pgyer = null;
        Source gitee = null;
        Source github = null;
        IOException pgyerError = null;
        IOException giteeError = null;
        IOException githubError = null;

        if (pgyerSupported) {
            try {
                pgyer = studyPgyer();
            } catch (IOException error) {
                pgyerError = error;
            }
        }
        try {
            gitee = studyGitee(variant);
        } catch (IOException error) {
            giteeError = error;
        }
        try {
            github = githubRelease(variant,
                    "https://api.github.com/repos/chadyi/StudyOnTV/releases/latest",
                    "/chadyi/StudyOnTV/releases/download/",
                    "studyontv", true);
        } catch (IOException error) {
            githubError = error;
        }

        List<Source> available = new ArrayList<Source>();
        if (pgyer != null) {
            available.add(pgyer);
        }
        if (gitee != null) {
            available.add(gitee);
        }
        if (github != null) {
            available.add(github);
        }
        if (available.isEmpty()) {
            throw new IOException("没有可用的 StudyOnTV 官方下载源。"
                    + shortError(pgyerError, giteeError, githubError));
        }

        String latest = available.get(0).version;
        for (int i = 1; i < available.size(); i++) {
            if (compareVersions(available.get(i).version, latest) > 0) {
                latest = available.get(i).version;
            }
        }

        List<Source> sources = new ArrayList<Source>();
        if (gitee != null && compareVersions(gitee.version, latest) == 0) {
            sources.add(gitee);
        }
        if (pgyer != null && compareVersions(pgyer.version, latest) == 0) {
            sources.add(pgyer);
        }
        if (github != null && compareVersions(github.version, latest) == 0) {
            sources.add(github);
        }
        if (sources.isEmpty()) {
            throw new IOException("没有找到 StudyOnTV 最新版本的官方下载源。");
        }

        StringBuilder note = new StringBuilder();
        if (sources.get(0) == gitee) {
            note.append("Gitee 官方镜像已是最新版本 ").append(latest)
                    .append("，优先从国内镜像下载。");
        } else if (sources.get(0) == pgyer) {
            note.append("StudyOnTV 最新 ").append(latest)
                    .append("，使用官方蒲公英更新通道下载。");
        } else {
            note.append("使用 GitHub 官方 Release ").append(latest).append("。");
        }
        if (gitee != null && compareVersions(gitee.version, latest) < 0) {
            note.append(" Gitee 官方镜像为 ").append(gitee.version)
                    .append("，落后于最新版，不会自动降级。");
        }
        if (pgyer != null && compareVersions(pgyer.version, latest) < 0) {
            note.append(" 蒲公英官方更新通道为 ").append(pgyer.version)
                    .append("，落后于最新版。");
        }
        if (pgyerSupported) {
            if (pgyer == null) {
                if (githubError != null && sources.get(0) == gitee) {
                    note.append(" 蒲公英与 GitHub 官方索引暂时不可达，使用 Gitee 官方镜像。");
                } else {
                    note.append(" 蒲公英官方更新通道暂时不可达。");
                }
            } else if (githubError != null && sources.get(0) == pgyer) {
                note.append(" GitHub 官方索引暂时不可达。");
            }
        } else if (githubError != null && sources.get(0) == gitee) {
            note.append(" GitHub 官方索引暂时不可达，使用 Gitee 官方镜像。");
        }
        return new Resolved(variant.packageName, STUDY_CERT, sources, note.toString().trim());
    }

    /**
     * Reads the latest version and download URL from the public update
     * endpoint used by StudyOnTV's own Android TV updater. It only offers the
     * standard TV flavor, so the local-only flavor intentionally skips it.
     */
    private static Source studyPgyer() throws IOException {
        String body = HttpDownloader.postFormText(
                PGYER_CHECK_URL, PGYER_API_HOSTS, PGYER_CHECK_PATH, MAX_INDEX_BYTES,
                new String[][]{
                        {"_api_key", PGYER_PUBLIC_SDK_KEY},
                        {"agKey", PGYER_TV_AGKEY},
                        {"version", "0.0.0"},
                        {"build", "0"},
                        {"sdkapi", String.valueOf(Build.VERSION.SDK_INT)}
                });
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (Exception error) {
            throw new IOException("蒲公英官方更新索引格式异常。");
        }
        if (root.optInt("code", -1) != 0) {
            String message = root.optString("message").trim();
            throw new IOException("蒲公英官方更新索引暂时不可用"
                    + (message.isEmpty() ? "。" : "：" + message + "。"));
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            throw new IOException("蒲公英官方更新索引缺少版本信息。");
        }
        if (data.optBoolean("updateDeny", false)) {
            throw new IOException("蒲公英官方更新通道暂时拒绝更新。");
        }
        String version = normalizeVersion(data.optString("version"));
        if (version.isEmpty()) {
            throw new IOException("蒲公英官方更新索引没有返回有效版本。");
        }
        String url = data.optString("downloadURL").trim();
        if (url.startsWith("http://")) {
            url = "https://" + url.substring("http://".length());
        }
        requirePgyerUrl(url);
        String sha = data.optString("sha256").trim();
        if (!sha.isEmpty()) {
            sha = HttpDownloader.normalizeSha256(sha);
        }
        return new Source("蒲公英官方更新", url, sha, version,
                PGYER_INSTALL_PATH, PGYER_HOSTS, true);
    }

    private static Source studyGitee(Catalog.Variant variant) throws IOException {
        String body = HttpDownloader.fetchText(
                STUDY_GITEE_RELEASES, GITEE_HOSTS, MAX_INDEX_BYTES);
        JSONArray releases;
        try {
            releases = new JSONArray(body);
        } catch (Exception error) {
            throw new IOException("Gitee 官方镜像索引格式异常。");
        }

        JSONObject bestRelease = null;
        JSONObject bestAsset = null;
        String bestVersion = "";
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("prerelease", false)) {
                continue;
            }
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                continue;
            }
            JSONObject asset = findAsset(assets, "studyontv", variant.id);
            if (asset == null) {
                continue;
            }
            String version = normalizeVersion(release.optString("tag_name"));
            if (!VERSION.matcher(version).matches()) {
                continue;
            }
            if (bestRelease == null || compareVersions(version, bestVersion) > 0) {
                bestRelease = release;
                bestAsset = asset;
                bestVersion = version;
            }
        }
        if (bestRelease == null || bestAsset == null) {
            return null;
        }

        String name = bestAsset.optString("name");
        String url = bestAsset.optString("browser_download_url");
        requireGiteeAsset(url);
        String sha = hashFromBody(bestRelease.optString("body"), name);
        return new Source("Gitee 官方镜像", url, sha, bestVersion,
                STUDY_GITEE_PATH, GITEE_HOSTS, true);
    }

    /**
     * Quiet TV's official updater prefers its published GitHub proxy mirrors
     * and only then the native GitHub release URL. Mirror entries use the same
     * official SHA-256, so a mirror cannot change the artifact; a hash mismatch
     * stops the install instead of falling through to another source.
     */
    private static Resolved quietTv(Catalog.Variant variant) throws IOException {
        Source github = quietTvRelease(variant);
        List<Source> sources = new ArrayList<Source>();
        for (String mirror : QUIET_MIRRORS) {
            sources.add(new Source("Quiet TV 官方镜像 · " + mirror,
                    "https://" + mirror + "/" + github.url,
                    github.sha256, github.version, QUIET_MIRROR_PATH,
                    hosts(mirror), true));
        }
        sources.add(github);
        String note = "Quiet TV 官方更新按 ghfast → gh-proxy → v4.gh-proxy → GitHub "
                + "顺序尝试镜像，安装包使用官方索引 SHA-256 校验。";
        return new Resolved(variant.packageName, QUIET_CERT, sources, note);
    }

    private static Source quietTvRelease(Catalog.Variant variant) throws IOException {
        String body = HttpDownloader.fetchText(
                "https://beatsad.github.io/quiet-tv/latest.json", QUIET_HOSTS, MAX_INDEX_BYTES);
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (Exception error) {
            throw new IOException("官方索引格式异常，已停止。");
        }
        JSONArray releases = root.optJSONArray("releases");
        if (releases == null) {
            throw new IOException("官方索引缺少版本列表，已停止。");
        }
        boolean legacy = "legacy14".equals(variant.id);
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null) {
                continue;
            }
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                continue;
            }
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.optJSONObject(j);
                if (asset == null || !variant.packageName.equals(asset.optString("packageName"))) {
                    continue;
                }
                String compatibility = asset.optString("compatibility").toLowerCase(Locale.US);
                boolean match = legacy
                        ? compatibility.startsWith("legacy")
                        : "standard".equals(compatibility);
                if (!match) {
                    continue;
                }
                String url = asset.optString("url");
                String sha = HttpDownloader.normalizeSha256(asset.optString("sha256"));
                JSONArray signers = asset.optJSONArray("signerSha256");
                String signer = signers == null || signers.length() == 0
                        ? QUIET_CERT : signers.optString(0).toLowerCase(Locale.US);
                if (!QUIET_CERT.equals(signer)) {
                    throw new IOException("官方索引的应用签名与已核对的 Quiet TV 证书不一致，已停止。");
                }
                long bytes = asset.optLong("bytes", 0);
                if (bytes > variant.maxBytes) {
                    throw new IOException("官方索引的安装包大小异常，已停止。");
                }
                requireGitHubAsset(url, "/BeatsAD/quiet-tv/releases/download/");
                String version = release.optString("versionName");
                if (version.isEmpty()) {
                    version = release.optString("tag");
                }
                return new Source("GitHub 官方 Release", url, sha, version,
                        "/BeatsAD/quiet-tv/releases/download/",
                        GITHUB_ASSET_HOSTS, true);
            }
        }
        throw new IOException("官方索引中暂时没有适配这个版本的安装包。");
    }

    private static Source githubRelease(Catalog.Variant variant, String apiUrl,
                                        String pathPrefix, String sourceId,
                                        boolean requireVersion)
            throws IOException {
        String body = HttpDownloader.fetchText(apiUrl, GITHUB_API_HOSTS, MAX_INDEX_BYTES);
        JSONObject release;
        try {
            release = new JSONObject(body);
        } catch (Exception error) {
            throw new IOException("GitHub Release 索引格式异常，已停止。");
        }
        if (release.optBoolean("draft", false)) {
            throw new IOException("官方 Release 还是草稿，已停止。");
        }
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            throw new IOException("官方 Release 没有安装包。");
        }
        JSONObject selected = findAsset(assets, sourceId, variant.id);
        if (selected == null) {
            throw new IOException("官方 Release 中没有找到 " + variant.label + " 安装包。");
        }
        String url = selected.optString("browser_download_url");
        requireGitHubAsset(url, pathPrefix);
        String digest = selected.optString("digest").trim();
        String sha = digest.isEmpty() ? "" : HttpDownloader.normalizeSha256(digest);
        long bytes = selected.optLong("size", 0);
        if (bytes > variant.maxBytes) {
            throw new IOException("官方安装包大小异常，已停止。");
        }
        String version = normalizeVersion(release.optString("tag_name"));
        if (version.isEmpty()) {
            version = "latest";
        }
        return new Source("GitHub 官方 Release", url, sha, version, pathPrefix,
                GITHUB_ASSET_HOSTS, requireVersion);
    }

    private static JSONObject findAsset(JSONArray assets, String sourceId, String variantId) {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            if (matchesAsset(sourceId, variantId, asset.optString("name"))) {
                return asset;
            }
        }
        return null;
    }

    private static boolean matchesAsset(String sourceId, String variantId, String name) {
        String value = name == null ? "" : name;
        if ("aerial".equals(sourceId)) {
            return value.equals("aerial-" + variantId + ".apk");
        }
        if ("halotv".equals(sourceId)) {
            return value.matches("HaloTV-.+-" + Pattern.quote(variantId) + "\\.apk");
        }
        if ("studyontv".equals(sourceId)) {
            if (!value.startsWith("StudyOnTV-Android-TV-")
                    || !value.endsWith(".apk")) {
                return false;
            }
            boolean local = value.contains("LocalOnly");
            return "localonly".equals(variantId) ? local : !local;
        }
        return false;
    }

    private static void requireGitHubAsset(String url, String pathPrefix) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IOException("官方 Release 缺少下载地址，已停止。");
        }
        try {
            java.net.URL parsed = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(parsed.getProtocol())
                    || !"github.com".equalsIgnoreCase(parsed.getHost())
                    || !parsed.getPath().startsWith(pathPrefix)) {
                throw new IOException("官方 Release 指向了非预期下载地址，已停止。");
            }
        } catch (java.net.MalformedURLException error) {
            throw new IOException("官方 Release 下载地址格式异常，已停止。");
        }
    }

    private static void requireGiteeAsset(String url) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IOException("Gitee 官方镜像缺少下载地址，已停止。");
        }
        try {
            java.net.URL parsed = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(parsed.getProtocol())
                    || !"gitee.com".equalsIgnoreCase(parsed.getHost())
                    || !parsed.getPath().startsWith(STUDY_GITEE_PATH)) {
                throw new IOException("Gitee 官方镜像指向了非预期下载地址，已停止。");
            }
        } catch (java.net.MalformedURLException error) {
            throw new IOException("Gitee 官方镜像下载地址格式异常，已停止。");
        }
    }

    private static void requirePgyerUrl(String url) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IOException("蒲公英官方更新缺少下载地址，已停止。");
        }
        try {
            java.net.URL parsed = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(parsed.getProtocol())
                    || !"www.pgyer.com".equalsIgnoreCase(parsed.getHost())
                    || !parsed.getPath().startsWith(PGYER_INSTALL_PATH)) {
                throw new IOException("蒲公英官方更新指向了非预期下载地址，已停止。");
            }
        } catch (java.net.MalformedURLException error) {
            throw new IOException("蒲公英官方更新下载地址格式异常，已停止。");
        }
    }

    private static String hashFromBody(String body, String assetName) {
        if (body == null || body.isEmpty() || assetName == null || assetName.isEmpty()) {
            return "";
        }
        Matcher matcher = HASH_LINE.matcher(body);
        while (matcher.find()) {
            if (assetName.equals(matcher.group(2))) {
                return matcher.group(1).toLowerCase(Locale.US);
            }
        }
        return "";
    }

    private static int compareVersions(String left, String right) {
        int[] a = versionParts(left);
        int[] b = versionParts(right);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return av < bv ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        String version = normalizeVersion(value);
        if (version.isEmpty()) {
            return new int[0];
        }
        String[] pieces = version.split("\\.");
        int[] parts = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                parts[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException ignored) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    private static String shortError(IOException... errors) {
        StringBuilder message = new StringBuilder();
        for (IOException error : errors) {
            if (error != null && error.getMessage() != null
                    && !error.getMessage().trim().isEmpty()) {
                message.append(' ').append(error.getMessage().trim());
            }
        }
        return message.toString();
    }

    private static String normalizeVersion(String text) {
        Matcher matcher = VERSION.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : "";
    }

    private static Set<String> hosts(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }
}