package com.cuzz.rookiecitystate.guardian;

import java.util.List;

/** Immutable catalog for the default ModelEngine blueprints embedded in the release JAR. */
public final class GuardianBundledAssets {
    public static final int REVISION = 1;
    public static final String RESOURCE_ROOT = "modelengine/blueprints/rookiecitystate/r1/";
    public static final List<Asset> MODELS = List.of(
            new Asset("rcs_guardian_egg_r1", "234b4639b8b1a6f06a931143f072b8df0a35fdc21350b1586ff880b002b15590"),
            new Asset("rcs_guardian_1_baby_r1", "5fe94ea9ed7255933998e328367ceb422fe562162b0bff5b7503fda5115b2afb"),
            new Asset("rcs_guardian_1_adult_r1", "f7940ebe573fe90be078b1e6f83bba9ffe07aaa68bd3d628e674ce2f6c35a0a8"),
            new Asset("rcs_guardian_2_baby_r1", "4d9bef269a0431a8e71739029ba3d1aab2e55ef6322095cc0ea1f80953b149c4"),
            new Asset("rcs_guardian_2_adult_r1", "5a2cfadba03402988eb23d366c85cdfe033cc2f835d6234704fada454db45781"),
            new Asset("rcs_guardian_3_baby_r1", "382e394cd69fb8571ed883355c83f59478ffa6023a7f81ff5457695d60976f8d"),
            new Asset("rcs_guardian_3_adult_r1", "47350703bf7fcc6d67bf84931ac1859d5c50ebcb73037818865ccde0bcddca46")
    );

    public static List<String> modelIds() { return MODELS.stream().map(Asset::id).toList(); }
    public static Asset require(String id) {
        return MODELS.stream().filter(asset -> asset.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的内置灵兽模型: " + id));
    }

    public record Asset(String id, String sha256) {
        public Asset {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("模型 ID 不能为空");
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("模型 SHA-256 无效: " + id);
            }
        }

        public String resourcePath() { return RESOURCE_ROOT + id + ".bbmodel"; }
    }

    private GuardianBundledAssets() { }
}
