package com.toolstore.installer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure native, framework-only host. All network/ADB work goes through the
 * existing Java engine; this Activity never runs shell from user input.
 */
public final class MainActivity extends Activity {

    static final int TAB_MARKET = 0;
    static final int TAB_DEVICE = 1;

    private static final int BACKGROUND = 0xFFF6F8FC;
    private static final int TEXT = 0xFF16212F;
    private static final int TEXT_2 = 0xFF4C5C70;
    private static final int MUTED = 0xFF8391A3;
    private static final int ACCENT = 0xFF2F7DFF;
    private static final int GREEN = 0xFF129B67;
    private static final int AMBER = 0xFFB9770E;
    private static final int RED = 0xFFDE464D;

    ExecutorService executor;
    Handler main;
    JSONObject device;
    JSONObject lastTv;
    String devHost = "";
    int devPort = 0;
    String devTransport = "";

    private FrameLayout content;
    private FrameLayout detailHost;
    private AppDetailPage detailPage;
    private View marketView;
    private View deviceView;
    private LinearLayout deviceContent;
    private LinearLayout marketSections;
    private EditText searchInput;
    private View searchClear;
    private TextView filterAll;
    private TextView filterLauncher;
    private TextView filterTool;

    private LinearLayout navMarket;
    private LinearLayout navDevice;
    private View navMarketIconWrap;
    private View navDeviceIconWrap;
    private LineIconView navMarketIcon;
    private LineIconView navDeviceIcon;
    private TextView navMarketLabel;
    private TextView navDeviceLabel;
    private View navDeviceDot;
    private TextView brandSubtitle;

    private int currentTab = TAB_MARKET;
    private String marketFilter = "all";
    private String marketQuery = "";
    private boolean finishing;
    private boolean refreshInFlight;
        private boolean restoreInFlight;
    private final List<Runnable> refreshCallbacks = new ArrayList<Runnable>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        applyLightSystemBars();

        executor = Executors.newFixedThreadPool(3);
        main = new Handler(Looper.getMainLooper());
        setContentView(R.layout.activity_main);

        content = (FrameLayout) findViewById(R.id.content);
        detailHost = (FrameLayout) findViewById(R.id.detailHost);
        brandSubtitle = (TextView) findViewById(R.id.brandSubtitle);
        navMarket = (LinearLayout) findViewById(R.id.navMarket);
        navDevice = (LinearLayout) findViewById(R.id.navDevice);
        navMarketIconWrap = findViewById(R.id.navMarketIconWrap);
        navDeviceIconWrap = findViewById(R.id.navDeviceIconWrap);
        navMarketIcon = (LineIconView) findViewById(R.id.navMarketIcon);
        navDeviceIcon = (LineIconView) findViewById(R.id.navDeviceIcon);
        navMarketLabel = (TextView) findViewById(R.id.navMarketLabel);
        navDeviceLabel = (TextView) findViewById(R.id.navDeviceLabel);
        navDeviceDot = findViewById(R.id.navDeviceDot);

        UiKit.prepareIcon(findViewById(android.R.id.content), R.id.brandLogo, "dot", MUTED);
        LogoView brand = (LogoView) findViewById(R.id.brandLogo);
        brand.setShellLogo();
        UiKit.prepareIcon(findViewById(android.R.id.content), R.id.settingsIcon,
                "settings", TEXT_2);
        findViewById(R.id.settingsButton).setOnClickListener(v -> showSettingsSheet());

