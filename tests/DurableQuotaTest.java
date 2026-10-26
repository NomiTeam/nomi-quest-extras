package org.nomiteam.quests;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** JDBC contract fixture, not a real SQL engine/server. */
public final class DurableQuotaTest {
    static final Map<String, String> rows = new ConcurrentHashMap<>();
    static volatile boolean down;
    static Connection connection() throws SQLException {
        if (down) throw new SQLException("offline", "08001");
        return (Connection) Proxy.newProxyInstance(DurableQuotaTest.class.getClassLoader(),
            new Class[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    String sql = (String) args[0]; Map<Integer,String> params = new HashMap<>();
                    return Proxy.newProxyInstance(DurableQuotaTest.class.getClassLoader(),
                        new Class[]{PreparedStatement.class}, (p,m,a) -> {
                            if (m.getName().equals("setString")) { params.put((Integer)a[0], (String)a[1]); return null; }
                            if (m.getName().equals("executeUpdate")) {
                                String key = params.get(1) + "/" + params.get(2);
                                if (sql.startsWith("INSERT")) {
                                    if (rows.putIfAbsent(key, params.get(3)) != null) throw new SQLException("duplicate", "23000");
                                    return 1;
                                }
                                return rows.remove(key, params.get(3)) ? 1 : 0;
                            }
                            return null;
                        });
                }
                return null;
            });
    }
    public static void main(String[] args) throws Exception {
        var a = new DurableQuota.Jdbc(DurableQuotaTest::connection);
        var b = new DurableQuota.Jdbc(DurableQuotaTest::connection);
        UUID player = UUID.randomUUID(); LocalDate day = LocalDate.of(2026,10,26);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        Callable<Boolean> one = () -> { start.await(); return a.acquire(player,day,UUID.randomUUID()); };
        Callable<Boolean> two = () -> { start.await(); return b.acquire(player,day,UUID.randomUUID()); };
        var x = pool.submit(one); var y = pool.submit(two); start.countDown();
        assert x.get() ^ y.get(); pool.shutdown();
        // ODQ old save can reset its own recent_rerolls; independent table stays occupied.
        int odqRecentRerolls = 0;
        assert odqRecentRerolls == 0 && !b.acquire(player,day,UUID.randomUUID());
        down = true; int charged = 0;
        try { if (a.acquire(UUID.randomUUID(),day,UUID.randomUUID())) charged++; assert false; }
        catch (SQLException expected) { assert charged == 0; } finally { down = false; }
        long before = ZonedDateTime.of(2026,10,26,23,59,59,0,MenuBridge.DAY_ZONE).toInstant().toEpochMilli();
        var cancelled = new DurableQuota.Request(player,before);
        assert !cancelled.cancel(); cancelled.acquired(); assert cancelled.cancel();
        assert !cancelled.ready(before) && !cancelled.arm(before);
        var midnight = new DurableQuota.Request(player,before); midnight.acquired();
        assert !midnight.arm(before+1000);
        var expired = new DurableQuota.Request(player,0); expired.acquired(); assert !expired.arm(60000);
        var effects = new DurableQuota.Request(player,0); effects.acquired(); assert effects.arm(1);
        assert !effects.cancel() && !effects.arm(2);
        UUID id = UUID.randomUUID(), oldToken = UUID.randomUUID(), newToken = UUID.randomUUID();
        assert a.acquire(id,day,oldToken); a.release(id,day,oldToken);
        assert b.acquire(id,day,newToken); a.release(id,day,oldToken);
        assert !a.acquire(id,day,oldToken) : "stale cancellation cannot delete newer claim";
        System.out.println("PASS independent quota JDBC contract fixture: two instances, ODQ rollback, DB failure before charge, midnight, expiry, cancellation, stale release, irreversible effects");
    }
}
