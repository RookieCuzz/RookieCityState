package com.cuzz.rookiecitystate.messagebox;

import java.util.UUID;

public interface Message {
    long getCreationTime();
    String getMessage();
    UUID getUuid();
}
