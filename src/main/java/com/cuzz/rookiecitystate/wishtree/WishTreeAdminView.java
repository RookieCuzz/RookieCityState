package com.cuzz.rookiecitystate.wishtree;

import java.util.Set;
import java.util.UUID;

public record WishTreeAdminView(UUID cityStateId, int level, int experience, int visualLevel,
                                String visualState, String visualError, String week, int growth,
                                int target, int participants, Set<Integer> unlocked) { }
