package com.depchain.consensus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Timeout {
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> task;
    private long timeout;
    private final long INICIAL_TIMEOUT = 2000;

    public Timeout() {
        this.timeout = INICIAL_TIMEOUT;
    }

    public void startTime(Runnable onTimeoutAction) {
        cancel();

        this.task = scheduler.schedule(() -> {
            dublicateTimeTask();
            onTimeoutAction.run();
        }, timeout, TimeUnit.MILLISECONDS);
    }

    public void resetTime() {
        cancel();

        this.timeout = INICIAL_TIMEOUT;
    }

    private void dublicateTimeTask() {
        this.timeout *= 2;
    }

    public void cancel() {
        if (task != null) {
            task.cancel(false);
        }
    }
}