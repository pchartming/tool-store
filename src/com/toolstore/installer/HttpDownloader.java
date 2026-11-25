package com.toolstore.installer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small HTTPS-only downloader for official metadata and APKs.
 *
 * Initial requests are restricted to exact hosts and, for GitHub, the exact
 * repository release path. Redirects may only land on the official asset CDN
 * hosts. This complements the package name, signer certificate and SHA-256
 * checks performed after every download.
 */
final class HttpDownloader {

    static final class Artifact {
        final File file;
        final String sha256;
        final long bytes;

        Artifact(File file, String sha256, long bytes) {
            this.file = file;
            this.sha256 = sha256;
            this.bytes = bytes;
        }
    }

    /**
     * A security or integrity rejection. Callers must stop immediately instead
     * of retrying another mirror: the file, hash, host or path was wrong, so a
     * fallback would only turn a clear failure into an ambiguous one.
     */
    static final class IntegrityException extends IOException {
        IntegrityException(String message) {
            super(message);
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 4;
    private static final long RETRY_BASE_DELAY_MS = 900L;
    private static final String USER_AGENT =
            "ToolStore-Installer/0.1 (Android; local LAN installer)";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CONTENT_RANGE = Pattern.compile(
            "bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> GITHUB_CDN = new HashSet<String>(Arrays.asList(
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com"
    ));

    private HttpDownloader() {
    }

