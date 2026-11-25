package com.toolstore.installer;

import android.graphics.Color;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * The connection assistant. It only ever connects to a private-LAN address or
 * a .local host. A scan candidate connects only when the user taps its explicit
 * “连接” button; pairing codes are never logged or persisted.
 */
final class ConnectSheet {

    static final String MODE_WIRELESS = "wireless";
    static final String MODE_CLASSIC = "classic";
    static final String MODE_USB = "usb";

    private final MainActivity activity;
    private final ExecutorService executor;
    private final Handler main;
    private SheetPanel sheet;
    private String mode;
    private String hostValue = "";
    private String pairPortValue = "";
    private String connectPortValue = "";
    private String classicPortValue = "5555";
    private boolean paired;
    private boolean manualExpanded;
    private boolean busy;
    private boolean closed;
    private String currentAction = "";
    private final StringBuilder log = new StringBuilder();

    private LinearLayout statusSlot;
    private LinearLayout candidatesSlot;
    private View manualToggleView;
    private LinearLayout logWrap;
    private TextView logText;
    private ProgressBar progress;
    private TextView pairButton;
    private TextView connectButton;
    private TextView scanButton;
    private JSONArray scanResults;
    private final List<TextView> candidateButtons = new ArrayList<>();
    private EditText wirelessHost;
    private EditText pairPortInput;
    private EditText pairCodeInput;
    private EditText connectPortInput;
    private EditText classicHost;
    private EditText classicPortInput;
    private final TextView[] modeTabs = new TextView[3];

    ConnectSheet(MainActivity activity, String initialMode) {
        this.activity = activity;
        this.executor = activity.executor;
        this.main = activity.main;
        this.mode = MODE_CLASSIC.equals(initialMode) || MODE_USB.equals(initialMode)
                ? initialMode : MODE_WIRELESS;
        JSONObject last = activity.lastTv;
        if (last != null) {
            String ip = last.optString("ip", "");
            if (!TextUtils.isEmpty(ip)) {
                hostValue = ip;
            }
            int port = last.optInt("port", 0);
            if (port > 0 && port <= 65535) {
                if ("classic".equals(last.optString("transport"))) {
                    classicPortValue = String.valueOf(port);
                } else {
                    connectPortValue = String.valueOf(port);
                }
            }
        }
        JSONObject connected = activity.device;
        if (connected != null) {
            String serial = connected.optString("serial", "");
            int colon = serial.lastIndexOf(':');
            String currentHost = colon > 0 ? serial.substring(0, colon) : "";
            if (!TextUtils.isEmpty(currentHost)) {
                hostValue = currentHost;
                String currentPort = colon > 0 ? serial.substring(colon + 1) : "";
                if ("classic".equals(connected.optString("transport"))) {
                    classicPortValue = currentPort;
                } else {
                    connectPortValue = currentPort;
                }
            }
        }
    }

    void show() {
        sheet = SheetPanel.show(activity, "连接电视", "在本机通过 ADB 完成，不需要云端账号");
        sheet.setOnDismiss(() -> closed = true);
        build();
    }

    private void build() {
        if (sheet == null || closed) {
            return;
        }
        sheet.setTitle(MODE_WIRELESS.equals(mode) ? "无线连接"
                : MODE_CLASSIC.equals(mode) ? "网络 ADB 连接" : "USB 调试准备");
        sheet.setSubtitle("在本机通过 ADB 完成，不需要云端账号");
        sheet.clearBody();
        statusSlot = null;
        candidatesSlot = null;
        manualToggleView = null;
        logWrap = null;
        logText = null;
        progress = null;
        pairButton = null;
        connectButton = null;
        scanButton = null;
        candidateButtons.clear();
        wirelessHost = null;
        pairPortInput = null;
        pairCodeInput = null;
        connectPortInput = null;
        classicHost = null;
        classicPortInput = null;

        LinearLayout root = UiKit.column(activity);
        root.addView(buildTabs());

        if (activity.device != null) {
            LinearLayout note = UiKit.note(activity, "当前已有连接",
                    "再次连接会切换到新的电视；原连接会被新的设备信息替换。",
                    UiData.TONE_INFO);
            UiKit.add(root, note, 0, 12, 0, 0);
        }

        statusSlot = UiKit.column(activity);
        UiKit.add(root, statusSlot, 0, 12, 0, 0);

        if (MODE_WIRELESS.equals(mode)) {
            buildWireless(root);
        } else if (MODE_CLASSIC.equals(mode)) {
            buildClassic(root);
        } else {
            buildUsb(root);
        }

        buildLogs(root);
        LinearLayout safety = UiKit.note(activity, "仅限家庭局域网",
                "请勿在路由器上把 ADB 端口映射到公网。调试授权请在电视上当面确认。",
                UiData.TONE_WARN);
        UiKit.add(root, safety, 0, 12, 0, 0);

        sheet.add(root);
        refreshLog();
        applyBusyState();
    }

