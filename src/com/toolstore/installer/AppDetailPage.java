package com.toolstore.installer;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Full-screen application detail page: official artwork, description, install
 * options and a fixed bottom action bar. Operations keep using SheetPanel for
 * progress/result so the safety and truthfulness of the old detail sheet are
 * preserved without duplicating the ADB layer.
 */
final class AppDetailPage {

    private static final String OP_INSTALL = "install";
    private static final String OP_UNINSTALL = "uninstall";
    private static final String OP_HOME = "home";
    private static final String OP_PHONE = "phone";

    private final MainActivity activity;
    private final UiData.App app;
    private final ExecutorService executor;
    private final Handler main;
    private final FrameLayout host;
    private final View root;
    private final ScrollView scroll;
    private final LinearLayout body;
    private final TextView topTitle;
    private final TextView footerTitle;
    private final TextView footerMeta;
    private final LinearLayout footerActions;
    private final boolean focusInstalled;

    private SheetPanel opSheet;
    private String selectedVariantId;
    private boolean installHome;
    private boolean busy;
    private boolean closing;
    private boolean closed;
    private boolean launchInFlight;

    private final StringBuilder opLog = new StringBuilder();
    private ProgressBar opProgress;
    private TextView opPhase;
    private TextView opLogView;
    private String activeOperation;
    private String retryVariant;
    private boolean retryHome;
    private JSONObject retryEntry;

    private FlowLayout variantChips;
    private TextView variantNote;
    private LinearLayout installExtra;
    private View installedAnchor;
    private View computerGuideAnchor;
    private View phoneAppAnchor;
    private LinearLayout shotDots;
    private TextView shotCaption;

    AppDetailPage(MainActivity activity, FrameLayout host, UiData.App app) {
        this(activity, host, app, false);
    }

    AppDetailPage(MainActivity activity, FrameLayout host, UiData.App app,
                  boolean focusInstalled) {
        this.activity = activity;
        this.host = host;
        this.app = app;
        this.focusInstalled = focusInstalled;
        this.executor = activity.executor;
        this.main = activity.main;
        this.selectedVariantId = app.defaultVariant() == null ? "" : app.defaultVariant().id;
        List<JSONObject> installed = activity.installedFor(app.id());
        if (!installed.isEmpty()) {
            String variant = installed.get(0).optString("variantId", "");
            if (app.variant(variant) != null) {
                selectedVariantId = variant;
            }
        }

        root = LayoutInflater.from(activity).inflate(R.layout.screen_app_detail, host, false);
        host.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topTitle = (TextView) root.findViewById(R.id.detailTopTitle);
        footerTitle = (TextView) root.findViewById(R.id.detailFooterTitle);
        footerMeta = (TextView) root.findViewById(R.id.detailFooterMeta);
        footerActions = (LinearLayout) root.findViewById(R.id.detailFooterActions);
        scroll = (ScrollView) root.findViewById(R.id.detailScroll);
        body = (LinearLayout) root.findViewById(R.id.detailBody);
        View back = root.findViewById(R.id.detailBack);
        UiKit.prepareIcon(root, R.id.detailBackIcon, "back", 0xFF16212F);
        back.setOnClickListener(v -> requestClose());
        topTitle.setText(app.name());

        build();
    }

    String appId() {
        return app.id();
    }

    boolean isShowing() {
        return host.getVisibility() == View.VISIBLE;
    }

    void show() {
        root.setAlpha(0f);
        root.setTranslationX(UiKit.dp(activity, 26));
        host.setVisibility(View.VISIBLE);
        activity.getWindow().setStatusBarColor(Color.WHITE);
        root.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(190L)
                .start();
        if (focusInstalled) {
            root.postDelayed(this::focusInstalledNow, 320L);
        }
    }

    /** Opens the installed section and starts the uninstall confirmation. */
    void focusInstalledNow() {
        if (closed || busy) {
            return;
        }
        List<JSONObject> installed = activity.installedFor(app.id());
        scrollToAnchor(installedAnchor);
        if (installed.isEmpty()) {
            activity.toast("电视上还没有安装 " + app.name());
            return;
        }
        if (app.needsPc) {
            activity.toast("CleanYourTV 需先运行官方 restore.sh，再按官方说明卸载。");
            return;
        }
        if (installed.size() > 1) {
            showUninstallChooser(installed);
            return;
        }
        confirmUninstall(installed.get(0));
    }

    void requestClose() {
        if (closed || closing) {
            return;
        }
        if (busy) {
            activity.toast("安装进行中，请保持本应用在前台。");
            return;
        }
        closing = true;
        root.animate()
                .alpha(0f)
                .translationX(UiKit.dp(activity, 18))
                .setDuration(140L)
                .withEndAction(this::finishClose)
                .start();
    }

    private void finishClose() {
        if (closed) {
            return;
        }
        closed = true;
        closing = false;
        host.removeAllViews();
        host.setVisibility(View.GONE);
        host.setAlpha(1f);
        host.setTranslationX(0f);
        activity.getWindow().setStatusBarColor(0xFFF6F8FC);
        activity.onDetailClosed(this);
    }

    void closeImmediately() {
        if (closed) {
            return;
        }
        closed = true;
        closing = true;
        if (opSheet != null) {
            opSheet.setOnDismiss(null);
            opSheet.dismiss();
            opSheet = null;
        }
        root.animate().cancel();
        host.removeAllViews();
        host.setVisibility(View.GONE);
        host.setAlpha(1f);
        host.setTranslationX(0f);
        activity.getWindow().setStatusBarColor(0xFFF6F8FC);
        activity.onDetailClosed(this);
    }

    void onDeviceStateChanged() {
        if (closed || busy) {
            return;
        }
        build();
    }

    // ------------------------------------------------------------------
    // Page content
    // ------------------------------------------------------------------

    private void build() {
        if (closed) {
            return;
        }
        body.removeAllViews();
        variantChips = null;
        variantNote = null;
        installExtra = null;
        installedAnchor = null;
        computerGuideAnchor = null;
        phoneAppAnchor = null;
        shotDots = null;
        shotCaption = null;

        addHero(body);
        addScreenshots(body);
        addIntro(body);
        addFeatures(body);

        List<JSONObject> installed = activity.installedFor(app.id());
        if (!installed.isEmpty()) {
            addInstalled(body, installed);
        }

        if (app.needsPc) {
            addPhoneApp(body);
            addComputerGuide(body);
        } else {
            addInstallOptions(body);
        }

        addPermissions(body);
        addFacts(body);
        addOfficial(body);
        updateFooter();
    }

    private void addHero(LinearLayout root) {
        LinearLayout top = UiKit.row(root.getContext());
        top.setGravity(Gravity.TOP);

        RoundedImageView icon = new RoundedImageView(activity);
        icon.setCornerRadiusDp(19f);
        icon.setImageResource(MediaCatalog.icon(app.id()));
        icon.setContentDescription(app.name() + " 图标");
        top.addView(icon, new LinearLayout.LayoutParams(
                UiKit.dp(activity, 78), UiKit.dp(activity, 78)));

        LinearLayout copy = UiKit.column(activity);
        TextView name = UiKit.text(activity, app.name(), 21f, 0xFF16212F, true);
        copy.addView(name);
        TextView tagline = UiKit.text(activity, app.tagline, 12.5f, 0xFF4C5C70, false);
        tagline.setLineSpacing(UiKit.dp(activity, 2.5f), 1f);
        tagline.setPadding(0, UiKit.dp(activity, 4), 0, 0);
        copy.addView(tagline);
        TextView meta = UiKit.caption(activity,
                app.minAndroid + " · " + app.sourceShort);
        meta.setPadding(0, UiKit.dp(activity, 6), 0, 0);
        copy.addView(meta);

        FlowLayout badges = new FlowLayout(activity);
        badges.setGap(UiKit.dp(activity, 5), UiKit.dp(activity, 5));
        List<JSONObject> installed = activity.installedFor(app.id());
        if (!installed.isEmpty()) {
            badges.addView(UiKit.badge(activity, "已安装", UiData.TONE_GREEN));
        }
        if (activity.isDefaultApp(app.id())) {
            badges.addView(UiKit.badge(activity, "默认桌面", UiData.TONE_INFO));
        }
        if (app.isNew) {
            badges.addView(UiKit.badge(activity, "新上架", UiData.TONE_INFO));
        }
        if (app.testBuild) {
            badges.addView(UiKit.badge(activity, "测试版", UiData.TONE_WARN));
        }
        if (app.needsPc) {
            badges.addView(UiKit.badge(activity, "手机版可装", UiData.TONE_INFO));
        }
        if (!TextUtils.isEmpty(app.licenseNote)) {
            badges.addView(UiKit.badge(activity, app.licenseNote,
                    app.licenseNote.contains("仅个人使用")
                            ? UiData.TONE_WARN : UiData.TONE_INFO));
        }
        if (badges.getChildCount() > 0) {
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.topMargin = UiKit.dp(activity, 7);
            copy.addView(badges, badgeParams);
        }

        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(activity, 13);
        top.addView(copy, copyParams);
        root.addView(top);
    }

