package com.toolstore.installer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

/** Shared native view helpers. All screens use framework widgets only. */
final class UiKit {

    static final int BTN_PRIMARY = 1;
    static final int BTN_SECONDARY = 2;
    static final int BTN_AMBER = 3;
    static final int BTN_DANGER = 4;
    static final int BTN_GHOST = 5;

    private UiKit() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static final class Tint {
        final int text;
        final int background;
        final int border;

        Tint(int text, int background, int border) {
            this.text = text;
            this.background = background;
            this.border = border;
        }
    }

    static Tint tint(Context context, int tone) {
        if (tone == UiData.TONE_WARN) {
            return new Tint(0xFF856723, 0xFFFFF8EA, 0xFFF2DDB2);
        }
        if (tone == UiData.TONE_RED) {
            return new Tint(0xFF9E3238, 0xFFFDEEEF, 0xFFF4CDCF);
        }
        if (tone == UiData.TONE_GREEN) {
            return new Tint(0xFF147954, 0xFFEAF9F2, 0xFFC9EDDD);
        }
        if (tone == UiData.TONE_INFO) {
            return new Tint(0xFF33618F, 0xFFF1F7FF, 0xFFD5E5FF);
        }
        return new Tint(0xFF647488, 0xFFF1F5FA, 0xFFE6EBF2);
    }

