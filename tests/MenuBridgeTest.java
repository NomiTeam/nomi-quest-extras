package org.nomiteam.quests;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MenuBridgeTest {
    /** 仅模拟桥的回调边界；不是在线玩家或经济系统测试。 */
    static final class Port implements MenuBridge.Port {
        boolean thread = true;
        boolean valid = true;
        boolean failDraw;
        boolean failApply;
        int applied;
        Runnable duringDraw = () -> {};
        Runnable duringApply = () -> {};
        Runnable duringValid = () -> {};
        Runnable duringMatches = () -> {};
        Object state = new Object();

        public boolean owned() {
            return thread;
        }

        public boolean valid() {
            duringValid.run();
            return valid;
        }

        public Object snapshot() {
            return state;
        }

        public boolean matches(Object snapshot) {
            duringMatches.run();
            return state == snapshot;
        }

        public Object draw() {
            duringDraw.run();
            if (failDraw) {
                throw new IllegalStateException("draw failed");
            }
            return new Object();
        }

        public String apply(Object next) {
            duringApply.run();
            applied++;
            if (failApply) {
                throw new IllegalStateException("apply failed");
            }
            return "SUCCESS";
        }
    }

    static void cancellationDuringPrepare() {
        List<String> failures = new ArrayList<>();
        for (String point : List.of("draw cancel", "draw clear", "initial valid cancel",
                "final valid cancel", "matches cancel")) {
            MenuBridge bridge = new MenuBridge();
            UUID id = UUID.randomUUID();
            Port port = new Port();
            Runnable cancel = () -> bridge.cancel(id);
            switch (point) {
                case "draw cancel" -> port.duringDraw = cancel;
                case "draw clear" -> port.duringDraw = bridge::clear;
                case "initial valid cancel" -> port.duringValid = cancel;
                case "final valid cancel" -> {
                    int[] calls = {0};
                    port.duringValid = () -> {
                        if (++calls[0] == 2) {
                            cancel.run();
                        }
                    };
                }
                case "matches cancel" -> port.duringMatches = cancel;
            }
            boolean prepared = bridge.prepare(id, port, 0);
            port.duringDraw = () -> {};
            port.duringValid = () -> {};
            port.duringMatches = () -> {};
            String result = bridge.apply(id, port, 1);
            if (prepared || !result.equals("REJECTED") || port.applied != 0) {
                failures.add(point + ": prepare=" + prepared + ", apply=" + result
                        + ", applied=" + port.applied);
            }
            assert bridge.prepare(id, port, 2) : point + " next prepare";
            assert bridge.apply(id, port, 3).equals("SUCCESS") : point + " next apply";
            assert bridge.apply(id, port, 4).equals("REJECTED") : point + " consume once";
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }
    }

    static void midnightBoundary() {
        long beforeMidnight = ZonedDateTime.of(2026, 10, 25, 23, 59, 50, 0,
                MenuBridge.DAY_ZONE).toInstant().toEpochMilli();
        MenuBridge bridge = new MenuBridge();
        UUID id = UUID.randomUUID();
        Port port = new Port();
        assert bridge.prepare(id, port, beforeMidnight);
        assert bridge.apply(id, port, beforeMidnight + 11_000).equals("REJECTED")
                : "昨日准备不能在次日扣费后应用旧周期任务";
        assert port.applied == 0;
        assert bridge.prepare(id, port, beforeMidnight + 11_000);
        assert bridge.apply(id, port, beforeMidnight + 12_000).equals("SUCCESS");
    }

    public static void main(String[] args) {
        cancellationDuringPrepare();
        midnightBoundary();
        MenuBridge bridge = new MenuBridge();
        UUID id = UUID.randomUUID();
        Port port = new Port();
        assert bridge.prepare(id, port, 0);
        assert port.applied == 0;
        assert bridge.apply(id, port, 1).equals("SUCCESS");
        assert bridge.apply(id, port, 2).equals("REJECTED");

        assert bridge.prepare(id, port, 0);
        port.state = new Object();
        assert bridge.apply(id, port, 1).equals("REJECTED");
        assert bridge.prepare(id, port, 0);
        assert bridge.apply(id, port, 60_001).equals("REJECTED");

        assert bridge.prepare(id, port, 0);
        port.failDraw = true;
        assert !bridge.prepare(id, port, 1);
        assert bridge.apply(id, port, 2).equals("REJECTED");
        port.failDraw = false;
        assert bridge.prepare(id, port, 0);
        port.thread = false;
        assert !bridge.prepare(id, port, 1);
        assert bridge.apply(id, port, 1).equals("REJECTED");
        port.thread = true;
        assert bridge.apply(id, port, 1).equals("SUCCESS");

        assert bridge.prepare(id, port, 0);
        port.valid = false;
        assert bridge.apply(id, port, 1).equals("REJECTED");
        port.valid = true;
        assert bridge.prepare(id, port, 0);
        assert bridge.apply(UUID.randomUUID(), port, 1).equals("REJECTED");
        bridge.cancel(id);
        assert bridge.apply(id, port, 1).equals("REJECTED");

        assert bridge.prepare(id, port, 0);
        port.failApply = true;
        assert bridge.apply(id, port, 1).equals("UNCERTAIN");
        assert bridge.apply(id, port, 2).equals("REJECTED");
        port.failApply = false;
        port.duringDraw = () -> {
            assert !bridge.prepare(id, port, 0);
            assert bridge.apply(id, port, 0).equals("REJECTED");
        };
        assert bridge.prepare(id, port, 0);
        port.duringDraw = () -> {};
        port.duringApply = () -> {
            bridge.cancel(id);
            assert !bridge.prepare(id, port, 0);
            assert bridge.apply(id, port, 0).equals("REJECTED");
        };
        assert bridge.apply(id, port, 1).equals("SUCCESS");
        port.duringApply = () -> {};
        assert bridge.prepare(id, port, 0);
        bridge.clear();
        assert bridge.apply(id, port, 1).equals("REJECTED");
        System.out.println("PASS preparation: cancellation, owner, expiry, midnight,"
                + " stale state, wrong thread, reentry, one use, uncertain effects");
    }
}
