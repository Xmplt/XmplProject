package me.xmpl.ess.model;

public class UserRecord {
    public final String name;
    public final String uuid;
    public final long lastSeen;
    public final String pendingUnbanAdmin;
    public final String pendingUnbanReason;
    public final boolean pendingExpiredBanNotice;
    public final String pendingOfflineMuteNotice;
    public final String pendingTakeCmdNotice;

    public UserRecord(String name, String uuid, long lastSeen, String pendingUnbanAdmin, String pendingUnbanReason,
                      boolean pendingExpiredBanNotice, String pendingOfflineMuteNotice, String pendingTakeCmdNotice) {
        this.name = name;
        this.uuid = uuid;
        this.lastSeen = lastSeen;
        this.pendingUnbanAdmin = pendingUnbanAdmin;
        this.pendingUnbanReason = pendingUnbanReason;
        this.pendingExpiredBanNotice = pendingExpiredBanNotice;
        this.pendingOfflineMuteNotice = pendingOfflineMuteNotice;
        this.pendingTakeCmdNotice = pendingTakeCmdNotice;
    }
}