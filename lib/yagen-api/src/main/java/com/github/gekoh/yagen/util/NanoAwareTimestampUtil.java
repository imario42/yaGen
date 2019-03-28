package com.github.gekoh.yagen.util;

import java.sql.Timestamp;

class NanoAwareTimestampUtil {
    private static final long NANO_DIFF = (System.currentTimeMillis() * 1000000L) - System.nanoTime();

    static Timestamp getCurrentTimestamp() {
        long nanoTime = System.nanoTime() + NANO_DIFF;
        Timestamp timestamp = new Timestamp(nanoTime / 1000000L);
        long nanos = nanoTime % 1000000000L;
        timestamp.setNanos((int) nanos);
        return timestamp;
    }
}