    private void addScreenshots(LinearLayout root) {
        MediaCatalog.Media media = MediaCatalog.forApp(app.id());
        if (media.shots == null || media.shots.length == 0) {
            return;
        }
        addSection(root, "应用截图", "来自官方仓库 / 官网",
                media.shots.length + " 张");

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.round((screenWidth - UiKit.dp(activity, 36f)) * 0.84f);
        cardWidth = Math.max(UiKit.dp(activity, 248), Math.min(UiKit.dp(activity, 342), cardWidth));
        int cardHeight = Math.round(cardWidth * 9f / 16f);
        final int gap = UiKit.dp(activity, 10);
        final MediaCatalog.Shot[] shots = media.shots;

        HorizontalScrollView pager = new HorizontalScrollView(activity);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setClipToPadding(false);
        pager.setFillViewport(false);

        LinearLayout row = UiKit.row(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < shots.length; i++) {
            RoundedImageView image = new RoundedImageView(activity);
            image.setCornerRadiusDp(15f);
            image.setImageResource(shots[i].image);
            image.setContentDescription(shots[i].caption);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    cardWidth, cardHeight);
            imageParams.rightMargin = i == shots.length - 1 ? 0 : gap;
            row.addView(image, imageParams);
        }
        pager.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        UiKit.add(root, pager, 0, 8, 0, 0);

        LinearLayout captionRow = UiKit.row(activity);
        captionRow.setGravity(Gravity.CENTER_VERTICAL);
        captionRow.setPadding(0, UiKit.dp(activity, 9), 0, 0);
        shotDots = UiKit.row(activity);
        shotDots.setGravity(Gravity.CENTER_VERTICAL);
        captionRow.addView(shotDots);
        shotCaption = UiKit.caption(activity,
                "1/" + shots.length + " · " + shots[0].caption);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        captionParams.leftMargin = UiKit.dp(activity, 10);
        captionRow.addView(shotCaption, captionParams);
        root.addView(captionRow);
        renderShotDots(0);

