package com.cuzz.rookiecitystate.citystate;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.api.event.CityStateDeletedEvent;
import com.cuzz.rookiecitystate.config.setting.MainSettings;
import com.cuzz.rookiecitystate.citystate.member.CityStateMember;
import com.cuzz.rookiecitystate.citystate.member.CityStateOwner;
import com.cuzz.rookiecitystate.citystate.member.CityStatePosition;
import com.cuzz.rookiecitystate.placeholder.PlaceholderContainer;
import com.cuzz.rookiecitystate.placeholder.PlaceholderText;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import com.cuzz.rookiecitystate.request.Receiver;
import com.cuzz.rookiecitystate.request.Request;
import com.cuzz.rookiecitystate.request.Sender;
import com.cuzz.rookiecitystate.util.Util;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import com.cuzz.rookiecitystate.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import parsii.eval.Parser;
import parsii.tokenizer.ParseException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class CityState implements Sender, Receiver {
    private final RookieCityState plugin = RookieCityState.inst();
    private final File file;
    private YamlConfiguration yaml;
    private boolean deleted;
    private UUID uuid;
    private String name;
    private CityStateOwner owner;
    private Map<UUID, CityStateMember> memberMap = new HashMap<>();
    private Map<UUID, CityStateIcon> iconMap = new HashMap<>();
    private CityStateIcon currentIcon;
    private CityStateBank cityStateBank;
    private CityStateMessageBox cityStateMessageBox;
    private List<String> announcements;
    private long createTime;
    private int additionMemberCount;
    private CityStateSpawn spawn;
    private boolean memberDamageEnabled;
    private boolean valid = true;

    public CityState(File file) {
        this.file = file;

        if (!file.exists()) {
            throw new RuntimeException("城邦不存在");
        }

        load();
    }

    /**
     * 载入
     * @return
     */
    private void load() {
        this.yaml = YamlFiles.load(file, StandardCharsets.UTF_8);
        this.deleted = yaml.getBoolean("deleted");

        if (isDeleted()) {
            return;
        }

        this.name = yaml.getString("name");
        this.uuid = UUID.fromString(yaml.getString("uuid"));
        this.cityStateBank = new CityStateBank(this);

        if (!yaml.contains("message_box")) {
            yaml.createSection("message_box");
        }

        this.cityStateMessageBox = new CityStateMessageBox(this);
        this.announcements = yaml.getStringList("announcements");
        this.createTime = yaml.getLong("creation_time");
        this.additionMemberCount = yaml.getInt("addition_member_count");
        this.memberDamageEnabled = yaml.getBoolean("member_damage_enabled", true);

        if (yaml.contains("spawn")) {
            this.spawn = new CityStateSpawn(this);
        }

        loadMembers();
        loadIcons();

        this.currentIcon = Optional.ofNullable(yaml.getString("current_icon")).map(s -> iconMap.get(UUID.fromString(s))).orElse(null);
    }

    public CityStateSpawn getSpawn() {
        return spawn;
    }

    public void setSpawn(@NotNull Location location) {
        if (location.getWorld() == null) throw new IllegalArgumentException("主城世界不可用");
        ConfigurationSection oldSpawn = yaml.getConfigurationSection("spawn");
        Map<String, Object> previous = oldSpawn == null ? null : new LinkedHashMap<>(oldSpawn.getValues(false));
        if (!yaml.contains("spawn")) {
            yaml.createSection("spawn");
        }

        YamlFiles.writeLocation(yaml.getConfigurationSection("spawn"), location);
        try {
            save();
        } catch (RuntimeException exception) {
            yaml.set("spawn", null);
            if (previous != null) {
                ConfigurationSection restored = yaml.createSection("spawn");
                previous.forEach(restored::set);
            }
            throw exception;
        }
        this.spawn = new CityStateSpawn(this);
    }

    public boolean isMemberDamageEnabled() {
        return memberDamageEnabled;
    }

    public void setMemberDamageEnabled(boolean b) {
        boolean old = memberDamageEnabled;
        yaml.set("member_damage_enabled", b);
        try { save(); } catch (RuntimeException exception) {
            yaml.set("member_damage_enabled", old);
            throw exception;
        }
        this.memberDamageEnabled = b;
    }

    public CityStateMessageBox getCityStateMessageBox() {
        return cityStateMessageBox;
    }

    private void loadMembers() {
        memberMap.clear();
        this.owner = null;

        if (yaml.contains("members")) {
            for (String memberUuidStr : yaml.getConfigurationSection("members").getKeys(false)) {
                CityStatePosition cityStatePosition = CityStatePosition.valueOf(yaml
                        .getConfigurationSection("members")
                        .getConfigurationSection(memberUuidStr)
                        .getString("position"));
                UUID memberUuid = UUID.fromString(memberUuidStr);
                CityStatePlayer cityStatePlayer = RookieCityState.inst().getCityStatePlayerManager().getCityStatePlayer(memberUuid);
                CityStateMember member = cityStatePosition == CityStatePosition.MEMBER
                        ? new CityStateMember(this, cityStatePlayer)
                        : new CityStateOwner(this, cityStatePlayer);

                memberMap.put(memberUuid, member);

                if (member instanceof CityStateOwner) {
                    this.owner = (CityStateOwner) member;
                }
            }
        }

        long ownerCount = memberMap.values().stream().filter(CityStateOwner.class::isInstance).count();
        if (ownerCount != 1) {
            throw new IllegalArgumentException("城邦必须有且只能有一名会长");
        }
    }

    private void loadIcons() {
        iconMap.clear();

        if (yaml.contains("icons")) {
            for (String iconUuidStr : yaml.getConfigurationSection("icons").getKeys(false)) {
                UUID iconUuid = UUID.fromString(iconUuidStr);

                iconMap.put(iconUuid, new CityStateIcon(this, iconUuid));
            }
        }
    }

    public void removeIcon(@NotNull CityStateIcon cityStateIcon) {
        if (iconMap.get(cityStateIcon.getUuid()) != cityStateIcon) {
            throw new IllegalArgumentException("图标不存在或实例已失效");
        }
        yaml.set("icons." + cityStateIcon.getUuid(), null);
        try { save(); } catch (RuntimeException exception) {
            throw exception;
        }
        iconMap.remove(cityStateIcon.getUuid());
        if (currentIcon == cityStateIcon) currentIcon = null;
    }


    public void setCurrentIcon(@Nullable CityStateIcon cityStateIcon) {
        if (cityStateIcon == null) {
            String old = yaml.getString("current_icon");
            yaml.set("current_icon", null);
            try { save(); } catch (RuntimeException exception) {
                yaml.set("current_icon", old);
                throw exception;
            }
            this.currentIcon = null;
            return;
        }

        UUID iconUuid = cityStateIcon.getUuid();

        if (!iconMap.containsKey(iconUuid)) {
            throw new RuntimeException("图标不存在");
        }

        String old = yaml.getString("current_icon");
        yaml.set("current_icon", iconUuid.toString());
        try { save(); } catch (RuntimeException exception) {
            yaml.set("current_icon", old);
            throw exception;
        }
        this.currentIcon = cityStateIcon;
    }

    public CityStateIcon giveIcon(@NotNull Material material, @Nullable String firstLore, @Nullable String displayName) {
        UUID uuid = UUID.randomUUID();

        if (!yaml.contains("icons." + uuid)) {
            yaml.createSection("icons." + uuid);
        }

        ConfigurationSection iconSection = yaml.getConfigurationSection("icons." + uuid);

        iconSection.set("material", material.name());
        iconSection.set("first_lore", firstLore);
        iconSection.set("display_name", displayName);
        try { save(); } catch (RuntimeException exception) {
            yaml.set("icons." + uuid, null);
            throw exception;
        }
        CityStateIcon icon = new CityStateIcon(this, uuid);
        iconMap.put(uuid, icon);
        return icon;
    }

    public boolean hasSpawn() {
        return spawn != null;
    }

    /**
     * 得到城邦唯一标识符
     * @return
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * 是否已被解散
     * @return
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * 得到城邦银行
     * @return
     */
    public CityStateBank getCityStateBank() {
        return cityStateBank;
    }

    /**
     * 设置主人
     * 旧主人将变成普通成员
     * @param newOwner
     */
    public synchronized void setOwner(@NotNull CityStateMember newOwner) {
        CityStateMember oldOwner = owner;
        UUID newOwnerUuid = newOwner.getUuid();

        if (newOwner == owner) {
            throw new IllegalArgumentException("成员已是会长");
        }

        if (memberMap.get(newOwnerUuid) != newOwner) {
            throw new IllegalArgumentException("成员不存在或实例已失效");
        }

        yaml.set("members." + newOwnerUuid + ".position", CityStatePosition.OWNER.name());
        yaml.set("members." + oldOwner.getUuid() + ".position", CityStatePosition.MEMBER.name());
        try {
            save();
        } catch (RuntimeException exception) {
            yaml.set("members." + newOwnerUuid + ".position", CityStatePosition.MEMBER.name());
            yaml.set("members." + oldOwner.getUuid() + ".position", CityStatePosition.OWNER.name());
            throw exception;
        }
        CityStateOwner replacementOwner = new CityStateOwner(this, newOwner.getCityStatePlayer());
        CityStateMember replacementMember = new CityStateMember(this, oldOwner.getCityStatePlayer());
        memberMap.put(newOwnerUuid, replacementOwner);
        memberMap.put(oldOwner.getUuid(), replacementMember);
        owner = replacementOwner;
    }

    /**
     * 是否为成员
     * @param cityStatePlayer
     * @return
     */
    public boolean isMember(@NotNull CityStatePlayer cityStatePlayer) {
        return memberMap.containsKey(cityStatePlayer.getUuid());
    }

    public boolean isMember(@NotNull UUID uuid) {
        return memberMap.containsKey(uuid);
    }

    public CityStateIcon getCurrentIcon() {
        return currentIcon;
    }

    public List<CityStateIcon> getIcons() {
        return new ArrayList<>(iconMap.values());
    }

    public boolean isOwner(@NotNull CityStateMember cityStateMember) {
        return owner == cityStateMember;
    }

    /**
     * 是否为宗主
     * @param cityStatePlayer
     * @return
     */
    public boolean isOwner(@NotNull CityStatePlayer cityStatePlayer) {
        return owner.getCityStatePlayer().equals(cityStatePlayer);
    }

    public CityStateMember getMember(@NotNull UUID uuid) {
        return memberMap.get(uuid);
    }

    /**
     * 得到成员
     * @param cityStatePlayer
     * @return
     */
    public CityStateMember getMember(@NotNull CityStatePlayer cityStatePlayer) {
        return getMember(cityStatePlayer.getUuid());
    }

    /**
     * 城邦文件
     * @return
     */
    public File getFile() {
        return file;
    }

    /**
     * 城邦名
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 成员数量
     * @return
     */
    public int getMemberCount() {
        return memberMap.size();
    }

    /**
     * 得到城邦主人
     * @return
     */
    public CityStateOwner getOwner() {
        return owner;
    }

    /**
     * 得到成员（包含主人）
     * @return
     */
    public List<CityStateMember> getMembers() {
        return new ArrayList<>(memberMap.values());
    }

    /**
     * 添加成员
     * @param cityStatePlayer
     */
    public synchronized void addMember(@NotNull CityStatePlayer cityStatePlayer) {
        UUID uuid = cityStatePlayer.getUuid();

        if (isMember(cityStatePlayer)) {
            throw new IllegalArgumentException("成员已存在");
        }
        if (!isValid()) throw new IllegalStateException("城邦已失效");
        if (cityStatePlayer.isInCityState()) throw new IllegalArgumentException("玩家已经加入其他城邦");
        if (getMemberCount() >= getMaxMemberCount()) throw new IllegalStateException("城邦人数已满");

        plugin.getCityStateManager().registerMember(this, uuid);
        yaml.set("members." + uuid + ".position", CityStatePosition.MEMBER.name());
        yaml.set("members." + uuid + ".join_time", System.currentTimeMillis());
        try {
            save();
        } catch (RuntimeException exception) {
            yaml.set("members." + uuid, null);
            plugin.getCityStateManager().unregisterMember(this, uuid);
            throw exception;
        }
        memberMap.put(uuid, new CityStateMember(this, cityStatePlayer));
        cleanRequests(cityStatePlayer.getSentRequests().stream()
                .filter(request -> request instanceof com.cuzz.rookiecitystate.request.entities.JoinRequest)
                .toList());
    }

    /**
     * 删除成员
     * @param cityStateMember
     */
    public synchronized void removeMember(@NotNull CityStateMember cityStateMember) {
        if (cityStateMember instanceof CityStateOwner) {
            throw new IllegalArgumentException("不能删除会长成员");
        }

        if (memberMap.get(cityStateMember.getUuid()) != cityStateMember) {
            throw new IllegalArgumentException("成员不存在或实例已失效");
        }

        String path = "members." + cityStateMember.getUuid();
        ConfigurationSection previousSection = yaml.getConfigurationSection(path);
        Map<String, Object> previous = previousSection == null
                ? Map.of() : new LinkedHashMap<>(previousSection.getValues(true));
        yaml.set(path, null);
        try {
            save();
        } catch (RuntimeException exception) {
            yaml.createSection(path, previous);
            throw exception;
        }
        memberMap.remove(cityStateMember.getUuid());
        plugin.getCityStateManager().unregisterMember(this, cityStateMember.getUuid());
        cleanRequests(cityStateMember.getReceivedRequests());
        cleanRequests(cityStateMember.getSentRequests());
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid && !deleted && plugin.getCityStateManager().isValid(this);
    }

    /**
     * 删除城邦
     * @return
     */
    public void delete() {
        yaml.set("deleted", true);
        YamlFiles.save(yaml, file, StandardCharsets.UTF_8);
        this.deleted = true;
        getMembers().forEach(cityStateMember ->
                cityStateMember.getReceivedRequests().stream().toList().forEach(Request::delete));
        getSentRequests().stream().toList().forEach(Request::delete);
        getReceivedRequests().stream().toList().forEach(Request::delete);
        plugin.getCityStateManager().unloadCityState(this);
        Bukkit.getPluginManager().callEvent(new CityStateDeletedEvent(this));
    }

    public int getMaxMemberCount() {
        return MainSettings.getCityStateDefaultMaxMemberCount() + getAdditionMemberCount();
    }

    /**
     * 得到最大成员数
     * @return
     */
    public int getAdditionMemberCount() {
        return this.additionMemberCount;
    }

    /**
     * 设置最大成员数
     * @param additionMemberCount
     * @return
     */
    public void setAdditionMemberCount(int additionMemberCount) {
        int old = this.additionMemberCount;
        yaml.set("addition_member_count", additionMemberCount);
        try { save(); } catch (RuntimeException exception) {
            yaml.set("addition_member_count", old);
            throw exception;
        }
        this.additionMemberCount = additionMemberCount;
    }

    /**
     * 得到创建时间
     * @return
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 设置公告
     * @param announcements
     */
    public void setAnnouncements(@NotNull List<String> announcements) {
        List<String> previous = getAnnouncements();
        yaml.set("announcements", announcements);
        try {
            save();
        } catch (RuntimeException exception) {
            yaml.set("announcements", previous);
            throw exception;
        }
        this.announcements = new ArrayList<>(announcements);
    }

    private void cleanRequests(Collection<Request> requests) {
        for (Request request : requests.stream().toList()) {
            try {
                request.delete();
            } catch (RuntimeException exception) {
                PluginLogger.warning("清理失效请求失败 " + request.getUuid() + ": " + exception.getMessage());
            }
        }
    }

    /**
     * 得到公告
     * @return
     */
    public List<String> getAnnouncements() {
        return new ArrayList<>(announcements);
    }

    /**
     * 得到yaml
     * @return
     */
    public YamlConfiguration getYaml() {
        return yaml;
    }

    /**
     * 保存文件
     */
    public void save() {
        YamlFiles.save(yaml, file, StandardCharsets.UTF_8);
    }

    /**
     * 得到城邦等级权重值
     * @return
     */
    public int getRank() {
        String formula = PlaceholderText.replacePlaceholders(MainSettings.getCityStateRankFormula(), new PlaceholderContainer().addCityStatePlaceholders(this));

        try {
            return (int) Parser.parse(formula).evaluate();
        } catch (ParseException e) {
            e.printStackTrace();
            throw new RuntimeException("城邦等级计算公式不合法: " + formula);
        }
    }

    /**
     * 广播消息（在线成员）
     * @param message
     */
    public void broadcastMessage(String message) {
        for (CityStateMember member : getMembers()) {
            if (member.isOnline()) {
                Util.sendMsg(member.getCityStatePlayer().getBukkitPlayer(), message);
            }
        }
    }

    /**
     * 得到在线的成员
     * @return
     */
    public List<CityStateMember> getOnlineMembers() {
        return getMembers().stream().filter(CityStateMember::isOnline).collect(Collectors.toList());
    }

    /**
     * 得到在线的成员数量
     * @return
     */
    public int getOnlineMemberCount() {
        return getOnlineMembers().size();
    }
}
