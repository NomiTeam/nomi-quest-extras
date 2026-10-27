package org.nomiteam.quests;

import com.ordwen.odailyquests.quests.player.PlayerQuests;
import com.ordwen.odailyquests.quests.player.progression.Progression;
import com.ordwen.odailyquests.quests.types.AbstractQuest;
import com.ordwen.odailyquests.tools.RenewSchedule;
import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 只管理 ODQ 任务状态；日限由独立 DurableQuota 权威控制。 */
public final class QuestState {
    private QuestState() {}

    public static boolean calendarSchedule(RenewSchedule.Settings settings) {
        return settings != null && settings.mode() == 1
                && LocalTime.MIDNIGHT.equals(settings.renewTime())
                && Duration.ofDays(1).equals(settings.interval())
                && MenuBridge.DAY_ZONE.equals(settings.zone());
    }

    public static boolean available(PlayerQuests quests, RenewSchedule.Settings settings, long now) {
        return current(quests, settings, now) && quests.getRecentlyRolled() < 1;
    }

    public static boolean current(PlayerQuests quests, RenewSchedule.Settings settings, long now) {
        // 任务过期时等 ODQ 自己刷新；不能仅清空计数后让旧任务再获得一次额度。
        return calendarSchedule(settings) && quests != null && quests.getTimestamp() != null
                && quests.getTimestamp() <= now
                && MenuBridge.day(quests.getTimestamp()).equals(MenuBridge.day(now));
    }

    /** 库存关闭回调开始后，异常不能证明没有副作用，不盲目回滚或重试。 */
    public static String apply(Map<String, PlayerQuests> active, String name, PlayerQuests old,
            PlayerQuests next, Runnable close, BooleanSupplier valid, Consumer<PlayerQuests> save) {
        try {
            close.run();
            if (!valid.getAsBoolean() || !active.replace(name, old, next)) {
                return "UNCERTAIN";
            }
            save.accept(next);
            return "SUCCESS";
        } catch (Exception | LinkageError failure) {
            return "UNCERTAIN";
        }
    }

    public static PlayerQuests replacement(PlayerQuests old, Map<AbstractQuest, Progression> drawn) {
        PlayerQuests next = new PlayerQuests(old.getTimestamp(), new LinkedHashMap<>(drawn));
        next.setTotalAchievedQuests(old.getTotalAchievedQuests());
        next.setTotalAchievedQuestsByCategory(new HashMap<>(old.getTotalAchievedQuestsByCategory()));
        next.setRecentRerolls(1);
        return next;
    }

    public static boolean complete(Map<String, Integer> expected, List<String> actual) {
        Map<String, Integer> positive = new HashMap<>();
        expected.forEach((category, count) -> {
            if (count > 0) {
                positive.put(category, count);
            }
        });
        Map<String, Integer> counts = new HashMap<>();
        actual.forEach(category -> counts.merge(category, 1, Integer::sum));
        return !positive.isEmpty() && positive.equals(counts);
    }

    public record Progress(Progression identity, int advancement, boolean achieved,
            int required, int index, double reward) {}

    public record Snapshot(PlayerQuests identity, Long timestamp, int total, int achieved,
            int rerolls, Map<String, Integer> categories, Map<AbstractQuest, Progress> quests) {
        public boolean matches(PlayerQuests current) {
            return current == identity && equals(capture(current));
        }
    }

    public static Snapshot capture(PlayerQuests quests) {
        Map<AbstractQuest, Progress> entries = new LinkedHashMap<>();
        quests.getQuests().forEach((quest, progress) -> entries.put(quest,
                new Progress(progress, progress.getAdvancement(), progress.isAchieved(),
                        progress.getRequiredAmount(), progress.getSelectedRequiredIndex(),
                        progress.getRewardAmount())));
        return new Snapshot(quests, quests.getTimestamp(), quests.getTotalAchievedQuests(),
                quests.getAchievedQuests(), quests.getRecentlyRolled(),
                new HashMap<>(quests.getTotalAchievedQuestsByCategory()), entries);
    }
}
