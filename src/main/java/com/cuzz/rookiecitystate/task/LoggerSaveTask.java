package com.cuzz.rookiecitystate.task;

import com.cuzz.rookiecitystate.logger.PluginLogger;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

public class LoggerSaveTask extends BukkitRunnable {
	@Override
	public void run() {

		PluginLogger.flushWriter();
	}
}
