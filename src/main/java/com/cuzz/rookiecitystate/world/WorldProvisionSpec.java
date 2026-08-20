package com.cuzz.rookiecitystate.world;

import java.util.UUID;

public record WorldProvisionSpec(
        UUID cityStateId,
        UUID operationId,
        String templateWorld,
        String targetWorld,
        int templateRevision,
        int borderSize
) {
}
