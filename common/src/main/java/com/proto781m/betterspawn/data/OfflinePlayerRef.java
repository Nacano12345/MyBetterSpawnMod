package com.proto781m.betterspawn.data;

import java.util.UUID;

/** A player found in the server player-data directory. */
public record OfflinePlayerRef(UUID id, String name) {

    public String displayName() {
        return name == null || name.isBlank() ? id.toString() : name;
    }
}
