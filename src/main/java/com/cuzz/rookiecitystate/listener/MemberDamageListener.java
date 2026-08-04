package com.cuzz.rookiecitystate.listener;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.player.CityStatePlayerManager;
import com.cuzz.rookiecitystate.util.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 成员免伤
 */
public class MemberDamageListener implements Listener {
    private RookieCityState plugin = RookieCityState.inst();
    private CityStatePlayerManager cityStatePlayerManager = plugin.getCityStatePlayerManager();
    private Map<UUID, Long> msgIntervalMap = new HashMap<>();

    @EventHandler
    public void onEntityDamageEvent(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity target = event.getEntity();

        if (!(target instanceof Player)) {
            return;
        }

        Player damagerBukkitPlayer;
        if (damager instanceof Player player) {
            damagerBukkitPlayer = player;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            damagerBukkitPlayer = player;
        } else {
            return;
        }
        CityStatePlayer damagerCityStatePlayer = cityStatePlayerManager.getCityStatePlayer(damagerBukkitPlayer);

        if (!damagerCityStatePlayer.isInCityState()) {
            return;
        }

        CityState cityState = damagerCityStatePlayer.getCityState();

        if (!cityState.isMember(target.getUniqueId())) {
            return;
        }

        UUID damagerUuid = damagerBukkitPlayer.getUniqueId();

        // 开启了成员免伤
        if (!cityState.isMemberDamageEnabled()) {
            event.setCancelled(true);

            if (!msgIntervalMap.containsKey(damagerUuid) || (System.currentTimeMillis() - msgIntervalMap.get(damagerUuid)) / 1000L > MainSettings.getCityStateMemberDamageDisableNoticeInterval()) {
                Util.sendMsg(damagerBukkitPlayer, plugin.getLangYaml().getString("CityState.member_damage_disabled"));
                msgIntervalMap.put(damagerUuid, System.currentTimeMillis());
            }
        }
    }
}
