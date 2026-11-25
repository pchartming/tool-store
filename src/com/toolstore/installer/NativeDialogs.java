package com.toolstore.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

/** Small framework-only dialog helpers. */
final class NativeDialogs {

    private NativeDialogs() {
    }

    static void message(Activity activity, String title, String body) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("知道了", null)
                .show();
    }

    static void confirm(Activity activity, String title, String body,
                        String positive, Runnable action) {
        confirm(activity, title, body, positive, false, action);
    }

    static void confirmDanger(Activity activity, String title, String body,
                              String positive, Runnable action) {
        confirm(activity, title, body, positive, true, action);
    }

    private static void confirm(Activity activity, String title, String body,
                                String positive, final boolean danger, final Runnable action) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton("取消", null)
                .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (action != null) {
                            action.run();
                        }
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            if (danger) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(0xFFDE464D);
            }
        });
        dialog.show();
    }
}