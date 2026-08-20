package com.cuzz.rookiecitystate.guardian;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.internal.io.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Crash evidence for the cross-file guardian feed transaction. */
final class GuardianFeedOperation {
    enum Recovery { NONE, ABORTED, COMPLETED, RECONCILIATION_REQUIRED }

    private final File file;
    private final YamlConfiguration yaml;

    private GuardianFeedOperation(File file, YamlConfiguration yaml) { this.file = file; this.yaml = yaml; }

    static GuardianFeedOperation create(RookieCityState plugin, Player player, UUID cityId,
                                        GuardianBeastState state, PlayerGuardianData personal, ItemStack before) {
        File file = file(plugin, player.getUniqueId());
        if (file.isFile()) throw new IllegalStateException("该玩家有未完成的喂养操作");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("type", "GUARDIAN_FEED");
        yaml.set("id", UUID.randomUUID().toString());
        yaml.set("player_uuid", player.getUniqueId().toString());
        yaml.set("city_uuid", cityId.toString());
        yaml.set("phase", "PREPARED");
        yaml.set("before.state", state.snapshot());
        yaml.set("before.player", personal.snapshot());
        yaml.set("before.item", encode(before));
        yaml.set("before.state_hash", hash(state.snapshot()));
        yaml.set("before.player_hash", hash(personal.snapshot()));
        yaml.set("before.item_hash", hash(encode(before)));
        yaml.set("updated_at", System.currentTimeMillis());
        YamlFiles.save(yaml, file);
        return new GuardianFeedOperation(file, yaml);
    }

    static GuardianFeedOperation load(RookieCityState plugin, UUID playerId) {
        File file = file(plugin, playerId);
        return file.isFile() ? new GuardianFeedOperation(file, YamlFiles.load(file)) : null;
    }

    void after(GuardianBeastState state, PlayerGuardianData personal, ItemStack item) {
        yaml.set("after.state", state.snapshot());
        yaml.set("after.player", personal.snapshot());
        yaml.set("after.item", encode(item));
        yaml.set("after.state_hash", hash(state.snapshot()));
        yaml.set("after.player_hash", hash(personal.snapshot()));
        yaml.set("after.item_hash", hash(encode(item)));
        phase("AFTER_PREPARED");
    }

    Recovery recover(Player player, GuardianBeastState state, PlayerGuardianData personal) {
        String stateHash = hash(state.snapshot());
        String playerHash = hash(personal.snapshot());
        String itemHash = hash(encode(player.getInventory().getItemInMainHand()));
        boolean before = stateHash.equals(yaml.getString("before.state_hash"))
                && playerHash.equals(yaml.getString("before.player_hash"))
                && itemHash.equals(yaml.getString("before.item_hash"));
        if (before && yaml.getString("after.state") == null) {
            remove();
            return Recovery.ABORTED;
        }
        String afterState = yaml.getString("after.state");
        String afterPlayer = yaml.getString("after.player");
        String afterItem = yaml.getString("after.item");
        if (afterState == null || afterPlayer == null || afterItem == null) {
            phase("RECONCILIATION_REQUIRED");
            return Recovery.RECONCILIATION_REQUIRED;
        }
        boolean stateKnown = stateHash.equals(yaml.getString("before.state_hash"))
                || stateHash.equals(yaml.getString("after.state_hash"));
        boolean playerKnown = playerHash.equals(yaml.getString("before.player_hash"))
                || playerHash.equals(yaml.getString("after.player_hash"));
        boolean itemKnown = itemHash.equals(yaml.getString("before.item_hash"))
                || itemHash.equals(yaml.getString("after.item_hash"));
        if (!stateKnown || !playerKnown || !itemKnown) {
            phase("RECONCILIATION_REQUIRED");
            return Recovery.RECONCILIATION_REQUIRED;
        }
        try {
            state.restore(afterState);
            personal.restore(afterPlayer);
            player.getInventory().setItemInMainHand(decode(afterItem));
            personal.save();
            state.save();
            complete();
            return Recovery.COMPLETED;
        } catch (RuntimeException error) {
            yaml.set("last_error", error.getClass().getSimpleName() + ": " + error.getMessage());
            phase("RECONCILIATION_REQUIRED");
            return Recovery.RECONCILIATION_REQUIRED;
        }
    }

    void phase(String phase) {
        yaml.set("phase", phase);
        yaml.set("updated_at", System.currentTimeMillis());
        YamlFiles.save(yaml, file);
    }

    void complete() { phase("COMPLETE"); remove(); }
    void reconciliation(Throwable error) {
        yaml.set("last_error", error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage());
        phase("RECONCILIATION_REQUIRED");
    }
    void remove() {
        if (!file.delete() && file.exists()) throw new IllegalStateException("无法删除已完成的喂养操作");
    }

    private static File file(RookieCityState plugin, UUID playerId) {
        return new File(plugin.getDataFolder(), "data" + File.separator + "operations"
                + File.separator + "guardian-feed" + File.separator + playerId + ".yml");
    }
    private static String encode(ItemStack item) {
        if (item == null || item.getType().isAir()) return "EMPTY";
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
    private static ItemStack decode(String value) {
        return "EMPTY".equals(value) ? null : ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("无法计算喂养证据哈希", error); }
    }
}
