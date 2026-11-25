package com.toolstore.installer;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Finds ADB-capable devices on the current LAN without connecting to them.
 *
 * Two independent discovery methods are used:
 *  - mDNS ADB advertisements (wireless debugging and some network-adb devices);
 *  - a short TCP probe of port 5555 on the local IPv4 subnet.
 *
 * A bare TCP connect is sent so no ADB handshake or authorization prompt is
 * triggered. The scan only uses the subnets of active, non-cellular interfaces.
 */
final class LanScanner {

    private static final int ADB_PORT = 5555;
    private static final int CONNECT_TIMEOUT_MS = 350;
    private static final int PORT_SCAN_WORKERS = 32;
    private static final int MAX_HOSTS = 512;
    private static final int TOTAL_SCAN_MS = 3800;
    private static final String[] MDNS_TYPES = {
            "_adb-tls-connect._tcp.",
            "_adb._tcp."
    };

    private final AdbClient.Sink sink;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<String, Candidate> candidates = new HashMap<String, Candidate>();
    private final Set<Long> targets = new TreeSet<Long>();
    private final Set<Long> localAddresses = new HashSet<Long>();
    private final Set<String> resolving = new HashSet<String>();
    private final List<NsdManager.DiscoveryListener> listeners =
            new CopyOnWriteArrayList<NsdManager.DiscoveryListener>();
    private final NsdManager nsd;
    private volatile boolean stopped;

