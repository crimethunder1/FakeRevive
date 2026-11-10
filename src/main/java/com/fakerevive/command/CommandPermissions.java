package com.fakerevive.command;

import java.util.List;

public final class CommandPermissions {
    public static final String NONE = "";
    public static final String REVIVE = "fakerevive.revive";
    public static final String DISGUISE = "fakerevive.disguise";
    public static final String KIT = "fakerevive.kit";
    public static final String TEAM = "fakerevive.team";
    public static final String ARMOR = "fakerevive.armor";
    public static final String POOL = "fakerevive.pool";
    public static final String RELOAD = "fakerevive.reload";
    public static final String LEAVE = "fakerevive.leave";
    public static final List<String> ALL = List.of(REVIVE, DISGUISE, KIT, TEAM, ARMOR, POOL, RELOAD, LEAVE);

    private CommandPermissions() {
    }
}
