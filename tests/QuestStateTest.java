package org.nomiteam.quests;

import com.ordwen.odailyquests.quests.player.PlayerQuests;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ordwen.odailyquests.quests.types.shared.BasicQuest;
import com.ordwen.odailyquests.quests.player.QuestsManager;
import com.ordwen.odailyquests.quests.player.progression.Progression;
import com.ordwen.odailyquests.rewards.Reward;
import com.ordwen.odailyquests.rewards.RewardType;

public final class QuestStateTest {
    static void check(boolean b) {
        if (!b) {
            throw new AssertionError();
        }
    }

    public static void main(String[] args) throws Exception {
        PlayerQuests old = new PlayerQuests(123456L, new LinkedHashMap<>());
        old.setTotalAchievedQuests(71);
        old.setTotalAchievedQuestsByCategory(new HashMap<>(Map.of("a", 12)));
        old.setAchievedQuests(3);
        PlayerQuests next = QuestState.replacement(old, new LinkedHashMap<>());
        check(next.getTimestamp().equals(old.getTimestamp()));
        check(next.getTotalAchievedQuests() == 71);
        check(next.getTotalAchievedQuestsByCategory("a") == 12);
        check(next.getRecentlyRolled() == 1);
        check(next.getAchievedQuests() == 0);
        next.getTotalAchievedQuestsByCategory().put("a", 1);
        check(old.getTotalAchievedQuestsByCategory("a") == 12);
        check(QuestState.complete(Map.of("a", 2, "b", 1), List.of("a", "a", "b")));
        check(!QuestState.complete(Map.of("a", 2, "b", 1), List.of("a", "b")));
        check(!QuestState.complete(Map.of("a", 2, "b", 1), List.of("a", "a", "a")));
        check(!QuestState.complete(Map.of(), List.of()));
        check(!QuestState.complete(Map.of("a", 1), List.of("a", "extra")));
        check(QuestState.complete(Map.of("a", 1, "disabled", 0), List.of("a")));
        var snapshot = QuestState.capture(old);
        check(snapshot.matches(old));
        old.setTotalAchievedQuests(72);
        check(!snapshot.matches(old));
        var timestamp = PlayerQuests.class.getDeclaredField("timestamp");
        timestamp.setAccessible(true);
        var epoch = QuestState.capture(old);
        timestamp.set(old, 999L);
        check(!epoch.matches(old));
        timestamp.set(old, 123456L);
        var quota = QuestState.capture(old);
        old.setRecentRerolls(1);
        check(!quota.matches(old));
        old.setRecentRerolls(0);
        BasicQuest realQuest = new BasicQuest(1, "1", "test", "a", List.of(), "BASIC", null, 1, null,
                "10", new Reward(RewardType.NONE, 0.0, ""), List.of(), List.of(), false, List.of(), List.of());
        Progression progress = QuestsManager.createFreshProgression(realQuest);
        check(progress.getRequiredAmount() == 10 && progress.getAdvancement() == 0 && !progress.isAchieved());
        old.getQuests().put(realQuest, progress);
        var progressSnapshot = QuestState.capture(old);
        check(progressSnapshot.matches(old));
        progress.increaseAdvancement();
        check(!progressSnapshot.matches(old));
        var objectSnapshot = QuestState.capture(old);
        PlayerQuests other = QuestState.replacement(old, old.getQuests());
        check(!objectSnapshot.matches(other));
        Progression fresh = QuestsManager.createFreshProgression(realQuest);
        PlayerQuests withFresh = QuestState.replacement(old, Map.of(realQuest, fresh));
        check(withFresh.getQuests().get(realQuest).getAdvancement() == 0);
        check(progress.getAdvancement() == 1);
        System.out.println("PASS real ODQ PlayerQuests: original epoch, lifetime/category counts,"
                + " zero daily progress, daily quota, category counts, stale mutation");
    }
}
