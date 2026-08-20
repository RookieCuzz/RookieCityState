package com.cuzz.rookiecitystate.wishtree;

import java.util.Set;
import java.util.UUID;

public record WishTreeView(
        UUID cityStateId,
        int level,
        int experience,
        int visualLevel,
        String visualState,
        String week,
        int weeklyGrowth,
        int weeklyTarget,
        Set<Integer> unlockedMilestones,
        int magicStones,
        int freeWishesRemaining,
        int paidWishesRemaining,
        String targetRewardId,
        int rarePity,
        int epicPity,
        boolean wateredToday,
        int mailboxSize
) { }
