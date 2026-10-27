package org.nomiteam.quests;

import com.ordwen.odailyquests.quests.player.PlayerQuests;
import java.util.HashMap;
import java.util.Map;

public final class QuestApplyTest {
    public static void main(String[] args) {
        PlayerQuests old = new PlayerQuests(123L, new HashMap<>());
        PlayerQuests next = QuestState.replacement(old, Map.of());
        Map<String, PlayerQuests> active = new HashMap<>();
        active.put("p", old);
        int[] saves = {0};
        assert QuestState.apply(active, "p", old, next, () -> {}, () -> true,
                quests -> saves[0]++).equals("SUCCESS");
        assert active.get("p") == next && saves[0] == 1;

        active.put("p", old);
        assert QuestState.apply(active, "p", old, next, () -> {}, () -> true, quests -> {
            saves[0]++;
            throw new IllegalStateException("save uncertain");
        }).equals("UNCERTAIN");
        assert active.get("p") == next && saves[0] == 2;

        active.put("p", old);
        assert QuestState.apply(active, "p", old, next, () -> active.remove("p"), () -> false,
                quests -> { throw new AssertionError(); }).equals("UNCERTAIN");
        assert active.isEmpty();

        active.put("p", old);
        assert QuestState.apply(active, "p", old, next, () -> {
            throw new IllegalStateException("close callback");
        }, () -> true, quests -> { throw new AssertionError(); }).equals("UNCERTAIN");
        assert active.get("p") == old;
        System.out.println("PASS real ODQ application: identity swap, save normal return,"
                + " save exception retains applied state, close callback stale/throw, no blind rollback");
    }
}
