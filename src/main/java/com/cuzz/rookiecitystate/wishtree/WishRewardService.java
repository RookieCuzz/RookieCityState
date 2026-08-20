package com.cuzz.rookiecitystate.wishtree;

import com.cuzz.rookiecitystate.RookieCityState;
import com.cuzz.rookiecitystate.citystate.CityState;
import com.cuzz.rookiecitystate.citystate.CityStateBank;
import com.cuzz.rookiecitystate.player.CityStatePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WishRewardService {
    private final RookieCityState plugin;

    public WishRewardService(RookieCityState plugin) { this.plugin = plugin; }

    ClaimResult claim(Player player, CityStatePlayer data, PlayerWishData wishData, UUID claimId) {
        requireMainThread();
        WishRewardClaim claim = wishData.claim(claimId);
        if (claim == null) return ClaimResult.failed("奖励不存在", null);
        if (claim.state() == WishClaimState.CLAIMED) return ClaimResult.failed("奖励已经领取", claim.state());
        if (claim.state() == WishClaimState.DISPATCHING) {
            wishData.claimState(claimId, WishClaimState.AMBIGUOUS);
            wishData.save();
            return ClaimResult.failed("上次发放在服务器中断时未确认，请联系管理员", WishClaimState.AMBIGUOUS);
        }
        if (claim.state() == WishClaimState.AMBIGUOUS) {
            return ClaimResult.failed("奖励发放结果需要管理员核对", claim.state());
        }
        String validation = validate(player, claim);
        if (validation != null) return ClaimResult.failed(validation, claim.state());

        String playerBefore = data.getYaml().saveToString();
        wishData.claimState(claimId, WishClaimState.DISPATCHING);
        try { wishData.save(); }
        catch (RuntimeException saveError) {
            data.restoreYamlSnapshot(playerBefore);
            return ClaimResult.failed("奖励状态保存失败，尚未发放", WishClaimState.READY);
        }
        try {
            for (WishRewardAction action : claim.actions()) deliver(player, wishData, claim, action);
            wishData.claimState(claimId, WishClaimState.CLAIMED);
            wishData.save();
            return ClaimResult.ok();
        } catch (Throwable error) {
            data.restoreYamlSnapshot(playerBefore);
            PlayerWishData restored = new PlayerWishData(data);
            restored.claimState(claimId, WishClaimState.AMBIGUOUS);
            restored.save();
            return ClaimResult.failed(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                    WishClaimState.AMBIGUOUS);
        }
    }

    void resolve(CityStatePlayer data, UUID claimId, boolean delivered) {
        PlayerWishData wishData = new PlayerWishData(data);
        WishRewardClaim claim = wishData.claim(claimId);
        if (claim == null) throw new IllegalArgumentException("奖励记录不存在");
        if (claim.state() != WishClaimState.AMBIGUOUS && claim.state() != WishClaimState.DISPATCHING) {
            throw new IllegalStateException("只有异常奖励可以核对");
        }
        wishData.claimState(claimId, delivered ? WishClaimState.CLAIMED : WishClaimState.READY);
        wishData.save();
    }

    private String validate(Player player, WishRewardClaim claim) {
        ItemStack[] simulation = player.getInventory().getStorageContents();
        for (int i = 0; i < simulation.length; i++) {
            if (simulation[i] != null) simulation[i] = simulation[i].clone();
        }
        for (WishRewardAction action : claim.actions()) {
            if (action.type() == WishRewardType.ITEM) {
                Material material = Material.matchMaterial(action.material());
                if (material == null) return "奖励物品配置无效";
                ItemStack item = new ItemStack(material, (int) action.amount());
                if (!place(simulation, item)) return "背包空间不足";
            }
            if (action.type() == WishRewardType.PLAYER_POINTS && !plugin.isPlayerPointsHooked()) {
                return "PlayerPoints 当前不可用";
            }
            if (action.type() == WishRewardType.CITY_GMONEY) {
                CityState city = claim.cityStateId() == null ? null : plugin.getCityStateManager().getCityState(claim.cityStateId());
                if (city == null || !city.isValid()) return "奖励所属城邦已失效";
            }
        }
        return null;
    }

    private void deliver(Player player, PlayerWishData wishData, WishRewardClaim claim, WishRewardAction action) {
        switch (action.type()) {
            case ITEM -> {
                ItemStack item = new ItemStack(Material.matchMaterial(action.material()), (int) action.amount());
                if (!player.getInventory().addItem(item).isEmpty()) throw new IllegalStateException("背包空间不足");
            }
            case VAULT_MONEY -> {
                if (!plugin.getVaultEconomy().deposit(player, action.amount())) throw new IllegalStateException("Vault 发放失败");
            }
            case PLAYER_POINTS -> {
                if (!plugin.getPlayerPointsEconomy().deposit(player, (int) action.amount())) {
                    throw new IllegalStateException("PlayerPoints 发放失败");
                }
            }
            case CITY_GMONEY -> {
                CityState city = plugin.getCityStateManager().getCityState(claim.cityStateId());
                if (city == null) throw new IllegalStateException("奖励所属城邦已失效");
                city.getCityStateBank().deposit(CityStateBank.BalanceType.GMONEY, action.amount());
            }
            case MAGIC_STONE -> wishData.addStones((int) action.amount());
            case COMMANDS -> {
                CityState city = claim.cityStateId() == null ? null : plugin.getCityStateManager().getCityState(claim.cityStateId());
                for (String configured : action.commands()) {
                    String command = configured.replace("<player>", player.getName())
                            .replace("<city>", city == null ? "" : city.getName());
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                        throw new IllegalStateException("奖励指令执行失败: " + command);
                    }
                }
            }
        }
    }

    private boolean place(ItemStack[] contents, ItemStack incoming) {
        int remaining = incoming.getAmount();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || !item.isSimilar(incoming)) continue;
            int moved = Math.min(remaining, Math.max(0, item.getMaxStackSize() - item.getAmount()));
            item.setAmount(item.getAmount() + moved);
            remaining -= moved;
            if (remaining == 0) return true;
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) continue;
            int moved = Math.min(remaining, incoming.getMaxStackSize());
            ItemStack placed = incoming.clone();
            placed.setAmount(moved);
            contents[i] = placed;
            remaining -= moved;
        }
        return remaining == 0;
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("奖励必须在服务器主线程发放");
    }
}
