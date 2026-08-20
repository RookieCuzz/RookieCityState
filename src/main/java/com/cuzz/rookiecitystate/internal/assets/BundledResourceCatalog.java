package com.cuzz.rookiecitystate.internal.assets;

import java.util.List;

/** Single source of truth for configuration files shipped in the plugin JAR. */
public final class BundledResourceCatalog {
    public static final List<String> CONFIG_FILES = List.of(
            "settings.yml", "lang.yml", "wish_tree_rewards.yml",
            "guardian_beast.yml", "guardian_shop.yml", "city_social.yml"
    );
    public static final List<String> GUI_FILES = List.of(
            "CityStateCreateGUI.yml", "CityStateInfoGUI.yml", "CityStateMemberListGUI.yml",
            "CityStateMineGUI.yml", "CityStateDonateGUI.yml", "CityStateJoinCheckGUI.yml",
            "CityStateMemberManageGUI.yml", "CityStateIconRepositoryGUI.yml", "MainGUI.yml",
            "WishTreeGUI.yml", "WishTargetGUI.yml", "WishRewardInboxGUI.yml",
            "GuardianBeastGUI.yml", "GuardianSpeciesGUI.yml", "GuardianContributionShopGUI.yml",
            "GuardianShopConfirmGUI.yml", "GuardianCosmeticLockerGUI.yml",
            "PopularCityStateGUI.yml", "CityLikeConfirmGUI.yml"
    );
    public static final List<String> SHOP_FILES = List.of("Shop1.yml", "Shop2.yml");

    private BundledResourceCatalog() { }
}