        LayoutInflater inflater = LayoutInflater.from(this);
        marketView = inflater.inflate(R.layout.screen_market, content, false);
        deviceView = inflater.inflate(R.layout.screen_device, content, false);
        content.addView(marketView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(deviceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        deviceContent = (LinearLayout) deviceView.findViewById(R.id.deviceContent);

        wireBottomNav();
        wireMarket();
        renderMarket();
        renderDevice();
        switchTab(TAB_MARKET);

        executor.execute(() -> {
            JSONObject saved = null;
            try {
                saved = new DeviceSession(this, null).lastTv();
            } catch (Throwable ignored) {
            }
            final JSONObject value = saved;
            main.post(() -> {
                lastTv = value;
                if (currentTab == TAB_DEVICE && device == null) {
                    renderDevice();
                }
            });
        });
    }

    private void applyLightSystemBars() {
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = getWindow().getDecorView().getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void wireBottomNav() {
        navMarket.setOnClickListener(v -> switchTab(TAB_MARKET));
        navDevice.setOnClickListener(v -> switchTab(TAB_DEVICE));
        navMarketIcon.setIcon("store");
        navDeviceIcon.setIcon("tv");
        navMarketIcon.setIconColor(ACCENT);
        navDeviceIcon.setIconColor(0xFF8A97A8);
        navDeviceDot.setVisibility(View.GONE);
    }

    void switchTab(int tab) {
        if (detailPage != null) {
            detailPage.closeImmediately();
        }
        currentTab = tab;
        boolean market = tab == TAB_MARKET;
        marketView.setVisibility(market ? View.VISIBLE : View.GONE);
        deviceView.setVisibility(market ? View.GONE : View.VISIBLE);

        navMarketIconWrap.setBackgroundResource(market
                ? R.drawable.bg_nav_selected : android.R.color.transparent);
        navDeviceIconWrap.setBackgroundResource(market
                ? android.R.color.transparent : R.drawable.bg_nav_selected);
        navMarketIcon.setIconColor(market ? ACCENT : 0xFF8A97A8);
        navDeviceIcon.setIconColor(market ? 0xFF8A97A8 : ACCENT);
        navMarketLabel.setTextColor(market ? ACCENT : 0xFF8A97A8);
        navDeviceLabel.setTextColor(market ? 0xFF8A97A8 : ACCENT);
        navMarketLabel.setTypeface(Typeface.create("sans-serif",
                market ? Typeface.BOLD : Typeface.NORMAL));
        navDeviceLabel.setTypeface(Typeface.create("sans-serif",
                market ? Typeface.NORMAL : Typeface.BOLD));
        brandSubtitle.setText(market
                ? "Tool Store · 精选电视工具"
                : "我的电视 · 全部操作在本机完成");
        if (market) {
            hideKeyboard();
        } else {
            renderDevice();
        }
    }

    private void openAppDetail(UiData.App app) {
        openAppDetail(app, false);
    }

    private void openAppDetail(UiData.App app, boolean focusInstalled) {
        if (app == null || detailHost == null) {
            return;
        }
        if (detailPage != null) {
            if (app.id().equals(detailPage.appId())) {
                if (focusInstalled) {
                    detailPage.focusInstalledNow();
                }
                return;
            }
            detailPage.closeImmediately();
        }
        detailPage = new AppDetailPage(this, detailHost, app, focusInstalled);
        detailPage.show();
    }

    void onDetailClosed(AppDetailPage page) {
        if (detailPage == page) {
            detailPage = null;
        }
    }

    private void wireMarket() {
        marketSections = (LinearLayout) marketView.findViewById(R.id.marketSections);
        searchInput = (EditText) marketView.findViewById(R.id.searchInput);
        searchClear = marketView.findViewById(R.id.searchClear);
        filterAll = (TextView) marketView.findViewById(R.id.filterAll);
        filterLauncher = (TextView) marketView.findViewById(R.id.filterLauncher);
        filterTool = (TextView) marketView.findViewById(R.id.filterTool);

        UiKit.prepareIcon(marketView, R.id.searchIcon, "search", MUTED);
        UiKit.prepareIcon(marketView, R.id.searchClearIcon, "close", MUTED);
        UiKit.prepareIcon(marketView, R.id.safetyIcon, "shield", GREEN);
        UiKit.prepareIcon(marketView, R.id.curationIcon, "plus", ACCENT);
        UiKit.prepareIcon(marketView, R.id.curationChevron, "chevron", 0xFF5E7FA6);

        marketView.findViewById(R.id.searchBox).setOnClickListener(v -> {
            searchInput.requestFocus();
            InputMethodManager keyboard = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                marketQuery = s == null ? "" : s.toString().trim();
                searchClear.setVisibility(TextUtils.isEmpty(marketQuery)
                        ? View.GONE : View.VISIBLE);
                renderMarket();
            }
        });
        searchClear.setOnClickListener(v -> searchInput.setText(""));

        filterAll.setOnClickListener(v -> setMarketFilter("all"));
        filterLauncher.setOnClickListener(v -> setMarketFilter("launcher"));
        filterTool.setOnClickListener(v -> setMarketFilter("tool"));

        View deviceCard = marketView.findViewById(R.id.marketDeviceCard);
        deviceCard.setOnClickListener(v -> {
            if (isConnected()) {
                switchTab(TAB_DEVICE);
            } else {
                openConnectSheet(ConnectSheet.MODE_WIRELESS);
            }
        });
        marketView.findViewById(R.id.marketCuration).setOnClickListener(
                v -> showCurationSheet());
    }

    private void setMarketFilter(String filter) {
        if (filter.equals(marketFilter)) {
            return;
        }
        marketFilter = filter;
        renderMarket();
    }

    private void renderMarket() {
        if (marketSections == null) {
            return;
        }
        applyFilterChip(filterAll, "全部 " + UiData.apps().size(), "all".equals(marketFilter));
        updateFilterLabels();
        updateMarketDeviceCard();

        marketSections.removeAllViews();
        List<UiData.App> visible = filteredApps();
        if (visible.isEmpty()) {
            marketSections.addView(buildEmptyMarket());
            return;
        }

        boolean searching = !TextUtils.isEmpty(marketQuery);
        if (searching) {
            addGroup(marketSections, "搜索结果",
                    "与“" + marketQuery + "”匹配的工具", visible);
        } else if ("tool".equals(marketFilter)) {
            addGroup(marketSections, "学习与专注",
                    "答题换电视时间，不改默认桌面", visible);
        } else {
            addGroupByIds(marketSections, "为你推荐",
                    "儿童桌面、去广告桌面与答题工具",
                    visible, new String[]{
                            Catalog.PLAN_YOUR_TV, Catalog.AERIAL, Catalog.STUDY_ON_TV
                    });
            addGroupByIds(marketSections, "更多选择",
                    "安静、轻量与独立包名分支",
                    visible, new String[]{Catalog.QUIET_TV, Catalog.HALO_TV});
            addGroupByIds(marketSections, "进阶安装",
                    "CleanYourTV 官方脚本或手机版 App",
                    visible, new String[]{Catalog.CLEAN_YOUR_TV});
        }
    }

    private void applyFilterChip(TextView view, String label, boolean active) {
        if (view == null) {
            return;
        }
        view.setText(label);
        view.setTextColor(active ? Color.WHITE : TEXT_2);
        view.setTypeface(Typeface.create("sans-serif", active ? Typeface.BOLD : Typeface.NORMAL));
        view.setBackgroundResource(active
                ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive);
    }

    private void updateFilterLabels() {
        int all = UiData.apps().size();
        int launchers = 0;
        int tools = 0;
        for (UiData.App app : UiData.apps()) {
            if (app.isTool()) {
                tools++;
            } else {
                launchers++;
            }
        }
        applyFilterChip(filterAll, "全部 " + all, "all".equals(marketFilter));
        applyFilterChip(filterLauncher, "桌面 " + launchers, "launcher".equals(marketFilter));
        applyFilterChip(filterTool, "工具 " + tools, "tool".equals(marketFilter));
    }

    private List<UiData.App> filteredApps() {
        List<UiData.App> result = new ArrayList<UiData.App>();
        String query = marketQuery.toLowerCase(Locale.getDefault());
        for (UiData.App app : UiData.apps()) {
            if ("launcher".equals(marketFilter) && app.isTool()) {
                continue;
            }
            if ("tool".equals(marketFilter) && !app.isTool()) {
                continue;
            }
            if (!query.isEmpty() && !matches(app, query)) {
                continue;
            }
            result.add(app);
        }
        return result;
    }

    private static boolean matches(UiData.App app, String query) {
        StringBuilder text = new StringBuilder();
        text.append(app.name()).append(' ')
                .append(app.tagline).append(' ')
                .append(app.desc).append(' ')
                .append(app.sourceShort).append(' ')
                .append(app.licenseNote).append(' ')
                .append(app.cardNote).append(' ')
                .append(app.isTool() ? "工具 答题 学习" : "桌面 启动器 launcher");
        for (String feature : app.features) {
            text.append(' ').append(feature);
        }
        return text.toString().toLowerCase(Locale.getDefault()).contains(query);
    }

    private void addGroup(LinearLayout parent, String title, String subtitle,
                          List<UiData.App> apps) {
        if (apps.isEmpty()) {
            return;
        }
        LinearLayout header = UiKit.sectionHeader(this, title, subtitle,
                apps.size() + " 个");
        UiKit.add(parent, header, 0, 16, 0, 9);
        for (UiData.App app : apps) {
            parent.addView(buildAppCard(app));
        }
    }

    private void addGroupByIds(LinearLayout parent, String title, String subtitle,
                               List<UiData.App> visible, String[] ids) {
        List<UiData.App> group = new ArrayList<UiData.App>();
        for (String id : ids) {
            for (UiData.App app : visible) {
                if (app.id().equals(id)) {
                    group.add(app);
                    break;
                }
            }
        }
        addGroup(parent, title, subtitle, group);
    }

    private View buildEmptyMarket() {
        LinearLayout card = UiKit.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(UiKit.icon(this, "search", 28, MUTED));
        TextView title = UiKit.title(this, "没有找到匹配工具");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, UiKit.dp(this, 10), 0, 0);
        card.addView(title);
        TextView caption = UiKit.caption(this, "试试“答题”“去广告”或“儿童桌面”，也可以清除筛选。");
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 10));
        card.addView(caption);
        TextView clear = UiKit.button(this, "清除筛选", UiKit.BTN_SECONDARY);
        clear.setOnClickListener(v -> {
            searchInput.setText("");
            marketFilter = "all";
            renderMarket();
        });
        card.addView(clear);
        return card;
    }

    private View buildAppCard(UiData.App app) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_app_card,
                marketSections, false);
        RoundedImageView logo = (RoundedImageView) card.findViewById(R.id.appLogo);
        logo.setCornerRadiusDp(14f);
        logo.setImageResource(MediaCatalog.icon(app.id()));
        logo.setContentDescription(app.name() + " 图标");
        ((TextView) card.findViewById(R.id.appName)).setText(app.name());
        ((TextView) card.findViewById(R.id.appTagline)).setText(app.tagline);
        ((TextView) card.findViewById(R.id.appDesc)).setText(app.desc);

        FlowLayout badges = (FlowLayout) card.findViewById(R.id.appBadges);
        badges.setGap(UiKit.dp(this, 5), UiKit.dp(this, 5));
        boolean installed = isAppInstalled(app.id());
        if (installed) {
            badges.addView(UiKit.badge(this, "已安装", UiData.TONE_GREEN));
        }
        if (isDefaultApp(app.id())) {
            badges.addView(UiKit.badge(this, "默认桌面", UiData.TONE_INFO));
        }
        if (app.isNew) {
            badges.addView(UiKit.badge(this, "新上架", UiData.TONE_INFO));
        }
        if (app.testBuild) {
            badges.addView(UiKit.badge(this, "测试版", UiData.TONE_WARN));
        }
        if (app.needsPc) {
            badges.addView(UiKit.badge(this, "手机版可装", UiData.TONE_INFO));
        }
        if (badges.getChildCount() == 0) {
            badges.setVisibility(View.GONE);
        }

        FlowLayout features = (FlowLayout) card.findViewById(R.id.appFeatures);
        features.setGap(UiKit.dp(this, 6), UiKit.dp(this, 6));
        for (String feature : app.features) {
            TextView chip = UiKit.text(this, feature, 10.5f, TEXT_2, true);
            chip.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 4),
                    UiKit.dp(this, 8), UiKit.dp(this, 4));
            chip.setBackground(UiKit.roundRect(0xFFF1F5FA, UiKit.dp(this, 7),
                    0xFFE6EBF2, 1));
            features.addView(chip);
        }

        TextView action = (TextView) card.findViewById(R.id.appAction);
        if (app.needsPc) {
            action.setText("教程");
            action.setBackgroundResource(R.drawable.bg_button_secondary);
            action.setTextColor(ACCENT);
        } else if (installed) {
            action.setText("管理");
            action.setBackgroundResource(R.drawable.bg_button_secondary);
            action.setTextColor(ACCENT);
        } else {
            action.setText("安装");
            action.setBackgroundResource(R.drawable.bg_button_primary);
            action.setTextColor(Color.WHITE);
        }

        LinearLayout note = (LinearLayout) card.findViewById(R.id.appNote);
        if (!TextUtils.isEmpty(app.cardNote)) {
            if (app.noteTone == UiData.TONE_WARN || app.noteTone == UiData.TONE_RED) {
                note.addView(UiKit.note(this, null, app.cardNote, app.noteTone));
            } else {
                LinearLayout info = UiKit.row(this);
                info.setGravity(Gravity.TOP);
                LineIconView icon = UiKit.icon(this, "info", 14, MUTED);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        UiKit.dp(this, 14), UiKit.dp(this, 14));
                iconParams.topMargin = UiKit.dp(this, 1);
                iconParams.rightMargin = UiKit.dp(this, 5);
                info.addView(icon, iconParams);
                TextView copy = UiKit.caption(this, app.cardNote);
                copy.setMaxLines(2);
                info.addView(copy, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                note.addView(info);
            }
        } else {
            note.setVisibility(View.GONE);
        }

        final UiData.App target = app;
        View.OnClickListener open = v -> openAppDetail(target);
        card.setOnClickListener(open);
        action.setOnClickListener(open);
        card.setBackground(UiKit.ripple(Color.WHITE, UiKit.dp(this, 20)));
        return card;
    }

    private void updateMarketDeviceCard() {
        View card = marketView.findViewById(R.id.marketDeviceCard);
        if (card == null) {
            return;
        }
        View dot = marketView.findViewById(R.id.marketDeviceDot);
        TextView status = (TextView) marketView.findViewById(R.id.marketDeviceStatus);
        TextView name = (TextView) marketView.findViewById(R.id.marketDeviceName);
        TextView meta = (TextView) marketView.findViewById(R.id.marketDeviceMeta);
        TextView cta = (TextView) marketView.findViewById(R.id.marketDeviceCta);
        LineIconView icon = (LineIconView) marketView.findViewById(R.id.marketDeviceIcon);

        if (isConnected()) {
            card.setBackgroundResource(R.drawable.bg_device_online);
            dot.setBackgroundResource(R.drawable.bg_badge_green);
            status.setText("已连接");
            status.setTextColor(GREEN);
            name.setText(device.optString("label", "已连接电视"));
            String home = defaultHomeLabel();
            meta.setText("Android " + device.optString("android", "?") + " · "
                    + ("classic".equals(device.optString("transport")) ? "网络 ADB" : "无线调试")
                    + " · 默认 " + home);
            cta.setText("查看");
            icon.setIconColor(ACCENT);
        } else {
            card.setBackgroundResource(R.drawable.bg_device_offline);
            dot.setBackgroundResource(R.drawable.bg_badge_amber);
            status.setText("未连接电视");
            status.setTextColor(AMBER);
            name.setText("连接你的电视");
            meta.setText("同一 Wi-Fi · 自动发现或输入 IP");
            cta.setText("去准备");
            icon.setIconColor(AMBER);
        }
    }

    // ---------------------------------------------------------------------
    // Device screen
    // ---------------------------------------------------------------------

    private void renderDevice() {
        if (deviceContent == null) {
            return;
        }
        deviceContent.removeAllViews();
        deviceContent.addView(deviceHeader());
        if (isConnected()) {
            deviceContent.addView(buildConnectedHero());
            deviceContent.addView(buildHomeCard());
            addInstalledSection(deviceContent);
            UiKit.add(deviceContent, UiKit.note(this, "卸载会自动复原",
                    "卸载桌面应用时，会先按官方流程切回原桌面、恢复被改动的系统设置，"
                            + "再移除应用。系统桌面和系统应用永远不会被卸载或停用。",
                    UiData.TONE_INFO), 0, 14, 0, 0);
        } else {
            addDisconnectedContent(deviceContent);
        }
    }

    private View deviceHeader() {
        LinearLayout row = UiKit.row(this);
        row.setPadding(0, UiKit.dp(this, 2), 0, 0);
        LinearLayout copy = UiKit.column(this);
        copy.addView(UiKit.text(this, "我的电视", 18f, TEXT, true));
        TextView sub = UiKit.caption(this, isConnected()
                ? "已授权调试 · 安装与卸载均在本机完成"
                : "先准备电视，再连接与安装");
        sub.setPadding(0, UiKit.dp(this, 3), 0, 0);
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (isConnected()) {
            FrameLayout refresh = new FrameLayout(this);
            refresh.setBackgroundResource(R.drawable.bg_icon_button);
            refresh.setClickable(true);
            refresh.setFocusable(true);
            refresh.setContentDescription("刷新电视状态");
            LineIconView icon = UiKit.icon(this, "refresh", 18, TEXT_2);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    UiKit.dp(this, 18), UiKit.dp(this, 18));
            iconParams.gravity = Gravity.CENTER;
            refresh.addView(icon, iconParams);
            refresh.setOnClickListener(v -> {
                refresh.setEnabled(false);
                refresh.setAlpha(0.5f);
                refreshDevice(() -> {
                    refresh.setEnabled(true);
                    refresh.setAlpha(1f);
                });
            });
            row.addView(refresh, new LinearLayout.LayoutParams(
                    UiKit.dp(this, 38), UiKit.dp(this, 38)));
        }
        return row;
    }

    private void addDisconnectedContent(LinearLayout root) {
        LinearLayout hero = UiKit.column(this);
        hero.setBackgroundResource(R.drawable.bg_device_offline);
        hero.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 16),
                UiKit.dp(this, 16), UiKit.dp(this, 16));
        LinearLayout statusRow = UiKit.row(this);
        statusRow.addView(UiKit.dot(this, AMBER, 7));
        TextView status = UiKit.text(this, "未连接", 10.5f, AMBER, true);
        status.setPadding(UiKit.dp(this, 6), 0, 0, 0);
        statusRow.addView(status);
        hero.addView(statusRow);
        TextView title = UiKit.text(this, "把电视准备好，再开始安装", 20f, TEXT, true);
        title.setPadding(0, UiKit.dp(this, 9), 0, 0);
        hero.addView(title);
        TextView copy = UiKit.text(this,
                "优先使用无线调试。整个过程只连接家庭局域网，不经过云端服务器。",
                12.5f, TEXT_2, false);
        copy.setLineSpacing(UiKit.dp(this, 3), 1f);
        copy.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 13));
        hero.addView(copy);

        TextView wireless = UiKit.button(this, "无线连接电视", UiKit.BTN_PRIMARY);
        wireless.setOnClickListener(v -> openConnectSheet(ConnectSheet.MODE_WIRELESS));
        hero.addView(wireless, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));
        TextView guide = UiKit.button(this, "USB / 网络 ADB 排障指引", UiKit.BTN_SECONDARY);
        guide.setOnClickListener(v -> openConnectSheet(ConnectSheet.MODE_USB));
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44));
        guideParams.topMargin = UiKit.dp(this, 8);
        hero.addView(guide, guideParams);
        UiKit.add(root, hero, 0, 14, 0, 0);

        if (lastTv != null) {
            UiKit.add(root, buildLastTvCard(), 0, 10, 0, 0);
        }

        LinearLayout prep = UiKit.column(this);
        prep.addView(UiKit.text(this, "连接前的 3 项准备", 15f, TEXT, true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        prep.addView(stepCard("1", "wifi", "同一 Wi-Fi",
                "手机与电视连接同一个家庭网络。访客网络或 AP 隔离会阻断发现。"));
        prep.addView(stepCard("2", "sliders", "打开无线调试",
                "Android 11+：设置 → 系统 → 开发者选项 → 无线调试。老电视用 USB 调试配合电脑准备。"));
        prep.addView(stepCard("3", "link", "配对并在电视上允许",
                "配对码每次都会变化。连接后电视会弹出调试授权，请点“允许”。"));
        UiKit.add(root, prep, 0, 16, 0, 0);

        UiKit.add(root, UiKit.note(this, "只走家庭局域网",
                "不要做 5555 端口映射，也不要在公共 Wi-Fi 上暴露 ADB。工具市场不会把电视地址上传。",
                UiData.TONE_INFO), 0, 14, 0, 0);
    }

    private View stepCard(String number, String icon, String title, String desc) {
        LinearLayout card = UiKit.row(this);
        card.setGravity(Gravity.TOP);
        card.setBackground(UiKit.roundRect(0xFFFFFFFF, UiKit.dp(this, 18),
                0xFFE6EBF2, 1));
        card.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 12),
                UiKit.dp(this, 12), UiKit.dp(this, 12));
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(UiKit.roundRect(0xFFEDF5FF, UiKit.dp(this, 11), 0, 0));
        LineIconView iconView = UiKit.icon(this, icon, 18, ACCENT);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                UiKit.dp(this, 18), UiKit.dp(this, 18));
        iconParams.gravity = Gravity.CENTER;
        tile.addView(iconView, iconParams);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                UiKit.dp(this, 34), UiKit.dp(this, 34));
        card.addView(tile, tileParams);
        LinearLayout copy = UiKit.column(this);
        TextView heading = UiKit.text(this, number + "  " + title, 13f, TEXT, true);
        copy.addView(heading);
        TextView body = UiKit.caption(this, desc);
        body.setPadding(0, UiKit.dp(this, 4), 0, 0);
        copy.addView(body);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(this, 10);
        card.addView(copy, copyParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = UiKit.dp(this, 8);
        card.setLayoutParams(params);
        return card;
    }

    private View buildLastTvCard() {
        LinearLayout card = UiKit.card(this);
        LinearLayout row = UiKit.row(this);
        row.addView(UiKit.icon(this, "tv", 18, MUTED));
        LinearLayout copy = UiKit.column(this);
        copy.addView(UiKit.text(this, "上次连接过 " + lastTv.optString("label", "电视"),
                12.5f, TEXT, true));
        String ip = lastTv.optString("ip", "");
        int port = lastTv.optInt("port", 0);
        String seen = lastTv.optLong("lastSeen", 0) > 0
                ? " · " + new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(lastTv.optLong("lastSeen", 0))) : "";
        copy.addView(UiKit.caption(this, ip + (port > 0 ? ":" + port : "")
                + (TextUtils.isEmpty(seen) ? "" : seen)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = UiKit.dp(this, 8);
        row.addView(copy, params);
        TextView use = UiKit.smallButton(this, "填入地址", UiKit.BTN_SECONDARY);
        use.setOnClickListener(v -> openConnectSheet(
                "classic".equals(lastTv.optString("transport"))
                        ? ConnectSheet.MODE_CLASSIC : ConnectSheet.MODE_WIRELESS));
        row.addView(use);
        card.addView(row);
        return card;
    }

    private View buildConnectedHero() {
        LinearLayout card = UiKit.column(this);
        card.setBackgroundResource(R.drawable.bg_device_online);
        card.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 14),
                UiKit.dp(this, 14), UiKit.dp(this, 14));

        LinearLayout top = UiKit.row(this);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(UiKit.roundRect(0xFFEAF2FF, UiKit.dp(this, 17), 0, 0));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                UiKit.dp(this, 24), UiKit.dp(this, 24));
        iconParams.gravity = Gravity.CENTER;
        tile.addView(UiKit.icon(this, "tv", 24, ACCENT), iconParams);
        top.addView(tile, new LinearLayout.LayoutParams(
                UiKit.dp(this, 50), UiKit.dp(this, 50)));

        LinearLayout copy = UiKit.column(this);
        copy.addView(UiKit.text(this, device.optString("label", "电视"), 18f, TEXT, true));
        String model = device.optString("model", "");
        String brand = device.optString("brand", "");
        String modelLine = TextUtils.isEmpty(brand) || model.toLowerCase(Locale.US)
                .contains(brand.toLowerCase(Locale.US))
                ? model : brand + " " + model;
        copy.addView(UiKit.caption(this, (TextUtils.isEmpty(modelLine) ? "Android TV" : modelLine)
                + " · Android " + device.optString("android", "?")));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(this, 11);
        top.addView(copy, copyParams);
        top.addView(UiKit.badge(this, "已连接", UiData.TONE_GREEN));
        card.addView(top);

        View divider = UiKit.divider(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dp(this, 0.8f)));
        dividerParams.topMargin = UiKit.dp(this, 12);
        dividerParams.bottomMargin = UiKit.dp(this, 4);
        card.addView(divider, dividerParams);

        card.addView(UiKit.kv(this, "连接方式",
                "classic".equals(device.optString("transport"))
                        ? "网络 ADB" : "无线调试", false));
        card.addView(UiKit.kv(this, "响应", device.optString("latency", "—"), false));
        card.addView(UiKit.kv(this, "调试序列号", device.optString("serial", "—"), true));

        LinearLayout actions = UiKit.row(this);
        TextView refresh = UiKit.smallButton(this, "刷新状态", UiKit.BTN_SECONDARY);
        refresh.setOnClickListener(v -> {
            refresh.setEnabled(false);
            refresh.setText("刷新中…");
            refreshDevice(() -> {
                refresh.setEnabled(true);
                refresh.setText("刷新状态");
            });
        });
        actions.addView(refresh);
        TextView disconnect = UiKit.smallButton(this, "断开连接", UiKit.BTN_SECONDARY);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.leftMargin = UiKit.dp(this, 8);
        actions.addView(disconnect, dp);
        disconnect.setOnClickListener(v -> confirmDisconnect());
        UiKit.add(card, actions, 0, 10, 0, 0);
        return card;
    }

    private View buildHomeCard() {
        LinearLayout card = UiKit.card(this);
        LinearLayout header = UiKit.row(this);
        header.addView(UiKit.icon(this, "home", 18, TEXT_2));
        TextView title = UiKit.text(this, "当前默认桌面", 13f, TEXT, true);
        title.setPadding(UiKit.dp(this, 7), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String appId = device.optString("defaultHomeApp", "stock");
        UiData.App homeApp = UiData.app(appId);
        if (homeApp != null) {
            header.addView(UiKit.badge(this, "市场收录", UiData.TONE_INFO));
        }
        card.addView(header);

        String homeName;
        String homePackage = device.optString("defaultHomePackage", "");
        if (homeApp != null) {
            homeName = homeApp.name();
        } else if ("stock".equals(appId)) {
            homeName = device.optString("stockLabel", "电视原桌面");
        } else {
            homeName = TextUtils.isEmpty(homePackage) ? "系统默认桌面" : homePackage;
        }
        TextView value = UiKit.text(this, homeName, 17f, TEXT, true);
        value.setPadding(0, UiKit.dp(this, 10), 0, 0);
        card.addView(value);
        TextView meta = UiKit.caption(this, TextUtils.isEmpty(homePackage)
                ? "已由电视系统确认" : homePackage);
        meta.setPadding(0, UiKit.dp(this, 3), 0, 0);
        card.addView(meta);

        if ("stock".equals(appId)) {
            UiKit.add(card, UiKit.note(this, "原桌面未被改动",
                    "工具市场没有替换、停用或卸载电视自带的桌面。",
                    UiData.TONE_GREEN), 0, 10, 0, 0);
        } else {
            TextView stock = UiKit.caption(this, "安装前记录的桌面：" + device.optString(
                    "stockLabel", "电视原桌面"));
            stock.setPadding(0, UiKit.dp(this, 7), 0, 0);
            card.addView(stock);
            if (homeApp != null) {
                UiKit.add(card, UiKit.note(this, "这是本市场收录的桌面",
                        "卸载时会先按官方流程切回原桌面、恢复系统设置，再移除它；"
                                + "不会让主页留空。也可以只切回原桌面，保留应用。",
                        UiData.TONE_INFO), 0, 10, 0, 0);
            }
            LinearLayout actions = UiKit.row(this);
            if (homeApp != null) {
                TextView manage = UiKit.smallButton(this, "管理此应用", UiKit.BTN_SECONDARY);
                manage.setOnClickListener(v -> openAppDetail(homeApp));
                actions.addView(manage);
            }
            TextView restore = UiKit.smallButton(this, "切回原桌面", UiKit.BTN_SECONDARY);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.leftMargin = UiKit.dp(this, 8);
            actions.addView(restore, p);
            restore.setOnClickListener(v -> confirmRestoreHome());
            UiKit.add(card, actions, 0, 10, 0, 0);
        }
        return card;
    }

    private void addInstalledSection(LinearLayout root) {
        JSONArray installed = device == null ? null : device.optJSONArray("installed");
        int count = installed == null ? 0 : installed.length();
        LinearLayout header = UiKit.sectionHeader(this, "已安装工具",
                count == 0 ? "电视上还没有市场收录的工具" : "打开、管理或恢复到原桌面",
                count + " 个");
        UiKit.add(root, header, 0, 16, 0, 9);

        if (count == 0) {
            LinearLayout empty = UiKit.card(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(UiKit.icon(this, "download", 26, MUTED));
            TextView title = UiKit.text(this, "还没有安装工具", 13f, TEXT, true);
            title.setPadding(0, UiKit.dp(this, 8), 0, 0);
            empty.addView(title);
            TextView copy = UiKit.caption(this, "连接已就绪，可以去市场选一个适合电视的工具。");
            copy.setPadding(0, UiKit.dp(this, 3), 0, UiKit.dp(this, 9));
            copy.setGravity(Gravity.CENTER);
            empty.addView(copy);
            TextView go = UiKit.button(this, "去工具市场", UiKit.BTN_PRIMARY);
            go.setOnClickListener(v -> switchTab(TAB_MARKET));
            empty.addView(go);
            UiKit.add(root, empty, 0, 0, 0, 0);
            return;
        }

        for (int i = 0; i < installed.length(); i++) {
            JSONObject entry = installed.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String appId = entry.optString("appId", "");
            UiData.App app = UiData.app(appId);
            if (app == null) {
                continue;
            }
            UiKit.add(root, buildInstalledRow(app, entry), 0, 0, 0, 8);
        }
    }

    private View buildInstalledRow(final UiData.App app, final JSONObject entry) {
        LinearLayout card = UiKit.card(this);
        LinearLayout row = UiKit.row(this);
        RoundedImageView logo = new RoundedImageView(this);
        logo.setCornerRadiusDp(11f);
        logo.setImageResource(MediaCatalog.icon(app.id()));
        logo.setContentDescription(app.name() + " 图标");
        row.addView(logo, new LinearLayout.LayoutParams(
                UiKit.dp(this, 40), UiKit.dp(this, 40)));
        LinearLayout copy = UiKit.column(this);
        String variant = variantLabel(app, entry.optString("variantId", ""));
        copy.addView(UiKit.text(this, app.name() + " · " + variant, 13f, TEXT, true));
        String version = entry.optString("versionName", "");
        copy.addView(UiKit.caption(this, TextUtils.isEmpty(version)
                ? entry.optString("packageName", "")
                : "版本 " + version + " · " + entry.optString("packageName", "")));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(this, 10);
        row.addView(copy, copyParams);
        if (isDefaultPackage(entry.optString("packageName", ""))) {
            row.addView(UiKit.badge(this, "默认桌面", UiData.TONE_INFO));
        }
        card.addView(row);

        String packageName = entry.optString("packageName", "");
        boolean isDefault = isDefaultPackage(packageName);
        if (!app.needsPc) {
            TextView uninstall = UiKit.button(this, "卸载", UiKit.BTN_DANGER);
            uninstall.setOnClickListener(v -> openAppDetail(app, true));
            UiKit.add(card, uninstall, 0, 10, 0, 0);
            TextView hint = UiKit.caption(this, app.isTool()
                    ? "会删除题库、规则和使用记录；不会更改默认桌面。"
                    : isDefault
                    ? "会先按官方流程切回原桌面、恢复系统设置，再移除应用和数据。"
                    : "会先按官方流程撤销通知监听、无障碍等设置，再移除应用和数据。");
            hint.setPadding(0, UiKit.dp(this, 6), 0, 0);
            card.addView(hint);
        } else {
            TextView hint = UiKit.caption(this,
                    "CleanYourTV 会停用系统项。请先在电脑上运行官方 restore.sh，"
                            + "再按官方说明卸载。");
            hint.setPadding(0, UiKit.dp(this, 6), 0, 0);
            card.addView(hint);
        }
        card.setOnClickListener(v -> openAppDetail(app));
        return card;
    }

    private String variantLabel(UiData.App app, String variantId) {
        UiData.Variant variant = app.variant(variantId);
        return variant == null ? "已安装" : variant.label;
    }

    private String defaultHomeLabel() {
        if (device == null) {
            return "—";
        }
        String appId = device.optString("defaultHomeApp", "stock");
        UiData.App app = UiData.app(appId);
        if (app != null) {
            return app.name();
        }
        return device.optString("stockLabel", "原桌面");
    }

    // ---------------------------------------------------------------------
    // ADB actions exposed to sheets
    // ---------------------------------------------------------------------

    boolean isConnected() {
        return device != null && device.optBoolean("authorized", false);
    }

    List<JSONObject> installedFor(String appId) {
        List<JSONObject> result = new ArrayList<JSONObject>();
        if (device == null || TextUtils.isEmpty(appId)) {
            return result;
        }
        JSONArray installed = device.optJSONArray("installed");
        if (installed == null) {
            return result;
        }
        for (int i = 0; i < installed.length(); i++) {
            JSONObject entry = installed.optJSONObject(i);
            if (entry != null && appId.equals(entry.optString("appId", ""))) {
                result.add(entry);
            }
        }
        return result;
    }

    boolean isAppInstalled(String appId) {
        return !installedFor(appId).isEmpty();
    }

    boolean isPackageInstalled(String packageName) {
        if (device == null || TextUtils.isEmpty(packageName)) {
            return false;
        }
        JSONArray installed = device.optJSONArray("installed");
        if (installed == null) {
            return false;
        }
        for (int i = 0; i < installed.length(); i++) {
            JSONObject entry = installed.optJSONObject(i);
            if (entry != null && packageName.equals(entry.optString("packageName", ""))) {
                return true;
            }
        }
        return false;
    }

    boolean isDefaultPackage(String packageName) {
        return device != null && !TextUtils.isEmpty(packageName)
                && packageName.equals(device.optString("defaultHomePackage", ""));
    }

    boolean isDefaultApp(String appId) {
        return device != null && !TextUtils.isEmpty(appId)
                && appId.equals(device.optString("defaultHomeApp", ""));
    }

    void attachDevice(JSONObject info, String host, int port, String transport) {
        device = info;
        devHost = host == null ? "" : host;
        devPort = port;
        devTransport = transport == null ? "" : transport;
        navDeviceDot.setVisibility(View.VISIBLE);
        renderMarket();
        renderDevice();
        if (detailPage != null) {
            detailPage.onDeviceStateChanged();
        }
    }

    void clearDevice() {
        device = null;
        devHost = "";
        devPort = 0;
        devTransport = "";
        navDeviceDot.setVisibility(View.GONE);
        renderMarket();
        renderDevice();
        brandSubtitle.setText("我的电视 · 全部操作在本机完成");
        if (detailPage != null) {
            detailPage.onDeviceStateChanged();
        }
    }

    void refreshDevice(final Runnable after) {
        if (!isConnected() || TextUtils.isEmpty(devHost)) {
            if (after != null) {
                main.post(after);
            }
            return;
        }
        if (after != null) {
            refreshCallbacks.add(after);
        }
        if (refreshInFlight) {
            return;
        }
        refreshInFlight = true;
        final String host = devHost;
        final int port = devPort;
        final String transport = devTransport;
        executor.execute(() -> {
            JSONObject info = null;
            Exception failure = null;
            try {
                DeviceSession session = new DeviceSession(this, null);
                info = session.inspect(host, port, transport);
            } catch (Exception error) {
                failure = error;
            }
            final JSONObject result = info;
            final Exception error = failure;
            main.post(() -> {
                refreshInFlight = false;
                if (error != null) {
                    toast("刷新失败：" + safe(error));
                } else if (result != null && result.optBoolean("authorized", false)) {
                    attachDevice(result, host, port, transport);
                } else {
                    clearDevice();
                    toast("调试授权已失效，请重新连接电视。");
                }
                List<Runnable> callbacks = new ArrayList<Runnable>(refreshCallbacks);
                refreshCallbacks.clear();
                for (Runnable callback : callbacks) {
                    callback.run();
                }
            });
        });
    }

    void openConnectSheet(String mode) {
        new ConnectSheet(this, mode).show();
    }

    void openOfficial(UiData.App app) {
        String url = UiData.officialSite(app.id());
        if (TextUtils.isEmpty(url)) {
            toast("官方地址暂不可用。");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            toast("没有可用的浏览器打开官方页面。");
        } catch (Throwable error) {
            toast("无法打开官方页面。");
        }
    }

    void toast(String message) {
        if (finishing || TextUtils.isEmpty(message)) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void confirmDisconnect() {
        NativeDialogs.confirm(this, "断开这台电视？",
                "只会断开本机与电视的 ADB 连接，不会卸载任何应用，也不会改变默认桌面。",
                "断开", this::disconnectDevice);
    }

    private void disconnectDevice() {
        if (!isConnected()) {
            clearDevice();
            return;
        }
        final String host = devHost;
        final int port = devPort;
        executor.execute(() -> {
            Exception failure = null;
            try {
                DeviceSession session = new DeviceSession(this, null);
                session.disconnect(host, port);
            } catch (Exception error) {
                failure = error;
            }
            final Exception error = failure;
            main.post(() -> {
                if (error != null) {
                    toast("断开没有完成：" + safe(error));
                } else {
                    clearDevice();
                    toast("已断开电视连接。");
                }
            });
        });
    }

    void confirmRestoreHome() {
        if (restoreInFlight || !isConnected()) {
            return;
        }
        NativeDialogs.confirm(this, "切回安装前的桌面？",
                "会把默认 HOME 切回安装开始前记录的桌面。如果电视没有立即确认，"
                        + "本应用会提示你按主页键手动选择，不会假装成功。",
                "切回", () -> {
                    restoreInFlight = true;
                    toast("正在切回原桌面…");
                    final String host = devHost;
                    final int port = devPort;
                    executor.execute(() -> {
                        JSONObject result = null;
                        Exception failure = null;
                        try {
                            DeviceSession session = new DeviceSession(this, null);
                            result = session.restoreHome(host, port);
                        } catch (Exception error) {
                            failure = error;
                        }
                        final JSONObject restored = result;
                        final Exception error = failure;
                        main.post(() -> {
                            restoreInFlight = false;
                            if (error != null) {
                                NativeDialogs.message(MainActivity.this,
                                        "没有切回", safe(error));
                                return;
                            }
                            if (restored.optBoolean("openedChooser", false)) {
                                NativeDialogs.message(MainActivity.this,
                                        "请在电视上选择原桌面",
                                        restored.optString("message",
                                                "请在电视上按主页键，选择原来的桌面。"));
                            } else if (restored.optBoolean("confirmed", false)) {
                                toast("已切回 " + restored.optString("label", "原桌面"));
                            } else {
                                NativeDialogs.message(MainActivity.this,
                                        "还没有确认切换",
                                        restored.optString("warning",
                                                "系统没有立即确认。请按电视主页键，选择原来的桌面。"));
                            }
                            refreshDevice(null);
                        });
                    });
                });
    }

    // ---------------------------------------------------------------------
    // Settings / guides
    // ---------------------------------------------------------------------

    private void showSettingsSheet() {
        final SheetPanel sheet = SheetPanel.show(this, "设置与说明",
                "纯原生 Android · 全部操作在本机完成");
        final LinearLayout root = UiKit.column(this);

        LinearLayout about = UiKit.row(this);
        LogoView logo = new LogoView(this);
        logo.setShellLogo();
        about.addView(logo, new LinearLayout.LayoutParams(
                UiKit.dp(this, 42), UiKit.dp(this, 42)));
        LinearLayout copy = UiKit.column(this);
        copy.addView(UiKit.text(this, "工具市场", 14f, TEXT, true));
        copy.addView(UiKit.caption(this, "版本 " + versionName()
                + " · 纯原生 Java + XML"));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(this, 10);
        about.addView(copy, copyParams);
        root.addView(about);

        UiKit.add(root, UiKit.note(this, "安全边界",
                "只连接家庭局域网；不卸载系统应用；不自动开启无障碍、设备管理员、悬浮窗或使用情况访问。",
                UiData.TONE_GREEN), 0, 12, 0, 0);

        LinearLayout group = UiKit.column(this);
        group.setBackgroundResource(R.drawable.bg_card);
        group.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 4),
                UiKit.dp(this, 4), UiKit.dp(this, 4));

        addSettingRow(group, "wifi", "连接 / 更换电视",
                "无线调试配对、网络 ADB 与局域网发现", () -> {
                    sheet.dismiss();
                    openConnectSheet(ConnectSheet.MODE_WIRELESS);
                });
        addSettingRow(group, "usb", "USB 调试指引",
                "Android 11+ 与老电视的准备步骤", () -> {
                    sheet.dismiss();
                    openConnectSheet(ConnectSheet.MODE_USB);
                });
        addSettingRow(group, "shield", "权限与隐私",
                "本应用申请什么、不会做什么", () -> {
                    sheet.dismiss();
                    showPrivacySheet();
                });
        addSettingRow(group, "book", "推荐新工具",
                "收录标准与提交所需信息", () -> {
                    sheet.dismiss();
                    showCurationSheet();
                });
        addSettingRow(group, "trash", "清理安装缓存",
                "只删除本机已下载的临时安装包", this::clearInstallCache);
        if (isConnected()) {
            addSettingRow(group, "power", "断开当前电视",
                    "不会卸载应用或改变默认桌面", () -> {
                        sheet.dismiss();
                        confirmDisconnect();
                    });
        }
        UiKit.add(root, group, 0, 12, 0, 0);

        TextView footer = UiKit.caption(this,
                "内置 ADB 运行时来自 Termux 官方 android-tools；许可证文本随 APK 打包在 assets/licenses/。"
                        + "电视地址仅保存在本机，不上传。");
        footer.setPadding(0, UiKit.dp(this, 12), 0, 0);
        root.addView(footer);
        sheet.add(root);
    }

    private void addSettingRow(LinearLayout parent, String iconName, String title,
                               String subtitle, final Runnable action) {
        if (parent.getChildCount() > 0) {
            parent.addView(UiKit.divider(this));
        }
        LinearLayout row = UiKit.row(this);
        row.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 11),
                UiKit.dp(this, 10), UiKit.dp(this, 11));
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(UiKit.roundRect(0xFFF1F5FA, UiKit.dp(this, 9), 0, 0));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                UiKit.dp(this, 17), UiKit.dp(this, 17));
        iconParams.gravity = Gravity.CENTER;
        tile.addView(UiKit.icon(this, iconName, 17, TEXT_2), iconParams);
        row.addView(tile, new LinearLayout.LayoutParams(
                UiKit.dp(this, 32), UiKit.dp(this, 32)));
        LinearLayout copy = UiKit.column(this);
        copy.addView(UiKit.text(this, title, 12.5f, TEXT, true));
        TextView sub = UiKit.caption(this, subtitle);
        sub.setPadding(0, UiKit.dp(this, 2), 0, 0);
        copy.addView(sub);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(this, 10);
        row.addView(copy, copyParams);
        row.addView(UiKit.icon(this, "chevron", 15, 0xFFB0BAC7));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(UiKit.ripple(0xFFFFFFFF, UiKit.dp(this, 14)));
        row.setOnClickListener(v -> action.run());
        parent.addView(row);
    }

    private void showPrivacySheet() {
        SheetPanel sheet = SheetPanel.show(this, "权限与隐私",
                "只用必要权限，不收集电视数据");
        LinearLayout root = UiKit.column(this);
        addSheetSection(root, "本应用申请");
        LinearLayout perms = UiKit.card(this);
        perms.addView(permissionRow("网络访问", "INTERNET · 下载官方安装包并连接局域网 ADB"));
        perms.addView(UiKit.divider(this));
        perms.addView(permissionRow("网络状态", "ACCESS_NETWORK_STATE · 判断当前是否在局域网"));
        UiKit.add(root, perms, 0, 8, 0, 0);
        UiKit.add(root, UiKit.note(this, "没有申请的权限",
                "不申请存储、位置、通讯录、电话、相机、麦克风、无障碍或悬浮窗。"
                        + "电视端权限必须由你在电视系统设置里手动开启。",
                UiData.TONE_GREEN), 0, 10, 0, 0);

        addSheetSection(root, "数据留在本机");
        UiKit.add(root, UiKit.note(this, "不上传",
                "没有账号、云同步、埋点或远程日志。上次连接的电视地址保存在本机设置中，"
                        + "用于下次预填；配对码从不保存，也不写入日志。",
                UiData.TONE_INFO), 0, 8, 0, 0);
        addSheetSection(root, "运行时来源");
        UiKit.add(root, UiKit.note(this, "Termux ADB 运行时",
                "内置的 adb 客户端与共享库来自 Termux 官方 android-tools 包，"
                        + "随 APK 分发许可证与 NOTICE。工具市场不上传命令输出。",
                UiData.TONE_INFO), 0, 8, 0, 0);
        sheet.add(root);
    }

    private View permissionRow(String title, String body) {
        LinearLayout row = UiKit.column(this);
        row.setPadding(0, UiKit.dp(this, 9), 0, UiKit.dp(this, 9));
        row.addView(UiKit.text(this, title, 12.5f, TEXT, true));
        TextView copy = UiKit.caption(this, body);
        copy.setPadding(0, UiKit.dp(this, 3), 0, 0);
        row.addView(copy);
        return row;
    }

    private void showCurationSheet() {
        SheetPanel sheet = SheetPanel.show(this, "推荐新工具",
                "好工具经人工审核后收录上架");
        LinearLayout root = UiKit.column(this);
        UiKit.add(root, UiKit.note(this, "先审核，再上架",
                "本版本不提供在线提交表单，也不收集你的联系方式。请通过项目仓库或站长公开渠道提交推荐。",
                UiData.TONE_INFO), 0, 4, 0, 0);
        addSheetSection(root, "收录标准");
        LinearLayout standards = UiKit.card(this);
        standards.addView(curationItem("官方来源可验证",
                "提供官方仓库 / Release / 官网，不接受网盘转载包。"));
        standards.addView(UiKit.divider(this));
        standards.addView(curationItem("安装包可校验",
                "包名、版本和签名证书可以在安装前固定。"));
        standards.addView(UiKit.divider(this));
        standards.addView(curationItem("不破坏系统",
                "不卸载 / 停用系统应用，有清楚的恢复与卸载方式。"));
        standards.addView(UiKit.divider(this));
        standards.addView(curationItem("权限说明清楚",
                "涉及无障碍、设备管理员、悬浮窗时必须逐项说明。"));
        UiKit.add(root, standards, 0, 8, 0, 0);
        addSheetSection(root, "请提供");
        UiKit.add(root, UiKit.note(this, "推荐信息",
                "工具名称 · 官方来源 · 适用系统 · 是否参与默认桌面 · 需要的电视端权限 · "
                        + "卸载 / 恢复方法 · 特色亮点。",
                UiData.TONE_WARN), 0, 8, 0, 0);
        sheet.add(root);
    }

    private View curationItem(String title, String body) {
        LinearLayout row = UiKit.row(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, UiKit.dp(this, 9), 0, UiKit.dp(this, 9));
        row.addView(UiKit.icon(this, "check", 15, GREEN));
        LinearLayout copy = UiKit.column(this);
        copy.addView(UiKit.text(this, title, 12f, TEXT, true));
        TextView desc = UiKit.caption(this, body);
        desc.setPadding(0, UiKit.dp(this, 2), 0, 0);
        copy.addView(desc);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = UiKit.dp(this, 8);
        row.addView(copy, params);
        return row;
    }

    private void addSheetSection(LinearLayout root, String title) {
        TextView header = UiKit.text(this, title, 14f, TEXT, true);
        header.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 7));
        root.addView(header);
    }

    private void clearInstallCache() {
        if (ApkManager.isBusy()) {
            toast("有安装任务正在进行，请完成后再清理缓存。");
            return;
        }
        ApkManager.cleanup(this);
        toast("本机安装缓存已清理。电视上的应用不受影响。");
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "0.1.0";
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && focus != null) {
            keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
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

    @Override
    public void onBackPressed() {
        if (finishing) {
            super.onBackPressed();
            return;
        }
        if (detailPage != null && detailPage.isShowing()) {
            detailPage.requestClose();
            return;
        }
        if (currentTab == TAB_DEVICE) {
            switchTab(TAB_MARKET);
            return;
        }
        if (!TextUtils.isEmpty(marketQuery)) {
            searchInput.setText("");
            return;
        }
        finishing = true;
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from the system installer or the "install unknown apps"
        // settings page should refresh the phone-app section truthfully.
        if (detailPage != null && detailPage.isShowing()) {
            detailPage.onDeviceStateChanged();
        }
    }

    @Override
    protected void onDestroy() {
        finishing = true;
        if (detailPage != null) {
            detailPage.closeImmediately();
            detailPage = null;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }
}