        final int pageWidth = cardWidth + gap;
        pager.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            int index = Math.round(scrollX / (float) pageWidth);
            index = Math.max(0, Math.min(shots.length - 1, index));
            renderShotDots(index);
            if (shotCaption != null) {
                shotCaption.setText((index + 1) + "/" + shots.length
                        + " · " + shots[index].caption);
            }
        });
    }

    private void renderShotDots(int active) {
        if (shotDots == null) {
            return;
        }
        shotDots.removeAllViews();
        MediaCatalog.Media media = MediaCatalog.forApp(app.id());
        int count = media.shots == null ? 0 : media.shots.length;
        for (int i = 0; i < count; i++) {
            boolean selected = i == active;
            View dot = new View(activity);
            int width = selected ? UiKit.dp(activity, 16) : UiKit.dp(activity, 6);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    width, UiKit.dp(activity, 6));
            params.rightMargin = i == count - 1 ? 0 : UiKit.dp(activity, 5);
            dot.setBackground(UiKit.roundRect(
                    selected ? 0xFF2F7DFF : 0xFFD6DFEA,
                    UiKit.dp(activity, 3f), 0, 0));
            shotDots.addView(dot, params);
        }
    }

    private void addIntro(LinearLayout root) {
        addSection(root, "应用介绍", "来自官方描述", "");
        TextView desc = UiKit.text(activity, app.desc, 13f, 0xFF4C5C70, false);
        desc.setLineSpacing(UiKit.dp(activity, 3.8f), 1f);
        root.addView(desc);
        if (!TextUtils.isEmpty(app.cardNote)) {
            UiKit.add(root, UiKit.note(activity, null, app.cardNote, app.noteTone),
                    0, 11, 0, 0);
        }
    }

    private void addFeatures(LinearLayout root) {
        if (app.features == null || app.features.length == 0) {
            return;
        }
        addSection(root, "特色亮点", "为什么值得安装", "");
        FlowLayout row = new FlowLayout(activity);
        row.setGap(UiKit.dp(activity, 6), UiKit.dp(activity, 6));
        for (String feature : app.features) {
            TextView chip = UiKit.text(activity, feature, 11f, 0xFF4C5C70, true);
            chip.setPadding(UiKit.dp(activity, 9), UiKit.dp(activity, 5),
                    UiKit.dp(activity, 9), UiKit.dp(activity, 5));
            chip.setBackground(UiKit.roundRect(0xFFF1F5FA, UiKit.dp(activity, 7),
                    0xFFE6EBF2, 1));
            row.addView(chip);
        }
        root.addView(row);
    }

    private void addInstalled(LinearLayout root, List<JSONObject> entries) {
        installedAnchor = addSection(root, "已安装到电视",
                "版本、默认桌面与卸载入口", "");
        for (JSONObject entry : entries) {
            String packageName = entry.optString("packageName", "");
            String variantId = entry.optString("variantId", "");
            boolean isDefault = activity.isDefaultPackage(packageName);
            LinearLayout card = UiKit.card(activity);

            LinearLayout top = UiKit.row(activity);
            RoundedImageView icon = new RoundedImageView(activity);
            icon.setCornerRadiusDp(11f);
            icon.setImageResource(MediaCatalog.icon(app.id()));
            icon.setContentDescription(app.name() + " 图标");
            top.addView(icon, new LinearLayout.LayoutParams(
                    UiKit.dp(activity, 40), UiKit.dp(activity, 40)));

            LinearLayout copy = UiKit.column(activity);
            copy.addView(UiKit.text(activity, variantLabel(variantId, packageName),
                    13f, 0xFF16212F, true));
            String version = entry.optString("versionName", "");
            TextView meta = UiKit.caption(activity, (TextUtils.isEmpty(version)
                    ? "版本未知" : "版本 " + version) + " · " + packageName);
            meta.setPadding(0, UiKit.dp(activity, 3), 0, 0);
            copy.addView(meta);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            copyParams.leftMargin = UiKit.dp(activity, 10);
            top.addView(copy, copyParams);
            if (isDefault) {
                top.addView(UiKit.badge(activity, "默认桌面", UiData.TONE_INFO));
            }
            card.addView(top);

            LinearLayout actions = UiKit.row(activity);
            TextView open = UiKit.smallButton(activity, "在电视上打开", UiKit.BTN_SECONDARY);
            open.setOnClickListener(v -> launch(entry));
            actions.addView(open);
            if (!app.needsPc && !app.isTool() && !isDefault) {
                boolean canHome = app.variant(variantId) != null
                        && catalogVariant(variantId) != null
                        && catalogVariant(variantId).homeComponent != null;
                if (canHome) {
                    TextView home = UiKit.smallButton(activity, "设为默认桌面", UiKit.BTN_SECONDARY);
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    p.leftMargin = UiKit.dp(activity, 6);
                    actions.addView(home, p);
                    home.setOnClickListener(v -> confirmSetHome(entry));
                }
            }
            UiKit.add(card, actions, 0, 10, 0, 0);

            if (!app.needsPc) {
                TextView uninstall = UiKit.button(activity,
                        entries.size() > 1 ? "卸载这个版本" : "卸载 " + app.name(),
                        UiKit.BTN_DANGER);
                uninstall.setOnClickListener(v -> confirmUninstall(entry));
                UiKit.add(card, uninstall, 0, 10, 0, 0);
                TextView hint = UiKit.caption(activity, app.isTool()
                        ? "会删除题库、规则和使用记录；不会更改默认桌面。"
                        : isDefault
                        ? "会先按官方流程切回原桌面、恢复系统设置，再移除应用和数据。"
                        : "会先按官方流程撤销通知监听、无障碍等设置，再移除应用和数据。");
                hint.setPadding(0, UiKit.dp(activity, 6), 0, 0);
                card.addView(hint);
            } else {
                UiKit.add(card, UiKit.note(activity, "卸载前先恢复系统项",
                        "CleanYourTV 清理过系统项。请先在电脑上运行官方 restore.sh，再按官方说明卸载。",
                        UiData.TONE_WARN), 0, 9, 0, 0);
            }
            UiKit.add(root, card, 0, 9, 0, 0);
        }
    }

    private void addPhoneApp(LinearLayout root) {
        phoneAppAnchor = addSection(root, "手机版 App",
                "官方站点推荐 · 社区维护", "");
        JSONObject phone = PhoneAppInstaller.installed(activity);
        boolean installed = phone.optBoolean("installed", false);
        LinearLayout card = UiKit.card(activity);

        LinearLayout top = UiKit.row(activity);
        RoundedImageView icon = new RoundedImageView(activity);
        icon.setCornerRadiusDp(11f);
        icon.setImageResource(MediaCatalog.icon(app.id()));
        icon.setContentDescription(PhoneAppInstaller.DISPLAY_NAME + " 图标");
        top.addView(icon, new LinearLayout.LayoutParams(
                UiKit.dp(activity, 46), UiKit.dp(activity, 46)));
        LinearLayout copy = UiKit.column(activity);
        copy.addView(UiKit.text(activity, PhoneAppInstaller.DISPLAY_NAME,
                14f, 0xFF16212F, true));
        copy.addView(UiKit.caption(activity, "Android 9+ · MIT · GitHub 开源"));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(activity, 10);
        top.addView(copy, copyParams);
        if (installed) {
            top.addView(UiKit.badge(activity, "已装在手机", UiData.TONE_GREEN));
        }
        card.addView(top);

        TextView body = UiKit.text(activity,
                "官方 cleanyour.tv 的 Community apps 页推荐这个免费、MIT 开源的手机 App。"
                        + "它在同一 Wi-Fi 下连接电视，先列出准备关闭的广告、追踪和预装应用，"
                        + "保存恢复点后再清理，并在支持的电视上安装 CleanYourTV 桌面。"
                        + "不需要电脑，也不用把电视的调试端口暴露到公网。",
                12f, 0xFF4C5C70, false);
        body.setLineSpacing(UiKit.dp(activity, 3.2f), 1f);
        UiKit.add(card, body, 0, 9, 0, 0);

        UiKit.add(card, UiKit.note(activity, "只走官方链接的发布",
                "工具市场只从 cleanyour.tv 官方页面链接的 GitHub Release 下载，"
                        + "核对发布方 SHA-256、包名 tv.cleanyour.app 和固定签名后，"
                        + "再交给系统安装器确认。短视频平台或应用市场里的同名 APK 不是本应用使用的渠道。",
                UiData.TONE_INFO), 0, 9, 0, 0);

        if (Build.VERSION.SDK_INT < PhoneAppInstaller.MIN_SDK) {
            UiKit.add(card, UiKit.note(activity, "手机系统版本不够",
                    "手机版 App 需要 Android 9 或更高版本。可以继续使用下面的电脑安装指引。",
                    UiData.TONE_WARN), 0, 9, 0, 0);
        } else if (installed) {
            String version = phone.optString("versionName", "");
            TextView open = UiKit.button(activity, TextUtils.isEmpty(version)
                            ? "打开手机版 App" : "打开手机版 App · " + version,
                    UiKit.BTN_PRIMARY);
            open.setOnClickListener(v -> openPhoneApp());
            UiKit.add(card, open, 0, 10, 0, 0);
            TextView hint = UiKit.caption(activity,
                    "已经安装在手机上。清理和电视端安装由这个 App 自己完成，"
                            + "工具市场不会代为改动电视。");
            hint.setPadding(0, UiKit.dp(activity, 6), 0, 0);
            card.addView(hint);
        } else if (!PhoneAppInstaller.canRequestInstalls(activity)) {
            TextView allow = UiKit.button(activity, "允许本应用安装应用", UiKit.BTN_PRIMARY);
            allow.setOnClickListener(v -> PhoneAppInstaller.openInstallSettings(activity));
            UiKit.add(card, allow, 0, 10, 0, 0);
            TextView hint = UiKit.caption(activity,
                    "Android 需要你手动允许一次。允许后返回这里，再点安装。");
            hint.setPadding(0, UiKit.dp(activity, 6), 0, 0);
            card.addView(hint);
        } else {
            TextView install = UiKit.button(activity, "下载并安装到手机", UiKit.BTN_PRIMARY);
            install.setOnClickListener(v -> startPhoneInstall());
            UiKit.add(card, install, 0, 10, 0, 0);
            TextView hint = UiKit.caption(activity,
                    "下载完成后系统会弹出安装确认；确认前不会改动手机。");
            hint.setPadding(0, UiKit.dp(activity, 6), 0, 0);
            card.addView(hint);
        }
        UiKit.add(root, card, 0, 9, 0, 0);
    }

    private void openPhoneApp() {
        Intent intent = PhoneAppInstaller.launchIntent(activity);
        if (intent == null) {
            activity.toast("还没有在手机上找到 " + PhoneAppInstaller.DISPLAY_NAME + "，请先安装。");
            return;
        }
        try {
            activity.startActivity(intent);
        } catch (Throwable error) {
            activity.toast("没能打开 " + PhoneAppInstaller.DISPLAY_NAME
                    + "，请在手机桌面上找到它。");
        }
    }

    private void startPhoneInstall() {
        if (busy || closed) {
            return;
        }
        if (Build.VERSION.SDK_INT < PhoneAppInstaller.MIN_SDK) {
            activity.toast("手机版 App 需要 Android 9 或更高版本。");
            return;
        }
        if (!PhoneAppInstaller.canRequestInstalls(activity)) {
            PhoneAppInstaller.openInstallSettings(activity);
            return;
        }
        busy = true;
        activeOperation = OP_PHONE;
        retryVariant = null;
        retryHome = false;
        retryEntry = null;
        opLog.setLength(0);
        updateFooter();
        showOperationPage("正在安装手机版 App",
                "正在读取官方 Release…",
                "下载并校验完成后，系统会弹出安装确认。确认前不会改动手机。");
        executor.execute(() -> {
            JSONObject result = null;
            Exception failure = null;
            try {
                result = PhoneAppInstaller.install(activity, this::postOpLog,
                        (phase, flavor, received, total) -> main.post(
                                () -> updateProgress(phase, received, total)));
            } catch (Exception error) {
                failure = error;
            }
            final JSONObject installed = result;
            final Exception error = failure;
            main.post(() -> {
                busy = false;
                if (opSheet != null) {
                    opSheet.setBusy(false);
                }
                if (error == null) {
                    // The phone-app card and the footer must both switch to the
                    // installed state as soon as the system installer confirms.
                    build();
                } else {
                    updateFooter();
                }
                showPhoneResult(error == null, installed, error);
            });
        });
    }

    private void showPhoneResult(boolean success, JSONObject data, Exception error) {
        if (closed) {
            return;
        }
        SheetPanel panel = ensureOperationSheet(success ? "安装完成" : "安装未完成");
        panel.setBusy(false);
        panel.clearBody();
        panel.setTitle(success ? "安装完成" : "安装未完成");
        panel.setSubtitle("");
        LinearLayout root = UiKit.column(activity);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        if (success) {
            root.addView(iconTile("check", 0xFF129B67, 0xFFEAF9F2));
            TextView title = UiKit.title(activity, PhoneAppInstaller.DISPLAY_NAME + " 已安装");
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, UiKit.dp(activity, 10), 0, 0);
            root.addView(title);
            String version = data == null ? "" : data.optString("versionName", "");
            TextView caption = UiKit.caption(activity, TextUtils.isEmpty(version)
                    ? "已经可以在手机上打开。" : "手机上的版本：" + version);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, UiKit.dp(activity, 5), 0, 0);
            root.addView(caption);
            UiKit.add(root, UiKit.note(activity, "接下来在手机上操作",
                    "打开这个 App，让它连接同一 Wi-Fi 下的电视。它会先展示清理计划并保存恢复点，"
                            + "确认后才开始。清理和电视桌面安装由它完成，工具市场不参与。",
                    UiData.TONE_INFO), 0, 10, 0, 0);

            TextView open = UiKit.button(activity, "打开 Clean Your TV", UiKit.BTN_PRIMARY);
            open.setOnClickListener(v -> openPhoneApp());
            UiKit.add(root, open, 0, 14, 0, 0);
        } else {
            root.addView(iconTile("warning", 0xFFDE464D, 0xFFFDEEEF));
            TextView title = UiKit.title(activity, "没有完成");
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, UiKit.dp(activity, 10), 0, 0);
            root.addView(title);
            TextView detail = UiKit.text(activity, safe(error), 12.5f, 0xFF4C5C70, false);
            detail.setGravity(Gravity.CENTER);
            detail.setLineSpacing(UiKit.dp(activity, 3), 1f);
            detail.setPadding(0, UiKit.dp(activity, 7), 0, UiKit.dp(activity, 4));
            root.addView(detail);
            UiKit.add(root, UiKit.note(activity, "手机与电视都没有被改动",
                    "没有安装任何应用，也没有触碰电视。可以检查网络和系统安装权限后重试。",
                    UiData.TONE_WARN), 0, 10, 0, 0);

            TextView retry = UiKit.button(activity, "重试", UiKit.BTN_AMBER);
            retry.setOnClickListener(v -> {
                if (opSheet != null) {
                    opSheet.dismiss();
                }
                main.postDelayed(this::startPhoneInstall, 260L);
            });
            UiKit.add(root, retry, 0, 14, 0, 0);
        }

        if (opLog.length() > 0) {
            opLogView = UiKit.logView(activity);
            opLogView.setText(opLog.toString());
            LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            logParams.topMargin = UiKit.dp(activity, 12);
            root.addView(opLogView, logParams);
        }

        TextView back = UiKit.button(activity, "返回应用详情", UiKit.BTN_SECONDARY);
        back.setOnClickListener(v -> {
            if (opSheet != null) {
                opSheet.dismiss();
            }
        });
        UiKit.add(root, back, 0, 8, 0, 0);

        panel.add(root);
    }

    private void addComputerGuide(LinearLayout root) {
        computerGuideAnchor = addSection(root, "电脑安装指引",
                "官方脚本 · 适合自定义主题与列表", "");
        LinearLayout guide = UiKit.card(activity);
        TextView title = UiKit.text(activity, "官方仓库不提供电视桌面二进制，需要电脑构建",
                12.5f, 0xFF856723, true);
        guide.addView(title);
        TextView guideBody = UiKit.text(activity, app.guide, 12f, 0xFF5B4A25, false);
        guideBody.setLineSpacing(UiKit.dp(activity, 4f), 1f);
        guideBody.setPadding(0, UiKit.dp(activity, 7), 0, 0);
        guide.addView(guideBody);
        UiKit.add(root, guide, 0, 9, 0, 0);

        TextView official = UiKit.button(activity, "查看官方安装说明", UiKit.BTN_SECONDARY);
        official.setOnClickListener(v -> activity.openOfficial(app));
        UiKit.add(root, official, 0, 9, 0, 0);

        if (activity.isConnected()) {
            final TextView check = UiKit.button(activity,
                    "重新检测电视安装状态", UiKit.BTN_GHOST);
            check.setOnClickListener(v -> {
                check.setEnabled(false);
                check.setText("正在检测…");
                activity.refreshDevice(() -> {
                    if (!closed) {
                        build();
                    }
                });
            });
            UiKit.add(root, check, 0, 6, 0, 0);
        }
    }

    private void addInstallOptions(LinearLayout root) {
        if (app.variants.length == 0) {
            return;
        }
        addSection(root, "安装选项", "选择版本后再安装到电视", "");
        LinearLayout card = UiKit.card(activity);

        variantChips = new FlowLayout(activity);
        variantChips.setGap(UiKit.dp(activity, 6), UiKit.dp(activity, 6));
        card.addView(variantChips);

        variantNote = UiKit.caption(activity, "");
        variantNote.setPadding(0, UiKit.dp(activity, 7), 0, 0);
        card.addView(variantNote);

        installExtra = UiKit.column(activity);
        card.addView(installExtra);

        UiKit.add(root, card, 0, 8, 0, 0);
        renderVariantChips();
        updateVariantNote();
        renderInstallExtra();
    }

    private void renderVariantChips() {
        if (variantChips == null) {
            return;
        }
        variantChips.removeAllViews();
        for (UiData.Variant variant : app.variants) {
            boolean active = variant.id.equals(selectedVariantId);
            TextView chip = UiKit.filterChip(activity, variant.label, active);
            chip.setOnClickListener(v -> {
                if (busy) {
                    return;
                }
                selectedVariantId = variant.id;
                renderVariantChips();
                updateVariantNote();
                renderInstallExtra();
                updateFooter();
            });
            variantChips.addView(chip);
        }
    }

    private void updateVariantNote() {
        if (variantNote == null) {
            return;
        }
        UiData.Variant selected = app.variant(selectedVariantId);
        if (selected != null && !TextUtils.isEmpty(selected.note)) {
            variantNote.setText("适用：" + selected.note);
            variantNote.setVisibility(View.VISIBLE);
        } else {
            variantNote.setVisibility(View.GONE);
        }
    }

    private void renderInstallExtra() {
        if (installExtra == null) {
            return;
        }
        installExtra.removeAllViews();
        Catalog.Variant catalogVariant = catalogVariant(selectedVariantId);
        boolean launcher = !app.isTool() && !app.needsPc
                && catalogVariant != null && catalogVariant.homeComponent != null;

        if (launcher) {
            LinearLayout switchRow = UiKit.switchRow(activity,
                    "安装后切换为默认桌面",
                    "默认关闭。安装完成并确认无误后，再到“我的电视”切换更安全。",
                    installHome, checked -> installHome = checked);
            UiKit.add(installExtra, switchRow, 0, 12, 0, 0);
            UiKit.add(installExtra, UiKit.note(activity, "不会停用系统桌面",
                    "切换 HOME 只是把默认桌面指向这个第三方应用；原桌面仍保留，可随时切回。",
                    UiData.TONE_INFO), 0, 8, 0, 0);
        } else {
            installHome = false;
            UiKit.add(installExtra, UiKit.note(activity, "不改默认桌面",
                    app.isTool() ? "这是答题工具，不会更改电视的默认桌面。"
                            : "此版本不会自动更改默认桌面。",
                    UiData.TONE_INFO), 0, 10, 0, 0);
        }

        if (!activity.isConnected()) {
            UiKit.add(installExtra, UiKit.note(activity, "还没有连接电视",
                    "安装前需要先通过无线调试或网络 ADB 连接电视，并在电视上允许调试授权。",
                    UiData.TONE_WARN), 0, 9, 0, 0);
            return;
        }

        boolean selectedInstalled = activity.isPackageInstalled(
                catalogVariant == null ? "" : catalogVariant.packageName);
        if (selectedInstalled) {
            TextView overwrite = UiKit.button(activity,
                    "覆盖安装所选版本", UiKit.BTN_SECONDARY);
            overwrite.setOnClickListener(v -> startInstall(selectedVariantId, installHome));
            UiKit.add(installExtra, overwrite, 0, 10, 0, 0);
            TextView hint = UiKit.caption(activity,
                    "已安装此版本；覆盖安装不会先卸载旧版本，数据会保留。");
            hint.setPadding(0, UiKit.dp(activity, 6), 0, 0);
            installExtra.addView(hint);
        }
    }

    private void addPermissions(LinearLayout root) {
        if (app.permissions == null || app.permissions.isEmpty()) {
            return;
        }
        addSection(root, "电视端权限说明", "全部由你在电视上手动开启", "");
        UiKit.add(root, UiKit.note(activity, "工具市场不代授权",
                "这些是应用正常运行所需的电视端权限。本安装器不会自动开启无障碍、设备管理员、"
                        + "悬浮窗或使用情况访问。",
                UiData.TONE_INFO), 0, 8, 0, 0);
        LinearLayout card = UiKit.card(activity);
        boolean first = true;
        for (UiData.Permission permission : app.permissions) {
            if (!first) {
                card.addView(UiKit.divider(activity));
            }
            first = false;
            LinearLayout row = UiKit.row(activity);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, UiKit.dp(activity, 9), 0, UiKit.dp(activity, 9));
            LinearLayout copy = UiKit.column(activity);
            copy.addView(UiKit.text(activity, permission.title, 12.5f, 0xFF16212F, true));
            TextView desc = UiKit.caption(activity, permission.desc);
            desc.setPadding(0, UiKit.dp(activity, 3), 0, 0);
            copy.addView(desc);
            row.addView(copy, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (permission.highRisk) {
                row.addView(UiKit.badge(activity, "高风险 · 可选", UiData.TONE_RED));
            } else if (permission.optional) {
                row.addView(UiKit.badge(activity, "可选", UiData.TONE_WARN));
            } else {
                row.addView(UiKit.badge(activity, "必需", UiData.TONE_INFO));
            }
            card.addView(row);
        }
        UiKit.add(root, card, 0, 8, 0, 0);
        TextView tip = UiKit.caption(activity,
                "设备管理员尤其不要随意开启；开启后需先在电视设置中取消，才能正常卸载。");
        tip.setPadding(0, UiKit.dp(activity, 7), 0, 0);
        root.addView(tip);
    }

    private void addFacts(LinearLayout root) {
        addSection(root, "应用信息", "", "");
        LinearLayout card = UiKit.card(activity);
        card.addView(UiKit.kv(activity, "官方来源", app.sourceShort, false));
        card.addView(UiKit.divider(activity));
        card.addView(UiKit.kv(activity, "最低系统", app.minAndroid, false));
        card.addView(UiKit.divider(activity));
        card.addView(UiKit.kv(activity, "许可", app.licenseNote, false));
        if (!TextUtils.isEmpty(app.packageName())) {
            card.addView(UiKit.divider(activity));
            card.addView(UiKit.kv(activity, "包名", app.packageName(), true));
        }
        String credit = MediaCatalog.credit(app.id());
        if (!TextUtils.isEmpty(credit)) {
            card.addView(UiKit.divider(activity));
            card.addView(UiKit.kv(activity, "图标与截图", credit, false));
        }
        UiKit.add(root, card, 0, 8, 0, 0);
    }

    private void addOfficial(LinearLayout root) {
        TextView official = UiKit.button(activity, "打开官方页面", UiKit.BTN_GHOST);
        official.setOnClickListener(v -> activity.openOfficial(app));
        UiKit.add(root, official, 0, 8, 0, 0);
        TextView disclaimer = UiKit.caption(activity,
                "第三方开源工具，版权与许可以官方仓库为准。图标和截图仅用于应用识别，"
                        + "版权归原作者所有。工具市场只负责安装与说明。");
        disclaimer.setGravity(Gravity.CENTER);
        disclaimer.setPadding(0, UiKit.dp(activity, 6), 0, UiKit.dp(activity, 8));
        root.addView(disclaimer);
    }

    private View addSection(LinearLayout root, String title, String subtitle, String count) {
        LinearLayout header = UiKit.sectionHeader(activity, title, subtitle, count);
        UiKit.add(root, header, 0, 18, 0, 8);
        return header;
    }

    // ------------------------------------------------------------------
    // Fixed bottom action bar
    // ------------------------------------------------------------------

    private void updateFooter() {
        if (closed) {
            return;
        }
        footerActions.removeAllViews();
        if (busy) {
            footerTitle.setText("正在处理");
            footerMeta.setText("请保持本应用在前台");
            addFooterAction("正在处理…", UiKit.BTN_PRIMARY, false, null);
            return;
        }

        List<JSONObject> installed = activity.installedFor(app.id());
        boolean connected = activity.isConnected();
        UiData.Variant selected = app.variant(selectedVariantId);

        if (app.needsPc) {
            if (!installed.isEmpty() && connected) {
                footerTitle.setText("已安装到电视");
                footerMeta.setText(installedMeta(installed.get(0)));
                addFooterAction("安装说明", UiKit.BTN_SECONDARY, true,
                        () -> scrollToAnchor(computerGuideAnchor));
                addFooterAction("打开应用", UiKit.BTN_PRIMARY, true,
                        () -> launch(installed.get(0)));
                return;
            }
            JSONObject phone = PhoneAppInstaller.installed(activity);
            boolean phoneInstalled = phone.optBoolean("installed", false);
            if (phoneInstalled) {
                footerTitle.setText("手机版 App 已安装");
                footerMeta.setText("可打开手机版，由它完成电视清理与桌面安装");
                addFooterAction("电脑教程", UiKit.BTN_SECONDARY, true,
                        () -> scrollToAnchor(computerGuideAnchor));
                addFooterAction("打开手机版", UiKit.BTN_PRIMARY, true,
                        this::openPhoneApp);
                return;
            }
            footerTitle.setText("电视安装方式");
            footerMeta.setText("官方无电视桌面 APK；可装手机版 App 或用电脑脚本");
            if (connected) {
                addFooterAction("重新检测", UiKit.BTN_SECONDARY, true,
                        () -> activity.refreshDevice(() -> {
                            if (!closed) {
                                build();
                            }
                        }));
            }
            addFooterAction("电脑教程", UiKit.BTN_SECONDARY, true,
                    () -> scrollToAnchor(computerGuideAnchor));
            addFooterAction("安装手机版 App", UiKit.BTN_PRIMARY, true,
                    () -> scrollToAnchor(phoneAppAnchor));
            return;
        }

        if (!connected) {
            footerTitle.setText(installed.isEmpty()
                    ? "还没有连接电视" : "电视未连接");
            footerMeta.setText(installed.isEmpty()
                    ? "连接后选择版本并安装"
                    : "连接后可打开或卸载已安装版本");
            addFooterAction("连接电视", UiKit.BTN_PRIMARY, true,
                    () -> activity.openConnectSheet(ConnectSheet.MODE_WIRELESS));
            return;
        }

        if (!installed.isEmpty()) {
            JSONObject first = installed.get(0);
            boolean isDefault = activity.isDefaultPackage(
                    first.optString("packageName", ""));
            footerTitle.setText(isDefault ? "已安装 · 当前默认桌面" : "已安装到电视");
            footerMeta.setText(installed.size() > 1
                    ? "电视上安装了 " + installed.size() + " 个版本"
                    : isDefault
                    ? "卸载前会自动切回原桌面并恢复系统设置"
                    : "可打开或卸载这个版本");
            addFooterAction("卸载", UiKit.BTN_DANGER, true,
                    this::requestUninstall);
            addFooterAction("打开应用", UiKit.BTN_PRIMARY, true,
                    () -> launch(first));
            return;
        }

        footerTitle.setText("准备安装");
        footerMeta.setText((selected == null ? "" : selected.label + " · ")
                + app.sourceShort);
        addFooterAction("安装到电视", UiKit.BTN_PRIMARY, true,
                () -> startInstall(selectedVariantId, installHome));
    }

    private void addFooterAction(String label, int style, boolean enabled, Runnable action) {
        boolean large = style == UiKit.BTN_PRIMARY || style == UiKit.BTN_DANGER;
        TextView button = large
                ? UiKit.button(activity, label, style)
                : UiKit.smallButton(activity, label, style);
        button.setMinWidth(UiKit.dp(activity,
                style == UiKit.BTN_PRIMARY ? 112 : large ? 64 : 54));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (footerActions.getChildCount() > 0) {
            params.leftMargin = UiKit.dp(activity, 7);
        }
        footerActions.addView(button, params);
        if (enabled && action != null) {
            button.setOnClickListener(v -> action.run());
        } else if (!enabled) {
            UiKit.setEnabled(button, false);
        }
    }

    private String installedMeta(JSONObject entry) {
        String variant = entry.optString("variantId", "");
        String version = entry.optString("versionName", "");
        String label = variantLabel(variant, entry.optString("packageName", ""));
        return label + (TextUtils.isEmpty(version) ? "" : " · 版本 " + version);
    }

    private void scrollToAnchor(View anchor) {
        if (anchor == null) {
            return;
        }
        scroll.post(() -> {
            if (!closed) {
                scroll.smoothScrollTo(0,
                        Math.max(0, anchor.getTop() - UiKit.dp(activity, 12)));
            }
        });
    }

    // ------------------------------------------------------------------
    // Actions (same guarantees as the previous detail sheet)
    // ------------------------------------------------------------------

    private void requestUninstall() {
        if (busy) {
            return;
        }
        List<JSONObject> installed = activity.installedFor(app.id());
        if (installed.isEmpty()) {
            activity.toast("电视上还没有安装 " + app.name());
            return;
        }
        if (installed.size() == 1) {
            confirmUninstall(installed.get(0));
            return;
        }
        showUninstallChooser(installed);
    }

    private void showUninstallChooser(final List<JSONObject> entries) {
        if (busy || closed) {
            return;
        }
        final SheetPanel sheet = SheetPanel.show(activity, "选择要卸载的版本",
                "电视上安装了 " + entries.size() + " 个版本");
        LinearLayout root = UiKit.column(activity);
        for (JSONObject entry : entries) {
            final String packageName = entry.optString("packageName", "");
            final boolean isDefault = activity.isDefaultPackage(packageName);
            LinearLayout card = UiKit.card(activity);

            LinearLayout row = UiKit.row(activity);
            RoundedImageView icon = new RoundedImageView(activity);
            icon.setCornerRadiusDp(11f);
            icon.setImageResource(MediaCatalog.icon(app.id()));
            icon.setContentDescription(app.name() + " 图标");
            row.addView(icon, new LinearLayout.LayoutParams(
                    UiKit.dp(activity, 40), UiKit.dp(activity, 40)));
            LinearLayout copy = UiKit.column(activity);
            copy.addView(UiKit.text(activity,
                    variantLabel(entry.optString("variantId", ""), packageName),
                    13f, 0xFF16212F, true));
            String version = entry.optString("versionName", "");
            TextView meta = UiKit.caption(activity, (TextUtils.isEmpty(version)
                    ? "版本未知" : "版本 " + version) + " · " + packageName);
            meta.setPadding(0, UiKit.dp(activity, 3), 0, 0);
            copy.addView(meta);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            copyParams.leftMargin = UiKit.dp(activity, 10);
            row.addView(copy, copyParams);
            if (isDefault) {
                row.addView(UiKit.badge(activity, "默认桌面", UiData.TONE_INFO));
            }
            card.addView(row);

            TextView action = UiKit.button(activity, "卸载这个版本",
                    UiKit.BTN_DANGER);
            action.setOnClickListener(v -> {
                sheet.dismiss();
                confirmUninstall(entry);
            });
            UiKit.add(card, action, 0, 10, 0, 0);
            UiKit.add(root, card, 0, 8, 0, 0);
        }
        sheet.add(root);
    }

    private void confirmUninstall(final JSONObject entry) {
        if (busy) {
            return;
        }
        String packageName = entry.optString("packageName", "");
        if (app.needsPc) {
            NativeDialogs.message(activity, "先运行官方恢复脚本",
                    "CleanYourTV 会清理系统项。卸载前请先在电脑上运行官方 restore.sh，"
                            + "恢复系统组件和原桌面。本应用不会代你删除它。");
            return;
        }
        boolean isDefault = activity.isDefaultPackage(packageName);
        String message;
        if (app.isTool()) {
            message = "会从电视移除这个应用，题库、规则和使用记录会一起删除。"
                    + "这是答题工具，不会更改默认桌面。";
        } else if (isDefault) {
            message = "这是电视当前的默认桌面。卸载会先按官方恢复流程切回原桌面，"
                    + "并恢复被它改过的通知监听、无障碍、悬浮窗和开机设置，"
                    + "再移除应用和数据。不会卸载或停用系统应用，也不会让主页留空。";
        } else {
            message = "会先按官方恢复流程撤销这个应用的通知监听、无障碍、悬浮窗等系统设置，"
                    + "再移除应用和数据。不会卸载或停用系统应用，也不会影响默认桌面。";
        }
        NativeDialogs.confirmDanger(activity, "卸载 " + app.name() + "？",
                message, "确认卸载", () -> startUninstall(entry));
    }

    private void confirmSetHome(final JSONObject entry) {
        if (busy) {
            return;
        }
        NativeDialogs.confirm(activity, "切换默认桌面？",
                "将把 " + app.name() + " 设为电视默认桌面。原桌面仍保留，可随时在“我的电视”切回。"
                        + "安装器不会停用或卸载系统桌面。",
                "确认切换", () -> startSetHome(entry));
    }

    private void launch(final JSONObject entry) {
        if (launchInFlight || !activity.isConnected() || closed) {
            return;
        }
        launchInFlight = true;
        final String variantId = entry.optString("variantId", selectedVariantId);
        final String host = activity.devHost;
        final int port = activity.devPort;
        activity.toast("正在电视上打开 " + app.name() + "…");
        executor.execute(() -> {
            Exception failure = null;
            try {
                DeviceSession session = new DeviceSession(activity, line -> {
                });
                session.launch(app.id(), variantId, host, port);
            } catch (Exception error) {
                failure = error;
            }
            final Exception error = failure;
            main.post(() -> {
                launchInFlight = false;
                if (closed) {
                    return;
                }
                if (error != null) {
                    NativeDialogs.message(activity, "没有打开", safe(error));
                } else {
                    activity.toast("已在电视上打开 " + app.name());
                }
            });
        });
    }

    private void startInstall(final String variantId, final boolean makeHome) {
        if (busy || !activity.isConnected() || closed) {
            return;
        }
        UiData.Variant variant = app.variant(variantId);
        if (variant == null) {
            NativeDialogs.message(activity, "请选择版本", "请选择一个明确的安装版本后再试。");
            return;
        }
        busy = true;
        activeOperation = OP_INSTALL;
        retryVariant = variantId;
        retryHome = makeHome;
        retryEntry = null;
        opLog.setLength(0);
        updateFooter();
        showOperationPage("正在安装 " + app.name(),
                "正在准备下载 " + variant.label + "…",
                "安装会从官方来源下载并校验，然后覆盖安装到电视。不会先卸载旧版本。");
        executor.execute(() -> {
            JSONObject result = null;
            Exception failure = null;
            try {
                ApkManager manager = new ApkManager(activity,
                        this::postOpLog,
                        (phase, flavor, received, total) -> main.post(
                                () -> updateProgress(phase, received, total)));
                result = manager.install(app.id(), variantId,
                        activity.devHost, activity.devPort, makeHome);
            } catch (Exception error) {
                failure = error;
            }
            final JSONObject installed = result;
            final Exception error = failure;
            main.post(() -> {
                busy = false;
                if (opSheet != null) {
                    opSheet.setBusy(false);
                }
                updateFooter();
                activity.refreshDevice(null);
                showOperationResult(error == null, OP_INSTALL, installed, error);
            });
        });
    }

    private void startUninstall(final JSONObject entry) {
        if (busy || !activity.isConnected() || closed) {
            return;
        }
        final String variantId = entry.optString("variantId", selectedVariantId);
        final String packageName = entry.optString("packageName", "");
        busy = true;
        activeOperation = OP_UNINSTALL;
        retryEntry = entry;
        retryVariant = variantId;
        opLog.setLength(0);
        updateFooter();
        showOperationPage("正在卸载 " + app.name(),
                app.isTool()
                        ? "正在从电视移除 " + packageName + "…"
                        : "正在按官方流程恢复默认桌面与系统设置，然后移除 "
                        + packageName + "…",
                app.isTool()
                        ? "会删除题库、规则和使用记录；不会更改默认桌面。"
                        : "会先切回原桌面、撤销通知监听 / 无障碍 / 悬浮窗等设置，"
                        + "再移除应用和数据；不会卸载或停用系统应用。");
        executor.execute(() -> {
            JSONObject result = null;
            Exception failure = null;
            try {
                DeviceSession session = new DeviceSession(activity,
                        this::postOpLog);
                result = session.uninstall(app.id(), variantId,
                        activity.devHost, activity.devPort);
            } catch (Exception error) {
                failure = error;
            }
            final JSONObject removed = result;
            final Exception error = failure;
            main.post(() -> {
                busy = false;
                if (opSheet != null) {
                    opSheet.setBusy(false);
                }
                updateFooter();
                activity.refreshDevice(null);
                showOperationResult(error == null, OP_UNINSTALL, removed, error);
            });
        });
    }

    private void startSetHome(final JSONObject entry) {
        if (busy || !activity.isConnected() || closed) {
            return;
        }
        final String variantId = entry.optString("variantId", selectedVariantId);
        busy = true;
        activeOperation = OP_HOME;
        retryEntry = entry;
        retryVariant = variantId;
        opLog.setLength(0);
        updateFooter();
        showOperationPage("正在切换默认桌面",
                "会把 " + app.name() + " 设为默认 HOME 应用…",
                "原桌面仍保留。切换后可以随时在“我的电视”切回。");
        executor.execute(() -> {
            JSONObject result = null;
            Exception failure = null;
            try {
                DeviceSession session = new DeviceSession(activity,
                        this::postOpLog);
                result = session.setHome(app.id(), variantId,
                        activity.devHost, activity.devPort);
            } catch (Exception error) {
                failure = error;
            }
            final JSONObject changed = result;
            final Exception error = failure;
            main.post(() -> {
                busy = false;
                if (opSheet != null) {
                    opSheet.setBusy(false);
                }
                updateFooter();
                activity.refreshDevice(null);
                boolean confirmed = changed != null && changed.optBoolean("confirmed", false);
                // A returned-but-unconfirmed switch is not a success and it is
                // not an "unknown error" either. Surface the actual warning.
                Exception homeError = error;
                if (homeError == null && changed != null && !confirmed) {
                    String warning = changed.optString("warning", "");
                    homeError = new Exception(TextUtils.isEmpty(warning)
                            ? "电视没有确认默认桌面的切换。请在电视上按主页键并选择 "
                            + app.name() + "，然后刷新状态。"
                            : warning);
                }
                showOperationResult(confirmed, OP_HOME, changed, homeError);
            });
        });
    }

    // ------------------------------------------------------------------
    // Operation sheet
    // ------------------------------------------------------------------

    private SheetPanel ensureOperationSheet(String heading) {
        if (opSheet == null || !opSheet.isShowing()) {
            opSheet = SheetPanel.show(activity, heading, "");
            opSheet.setOnDismiss(() -> {
                opSheet = null;
                opProgress = null;
                opPhase = null;
                opLogView = null;
                if (!closed && !busy) {
                    build();
                }
            });
        }
        opSheet.setTitle(heading);
        opSheet.setSubtitle("");
        opSheet.setBusy(true);
        opSheet.clearBody();
        return opSheet;
    }

    private void showOperationPage(String heading, String phase, String note) {
        if (closed) {
            return;
        }
        SheetPanel panel = ensureOperationSheet(heading);
        LinearLayout root = UiKit.column(activity);

        LinearLayout head = UiKit.row(activity);
        RoundedImageView icon = new RoundedImageView(activity);
        icon.setCornerRadiusDp(11f);
        icon.setImageResource(MediaCatalog.icon(app.id()));
        icon.setContentDescription(app.name() + " 图标");
        head.addView(icon, new LinearLayout.LayoutParams(
                UiKit.dp(activity, 42), UiKit.dp(activity, 42)));
        LinearLayout copy = UiKit.column(activity);
        copy.addView(UiKit.text(activity, app.name(), 14f, 0xFF16212F, true));
        copy.addView(UiKit.caption(activity, app.tagline));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = UiKit.dp(activity, 10);
        head.addView(copy, copyParams);
        root.addView(head);

        opProgress = UiKit.progress(activity);
        opProgress.setIndeterminate(true);
        UiKit.add(root, opProgress, 0, 16, 0, 0);
        opPhase = UiKit.text(activity, phase, 12.5f, 0xFF16212F, true);
        opPhase.setPadding(0, UiKit.dp(activity, 9), 0, 0);
        root.addView(opPhase);

        UiKit.add(root, UiKit.note(activity, "请保持本应用在前台",
                note, UiData.TONE_INFO), 0, 12, 0, 0);

        opLogView = UiKit.logView(activity);
        opLogView.setVisibility(View.GONE);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logParams.topMargin = UiKit.dp(activity, 12);
        root.addView(opLogView, logParams);

        panel.add(root);
        refreshOpLog();
    }

    private void updateProgress(final String phase, final long received, final long total) {
        if (opProgress == null || opPhase == null) {
            return;
        }
        if ("download".equals(phase)) {
            opPhase.setText("正在下载官方安装包…");
            if (total > 0) {
                opProgress.setIndeterminate(false);
                int percent = (int) Math.min(100, Math.max(0,
                        Math.round(received * 100.0 / total)));
                opProgress.setProgress(percent);
                opPhase.setText("正在下载安装包 · " + percent + "% · "
                        + UiKit.bytes(received) + " / " + UiKit.bytes(total));
            } else {
                opProgress.setIndeterminate(true);
            }
        } else if ("download-retry".equals(phase)) {
            opPhase.setText(received > 0
                    ? "网络中断，正在从断点续传 · " + UiKit.bytes(received)
                    + (total > 0 ? " / " + UiKit.bytes(total) : "") + "…"
                    : "连接中断，正在自动重试…");
            if (total > 0) {
                opProgress.setIndeterminate(false);
                int percent = (int) Math.min(100, Math.max(0,
                        Math.round(received * 100.0 / total)));
                opProgress.setProgress(percent);
            }
        } else if ("verify".equals(phase)) {
            opProgress.setIndeterminate(true);
            opPhase.setText("正在校验包名、签名证书与 SHA-256…");
        } else if ("install".equals(phase)) {
            opProgress.setIndeterminate(true);
            opPhase.setText("正在推送到电视并安装，可能需要几分钟…");
        } else if ("done".equals(phase)) {
            opProgress.setIndeterminate(false);
            opProgress.setProgress(100);
            opPhase.setText("安装完成，正在核对电视上的版本…");
        }
    }

    private void showOperationResult(boolean success, String operation,
                                     JSONObject data, Exception error) {
        if (closed) {
            return;
        }
        SheetPanel panel = ensureOperationSheet(success ? "操作完成" : "操作未完成");
        panel.setBusy(false);
        panel.clearBody();
        panel.setTitle(success ? "操作完成" : "操作未完成");
        panel.setSubtitle("");
        LinearLayout root = UiKit.column(activity);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        if (success) {
            root.addView(iconTile("check", 0xFF129B67, 0xFFEAF9F2));
            TextView title = UiKit.title(activity, resultTitle(operation, data));
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, UiKit.dp(activity, 10), 0, 0);
            root.addView(title);
            String detail = resultDetail(operation, data);
            if (!TextUtils.isEmpty(detail)) {
                TextView caption = UiKit.caption(activity, detail);
                caption.setGravity(Gravity.CENTER);
                caption.setPadding(0, UiKit.dp(activity, 5), 0, UiKit.dp(activity, 4));
                root.addView(caption);
            }
            addResultWarnings(root, operation, data);
        } else {
            root.addView(iconTile("warning", 0xFFDE464D, 0xFFFDEEEF));
            TextView title = UiKit.title(activity, "没有完成");
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, UiKit.dp(activity, 10), 0, 0);
            root.addView(title);
            TextView detail = UiKit.text(activity, safe(error), 12.5f, 0xFF4C5C70, false);
            detail.setGravity(Gravity.CENTER);
            detail.setLineSpacing(UiKit.dp(activity, 3), 1f);
            detail.setPadding(0, UiKit.dp(activity, 7), 0, UiKit.dp(activity, 4));
            root.addView(detail);
            UiKit.add(root, UiKit.note(activity, "电视状态没有被跳过",
                    "失败不会自动卸载、不会切换桌面。可以检查连接与电视端限制后重试。",
                    UiData.TONE_WARN), 0, 10, 0, 0);
        }

        if (opLog.length() > 0) {
            opLogView = UiKit.logView(activity);
            opLogView.setText(opLog.toString());
            LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            logParams.topMargin = UiKit.dp(activity, 12);
            root.addView(opLogView, logParams);
        }

        TextView view = UiKit.button(activity, "查看我的电视", UiKit.BTN_PRIMARY);
        view.setOnClickListener(v -> activity.switchTab(MainActivity.TAB_DEVICE));
        UiKit.add(root, view, 0, 14, 0, 0);

        if (!success) {
            TextView retry = UiKit.button(activity, "重试", UiKit.BTN_AMBER);
            retry.setOnClickListener(v -> retry());
            UiKit.add(root, retry, 0, 8, 0, 0);
        }
        TextView back = UiKit.button(activity, "返回应用详情", UiKit.BTN_SECONDARY);
        back.setOnClickListener(v -> {
            if (opSheet != null) {
                opSheet.dismiss();
            }
        });
        UiKit.add(root, back, 0, 8, 0, 0);

        panel.add(root);
    }

    private void addResultWarnings(LinearLayout root, String operation, JSONObject data) {
        if (data == null) {
            return;
        }
        if (OP_INSTALL.equals(operation)) {
            String homeWarning = data.optString("homeWarning", "");
            JSONObject home = data.optJSONObject("home");
            if (!TextUtils.isEmpty(homeWarning)) {
                UiKit.add(root, UiKit.note(activity, "应用已安装，默认桌面未切换",
                        homeWarning, UiData.TONE_WARN), 0, 10, 0, 0);
            } else if (home != null) {
                boolean confirmed = home.optBoolean("confirmed", false);
                String warning = home.optString("warning", "");
                if (confirmed) {
                    UiKit.add(root, UiKit.note(activity, "默认桌面已切换",
                            "电视已确认把默认 HOME 指向 " + app.name() + "。原桌面仍保留。",
                            UiData.TONE_GREEN), 0, 10, 0, 0);
                } else {
                    UiKit.add(root, UiKit.note(activity, "默认桌面没有确认切换",
                            TextUtils.isEmpty(warning)
                                    ? "请在电视上按主页键，选择 " + app.name() + "。"
                                    : warning, UiData.TONE_WARN), 0, 10, 0, 0);
                }
            } else if (data.optBoolean("homeKept", false)) {
                UiKit.add(root, UiKit.note(activity, "默认桌面保持原样",
                        "应用已安装，但没有改动电视默认桌面。可在“我的电视”里切换。",
                        UiData.TONE_INFO), 0, 10, 0, 0);
            }
        } else if (OP_UNINSTALL.equals(operation)) {
            String warning = data.optString("warning", "");
            if (!TextUtils.isEmpty(warning)) {
                UiKit.add(root, UiKit.note(activity, "卸载完成，请检查电视",
                        warning, UiData.TONE_WARN), 0, 10, 0, 0);
            } else if (data.optBoolean("systemRestored", false)) {
                String label = data.optString("homeLabel", "");
                String text = data.optBoolean("homeSwitched", false)
                        ? "默认桌面已切回" + (TextUtils.isEmpty(label) ? "原桌面" : " " + label)
                        + "；通知监听、无障碍、悬浮窗和开机设置已按官方流程恢复。"
                        : "通知监听、无障碍、悬浮窗和开机设置已按官方流程恢复；"
                        + "默认桌面没有被改动。";
                UiKit.add(root, UiKit.note(activity, "系统已复原", text,
                        UiData.TONE_GREEN), 0, 10, 0, 0);
            } else if (app.isTool()) {
                UiKit.add(root, UiKit.note(activity, "默认桌面未改动",
                        "这是答题工具，卸载不会触碰电视的默认桌面。",
                        UiData.TONE_GREEN), 0, 10, 0, 0);
            }
        } else if (OP_HOME.equals(operation)) {
            boolean confirmed = data.optBoolean("confirmed", false);
            String warning = data.optString("warning", "");
            if (confirmed) {
                UiKit.add(root, UiKit.note(activity, "电视已确认",
                        app.name() + " 现在是默认桌面。原桌面仍保留，可随时切回。",
                        UiData.TONE_GREEN), 0, 10, 0, 0);
            } else {
                UiKit.add(root, UiKit.note(activity, "还没有确认切换",
                        TextUtils.isEmpty(warning)
                                ? "系统没有立即确认。请在电视上按主页键并选择 " + app.name() + "。"
                                : warning, UiData.TONE_WARN), 0, 10, 0, 0);
            }
        }
    }

    private String resultTitle(String operation, JSONObject data) {
        if (OP_INSTALL.equals(operation)) {
            return app.name() + " 已安装";
        }
        if (OP_UNINSTALL.equals(operation)) {
            return app.name() + " 已卸载";
        }
        return "默认桌面已切换";
    }

    private String resultDetail(String operation, JSONObject data) {
        if (data == null) {
            return "";
        }
        if (OP_INSTALL.equals(operation)) {
            String version = data.optString("versionName", "");
            String source = data.optString("source", "");
            String detail = TextUtils.isEmpty(version)
                    ? "安装包已在电视上核对到。"
                    : "电视上的版本：" + version;
            return TextUtils.isEmpty(source) ? detail : detail + " · " + source;
        }
        if (OP_UNINSTALL.equals(operation)) {
            if (data.optBoolean("alreadyAbsent", false)) {
                return "电视上本来就没有这个包，已按完成处理。";
            }
            if (data.optBoolean("systemRestored", false)) {
                String label = data.optString("homeLabel", "");
                return data.optBoolean("homeSwitched", false)
                        ? "已移除应用和数据；默认桌面已切回"
                        + (TextUtils.isEmpty(label) ? "原桌面" : " " + label) + "。"
                        : "已移除应用和数据；系统设置已恢复，默认桌面未改动。";
            }
            return app.isTool()
                    ? "已移除应用和数据；默认桌面没有改动。"
                    : "已移除这个第三方应用及其数据。";
        }
        return "";
    }

    private void retry() {
        if (busy) {
            return;
        }
        if (OP_INSTALL.equals(activeOperation)) {
            startInstall(retryVariant, retryHome);
        } else if (OP_UNINSTALL.equals(activeOperation) && retryEntry != null) {
            startUninstall(retryEntry);
        } else if (OP_HOME.equals(activeOperation) && retryEntry != null) {
            startSetHome(retryEntry);
        }
    }

    private void postOpLog(final String line) {
        main.post(() -> {
            if (TextUtils.isEmpty(line)) {
                return;
            }
            if (opLog.length() > 0) {
                opLog.append('\n');
            }
            opLog.append(line);
            if (opLog.length() > 14000) {
                opLog.delete(0, opLog.length() - 14000);
            }
            refreshOpLog();
        });
    }

    private void refreshOpLog() {
        if (opLogView == null) {
            return;
        }
        opLogView.setText(opLog.toString());
        opLogView.setVisibility(opLog.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private Catalog.Variant catalogVariant(String variantId) {
        if (variantId == null) {
            return null;
        }
        return app.catalog.variants.get(variantId);
    }

    private String variantLabel(String variantId, String packageName) {
        UiData.Variant variant = app.variant(variantId);
        if (variant != null) {
            return variant.label;
        }
        if (!TextUtils.isEmpty(variantId)) {
            return variantId;
        }
        return TextUtils.isEmpty(packageName) ? app.name() : packageName;
    }

    private View iconTile(String iconName, int tint, int background) {
        FrameLayout wrap = new FrameLayout(activity);
        wrap.setBackground(UiKit.roundRect(background, UiKit.dp(activity, 26), 0, 0));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                UiKit.dp(activity, 28), UiKit.dp(activity, 28));
        iconParams.gravity = Gravity.CENTER;
        wrap.addView(UiKit.icon(activity, iconName, 28, tint), iconParams);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                UiKit.dp(activity, 56), UiKit.dp(activity, 56)));
        return wrap;
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
}