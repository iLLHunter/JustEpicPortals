package com.onlyk.justepicportals;

public enum PortalAccess {
    SELF, TEAM, EVERYONE;

    public static PortalAccess load(String value) {
        for (var access : values()) if (access.name().equals(value)) return access;
        return EVERYONE;
    }
}
