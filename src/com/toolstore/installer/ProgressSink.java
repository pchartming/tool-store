package com.toolstore.installer;

/** Download/install progress events sent to the local UI. */
interface ProgressSink {
    void update(String phase, String flavor, long received, long total);
}