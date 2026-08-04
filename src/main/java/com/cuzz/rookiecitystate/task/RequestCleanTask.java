package com.cuzz.rookiecitystate.task;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.request.RequestManager;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import org.bukkit.scheduler.BukkitRunnable;

public class RequestCleanTask extends BukkitRunnable {
    private final RequestManager requestManager = RookieCityState.inst().getRequestManager();

    @Override
    public void run() {
        requestManager.getRequests().forEach(request -> {
            boolean valid;
            try {
                valid = request.isValid();
            } catch (RuntimeException exception) {
                valid = false;
                PluginLogger.warning("请求有效性检查失败 " + request.getUuid() + ": " + exception.getMessage());
            }
            if (!valid) {
                try {
                    requestManager.deleteRequest(request);
                } catch (RuntimeException exception) {
                    PluginLogger.warning("失效请求删除失败 " + request.getUuid() + ": " + exception.getMessage());
                }
            }
        });
    }
}
