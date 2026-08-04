package com.cuzz.rookiecitystate.citystate.member;

import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.time.Instant;
import java.time.ZoneId;

public class CityStateMemberSign {
	private CityStateMember cityStateMember;
	private ConfigurationSection section;
	private long lastSign;
	private List<Long> signHistories = new ArrayList<>();

	public CityStateMemberSign(@NotNull CityStateMember cityStateMember) {
		this.cityStateMember = cityStateMember;

		load();
	}

	private void load() {
		if (!cityStateMember.getSection().contains("sign")) {
			cityStateMember.getSection().createSection("sign");
		}

		this.section = cityStateMember.getSection().getConfigurationSection("sign");
		this.signHistories = section.getLongList("sign_histories");
		this.lastSign = cityStateMember.getCityStatePlayer().getYaml().getLong("last_sign");
	}

	public void signToday() {
		if (isSignedToday()) {
			throw new RuntimeException("今日已签到");
		}

		long time = System.currentTimeMillis();

		setLastSign(time);
		save();
		addSignHistory(time);
	}

    public boolean isSignedToday() {
        ZoneId zone = ZoneId.systemDefault();
        return Instant.ofEpochMilli(getLastSign()).atZone(zone).toLocalDate()
                .equals(Instant.now().atZone(zone).toLocalDate());
	}

	public int getSignedCount() {
		return signHistories.size();
	}

	public Set<Long> getSignHistories() {
		return new HashSet<>(signHistories);
	}

	private void addSignHistory(long l) {
		List<Long> tmp = new ArrayList<>(signHistories);

		tmp.add(l);
		section.set("sign_histories", tmp);
		save();
		this.signHistories.add(l);
	}

	/**
	 * 必须存到 CityStatePlayer 里，不然可能会刷物品
	 * @param l
	 */
	private void setLastSign(long l) {
		cityStateMember.getCityStatePlayer().getYaml().set("last_sign", l);
		cityStateMember.getCityStatePlayer().save();
		this.lastSign = l;
	}

	public long getLastSign() {
		return lastSign;
	}

	public void save() {
		cityStateMember.save();
	}
}
