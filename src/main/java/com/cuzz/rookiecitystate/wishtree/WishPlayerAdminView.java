package com.cuzz.rookiecitystate.wishtree;

public record WishPlayerAdminView(int magicStones, String targetId, int rarePity, int epicPity,
                                  int freeUsed, int paidUsed, int pendingClaims, int ambiguousClaims) { }
