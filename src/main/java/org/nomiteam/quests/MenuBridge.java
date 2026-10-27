package org.nomiteam.quests;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 菜单的一次性短时准备；调用方必须已持有本人实体线程，不处理费用。 */
final class MenuBridge {
    static final ZoneId DAY_ZONE = ZoneId.of("Asia/Shanghai");

    interface Port {
        boolean owned();
        boolean valid();
        Object snapshot();
        boolean matches(Object snapshot);
        Object draw();
        String apply(Object next);
    }

    private record Prepared(Object snapshot, Object next, long created, long expires) {}

    private final Map<UUID, Prepared> pending = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    static LocalDate day(long millis) {
        return Instant.ofEpochMilli(millis).atZone(DAY_ZONE).toLocalDate();
    }

    boolean prepare(UUID id, Port port, long now) {
        if (!port.owned() || !busy.add(id)) {
            return false;
        }
        // 占位对象让关闭/退出回调能取消本次抽取，不能在回调后复活准备。
        Prepared preparing = new Prepared(new Object(), null, now, 0);
        try {
            pending.put(id, preparing);
            if (!port.valid()) {
                return false;
            }
            Object snapshot = port.snapshot();
            Object next = port.draw();
            if (next == null || !port.valid() || !port.matches(snapshot)) {
                return false;
            }
            return pending.replace(id, preparing, new Prepared(snapshot, next, now, now + 60_000));
        } catch (Exception | LinkageError failure) {
            return false;
        } finally {
            pending.remove(id, preparing);
            busy.remove(id);
        }
    }

    String apply(UUID id, Port port, long now) {
        if (!port.owned() || !busy.add(id)) {
            return "REJECTED";
        }
        boolean effects = false;
        try {
            Prepared prepared = pending.remove(id);
            // 即使 ODQ 的午夜刷新尚未执行，也不允许把昨日准备应用到今日。
            if (prepared == null || now > prepared.expires()
                    || !day(now).equals(day(prepared.created()))
                    || !port.valid() || !port.matches(prepared.snapshot())) {
                return "REJECTED";
            }
            effects = true;
            return port.apply(prepared.next());
        } catch (Exception | LinkageError failure) {
            return effects ? "UNCERTAIN" : "REJECTED";
        } finally {
            busy.remove(id);
        }
    }

    void cancel(UUID id) {
        pending.remove(id);
    }

    void clear() {
        pending.clear();
    }
}
