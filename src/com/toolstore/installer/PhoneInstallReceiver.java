package com.toolstore.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/**
 * Receives the PackageInstaller session result for the community Clean Your TV
 * phone app. The receiver is declared non-exported in the manifest; only the
 * PendingIntent created by this app can reach it.
 */
public final class PhoneInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !PhoneAppInstaller.ACTION_RESULT.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,
                intent.getIntExtra(PhoneAppInstaller.EXTRA_SESSION, -1));

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = null;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                } catch (Throwable ignored) {
                    // The wait will time out with a truthful message instead
                    // of pretending the install completed.
                }
            }
            return;
        }
        PhoneAppInstaller.onSessionResult(sessionId, status);
    }
}