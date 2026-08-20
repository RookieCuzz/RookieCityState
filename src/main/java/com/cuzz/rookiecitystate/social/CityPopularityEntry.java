package com.cuzz.rookiecitystate.social;

import com.cuzz.rookiecitystate.citystate.CityState;

public record CityPopularityEntry(CityState cityState, int rank, long hotScore,
                                  int recentVisitors, int recentLikes, long totalLikes) { }
