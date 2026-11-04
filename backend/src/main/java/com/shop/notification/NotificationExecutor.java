package com.shop.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Runs email work off the request thread (a small pool, so a slow mail server never slows the
 * shop). With app.notifications.async=false (tests) it runs immediately on the caller's thread.
 */
@Component
public class NotificationExecutor implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NotificationExecutor.class);

    private final ThreadPoolTaskExecutor pool;

    public NotificationExecutor(@Value("${app.notifications.async:true}") boolean async) {
        if (async) {
            pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(4);
            pool.setQueueCapacity(1000);
            pool.setThreadNamePrefix("mail-");
            pool.setWaitForTasksToCompleteOnShutdown(true);
            pool.setAwaitTerminationSeconds(20);
            pool.initialize();
        } else {
            pool = null;
        }
    }

    public void run(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                log.error("Notification failed: {}", ex.getMessage(), ex);
            }
        };
        if (pool == null) {
            safe.run();
        } else {
            pool.execute(safe);
        }
    }

    @Override
    public void destroy() {
        if (pool != null) pool.shutdown();
    }
}
