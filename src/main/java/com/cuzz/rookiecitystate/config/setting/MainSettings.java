package com.cuzz.rookiecitystate.config.setting;

import com.cuzz.rookiecitystate.internal.config.Config;
import com.cuzz.rookiecitystate.internal.config.Min;
import com.cuzz.rookiecitystate.internal.config.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MainSettings {
    @Min(0)
    @Config(path = "city_state.sign.reward.gmoney")
    private static double cityStateSignRewardGMoney;

    @NotNull
    @Config(path = "city_state.sign.reward.commands")
    private static List<String> cityStateSignRewardCommands;

    @Config(path = "metrics_enabled")
    private static boolean metricsEnabled;

    @NotNull
    @Config(path = "city_state.create.name_regex")
    private static String cityStateCreateNameRegex;

    @Min(0)
    @Config(path = "city_state.create.price.money.amount")
    private static double cityStateCreatePriceMoneyAmount;

    @Min(0)
    @Config(path = "city_state.create.price.points.amount")
    private static int cityStateCreatePricePointsAmount;

    @NotNull
    @Config(path = "city_state.announcement.split_str")
    private static String cityStateAnnouncementSplitStr;

    @NotNull
    @Config(path = "city_state.announcement.max_count")
    private static int cityStateAnnouncementMaxCount;

    @NotNull
    @Config(path = "city_state.announcement.default")
    private static List<String> cityStateAnnouncementDefault;

    @NotNull
    @Config(path = "city_state.announcement.input.cancel_str")
    private static String cityStateAnnouncementInputCancelStr;

    @Min(0)
    @Config(path = "city_state.request.join.timeout")
    private static int cityStateRequestJoinTimeout;

    @Min(1)
    @Config(path = "city_state.default_max_member_count")
    private static int cityStateDefaultMaxMemberCount;

    @NotNull
    @Config(path = "city_state.rank.formula")
    private static String cityStateRankFormula;

    @NotNull
    @Config(path = "city_state.icon.default.material")
    private static String cityStateIconDefaultMaterial;

    @Config(path = "city_state.icon.default.first_lore")
    private static String cityStateIconDefaultFirstLore;

    @Min(0)
    @Config(path = "city_state.dismiss.wait")
    private static int cityStateDismissWait;

    @NotNull
    @Config(path = "city_state.dismiss.confirm_str")
    private static String cityStateDismissConfirmStr;

    @Min(0)
    @Config(path = "city_state.exit.wait")
    private static int cityStateExitWait;

    @NotNull
    @Config(path = "city_state.exit.confirm_str")
    private static String cityStateExitConfirmStr;

    @NotNull
    @Config(path = "city_state.create.input.cancel_str")
    private static String cityStateCreateInputCancelStr;

    @Min(0)
    @Config(path = "city_state.create.input.wait_sec")
    private static int cityStateCreateInputWaitSec;

    @NotNull
    @Config(path = "city_state.papi.non_str")
    private static String cityStatePapiNonStr;

    @Config(path = "city_state.create.no_duplication_name")
    private static boolean cityStateCreateNoDuplicationName;

    @Min(0)
    @Config(path = "city_state.member_damage.disabled_notice_interval")
    private static int cityStateMemberDamageDisableNoticeInterval;

    @Config(path = "city_state.gui.default.colored")
    private static boolean cityStateGuiDefaultColored;

    @Config(path = "city_state.gui.default.use_papi")
    private static boolean cityStateGuiDefaultUsePapi;

    @Config(path = "city_state.gui.default.hide_all_flags")
    private static boolean cityStateGuiDefaultHideAllFlags;

    @NotNull
    @Config(path = "city_state.shop.launcher")
    private static String cityStateShopLauncher;

    @Min(0)
    @Config(path = "city_state.spawn.teleport.wait")
    private static int cityStateSpawnTeleportWait;

    @Min(0)
    @Config(path = "city_state.upgrade.max_member_count")
    private static int cityStateUpgradeMaxMemberCount;

    @Min(0)
    @Config(path = "city_state.tp_all.timeout")
    private static int cityStateTpAllTimeout;

    @Min(1)
    @Config(path = "city_state.tp_all.sneak_count")
    private static int cityStateTpAllSneakCount;

    @Min(0)
    @Config(path = "city_state.tp_all.sneak_count_interval")
    private static int cityStateTpAllSneakCountInterval;

    @NotNull
    @Config(path = "city_state.tp_all.send_worlds")
    private static List<String> cityStateTpAllSendWorlds;

    @NotNull
    @Config(path = "city_state.tp_all.receive_worlds")
    private static List<String> cityStateTpAllReceiveWorlds;

    @NotNull
    @Config(path = "city_state.ess_chat.non_str")
    private static String cityStateEssChatNotStr;

    public static String getCityStateEssChatNotStr() {
        return cityStateEssChatNotStr;
    }

    public static String getCityStateAnnouncementInputCancelStr() {
        return cityStateAnnouncementInputCancelStr;
    }

    public static double getCityStateSignRewardGMoney() {
        return cityStateSignRewardGMoney;
    }

    public static List<String> getCityStateSignRewardCommands() {
        return new ArrayList<>(cityStateSignRewardCommands);
    }

    public static List<String> getCityStateTpAllSendWorlds() {
        return new ArrayList<>(cityStateTpAllSendWorlds);
    }

    public static List<String> getCityStateTpAllReceiveWorlds() {
        return new ArrayList<>(cityStateTpAllReceiveWorlds);
    }

    public static int getCityStateTpAllTimeout() {
        return cityStateTpAllTimeout;
    }

    public static int getCityStateTpAllSneakCount() {
        return cityStateTpAllSneakCount;
    }

    public static int getCityStateTpAllSneakCountInterval() {
        return cityStateTpAllSneakCountInterval;
    }

    public static int getCityStateUpgradeMaxMemberCount() {
        return cityStateUpgradeMaxMemberCount;
    }

    public static int getCityStateSpawnTeleportWait() {
        return cityStateSpawnTeleportWait;
    }

    public static boolean isCityStateGuiDefaultHideAllFlags() {
        return cityStateGuiDefaultHideAllFlags;
    }

    public static String getCityStateShopLauncher() {
        return cityStateShopLauncher;
    }

    public static boolean isCityStateGuiDefaultUsePapi() {
        return cityStateGuiDefaultUsePapi;
    }

    public static boolean isCityStateGuiDefaultColored() {
        return cityStateGuiDefaultColored;
    }

    public static int getCityStateMemberDamageDisableNoticeInterval() {
        return cityStateMemberDamageDisableNoticeInterval;
    }

    public static boolean isCityStateCreateNoDuplicationName() {
        return cityStateCreateNoDuplicationName;
    }

    public static String getCityStatePapiNonStr() {
        return cityStatePapiNonStr;
    }

    public static String getCityStateCreateInputCancelStr() {
        return cityStateCreateInputCancelStr;
    }

    public static int getCityStateCreateInputWaitSec() {
        return cityStateCreateInputWaitSec;
    }

    public static int getCityStateDismissWait() {
        return cityStateDismissWait;
    }

    public static String getCityStateDismissConfirmStr() {
        return cityStateDismissConfirmStr;
    }

    public static String getCityStateIconDefaultMaterial() {
        return cityStateIconDefaultMaterial;
    }

    public static String getCityStateIconDefaultFirstLore() {
        return cityStateIconDefaultFirstLore;
    }

    public static boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public static String getCityStateCreateNameRegex() {
        return cityStateCreateNameRegex;
    }

    public static double getCityStateCreatePriceMoneyAmount() {
        return cityStateCreatePriceMoneyAmount;
    }

    public static int getCityStateCreatePricePointsAmount() {
        return cityStateCreatePricePointsAmount;
    }

    public static String getCityStateAnnouncementSplitStr() {
        return cityStateAnnouncementSplitStr;
    }

    public static int getCityStateAnnouncementMaxCount() {
        return cityStateAnnouncementMaxCount;
    }

    public static int getCityStateDefaultMaxMemberCount() {
        return cityStateDefaultMaxMemberCount;
    }

    public static int getCityStateRequestJoinTimeout() {
        return cityStateRequestJoinTimeout;
    }

    public static List<String> getCityStateAnnouncementDefault() {
        return new ArrayList<>(cityStateAnnouncementDefault);
    }

    public static String getCityStateRankFormula() {
        return cityStateRankFormula;
    }

    public static int getCityStateExitWait() {
        return cityStateExitWait;
    }

    public static String getCityStateExitConfirmStr() {
        return cityStateExitConfirmStr;
    }
}