    LanScanner(Context context, AdbClient.Sink sink) {
        this.sink = sink;
        this.nsd = (NsdManager) context.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE);
    }

    JSONObject scan() throws Exception {
        final long started = SystemClock.elapsedRealtime();
        buildTargets();
        log("搜索当前局域网中的 ADB 设备（只探测，不自动连接）…");

        boolean mdnsStarted = false;
        try {
            mdnsStarted = startMdns();
            if (mdnsStarted) {
                log("同时监听 ADB 无线调试广播…");
            }
            int scanned = scanPorts(started + TOTAL_SCAN_MS);
            if (mdnsStarted) {
                sleepUntil(started + TOTAL_SCAN_MS);
            }
            return buildResult(scanned, mdnsStarted);
        } finally {
            stopMdns();
        }
    }

    private void buildTargets() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                try {
                    if (!network.isUp() || network.isLoopback() || network.isPointToPoint()) {
                        continue;
                    }
                    String name = network.getName() == null
                            ? "" : network.getName().toLowerCase(Locale.US);
                    if (ignoredInterface(name)) {
                        continue;
                    }
                    for (InterfaceAddress interfaceAddress : network.getInterfaceAddresses()) {
                        InetAddress address = interfaceAddress.getAddress();
                        if (!(address instanceof Inet4Address)
                                || address.isLoopbackAddress()
                                || address.isLinkLocalAddress()) {
                            continue;
                        }
                        long ip = toLong(address);
                        localAddresses.add(ip);
                        int prefix = interfaceAddress.getNetworkPrefixLength();
                        if (prefix <= 0) {
                            prefix = 24;
                        }
                        addLocalRange(ip, prefix);
                    }
                } catch (Throwable ignored) {
                    // Interfaces can disappear while being enumerated.
                }
            }
        } catch (Throwable error) {
            log("! 枚举局域网地址失败：" + safeMessage(error));
        }

        for (Long local : localAddresses) {
            targets.remove(local);
        }
    }

    private void addLocalRange(long ip, int prefix) {
        if (prefix >= 31) {
            return;
        }
        long start;
        long end;
        if (prefix >= 24) {
            long mask = (0xffffffffL << (32 - prefix)) & 0xffffffffL;
            long network = ip & mask;
            long broadcast = network | (~mask & 0xffffffffL);
            start = network + 1;
            end = broadcast - 1;
        } else {
            // Home routers are normally /24. For a larger subnet, keep the
            // scan bounded to the /24 containing this interface address.
            long network = ip & 0xffffff00L;
            start = network + 1;
            end = network + 254;
        }
        for (long host = start; host <= end && targets.size() < MAX_HOSTS; host++) {
            targets.add(host);
        }
    }

    private int scanPorts(long deadline) {
        final List<Long> hosts = new ArrayList<Long>(targets);
        if (hosts.isEmpty()) {
            log("没有找到可扫描的局域网地址。请确认手机已连接 Wi-Fi。");
            return 0;
        }
        log("正在扫描 " + hosts.size() + " 个局域网地址的 " + ADB_PORT + " 端口…");
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(PORT_SCAN_WORKERS, hosts.size()), new DaemonThreadFactory());
        for (final Long host : hosts) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    if (stopped) {
                        return;
                    }
                    if (probe(host.longValue(), ADB_PORT)) {
                        addPortCandidate(longToIp(host.longValue()), ADB_PORT);
                    }
                }
            });
        }
        pool.shutdown();

        long waitMs = Math.max(500L, Math.min(deadline - SystemClock.elapsedRealtime(), 5000L));
        try {
            pool.awaitTermination(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            stopped = true;
        }
        pool.shutdownNow();
        return hosts.size();
    }

    private boolean startMdns() {
        if (nsd == null) {
            log("! 系统不支持 mDNS 搜索，只扫描 5555 端口。");
            return false;
        }
        for (final String type : MDNS_TYPES) {
            final NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
                @Override
                public void onDiscoveryStarted(String serviceType) {
                }

                @Override
                public void onServiceFound(final NsdServiceInfo serviceInfo) {
                    if (stopped) {
                        return;
                    }
                    final String serviceName = serviceInfo.getServiceName() == null
                            ? "" : serviceInfo.getServiceName();
                    final String key = type + "|" + serviceName;
                    synchronized (lock) {
                        if (!resolving.add(key)) {
                            return;
                        }
                    }
                    try {
                        nsd.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                            @Override
                            public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                                synchronized (lock) {
                                    resolving.remove(key);
                                }
                            }

                            @Override
                            public void onServiceResolved(NsdServiceInfo info) {
                                synchronized (lock) {
                                    resolving.remove(key);
                                }
                                if (stopped) {
                                    return;
                                }
                                InetAddress address = info.getHost();
                                int port = info.getPort();
                                if (address == null
                                        || !(address instanceof Inet4Address)
                                        || address.isLoopbackAddress()
                                        || address.isLinkLocalAddress()
                                        || port < 1
                                        || port > 65535) {
                                    return;
                                }
                                addMdnsCandidate(address.getHostAddress(), port,
                                        info.getServiceName(),
                                        type.contains("tls-connect"));
                            }
                        });
                    } catch (Throwable error) {
                        synchronized (lock) {
                            resolving.remove(key);
                        }
                        log("! 解析 ADB 广播失败：" + safeMessage(error));
                    }
                }

                @Override
                public void onServiceLost(NsdServiceInfo serviceInfo) {
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    log("! mDNS 搜索启动失败（" + errorCode + "）");
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                }
            };
            listeners.add(listener);
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (stopped) {
                        return;
                    }
                    try {
                        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener);
                    } catch (Throwable error) {
                        log("! mDNS 搜索启动失败：" + safeMessage(error));
                    }
                }
            });
        }
        return true;
    }

    private void stopMdns() {
        stopped = true;
        if (listeners.isEmpty()) {
            return;
        }
        final CountDownLatch done = new CountDownLatch(1);
        main.post(new Runnable() {
            @Override
            public void run() {
                for (NsdManager.DiscoveryListener listener : listeners) {
                    try {
                        if (nsd != null) {
                            nsd.stopServiceDiscovery(listener);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                listeners.clear();
                done.countDown();
            }
        });
        try {
            done.await(800L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void addPortCandidate(String ip, int port) {
        synchronized (lock) {
            String key = ip + ":" + port;
            Candidate candidate = candidates.get(key);
            if (candidate == null) {
                candidate = new Candidate(ip, port);
                candidates.put(key, candidate);
            }
            candidate.portOpen = true;
        }
    }

    private void addMdnsCandidate(String ip, int port, String name, boolean wireless) {
        synchronized (lock) {
            String key = ip + ":" + port;
            Candidate candidate = candidates.get(key);
            if (candidate == null) {
                candidate = new Candidate(ip, port);
                candidates.put(key, candidate);
            }
            candidate.mdns = true;
            candidate.wireless = candidate.wireless || wireless;
            if (name != null && !name.isEmpty()) {
                candidate.name = cap(name, 100);
            }
        }
    }

    private JSONObject buildResult(int scanned, boolean mdnsStarted) throws Exception {
        List<Candidate> found = new ArrayList<Candidate>();
        synchronized (lock) {
            found.addAll(candidates.values());
        }
        Collections.sort(found, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                int rank = left.rank() - right.rank();
                if (rank != 0) {
                    return rank;
                }
                long leftIp = ipToLong(left.ip);
                long rightIp = ipToLong(right.ip);
                if (leftIp != rightIp) {
                    return leftIp < rightIp ? -1 : 1;
                }
                return left.port - right.port;
            }
        });

        JSONArray array = new JSONArray();
        for (Candidate candidate : found) {
            array.put(candidate.toJson());
        }
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("candidates", array);
        result.put("scanned", scanned);
        result.put("mdns", mdnsStarted);

        if (found.isEmpty()) {
            log("没有自动找到电视。请确认电视已开启网络 ADB 或无线调试，然后手动填写 IP。");
        } else {
            log("搜索完成，找到 " + found.size() + " 个候选设备：");
            for (Candidate candidate : found) {
                log("✓ " + candidate.ip + ":" + candidate.port
                        + "（" + sourceLabel(candidate) + "）");
            }
        }
        return result;
    }

    private static boolean probe(long ip, int port) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(longToAddress(ip), port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException error) {
            return false;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean ignoredInterface(String name) {
        return name.startsWith("rmnet")
                || name.startsWith("ccmni")
                || name.startsWith("pdp")
                || name.startsWith("tun")
                || name.startsWith("ppp")
                || name.startsWith("dummy")
                || name.startsWith("clat")
                || name.startsWith("sit")
                || name.startsWith("ip6tnl");
    }

    private static long toLong(InetAddress address) {
        byte[] bytes = address.getAddress();
        long value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (bytes[i] & 0xffL);
        }
        return value;
    }

    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        long value = 0;
        try {
            for (String part : parts) {
                value = (value << 8) | (Integer.parseInt(part) & 0xffL);
            }
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return value;
    }

    private static InetAddress longToAddress(long ip) {
        byte[] bytes = new byte[]{
                (byte) ((ip >> 24) & 0xff),
                (byte) ((ip >> 16) & 0xff),
                (byte) ((ip >> 8) & 0xff),
                (byte) (ip & 0xff)
        };
        try {
            return InetAddress.getByAddress(bytes);
        } catch (IOException impossible) {
            return null;
        }
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xff) + "."
                + ((ip >> 16) & 0xff) + "."
                + ((ip >> 8) & 0xff) + "."
                + (ip & 0xff);
    }

    private static String sourceLabel(Candidate candidate) {
        if (candidate.portOpen && candidate.mdns) {
            return candidate.wireless ? "无线调试 + 5555" : "ADB 广播 + 5555";
        }
        if (candidate.portOpen) {
            return "5555 端口开放";
        }
        return candidate.wireless ? "无线调试广播" : "ADB 广播";
    }

    private static String cap(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        return message.trim();
    }

    private static void sleepUntil(long deadline) {
        long remaining = deadline - SystemClock.elapsedRealtime();
        if (remaining <= 0) {
            return;
        }
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(String line) {
        if (sink != null) {
            sink.line(line);
        }
    }

    private static final class Candidate {
        final String ip;
        final int port;
        boolean portOpen;
        boolean mdns;
        boolean wireless;
        String name = "";

        Candidate(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        int rank() {
            if (portOpen) {
                return 0;
            }
            if (mdns && !wireless) {
                return 1;
            }
            return 2;
        }

        JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("ip", ip);
            json.put("port", port);
            json.put("name", name);
            json.put("source", portOpen && mdns
                    ? (wireless ? "wireless+port" : "mdns+port")
                    : (portOpen ? "port" : (wireless ? "wireless" : "mdns")));
            json.put("wireless", wireless);
            return json;
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "lan-scan");
            thread.setDaemon(true);
            return thread;
        }
    }
}