    private LinearLayout buildTabs() {
        LinearLayout wrap = UiKit.row(activity);
        wrap.setPadding(UiKit.dp(activity, 4), UiKit.dp(activity, 4),
                UiKit.dp(activity, 4), UiKit.dp(activity, 4));
        wrap.setBackgroundResource(R.drawable.bg_segment_container);
        String[] labels = {"无线配对", "网络 ADB", "USB 排障"};
        String[] modes = {MODE_WIRELESS, MODE_CLASSIC, MODE_USB};
        for (int i = 0; i < labels.length; i++) {
            final String target = modes[i];
            TextView tab = UiKit.text(activity, labels[i], 12f,
                    target.equals(mode) ? 0xFF16212F : 0xFF6D7C8E, target.equals(mode));
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(UiKit.dp(activity, 36));
            tab.setBackgroundResource(target.equals(mode)
                    ? R.drawable.bg_segment_selected : android.R.color.transparent);
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> {
                if (busy || target.equals(mode)) {
                    return;
                }
                captureInputs();
                mode = target;
                build();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                params.leftMargin = UiKit.dp(activity, 3);
            }
            wrap.addView(tab, params);
            modeTabs[i] = tab;
        }
        return wrap;
    }

    private void buildWireless(LinearLayout root) {
        LinearLayout intro = UiKit.note(activity, "Android 11+ 无线调试",
                "电视打开：设置 → 系统 → 开发者选项 → 无线调试。"
                        + "先扫描局域网设备，点设备右侧“连接”；电视要求配对时，再展开“手动输入 IP”。",
                UiData.TONE_INFO);
        UiKit.add(root, intro, 0, 12, 0, 0);

        buildScanSection(root);
        buildManualToggle(root, "首次配对，或扫描不到电视时使用");
        if (manualExpanded) {
            buildWirelessManual(root);
        }
    }

