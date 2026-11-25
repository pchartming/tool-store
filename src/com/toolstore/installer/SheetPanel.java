package com.toolstore.installer;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Framework Dialog used as a bottom sheet. No Material dependency. */
final class SheetPanel {

    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout panel;
    private final MaxHeightScrollView scroll;
    private final LinearLayout body;
    private final TextView title;
    private final TextView subtitle;
    private final View close;
    private final View handle;
    private Runnable onDismiss;
    private boolean busy;

    private SheetPanel(Activity activity, String titleText, String subtitleText) {
        this.activity = activity;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(activity).inflate(R.layout.sheet_host, null, false);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.32f);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        }
        dialog.setCanceledOnTouchOutside(true);
        panel = (LinearLayout) root.findViewById(R.id.sheetPanel);
        scroll = (MaxHeightScrollView) root.findViewById(R.id.sheetScroll);
        body = (LinearLayout) root.findViewById(R.id.sheetBody);
        title = (TextView) root.findViewById(R.id.sheetTitle);
        subtitle = (TextView) root.findViewById(R.id.sheetSubtitle);
        close = root.findViewById(R.id.sheetClose);
        handle = root.findViewById(R.id.sheetHandle);
        title.setText(titleText == null ? "" : titleText);
        if (TextUtils.isEmpty(subtitleText)) {
            subtitle.setVisibility(View.GONE);
        } else {
            subtitle.setText(subtitleText);
            subtitle.setVisibility(View.VISIBLE);
        }
        UiKit.prepareIcon(root, R.id.sheetCloseIcon, "close", 0xFF8391A3);
        close.setOnClickListener(v -> dismiss());
        dialog.setOnDismissListener(d -> {
            busy = false;
            Runnable callback = onDismiss;
            onDismiss = null;
            if (callback != null) {
                callback.run();
            }
        });
    }

    static SheetPanel show(Activity activity, String title, String subtitle) {
        SheetPanel sheet = new SheetPanel(activity, title, subtitle);
        sheet.dialog.show();
        return sheet;
    }

    Activity activity() {
        return activity;
    }

    Dialog dialog() {
        return dialog;
    }

    LinearLayout body() {
        return body;
    }

    void setTitle(String value) {
        title.setText(value == null ? "" : value);
    }

    void setSubtitle(String value) {
        if (TextUtils.isEmpty(value)) {
            subtitle.setVisibility(View.GONE);
        } else {
            subtitle.setText(value);
            subtitle.setVisibility(View.VISIBLE);
        }
    }

    void setBody(View view) {
        body.removeAllViews();
        body.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.scrollTo(0, 0);
    }

    /** Bring the sheet back to its first row after content is replaced. */
    void scrollToTop() {
        scroll.post(() -> {
            if (dialog.isShowing()) {
                scroll.smoothScrollTo(0, 0);
            }
        });
    }

    /** Bring a specific row into view after its section is expanded. */
    void scrollToView(View view) {
        if (view == null) {
            return;
        }
        scroll.post(() -> {
            if (!dialog.isShowing() || view.getParent() == null) {
                return;
            }
            int[] viewLocation = new int[2];
            int[] scrollLocation = new int[2];
            view.getLocationInWindow(viewLocation);
            scroll.getLocationInWindow(scrollLocation);
            int target = scroll.getScrollY() + viewLocation[1] - scrollLocation[1];
            scroll.smoothScrollTo(0, Math.max(0, target));
        });
    }

    void clearBody() {
        body.removeAllViews();
    }

    void add(View view) {
        body.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void add(View view, LinearLayout.LayoutParams params) {
        body.addView(view, params);
    }

    ViewGroup.LayoutParams bodyParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    void setOnDismiss(Runnable callback) {
        onDismiss = callback;
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    boolean isBusy() {
        return busy;
    }

    void setBusy(boolean value) {
        busy = value;
        dialog.setCanceledOnTouchOutside(!value);
        dialog.setCancelable(!value);
        handle.setVisibility(View.VISIBLE);
        close.setVisibility(View.VISIBLE);
        close.setAlpha(value ? 0.35f : 1f);
        close.setEnabled(!value);
    }

    /** Called after showing to focus an input without immediately stealing the animation. */
    void focusInput(final View view) {
        if (view == null) {
            return;
        }
        view.postDelayed(() -> {
            if (!dialog.isShowing()) {
                return;
            }
            view.requestFocus();
            InputMethodManager keyboard = (InputMethodManager)
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 160L);
    }

    void hideKeyboard() {
        View focus = activity.getCurrentFocus();
        InputMethodManager keyboard = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null && focus != null) {
            keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}