    static TextView text(Context context, String value, float sizeSp, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, 2f), 1f);
        view.setTypeface(Typeface.create("sans-serif", medium
                ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    static TextView title(Context context, String value) {
        return text(context, value, 16f, 0xFF16212F, true);
    }

    static TextView caption(Context context, String value) {
        TextView view = text(context, value, 11.5f, 0xFF8391A3, false);
        view.setLineSpacing(dp(context, 2.5f), 1f);
        return view;
    }

    static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout card(Context context) {
        LinearLayout layout = column(context);
        layout.setBackgroundResource(R.drawable.bg_card);
        layout.setPadding(dp(context, 14f), dp(context, 14f),
                dp(context, 14f), dp(context, 14f));
        layout.setElevation(dp(context, 1.5f));
        return layout;
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(0xFFE6EBF2);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.8f))));
        return view;
    }

    static View spacer(Context context, float heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        return view;
    }

    static LineIconView icon(Context context, String name, int sizeDp, int color) {
        LineIconView view = new LineIconView(context);
        view.setIcon(name);
        view.setIconColor(color);
        view.setContentDescription(name);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return view;
    }

    static void prepareIcon(View root, int id, String name, int color) {
        View view = root.findViewById(id);
        if (view instanceof LineIconView) {
            LineIconView icon = (LineIconView) view;
            icon.setIcon(name);
            icon.setIconColor(color);
            icon.setContentDescription(name);
        }
    }

    static void setIcon(View root, int id, String name) {
        View view = root.findViewById(id);
        if (view instanceof LineIconView) {
            ((LineIconView) view).setIcon(name);
        }
    }

    static void tintIcon(View root, int id, int color) {
        View view = root.findViewById(id);
        if (view instanceof LineIconView) {
            ((LineIconView) view).setIconColor(color);
        }
    }

    static TextView button(Context context, String label, int style) {
        TextView view = text(context, label, 13f,
                style == BTN_PRIMARY ? Color.WHITE
                        : style == BTN_DANGER ? 0xFFDE464D
                        : style == BTN_AMBER ? 0xFFB9770E
                        : style == BTN_GHOST ? 0xFF4C5C70
                        : 0xFF2F7DFF, true);
        view.setGravity(Gravity.CENTER);
        int height = style == BTN_GHOST ? 40 : 44;
        view.setMinHeight(dp(context, height));
        view.setMinWidth(dp(context, style == BTN_GHOST ? 44 : 64));
        view.setPadding(dp(context, 16f), dp(context, 8f), dp(context, 16f), dp(context, 8f));
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackgroundResource(style == BTN_PRIMARY ? R.drawable.bg_button_primary
                : style == BTN_AMBER ? R.drawable.bg_button_amber
                : style == BTN_DANGER ? R.drawable.bg_button_danger
                : style == BTN_GHOST ? 0
                : R.drawable.bg_button_secondary);
        if (style == BTN_GHOST) {
            view.setPadding(dp(context, 2f), dp(context, 8f), dp(context, 2f), dp(context, 8f));
            view.setMinWidth(0);
        }
        return view;
    }

    static TextView smallButton(Context context, String label, int style) {
        TextView view = button(context, label, style);
        view.setTextSize(12f);
        view.setMinHeight(dp(context, 36f));
        view.setMinWidth(dp(context, 54f));
        view.setPadding(dp(context, 11f), dp(context, 6f), dp(context, 11f), dp(context, 6f));
        return view;
    }

    static void setEnabled(TextView button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.45f);
        if (!enabled) {
            button.setBackgroundResource(R.drawable.bg_button_disabled);
            button.setTextColor(0xFF8391A3);
        }
    }

    static TextView badge(Context context, String label, int tone) {
        Tint tint = tint(context, tone);
        TextView view = text(context, label, 10f, tint.text, true);
        view.setBackground(roundRect(tint.background, dp(context, 5f), tint.border, 1));
        view.setPadding(dp(context, 6f), dp(context, 2f), dp(context, 6f), dp(context, 2f));
        view.setGravity(Gravity.CENTER);
        return view;
    }

    static LinearLayout note(Context context, String titleText, String bodyText, int tone) {
        Tint tint = tint(context, tone);
        LinearLayout box = row(context);
        box.setBackground(roundRect(tint.background, dp(context, 13f), tint.border, 1));
        box.setPadding(dp(context, 11f), dp(context, 10f), dp(context, 11f), dp(context, 10f));
        box.setGravity(Gravity.TOP);

        LineIconView icon = icon(context, tone == UiData.TONE_RED ? "warning"
                : tone == UiData.TONE_WARN ? "warning"
                : tone == UiData.TONE_GREEN ? "check" : "info", 18, tint.text);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(context, 18f), dp(context, 18f));
        iconParams.rightMargin = dp(context, 8f);
        iconParams.topMargin = dp(context, 1f);
        box.addView(icon, iconParams);

        LinearLayout copy = column(context);
        if (!TextUtils.isEmpty(titleText)) {
            TextView title = text(context, titleText, 11.5f, tint.text, true);
            title.setPadding(0, 0, 0, dp(context, 2f));
            copy.addView(title);
        }
        if (!TextUtils.isEmpty(bodyText)) {
            TextView body = text(context, bodyText, 11.5f, tint.text, false);
            body.setLineSpacing(dp(context, 3f), 1f);
            copy.addView(body);
        }
        box.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    static LinearLayout kv(Context context, String label, String value, boolean mono) {
        LinearLayout row = row(context);
        row.setPadding(0, dp(context, 8f), 0, dp(context, 8f));
        TextView key = text(context, label, 12f, 0xFF8391A3, false);
        row.addView(key, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = text(context, value == null ? "" : value, mono ? 11f : 12.5f,
                0xFF16212F, true);
        val.setGravity(Gravity.END);
        if (mono) {
            val.setTypeface(Typeface.MONOSPACE);
        }
        val.setMaxWidth(dp(context, 220f));
        row.addView(val, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    static LinearLayout sectionHeader(Context context, String title, String subtitle, String count) {
        LinearLayout row = row(context);
        row.setGravity(Gravity.BOTTOM);
        LinearLayout copy = column(context);
        LinearLayout titleRow = row(context);
        titleRow.addView(text(context, title, 16f, 0xFF16212F, true));
        copy.addView(titleRow);
        if (!TextUtils.isEmpty(subtitle)) {
            TextView sub = caption(context, subtitle);
            sub.setPadding(0, dp(context, 3f), 0, 0);
            copy.addView(sub);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!TextUtils.isEmpty(count)) {
            TextView countView = text(context, count, 11f, 0xFF8391A3, false);
            row.addView(countView);
        }
        return row;
    }

    static TextView filterChip(Context context, String label, boolean active) {
        TextView view = text(context, label, 12f, active ? Color.WHITE : 0xFF4C5C70, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 32f));
        view.setPadding(dp(context, 13f), dp(context, 5f), dp(context, 13f), dp(context, 5f));
        view.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    static EditText input(Context context, String hint, int inputType) {
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setTextSize(14f);
        edit.setTextColor(0xFF16212F);
        edit.setHintTextColor(0xFF9AA7B7);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setBackgroundResource(R.drawable.bg_input);
        edit.setPadding(dp(context, 12f), dp(context, 10f), dp(context, 12f), dp(context, 10f));
        edit.setSelectAllOnFocus(false);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});
        return edit;
    }

    static LinearLayout fieldLabel(Context context, String label) {
        LinearLayout row = row(context);
        row.setPadding(0, dp(context, 9f), 0, dp(context, 5f));
        row.addView(text(context, label, 12f, 0xFF4C5C70, true));
        return row;
    }

    static Switch toggle(Context context, boolean checked) {
        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        toggle.setShowText(false);
        toggle.setMinWidth(dp(context, 44f));
        toggle.setMinHeight(dp(context, 32f));
        if (Build.VERSION.SDK_INT >= 23) {
            ColorStateList thumb = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{}
                    },
                    new int[]{0xFF2F7DFF, 0xFFFFFFFF});
            ColorStateList track = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{}
                    },
                    new int[]{0xFFB9D4FF, 0xFFDCE4EE});
            toggle.setThumbTintList(thumb);
            toggle.setTrackTintList(track);
        }
        return toggle;
    }

    static LinearLayout switchRow(Context context, String titleText, String subtitleText,
                                  boolean checked, final OnToggle listener) {
        LinearLayout row = row(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column(context);
        copy.addView(text(context, titleText, 13f, 0xFF16212F, true));
        if (!TextUtils.isEmpty(subtitleText)) {
            TextView sub = caption(context, subtitleText);
            sub.setPadding(0, dp(context, 3f), dp(context, 8f), 0);
            copy.addView(sub);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch toggle = toggle(context, checked);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onChanged(isChecked);
            }
        });
        row.addView(toggle);
        return row;
    }

    interface OnToggle {
        void onChanged(boolean checked);
    }

    static ProgressBar progress(Context context) {
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.setProgressTintList(ColorStateList.valueOf(0xFF2F7DFF));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE6EBF2));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 5f)));
        return bar;
    }

    static TextView logView(Context context) {
        TextView log = text(context, "", 10.5f, 0xFF50657D, false);
        log.setTypeface(Typeface.MONOSPACE);
        log.setBackgroundResource(R.drawable.bg_log);
        log.setPadding(dp(context, 10f), dp(context, 9f), dp(context, 10f), dp(context, 9f));
        log.setTextIsSelectable(true);
        log.setLineSpacing(dp(context, 2f), 1f);
        return log;
    }

    static View dot(Context context, int color, int sizeDp) {
        View dot = new View(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return dot;
    }

    static RoundedImageView roundedImage(Context context, int imageRes,
                                         int widthDp, int heightDp, float radiusDp) {
        RoundedImageView view = new RoundedImageView(context);
        view.setImageResource(imageRes);
        view.setCornerRadiusDp(radiusDp);
        view.setContentDescription("");
        view.setLayoutParams(new LinearLayout.LayoutParams(
                dp(context, widthDp), dp(context, heightDp)));
        return view;
    }

    static GradientDrawable roundRect(int color, float radiusPx, int strokeColor, int strokeWidthDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(radiusPx);
        if (strokeColor != 0) {
            shape.setStroke(Math.max(1, strokeWidthDp), strokeColor);
        }
        return shape;
    }

    static Drawable ripple(int color, int radiusPx) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(0x1A2F7DFF), shape, null);
    }

    static void add(LinearLayout parent, View child, int leftDp, int topDp, int rightDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(parent.getContext(), leftDp), dp(parent.getContext(), topDp),
                dp(parent.getContext(), rightDp), dp(parent.getContext(), bottomDp));
        parent.addView(child, params);
    }

    static void setMargins(View view, int leftDp, int topDp, int rightDp, int bottomDp) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMargins(dp(view.getContext(), leftDp), dp(view.getContext(), topDp),
                    dp(view.getContext(), rightDp), dp(view.getContext(), bottomDp));
            view.setLayoutParams(params);
        }
    }

    static String bytes(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", mb);
    }

    static String yes(boolean value) {
        return value ? "是" : "否";
    }

    static void setClickableRowBackground(View view) {
        view.setBackground(ripple(0xFFFFFFFF, dp(view.getContext(), 14f)));
        view.setClickable(true);
        view.setFocusable(true);
    }
}