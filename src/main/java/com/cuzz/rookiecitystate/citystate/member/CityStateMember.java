package com.cuzz.rookiecitystate.citystate.member;

import com.cuzz.rookiecitystate.citystate.*;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.Receiver;
import com.cuzz.rookiecitystate.request.Sender;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class CityStateMember implements CityStateHuman, Receiver, Sender {
    private CityState cityState;
    private CityStatePlayer cityStatePlayer;
    private UUID uuid;
    private ConfigurationSection section;
    private Set<CityStatePermission> permissions = new HashSet<>();
    private long joinTime;
    private Map<CityStateBank.BalanceType, BigDecimal> donatedMap = new HashMap<>();
    private CityStateMemberSign sign;

    public CityStateMember(@NotNull CityState cityState, @NotNull CityStatePlayer cityStatePlayer) {
        this.cityState = cityState;
        this.cityStatePlayer = cityStatePlayer;
        this.uuid = cityStatePlayer.getUuid();

        load();

        this.sign = new CityStateMemberSign(this);
    }

    public CityStateMemberSign getSign() {
        return sign;
    }

    private void load() {
        if (!cityState.getYaml().contains("members")) {
            cityState.getYaml().createSection("members");
        }

        this.section = cityState.getYaml().getConfigurationSection("members").getConfigurationSection(uuid.toString());

        if (section.contains("permissions")) {
            List<String> permissions = section.getStringList("permissions");

            if (permissions != null) {
                permissions.forEach(s -> this.permissions.add(CityStatePermission.valueOf(s)));
            }
        }

        if (section.contains("donated")) {
            for (String type : section.getConfigurationSection("donated").getKeys(false)) {
                CityStateBank.BalanceType balanceType = CityStateBank.BalanceType.valueOf(type);

                donatedMap.put(balanceType, new BigDecimal(section.getString("donated." + balanceType.name(), "0")));
            }
        }

        this.joinTime = section.getLong("join_time");
    }

    public String getName() {
        return getCityStatePlayer().getName();
    }

    public UUID getUuid() {
        return uuid;
    }

    /**
     * 设置权限
     * @param cityStatePermission
     * @param b true 为设置 false 为删除
     */
    public void setPermission(@NotNull CityStatePermission cityStatePermission, boolean b) {
        Set<CityStatePermission> newCityStatePermissions = getPermissions();
        Set<CityStatePermission> previous = getPermissions();

        if (b) {
            newCityStatePermissions.add(cityStatePermission);
        } else {
            newCityStatePermissions.remove(cityStatePermission);
        }

        section.set("permissions", newCityStatePermissions.stream().map(Enum::name).collect(Collectors.toList()));
        try {
            save();
        } catch (RuntimeException exception) {
            section.set("permissions", previous.stream().map(Enum::name).collect(Collectors.toList()));
            throw exception;
        }
        this.permissions = newCityStatePermissions;
    }

    public Set<CityStatePermission> getPermissions() {
        return new HashSet<>(permissions);
    }

    public boolean hasPermission(@NotNull CityStatePermission cityStatePermission) {
        return getPosition() == CityStatePosition.OWNER || getPermissions().contains(cityStatePermission);
    }

    public void addDonated(@NotNull CityStateBank.BalanceType balanceType, double amount) {
        if (amount <= 0) {
            throw new RuntimeException("数量必须大于0");
        }

        setDonated(balanceType, getDonated(balanceType).add(BigDecimal.valueOf(amount)));
    }

    public BigDecimal getDonated(@NotNull CityStateBank.BalanceType balanceType) {
        return donatedMap.getOrDefault(balanceType, BigDecimal.ZERO);
    }

    public void setDonated(@NotNull CityStateBank.BalanceType balanceType, @NotNull BigDecimal value) {
        if (value.signum() < 0) throw new IllegalArgumentException("赞助金额不能为负数");
        BigDecimal previous = getDonated(balanceType);
        section.set("donated." + balanceType.name(), value.toString());
        try {
            save();
        } catch (RuntimeException exception) {
            section.set("donated." + balanceType.name(), previous.toString());
            throw exception;
        }
        donatedMap.put(balanceType, value);
    }

    public long getJoinTime() {
        return joinTime;
    }

    public CityState getCityState() {
        return cityState;
    }

    public CityStatePlayer getCityStatePlayer() {
        return cityStatePlayer;
    }

    public boolean isOnline() {
        return getCityStatePlayer().isOnline();
    }

    public CityStatePosition getPosition() {
        return CityStatePosition.MEMBER;
    }

    public boolean isValid() {
        return cityState.isValid() && cityState.getMember(uuid) == this;
    }

    /**
     * 得到当前玩家节点
     * @return
     */
    public ConfigurationSection getSection() {
        return section;
    }

    public void save() {
        cityState.save();
    }
}