    private void buildWirelessManual(LinearLayout root) {
        wirelessHost = field(root, "电视 IP 地址", hostValue,
                "192.168.1.100", InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_URI);
        wirelessHost.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(64), alphaNumericDotFilter()
        });

        LinearLayout ports = UiKit.row(activity);
        LinearLayout portBlock = fieldBlock("配对端口");
        pairPortInput = UiKit.input(activity, "配对弹窗端口", InputType.TYPE_CLASS_NUMBER);
        pairPortInput.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        pairPortInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        pairPortInput.setText(pairPortValue);
        portBlock.addView(pairPortInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(activity, 44)));

        LinearLayout codeBlock = fieldBlock("配对码（6 位）");
        pairCodeInput = UiKit.input(activity, "电视弹窗中的数字",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pairCodeInput.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        pairCodeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        pairCodeInput.setSaveEnabled(false);
        codeBlock.addView(pairCodeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(activity, 44)));

        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ports.addView(portBlock, portParams);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        codeParams.leftMargin = UiKit.dp(activity, 8);
        ports.addView(codeBlock, codeParams);
        UiKit.add(root, ports, 0, 9, 0, 0);

        connectPortInput = field(root, "连接端口", connectPortValue,
                "电视无线调试主页显示的端口", InputType.TYPE_CLASS_NUMBER);
        connectPortInput.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        connectPortInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});

        LinearLayout actions = UiKit.row(activity);
        pairButton = UiKit.button(activity, "1. 仅配对", UiKit.BTN_SECONDARY);
        pairButton.setOnClickListener(v -> pairWireless());
        pairButton.setLayoutParams(new LinearLayout.LayoutParams(0,
                UiKit.dp(activity, 44), 1f));
        actions.addView(pairButton);
        connectButton = UiKit.button(activity, "2. 连接电视", UiKit.BTN_PRIMARY);
        connectButton.setOnClickListener(v -> connect());
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(0,
                UiKit.dp(activity, 44), 1.35f);
        connectParams.leftMargin = UiKit.dp(activity, 8);
        actions.addView(connectButton, connectParams);
        UiKit.add(root, actions, 0, 12, 0, 0);

        TextView hint = UiKit.caption(activity,
                "配对码只在本次操作中使用，不会保存或写进日志。配对失败时，电视上的弹窗可能已经关闭，请重新打开后再试。");
        hint.setPadding(0, UiKit.dp(activity, 8), 0, 0);
        root.addView(hint);

        if (paired) {
            setStatus("配对成功",
                    "调试密钥已保存在本机。现在填写电视无线调试主页显示的连接端口，然后连接电视。",
                    UiData.TONE_GREEN);
        }
    }

    private void buildClassic(LinearLayout root) {
        LinearLayout intro = UiKit.note(activity, "网络 ADB / 老电视",
                "电视开启开发者选项中的 USB 调试后，部分机型还需要电脑执行一次"
                        + " adb tcpip 5555，再在同一局域网里连接。"
                        + "扫描到设备后点“连接”；扫描不到时展开“手动输入 IP”。",
                UiData.TONE_INFO);
        UiKit.add(root, intro, 0, 12, 0, 0);

        buildScanSection(root);
        buildManualToggle(root, "扫描不到电视时使用；需要知道 ADB 端口");
        if (manualExpanded) {
            buildClassicManual(root);
        }

        LinearLayout warning = UiKit.note(activity, "不要暴露 5555",
                "网络 ADB 没有配对码。只在家里的 Wi-Fi 使用，确认路由器没有端口转发。",
                UiData.TONE_WARN);
        UiKit.add(root, warning, 0, 10, 0, 0);
    }

    private void buildClassicManual(LinearLayout root) {
        classicHost = field(root, "电视 IP 地址", hostValue,
                "192.168.1.100", InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_URI);
        classicHost.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(64), alphaNumericDotFilter()
        });
        classicPortInput = field(root, "ADB 端口", classicPortValue, "5555",
                InputType.TYPE_CLASS_NUMBER);
        classicPortInput.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        classicPortInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});

        connectButton = UiKit.button(activity, "连接电视", UiKit.BTN_PRIMARY);
        connectButton.setOnClickListener(v -> connect());
        UiKit.add(root, connectButton, 0, 12, 0, 0);
    }

    private void buildManualToggle(LinearLayout root, String subtitle) {
        LinearLayout card = UiKit.card(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = UiKit.column(activity);
        copy.addView(UiKit.text(activity, "手动输入 IP（高级）", 12.5f, 0xFF16212F, true));
        TextView sub = UiKit.caption(activity, subtitle);
        sub.setPadding(0, UiKit.dp(activity, 3), 0, 0);
        copy.addView(sub);
        card.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView action = UiKit.text(activity, manualExpanded ? "收起" : "展开",
                12f, 0xFF2F7DFF, true);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.rightMargin = UiKit.dp(activity, 6);
        card.addView(action, actionParams);
        LineIconView chevron = UiKit.icon(activity, "chevron", 15, 0xFFB0BAC7);
        if (manualExpanded) {
            chevron.setRotation(90f);
        }
        card.addView(chevron);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(manualExpanded ? "收起手动输入 IP" : "展开手动输入 IP");
        card.setOnClickListener(v -> {
            if (busy) {
                return;
            }
            captureInputs();
            manualExpanded = !manualExpanded;
            build();
        });
        manualToggleView = card;
        UiKit.add(root, card, 0, 10, 0, 0);
    }

    private void buildScanSection(LinearLayout root) {
        scanButton = UiKit.button(activity, "扫描局域网设备", UiKit.BTN_PRIMARY);
        scanButton.setOnClickListener(v -> scan());
        UiKit.add(root, scanButton, 0, 12, 0, 0);

        TextView scanHint = UiKit.caption(activity,
                "只列出已开启无线调试或网络 ADB 的设备；点右侧“连接”直接连接。"
                        + "首次使用无线调试仍需先在电视上配对。");
        scanHint.setPadding(UiKit.dp(activity, 2), UiKit.dp(activity, 6),
                UiKit.dp(activity, 2), 0);
        root.addView(scanHint);

        candidatesSlot = UiKit.column(activity);
        UiKit.add(root, candidatesSlot, 0, 9, 0, 0);
        showCandidates(scanResults);
    }

    private void buildUsb(LinearLayout root) {
        LinearLayout intro = UiKit.note(activity, "本应用不能远程打开 USB 调试",
                "这是 Android 的系统安全限制。请先在电视上手动开启开发者选项；"
                        + "USB 线只用于首次准备或老电视执行 adb tcpip。",
                UiData.TONE_WARN);
        UiKit.add(root, intro, 0, 12, 0, 0);

        usbStep(root, "1", "打开电视的开发者选项",
                "设置 → 关于 → 版本号，连续点按 7 次；返回 设置 → 系统 / 设备偏好设置 → 开发者选项。");
        usbStep(root, "2", "打开 USB 调试",
                "用 USB 线把电视接到电脑（或按电视厂商说明操作），在电视弹窗中允许这台电脑。");
        usbStep(root, "3", "切到无线连接",
                "Android 11+：打开无线调试，用配对码连接。老电视：在电脑执行 adb tcpip 5555，"
                        + "再回到本页使用“网络 ADB”。");

        LinearLayout notes = UiKit.note(activity, "不会自动授权",
                "本应用不会读取 USB 调试权限，也不会在电视上开启无障碍、设备管理员或悬浮窗。",
                UiData.TONE_INFO);
        UiKit.add(root, notes, 0, 10, 0, 0);

        final CheckBox[] boxes = new CheckBox[3];
        final String[] checks = {
                "电视已开启开发者选项",
                "已用 USB / 厂商方式启用调试",
                "电视与手机在同一 Wi-Fi"
        };
        for (int i = 0; i < checks.length; i++) {
            CheckBox box = new CheckBox(activity);
            box.setText(checks[i]);
            box.setTextSize(12.5f);
            box.setTextColor(0xFF34465B);
            box.setPadding(UiKit.dp(activity, 2), UiKit.dp(activity, 6),
                    UiKit.dp(activity, 2), UiKit.dp(activity, 6));
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                box.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF2F7DFF));
            }
            boxes[i] = box;
        }
        LinearLayout checklist = UiKit.card(activity);
        checklist.addView(UiKit.text(activity, "准备好了吗", 12.5f, 0xFF16212F, true));
        for (CheckBox box : boxes) {
            checklist.addView(box);
        }
        UiKit.add(root, checklist, 0, 10, 0, 0);

        final TextView go = UiKit.button(activity, "已确认，去无线连接", UiKit.BTN_PRIMARY);
        UiKit.setEnabled(go, false);
        for (CheckBox box : boxes) {
            box.setOnCheckedChangeListener((buttonView, checked) -> {
                boolean all = boxes[0].isChecked() && boxes[1].isChecked() && boxes[2].isChecked();
                UiKit.setEnabled(go, all);
                if (all) {
                    go.setBackgroundResource(R.drawable.bg_button_primary);
                    go.setTextColor(Color.WHITE);
                }
            });
        }
        go.setOnClickListener(v -> {
            if (!boxes[0].isChecked() || !boxes[1].isChecked() || !boxes[2].isChecked()) {
                return;
            }
            captureInputs();
            mode = MODE_WIRELESS;
            build();
        });
        UiKit.add(root, go, 0, 12, 0, 0);
    }

    private void usbStep(LinearLayout root, String number, String title, String body) {
        LinearLayout card = UiKit.row(activity);
        card.setGravity(Gravity.TOP);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(UiKit.dp(activity, 12), UiKit.dp(activity, 12),
                UiKit.dp(activity, 12), UiKit.dp(activity, 12));
        TextView badge = UiKit.text(activity, number, 12f, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(UiKit.roundRect(0xFF2F7DFF, UiKit.dp(activity, 10), 0, 0));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                UiKit.dp(activity, 26), UiKit.dp(activity, 26));
        badgeParams.rightMargin = UiKit.dp(activity, 10);
        card.addView(badge, badgeParams);
        LinearLayout copy = UiKit.column(activity);
        copy.addView(UiKit.text(activity, title, 13f, 0xFF16212F, true));
        TextView desc = UiKit.caption(activity, body);
        desc.setPadding(0, UiKit.dp(activity, 4), 0, 0);
        copy.addView(desc);
        card.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        UiKit.add(root, card, 0, 9, 0, 0);
    }

    private void buildLogs(LinearLayout root) {
        LinearLayout header = UiKit.row(activity);
        header.addView(UiKit.text(activity, "连接日志", 11.5f, 0xFF4C5C70, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        progress = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleSmall);
        progress.setIndeterminate(true);
        progress.setVisibility(View.INVISIBLE);
        header.addView(progress, new LinearLayout.LayoutParams(
                UiKit.dp(activity, 18), UiKit.dp(activity, 18)));
        logWrap = UiKit.column(activity);
        logWrap.addView(header);
        logText = UiKit.logView(activity);
        logText.setVisibility(View.GONE);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logParams.topMargin = UiKit.dp(activity, 6);
        logWrap.addView(logText, logParams);
        UiKit.add(root, logWrap, 0, 12, 0, 0);
    }

    private EditText field(LinearLayout root, String label, String value,
                           String hint, int inputType) {
        LinearLayout block = fieldBlock(label);
        EditText input = UiKit.input(activity, hint, inputType);
        input.setText(value == null ? "" : value);
        input.setSelection(input.getText().length());
        block.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(activity, 44)));
        UiKit.add(root, block, 0, 9, 0, 0);
        return input;
    }

    private LinearLayout fieldBlock(String label) {
        LinearLayout block = UiKit.column(activity);
        TextView text = UiKit.text(activity, label, 11.5f, 0xFF4C5C70, true);
        text.setPadding(0, 0, 0, UiKit.dp(activity, 5));
        block.addView(text);
        return block;
    }

    private void captureInputs() {
        if (wirelessHost != null) {
            hostValue = wirelessHost.getText().toString().trim();
        }
        if (classicHost != null) {
            hostValue = classicHost.getText().toString().trim();
        }
        if (pairPortInput != null) {
            pairPortValue = pairPortInput.getText().toString().trim();
        }
        if (connectPortInput != null) {
            connectPortValue = connectPortInput.getText().toString().trim();
        }
        if (classicPortInput != null) {
            classicPortValue = classicPortInput.getText().toString().trim();
        }
    }

    private void pairWireless() {
        if (busy) {
            return;
        }
        String host = wirelessHost == null ? "" : wirelessHost.getText().toString().trim();
        String portText = pairPortInput == null ? "" : pairPortInput.getText().toString().trim();
        String code = pairCodeInput == null ? "" : pairCodeInput.getText().toString().trim();
        if (!validLanHost(host)) {
            setStatus("地址不合法", "请填写电视在家庭局域网中的 IPv4 地址或 .local 主机名。", UiData.TONE_RED);
            return;
        }
        int port = portOf(portText);
        if (port < 0) {
            setStatus("配对端口不合法", "配对端口应为 1–65535；它和连接端口不是同一个数字。", UiData.TONE_RED);
            return;
        }
        if (!code.matches("\\d{6}")) {
            setStatus("配对码不完整", "请输入电视配对弹窗中的 6 位数字。每次弹窗的配对码都会变化。",
                    UiData.TONE_RED);
            return;
        }
        // Read once and clear immediately before pairing: the code must not
        // linger in a view, saved state, or a log.
        if (pairCodeInput != null) {
            pairCodeInput.setText("");
        }
        hostValue = host;
        pairPortValue = String.valueOf(port);
        setBusy(true, "pair");
        setStatus("正在配对", "请保持电视上的“使用配对码配对设备”弹窗打开。", UiData.TONE_INFO);
        appendLog("开始配对 " + host + ":" + port + "（配对码不会写入日志）…");
        final String pairHost = host;
        final int pairPort = port;
        final String pairCode = code;
        executor.execute(() -> {
            Exception failure = null;
            try {
                DeviceSession session = new DeviceSession(activity, line -> postLog(line));
                session.pair(pairHost, pairPort, pairCode);
            } catch (Exception error) {
                failure = error;
            }
            final Exception error = failure;
            main.post(() -> {
                if (closed) {
                    return;
                }
                setBusy(false, "");
                if (error == null) {
                    paired = true;
                    setStatus("配对成功",
                            "调试密钥已保存在本机。现在填写电视无线调试主页显示的连接端口，然后连接电视。",
                            UiData.TONE_GREEN);
                    appendLog("✓ 配对成功。请继续连接电视。");
                    if (connectPortInput != null) {
                        connectPortInput.requestFocus();
                    }
                } else {
                    paired = false;
                    setStatus("配对没有完成", safe(error), UiData.TONE_RED);
                    appendLog("! 配对失败：" + safe(error));
                }
            });
        });
    }

    private void connect() {
        if (busy) {
            return;
        }
        captureInputs();
        String host = MODE_WIRELESS.equals(mode)
                ? (wirelessHost == null ? "" : wirelessHost.getText().toString().trim())
                : (classicHost == null ? "" : classicHost.getText().toString().trim());
        String portText = MODE_WIRELESS.equals(mode)
                ? (connectPortInput == null ? "" : connectPortInput.getText().toString().trim())
                : (classicPortInput == null ? "" : classicPortInput.getText().toString().trim());
        if (!validLanHost(host)) {
            setStatus("地址不合法", "请填写电视在家庭局域网中的 IPv4 地址或 .local 主机名。", UiData.TONE_RED);
            return;
        }
        int port = portOf(portText);
        if (port < 0) {
            setStatus("端口不合法", "连接端口应为 1–65535。无线调试的端口可在电视无线调试主页查看。",
                    UiData.TONE_RED);
            return;
        }
        connectTo(host, port, MODE_WIRELESS.equals(mode) ? "wireless" : "classic");
    }

    /** Connects to an already validated LAN target. */
    private void connectTo(String host, int port, String transport) {
        if (busy) {
            return;
        }
        if (!validLanHost(host)) {
            setStatus("地址不合法", "请选择家庭局域网中的设备。", UiData.TONE_RED);
            return;
        }
        if (port < 1 || port > 65535) {
            setStatus("端口不合法", "端口应为 1–65535。", UiData.TONE_RED);
            return;
        }
        boolean wireless = "wireless".equals(transport);
        if (wireless && !paired) {
            appendLog("提示：还没有在本页配对。如果电视拒绝连接，请先用配对码配对。");
        }
        hostValue = host;
        if (wireless) {
            connectPortValue = String.valueOf(port);
        } else {
            classicPortValue = String.valueOf(port);
        }
        if (sheet != null) {
            sheet.hideKeyboard();
        }
        setBusy(true, "connect");
        setStatus("正在连接", "会先建立 ADB 连接；如果电视还没有授权，请留意电视屏幕并点“允许”。",
                UiData.TONE_INFO);
        appendLog("正在连接 " + host + ":" + port + "…");
        final String connectHost = host;
        final int connectPort = port;
        final String connectTransport = transport;
        executor.execute(() -> {
            JSONObject info = null;
            Exception failure = null;
            try {
                DeviceSession session = new DeviceSession(activity, line -> postLog(line));
                JSONObject result = session.connect(connectHost, connectPort, connectTransport);
                if (result.optBoolean("authorized", false)) {
                    info = session.inspect(connectHost, connectPort, connectTransport);
                } else {
                    postLog("等待电视授权，最多 120 秒。请在电视上点“允许”，建议勾选“始终允许”。");
                    info = session.authorize(connectHost, connectPort, connectTransport);
                }
                if (info == null || !info.optBoolean("authorized", false)) {
                    throw new DeviceSession.ConnectException(
                            "电视还没有授权调试。请先在电视上允许，然后再次连接。");
                }
            } catch (Exception error) {
                failure = error;
            }
            final JSONObject connected = info;
            final Exception error = failure;
            main.post(() -> {
                setBusy(false, "");
                if (error != null) {
                    if (wireless && !paired && !manualExpanded) {
                        manualExpanded = true;
                        build();
                        if (sheet != null) {
                            sheet.scrollToView(manualToggleView);
                        }
                        setStatus("可能需要先配对",
                                "如果这是首次无线连接，请在电视上打开“使用配对码配对设备”，"
                                        + "填写配对端口和 6 位配对码，先点“1. 仅配对”，再点“2. 连接电视”。"
                                        + "\n原因：" + safe(error),
                                UiData.TONE_WARN);
                        appendLog("! 连接失败，可能需要先配对：" + safe(error));
                    } else {
                        setStatus("连接没有完成", safe(error), UiData.TONE_RED);
                        appendLog("! 连接失败：" + safe(error));
                    }
                    return;
                }
                activity.attachDevice(connected, connectHost, connectPort, connectTransport);
                activity.toast("已连接 " + connected.optString("label", connectHost));
                appendLog("✓ 已连接：" + connected.optString("label", connectHost));
                showConnectedPage(connected);
            });
        });
    }

    private void showConnectedPage(JSONObject info) {
        if (closed || sheet == null) {
            return;
        }
        sheet.setTitle("连接成功");
        sheet.setSubtitle("");
        sheet.clearBody();
        LinearLayout root = UiKit.column(activity);

        LinearLayout hero = UiKit.card(activity);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.addView(iconTile("check", 0xFF129B67, 0xFFEAF9F2));
        TextView title = UiKit.title(activity, "已经连上 " + info.optString("label", "电视"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, UiKit.dp(activity, 10), 0, 0);
        hero.addView(title);
        TextView detail = UiKit.caption(activity, "Android "
                + info.optString("android", "?") + " · "
                + ("classic".equals(info.optString("transport"))
                ? "网络 ADB" : "无线调试") + " · " + info.optString("latency", ""));
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, UiKit.dp(activity, 3), 0, UiKit.dp(activity, 12));
        hero.addView(detail);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(hero, heroParams);

        UiKit.add(root, UiKit.note(activity, "下一步",
                "电视已经受信任。安装只会写入第三方应用，不会删除或停用系统桌面。",
                UiData.TONE_GREEN), 0, 12, 0, 0);

        TextView market = UiKit.button(activity, "去工具市场安装", UiKit.BTN_PRIMARY);
        market.setOnClickListener(v -> {
            sheet.dismiss();
            activity.switchTab(MainActivity.TAB_MARKET);
        });
        UiKit.add(root, market, 0, 14, 0, 0);
        TextView device = UiKit.button(activity, "查看电视状态", UiKit.BTN_SECONDARY);
        device.setOnClickListener(v -> {
            sheet.dismiss();
            activity.switchTab(MainActivity.TAB_DEVICE);
        });
        UiKit.add(root, device, 0, 8, 0, 0);

        sheet.add(root);
    }

    private View iconTile(String iconName, int tint, int background) {
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(activity);
        wrap.setBackground(UiKit.roundRect(background, UiKit.dp(activity, 22), 0, 0));
        LineIconView icon = UiKit.icon(activity, iconName, 24, tint);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                UiKit.dp(activity, 24), UiKit.dp(activity, 24));
        params.gravity = Gravity.CENTER;
        wrap.addView(icon, params);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                UiKit.dp(activity, 48), UiKit.dp(activity, 48)));
        return wrap;
    }

    private void scan() {
        if (busy) {
            return;
        }
        setBusy(true, "scan");
        setStatus("正在扫描", "会同时检查无线调试广播和 5555 端口，只探测，不会自动连接。", UiData.TONE_INFO);
        appendLog("正在扫描局域网中的 ADB 设备…");
        if (sheet != null) {
            sheet.scrollToTop();
        }
        scanResults = null;
        if (candidatesSlot != null) {
            candidatesSlot.removeAllViews();
        }
        executor.execute(() -> {
            JSONObject result = null;
            Exception failure = null;
            try {
                LanScanner scanner = new LanScanner(activity, line -> postLog(line));
                result = scanner.scan();
            } catch (Exception error) {
                failure = error;
            }
            final JSONObject scanResult = result;
            final Exception error = failure;
            main.post(() -> {
                setBusy(false, "");
                if (error != null) {
                    scanResults = null;
                    setStatus("扫描没有完成", safe(error), UiData.TONE_RED);
                    appendLog("! 扫描失败：" + safe(error));
                    if (sheet != null) {
                        sheet.scrollToTop();
                    }
                    return;
                }
                JSONArray candidates = scanResult.optJSONArray("candidates");
                scanResults = candidates == null ? new JSONArray() : candidates;
                int count = scanResults.length();
                if (count == 0) {
                    setStatus("没有发现 ADB 设备",
                            "请确认电视已打开无线调试 / 网络 ADB，并与手机处于同一 Wi-Fi。"
                                    + "未开启 ADB 的设备不会出现在列表里，也可以在下方手动填写 IP。",
                            UiData.TONE_WARN);
                } else {
                    setStatus("发现 " + count + " 台 ADB 设备",
                            "点设备右侧的“连接”即可直接连接；无线调试首次使用还需要配对码。",
                            UiData.TONE_GREEN);
                }
                showCandidates(scanResults);
                if (sheet != null) {
                    sheet.scrollToTop();
                }
            });
        });
    }

    private void showCandidates(JSONArray candidates) {
        if (candidatesSlot == null) {
            return;
        }
        candidatesSlot.removeAllViews();
        candidateButtons.clear();
        if (candidates == null || candidates.length() == 0) {
            return;
        }
        LinearLayout box = UiKit.column(activity);
        box.setBackgroundResource(R.drawable.bg_card);
        box.setPadding(UiKit.dp(activity, 12), UiKit.dp(activity, 10),
                UiKit.dp(activity, 12), UiKit.dp(activity, 4));
        box.addView(UiKit.text(activity, "发现的 ADB 设备 · " + candidates.length(),
                11.5f, 0xFF4C5C70, true));
        int shown = 0;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) {
                continue;
            }
            String ip = candidate.optString("ip", "");
            int port = candidate.optInt("port", 5555);
            String source = candidate.optString("source", "port");
            String name = candidate.optString("name", "").trim();
            boolean wireless = candidate.optBoolean("wireless", false);
            String address = ip + ":" + port;
            if (shown > 0) {
                box.addView(UiKit.divider(activity));
            }
            shown++;
            LinearLayout row = UiKit.row(activity);
            row.setPadding(0, UiKit.dp(activity, 10), 0, UiKit.dp(activity, 10));
            LinearLayout copy = UiKit.column(activity);
            TextView title = UiKit.text(activity,
                    name.isEmpty() ? address : name, 12.5f, 0xFF16212F, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);
            String detail = sourceLabel(source);
            if (!name.isEmpty()) {
                detail = address + " · " + detail;
            }
            if (wireless) {
                detail += " · 首次通常需要配对";
            }
            copy.addView(UiKit.caption(activity, detail));
            row.addView(copy, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView use = UiKit.smallButton(activity, "连接", UiKit.BTN_PRIMARY);
            use.setOnClickListener(v -> connectCandidate(ip, port, wireless));
            use.setEnabled(!busy);
            use.setAlpha(busy ? 0.5f : 1f);
            candidateButtons.add(use);
            row.addView(use);
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription("连接设备 " + address);
            row.setOnClickListener(v -> connectCandidate(ip, port, wireless));
            box.addView(row);
        }
        candidatesSlot.addView(box);
    }

    private void connectCandidate(String ip, int port, boolean wireless) {
        if (busy) {
            return;
        }
        // Keep the pairing fields the user already typed; only replace host/port.
        captureInputs();
        hostValue = ip;
        if (MODE_WIRELESS.equals(mode)) {
            connectPortValue = String.valueOf(port);
            setInputText(wirelessHost, ip);
            setInputText(connectPortInput, String.valueOf(port));
        } else {
            classicPortValue = String.valueOf(port);
            setInputText(classicHost, ip);
            setInputText(classicPortInput, String.valueOf(port));
        }
        appendLog("连接扫描结果 " + ip + ":" + port + "…");
        connectTo(ip, port, wireless ? "wireless" : "classic");
    }

    private static void setInputText(EditText input, String value) {
        if (input == null) {
            return;
        }
        input.setText(value);
        input.setSelection(input.getText().length());
    }

    private void setBusy(boolean value, String action) {
        busy = value;
        currentAction = action;
        applyBusyState();
    }

    private void applyBusyState() {
        if (progress != null) {
            progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        }
        if (pairButton != null) {
            pairButton.setEnabled(!busy);
            pairButton.setAlpha(busy ? 0.5f : 1f);
            pairButton.setText(busy && "pair".equals(currentAction) ? "配对中…" : "1. 仅配对");
        }
        if (connectButton != null) {
            connectButton.setEnabled(!busy);
            connectButton.setAlpha(busy ? 0.5f : 1f);
            connectButton.setText(busy && "connect".equals(currentAction) ? "连接中…" : "连接电视");
            if (MODE_CLASSIC.equals(mode)) {
                connectButton.setText(busy && "connect".equals(currentAction)
                        ? "连接中…" : "连接电视");
            }
        }
        if (scanButton != null) {
            scanButton.setEnabled(!busy);
            scanButton.setAlpha(busy ? 0.5f : 1f);
            scanButton.setText(busy && "scan".equals(currentAction) ? "扫描中…" : "扫描局域网设备");
        }
        for (TextView button : candidateButtons) {
            button.setEnabled(!busy);
            button.setAlpha(busy ? 0.5f : 1f);
        }
        for (EditText input : new EditText[]{
                wirelessHost, pairPortInput, pairCodeInput, connectPortInput,
                classicHost, classicPortInput}) {
            if (input != null) {
                input.setEnabled(!busy);
                input.setAlpha(busy ? 0.6f : 1f);
            }
        }
        for (TextView tab : modeTabs) {
            if (tab != null) {
                tab.setEnabled(!busy);
                tab.setAlpha(busy ? 0.6f : 1f);
            }
        }
    }

    private void setStatus(String title, String body, int tone) {
        if (statusSlot == null) {
            return;
        }
        statusSlot.removeAllViews();
        LinearLayout note = UiKit.note(activity, title, body, tone);
        statusSlot.addView(note);
    }

    private void appendLog(String line) {
        if (TextUtils.isEmpty(line)) {
            return;
        }
        if (log.length() > 0) {
            log.append('\n');
        }
        log.append(line);
        if (log.length() > 14000) {
            log.delete(0, log.length() - 14000);
        }
        refreshLog();
    }

    private void postLog(final String line) {
        main.post(() -> {
            if (!closed) {
                appendLog(line);
            }
        });
    }

    private void refreshLog() {
        if (logText == null) {
            return;
        }
        logText.setText(log.toString());
        logText.setVisibility(log.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private static String safe(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = error.getClass().getSimpleName();
        }
        return message.trim();
    }

    private static int portOf(String text) {
        try {
            int port = Integer.parseInt(text);
            return port > 0 && port <= 65535 ? port : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    /** Only a private/link-local IPv4 literal or a .local mDNS name. */
    static boolean validLanHost(String host) {
        if (host == null) {
            return false;
        }
        String value = host.trim().toLowerCase(Locale.US);
        if (value.isEmpty() || value.length() > 64) {
            return false;
        }
        if (value.matches("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.local")) {
            return true;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("\\d{1,3}")) {
                return false;
            }
            int n = Integer.parseInt(parts[i]);
            if (n < 0 || n > 255) {
                return false;
            }
            octets[i] = n;
        }
        if (octets[0] == 10) {
            return true;
        }
        if (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) {
            return true;
        }
        if (octets[0] == 192 && octets[1] == 168) {
            return true;
        }
        if (octets[0] == 169 && octets[1] == 254) {
            return true;
        }
        return octets[0] == 127 && octets[1] == 0 && octets[2] == 0 && octets[3] == 1;
    }

    private static InputFilter alphaNumericDotFilter() {
        return (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-')) {
                    return "";
                }
            }
            return null;
        };
    }

    private static String sourceLabel(String source) {
        if ("mdns".equals(source) || "mdns+port".equals(source)) {
            return "无线调试广播";
        }
        if ("wireless".equals(source) || "wireless+port".equals(source)) {
            return "ADB 无线调试";
        }
        return "局域网 5555 探测";
    }
}