    static String fetchText(String urlText, Set<String> exactHosts, int maxBytes)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = open(urlText, exactHosts, "", false, -1, null);
            InputStream input = connection.getInputStream();
            try {
                return readText(input, maxBytes);
            } finally {
                input.close();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * POST a small form to a fixed official JSON endpoint. Redirects are not
     * followed: the endpoint itself must match the allowlisted host and path.
     */
    static String postFormText(String urlText, Set<String> exactHosts,
                               String requiredPathPrefix, int maxBytes, String[][] fields)
            throws IOException {
        URL url = new URL(urlText);
        requireAllowed(url, exactHosts, requiredPathPrefix, true, false);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setDoOutput(true);
        try {
            StringBuilder form = new StringBuilder();
            for (String[] field : fields) {
                if (form.length() > 0) {
                    form.append('&');
                }
                form.append(URLEncoder.encode(field[0], "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(field[1], "UTF-8"));
            }
            byte[] data = form.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(data.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(data);
            } finally {
                output.close();
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("官方服务器返回 HTTP " + code + "，请稍后重试。");
            }
            InputStream input = connection.getInputStream();
            try {
                return readText(input, maxBytes);
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readText(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > maxBytes) {
                throw new IOException("官方索引返回内容过大，已停止。");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    static Artifact download(String urlText, Set<String> exactHosts, String requiredPathPrefix,
                             File target, File part, long maxBytes, String expectedSha256,
                             ProgressSink progress, String flavor) throws IOException {
        return download(urlText, exactHosts, requiredPathPrefix, target, part, maxBytes,
                expectedSha256, progress, flavor, null);
    }

    /**
     * Same checks as {@link #download}, with one extra Accept header on every
     * request. GitHub's release-asset API needs Accept: application/octet-stream
     * before it redirects to the official asset CDN; the CDN is still subject to
     * the exact host, redirect, size and SHA-256 rules.
     */
    static Artifact download(String urlText, Set<String> exactHosts, String requiredPathPrefix,
                             File target, File part, long maxBytes, String expectedSha256,
                             ProgressSink progress, String flavor, String accept)
            throws IOException {
        File parent = part.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建下载缓存目录。");
        }
        delete(part);
        delete(target);

        long total = -1;
        IOException lastError = null;
        for (int attempt = 0; attempt < MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                progress.update("download-retry", flavor, part.length(), total);
                pause(RETRY_BASE_DELAY_MS * attempt);
            }
            HttpURLConnection connection = null;
            FileOutputStream output = null;
            try {
                long received = part.length();
                MessageDigest digest;
                try {
                    digest = MessageDigest.getInstance("SHA-256");
                } catch (Exception error) {
                    throw new IOException("系统缺少 SHA-256 校验能力。");
                }
                // Re-hash the bytes already on disk so a dropped connection can
                // resume without trusting any in-memory state.
                if (received > 0) {
                    InputStream previous = new java.io.FileInputStream(part);
                    try {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = previous.read(buffer)) != -1) {
                            digest.update(buffer, 0, read);
                        }
                    } finally {
                        previous.close();
                    }
                }

                connection = open(urlText, exactHosts, requiredPathPrefix, true,
                        received > 0 ? received : -1, accept);
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_PARTIAL) {
                    ContentRange range = parseContentRange(
                            connection.getHeaderField("Content-Range"));
                    if (range == null || range.start != received
                            || (total > 0 && range.total > 0 && range.total != total)) {
                        delete(part);
                        throw new IOException("官方服务器续传位置异常，将重新下载。");
                    }
                    if (range.total > 0) {
                        total = range.total;
                    }
                } else {
                    if (received > 0) {
                        // The server ignored Range, so start this attempt over.
                        delete(part);
                        received = 0;
                        digest.reset();
                    }
                    total = contentLength(connection);
                }
                if (total > maxBytes) {
                    throw new IntegrityException("官方安装包大小异常（" + total
                            + " 字节），已停止。");
                }
                if (total > 0 && received > total) {
                    delete(part);
                    throw new IOException("续传缓存超过官方文件大小，将重新下载。");
                }

                output = new FileOutputStream(part, received > 0);
                InputStream input = connection.getInputStream();
                byte[] buffer = new byte[64 * 1024];
                int read;
                long lastReport = 0;
                while ((read = input.read(buffer)) != -1) {
                    received += read;
                    if (received > maxBytes) {
                        throw new IntegrityException("下载内容超过大小上限，已停止。");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    long now = System.currentTimeMillis();
                    if (now - lastReport >= 180L) {
                        progress.update("download", flavor, received, total);
                        lastReport = now;
                    }
                }
                output.flush();
                output.close();
                output = null;

                if (total > 0 && received != total) {
                    throw new IOException("下载不完整（" + received + " / " + total
                            + " 字节），将尝试续传。");
                }
                if (received == 0) {
                    throw new IntegrityException("官方服务器返回了空文件，请稍后重试。");
                }
                String actualSha = hex(digest.digest());
                if (expectedSha256 != null && !expectedSha256.trim().isEmpty()
                        && !expectedSha256.trim().equalsIgnoreCase(actualSha)) {
                    throw new IntegrityException("安装包 SHA-256 与官方索引不一致，已停止，"
                            + "不会把它安装到电视。");
                }
                if (!part.renameTo(target)) {
                    throw new IOException("无法保存下载的安装包，请重试。");
                }
                progress.update("download", flavor, received, received);
                return new Artifact(target, actualSha, received);
            } catch (IntegrityException error) {
                delete(part);
                delete(target);
                throw error;
            } catch (IOException error) {
                lastError = error;
            } catch (Exception error) {
                lastError = new IOException("下载失败：" + error.getMessage());
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        delete(part);
        delete(target);
        String reason = lastError == null || lastError.getMessage() == null
                ? "" : lastError.getMessage().trim();
        while (reason.endsWith("。") || reason.endsWith(".")) {
            reason = reason.substring(0, reason.length() - 1);
        }
        if (!reason.isEmpty()) {
            reason = "：" + reason + "。";
        }
        throw new IOException("下载多次中断，已尝试 " + MAX_DOWNLOAD_ATTEMPTS
                + " 次，请稍后重试" + reason);
    }

    private static void pause(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("下载已取消。", error);
        }
    }

    private static ContentRange parseContentRange(String header) {
        if (header == null) {
            return null;
        }
        java.util.regex.Matcher matcher = CONTENT_RANGE.matcher(header.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            long start = Long.parseLong(matcher.group(1));
            long total = "*".equals(matcher.group(3))
                    ? -1L : Long.parseLong(matcher.group(3));
            return new ContentRange(start, total);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class ContentRange {
        final long start;
        final long total;

        ContentRange(long start, long total) {
            this.start = start;
            this.total = total;
        }
    }

    static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("系统缺少 SHA-256 校验能力。");
        }
        InputStream input = null;
        try {
            input = new java.io.FileInputStream(file);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    static String normalizeSha256(String value) throws IOException {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (text.startsWith("sha256:")) {
            text = text.substring("sha256:".length()).trim();
        }
        if (!SHA256.matcher(text).matches()) {
            throw new IOException("官方索引没有提供有效的 SHA-256 校验值，已停止。");
        }
        return text;
    }

    static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xf, 16));
            builder.append(Character.forDigit(value & 0xf, 16));
        }
        return builder.toString();
    }

    static void delete(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static HttpURLConnection open(String urlText, Set<String> exactHosts,
                                          String requiredPathPrefix, boolean artifact,
                                          long rangeStart, String accept)
            throws IOException {
        URL current = new URL(urlText);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            requireAllowed(current, exactHosts, requiredPathPrefix, redirect == 0, artifact);
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            if (accept != null && !accept.isEmpty()) {
                connection.setRequestProperty("Accept", accept);
            }
            if (rangeStart >= 0) {
                connection.setRequestProperty("Range", "bytes=" + rangeStart + "-");
            }

            int code;
            try {
                code = connection.getResponseCode();
            } catch (IOException error) {
                connection.disconnect();
                throw new IOException("无法连接官方服务器，请检查手机网络后重试。", error);
            }
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("官方重定向缺少目标地址，已停止。");
                }
                current = new URL(current, location.trim());
                continue;
            }
            boolean ok = code == HttpURLConnection.HTTP_OK
                    || (rangeStart >= 0 && code == HttpURLConnection.HTTP_PARTIAL);
            if (!ok) {
                connection.disconnect();
                throw new IOException("官方服务器返回 HTTP " + code + "，请稍后重试。");
            }
            return connection;
        }
        throw new IOException("官方重定向次数过多，已停止。");
    }

    private static void requireAllowed(URL url, Set<String> exactHosts,
                                       String requiredPathPrefix, boolean firstRequest,
                                       boolean artifact) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IntegrityException("拒绝访问非 HTTPS 地址，已停止。");
        }
        if (url.getPort() != -1 && url.getPort() != 443) {
            throw new IntegrityException("拒绝访问非标准 HTTPS 端口，已停止。");
        }
        if (url.getUserInfo() != null) {
            throw new IntegrityException("拒绝带用户信息的下载地址，已停止。");
        }
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.US);
        boolean allowed = exactHosts != null && exactHosts.contains(host);
        if (!firstRequest && GITHUB_CDN.contains(host)) {
            allowed = true;
        }
        if (!allowed) {
            throw new IntegrityException("拒绝访问非官方下载域名，已停止。");
        }
        if (firstRequest && requiredPathPrefix != null && !requiredPathPrefix.isEmpty()
                && !url.getPath().startsWith(requiredPathPrefix)) {
            throw new IntegrityException("拒绝访问官方仓库之外的下载路径，已停止。");
        }
    }

    private static long contentLength(HttpURLConnection connection) {
        String header = connection.getHeaderField("Content-Length");
        if (header == null) {
            return -1L;
        }
        try {
            long value = Long.parseLong(header.trim());
            return value >= 0 ? value : -1L;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}