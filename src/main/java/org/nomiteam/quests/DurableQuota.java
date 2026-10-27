package org.nomiteam.quests;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

/** Independent quota only. No ODQ or asset tables are accessed. */
final class DurableQuota {
    interface Store {
        boolean acquire(UUID player, LocalDate day, UUID request) throws Exception;
        void release(UUID player, LocalDate day, UUID request) throws Exception;
    }
    @FunctionalInterface interface Connections { Connection open() throws SQLException; }

    static final class Jdbc implements Store {
        private final Connections connections;
        Jdbc(Connections connections) { this.connections = connections; }
        public boolean acquire(UUID player, LocalDate day, UUID request) throws SQLException {
            try (Connection c = connections.open()) {
                c.setAutoCommit(true);
                try (PreparedStatement s = c.prepareStatement(
                        "INSERT INTO nomi_quest_daily_quota (player_uuid, quota_day, request_uuid) VALUES (?, ?, ?)")) {
                    s.setString(1, player.toString()); s.setString(2, day.toString());
                    s.setString(3, request.toString());
                    return s.executeUpdate() == 1;
                }
            } catch (SQLException failure) {
                if (failure.getSQLState() != null && failure.getSQLState().startsWith("23")) return false;
                throw failure;
            }
        }
        public void release(UUID player, LocalDate day, UUID request) throws SQLException {
            try (Connection c = connections.open()) {
                c.setAutoCommit(true);
                try (PreparedStatement s = c.prepareStatement(
                        "DELETE FROM nomi_quest_daily_quota WHERE player_uuid=? AND quota_day=? AND request_uuid=?")) {
                    s.setString(1, player.toString()); s.setString(2, day.toString());
                    s.setString(3, request.toString()); s.executeUpdate();
                }
            }
        }
    }

    static final class Request {
        final UUID player, token = UUID.randomUUID();
        final LocalDate day;
        final long expires;
        private boolean cancelled, acquired, effects;
        Request(UUID player, long now) {
            this.player = player; day = MenuBridge.day(now); expires = now + 60_000;
        }
        synchronized boolean ready(long now) {
            return acquired && !cancelled && !effects && now < expires && day.equals(MenuBridge.day(now));
        }
        synchronized void acquired() { acquired = true; }
        synchronized boolean arm(long now) {
            if (!ready(now)) return false;
            effects = true; return true;
        }
        synchronized boolean cancel() {
            cancelled = true;
            return acquired && !effects;
        }
        synchronized boolean cancelled() { return cancelled; }
    }
}
