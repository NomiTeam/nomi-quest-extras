package org.nomiteam.quests;

import com.ordwen.odailyquests.quests.player.PlayerQuests;
import com.ordwen.odailyquests.tools.RenewSchedule;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;

public final class CalendarQuotaTest {
    public static void main(String[] args) {
        var settings = new RenewSchedule.Settings(LocalTime.MIDNIGHT, Duration.ofDays(1),
                MenuBridge.DAY_ZONE, 1);
        var start = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, MenuBridge.DAY_ZONE);
        long noon = start.plusHours(12).toInstant().toEpochMilli();
        PlayerQuests quests = new PlayerQuests(noon, new HashMap<>());
        assert QuestState.available(quests, settings, noon) : "未完成也可刷新";
        quests.setAchievedQuests(3);
        assert QuestState.available(quests, settings, noon) : "已完成数不限制刷新";
        PlayerQuests next = QuestState.replacement(quests, quests.getQuests());
        assert !QuestState.available(next, settings, noon) : "本人当日第二次拒绝";
        assert !QuestState.available(quests, settings, noon - 1) : "未来周期拒绝";
        assert !QuestState.available(quests, settings, start.plusDays(1).toInstant().toEpochMilli())
                : "等待 ODQ 刷新，不给昨日任务续额度";
        assert RenewSchedule.shouldRenewSince(start.plusHours(12), start.plusDays(1), settings);
        assert !RenewSchedule.shouldRenewSince(start.plusHours(12), start.plusDays(1).minusNanos(1), settings);
        PlayerQuests tomorrow = new PlayerQuests(start.plusDays(1).toInstant().toEpochMilli(), new HashMap<>());
        assert QuestState.available(tomorrow, settings, tomorrow.getTimestamp());
        assert !QuestState.calendarSchedule(new RenewSchedule.Settings(
                LocalTime.of(4, 0), Duration.ofDays(1), MenuBridge.DAY_ZONE, 1));
        assert !QuestState.calendarSchedule(new RenewSchedule.Settings(
                LocalTime.MIDNIGHT, Duration.ofDays(1), MenuBridge.DAY_ZONE, 2));
        assert !QuestState.calendarSchedule(new RenewSchedule.Settings(
                LocalTime.MIDNIGHT, Duration.ofHours(12), MenuBridge.DAY_ZONE, 1));
        assert !QuestState.calendarSchedule(new RenewSchedule.Settings(
                LocalTime.MIDNIGHT, Duration.ofDays(1), ZoneId.of("UTC"), 1));
        assert !QuestState.available(null, settings, noon);
        System.out.println("PASS real ODQ schedule: Shanghai midnight, no completion gate,"
                + " same-day quota, expired/future data rejected, next-day ODQ state accepted");
    }
}
