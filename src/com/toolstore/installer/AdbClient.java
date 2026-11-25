package com.toolstore.installer;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the bundled arm64 adb binary from the app's native library directory.
 *
 * The binary and its shared libraries are packaged as native libraries so that
 * Android extracts them to an executable, app-private directory. Network adb
 * itself runs on the TV; this process is only the adb client/server on the phone.
 */
final class AdbClient {

    interface Sink {
        void line(String text);
    }

    static final class Result {
        final int exitCode;
        final boolean timedOut;
        final String output;

        Result(int exitCode, boolean timedOut, String output) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.output = output == null ? "" : output;
        }

        boolean ok() {
            return !timedOut && exitCode == 0;
        }

        String text() {
            return output.trim();
        }
    }

    private final File adbFile;
    private final File adbHome;

    AdbClient(Context context) throws IOException {
        String nativePath = context.getApplicationInfo().nativeLibraryDir;
        if (nativePath == null) {
            throw new IOException("系统没有提供原生库目录，无法启动内置 adb。");
        }
        File nativeDir = new File(nativePath);
        adbFile = new File(nativeDir, "libadb.so");
        if (!adbFile.isFile()) {
            throw new IOException("这台手机的 ABI 不受支持，缺少内置 adb 运行时。");
        }
        if (!adbFile.canExecute()) {
            adbFile.setExecutable(true);
        }
        if (!adbFile.canExecute()) {
            throw new IOException("内置 adb 运行时不可执行，安装无法继续。");
        }
        adbHome = new File(context.getFilesDir(), "adbhome");
        File keyDir = new File(adbHome, ".android");
        if (!keyDir.isDirectory() && !keyDir.mkdirs()) {
            throw new IOException("无法创建 adb 密钥目录。");
        }
    }

    Result run(String[] args, long timeoutSeconds, Sink sink) {
        return runInternal(args, display(args), timeoutSeconds, sink);
    }

    /** Used for commands whose real arguments must not appear in the console. */
    Result runSanitized(String[] args, String display, long timeoutSeconds, Sink sink) {
        return runInternal(args, display, timeoutSeconds, sink);
    }

    private Result runInternal(String[] args, String display, long timeoutSeconds, Sink sink) {
        List<String> command = new ArrayList<String>();
        command.add(adbFile.getAbsolutePath());
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.clear();
        env.put("LD_LIBRARY_PATH", adbFile.getParent());
        env.put("HOME", adbHome.getAbsolutePath());
        env.put("TMPDIR", adbHome.getAbsolutePath());

        if (sink != null) {
            sink.line("$ " + display);
        }

        final Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            String message = "无法启动内置 adb：" + safeMessage(e);
            if (sink != null) {
                sink.line("! " + message);
            }
            return new Result(-1, false, message);
        }

        final StringBuilder output = new StringBuilder();
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                BufferedReader in = null;
                try {
                    in = new BufferedReader(new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (output.length() < 4 * 1024 * 1024) {
                            output.append(line).append('\n');
                        }
                        if (sink != null) {
                            sink.line(cleanLine(line));
                        }
                    }
                } catch (IOException ignored) {
                    // The process can be stopped on timeout; the exit result is authoritative.
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }, "adb-output");
        reader.setDaemon(true);
        reader.start();

        long timeoutMs = Math.max(1L, timeoutSeconds) * 1000L;
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean finished = false;
        boolean timedOut = false;
        int exit = -1;

        while (System.currentTimeMillis() < deadline) {
            try {
                exit = process.exitValue();
                finished = true;
                break;
            } catch (IllegalThreadStateException stillRunning) {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    timedOut = true;
                    break;
                }
            }
        }

        if (!finished) {
            if (!timedOut) {
                timedOut = true;
            }
            process.destroy();
            long grace = System.currentTimeMillis() + 3000L;
            while (System.currentTimeMillis() < grace) {
                try {
                    exit = process.exitValue();
                    break;
                } catch (IllegalThreadStateException stillRunning) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        try {
            reader.join(1500L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        if (sink != null) {
            if (timedOut) {
                sink.line("[命令超时（" + timeoutSeconds + " 秒），已停止等待]");
            } else {
                sink.line("[退出码 " + exit + "]");
            }
        }
        return new Result(exit, timedOut, output.toString());
    }

    private static String display(String[] args) {
        StringBuilder builder = new StringBuilder("adb");
        for (String arg : args) {
            builder.append(' ').append(arg);
        }
        return builder.toString();
    }

    private static String cleanLine(String line) {
        String value = line == null ? "" : line.replace("\r", "").replace('\0', ' ');
        if (value.length() > 1600) {
            value = value.substring(0, 1600) + " …";
        }
        return value;
    }

    static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.trim();
    }
}
