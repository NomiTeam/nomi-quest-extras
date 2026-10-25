package org.nomiteam.quests;

import com.ordwen.odailyquests.configuration.ConfigFactory;
import com.ordwen.odailyquests.configuration.IConfigurable;
import com.ordwen.odailyquests.configuration.essentials.QuestAmountSetting;
import com.ordwen.odailyquests.configuration.essentials.QuestsPerCategory;
import com.ordwen.odailyquests.quests.categories.CategoriesLoader;
import com.ordwen.odailyquests.quests.categories.Category;
import com.ordwen.odailyquests.quests.player.QuestsManager;
import com.ordwen.odailyquests.quests.types.shared.BasicQuest;
import com.ordwen.odailyquests.rewards.Reward;
import com.ordwen.odailyquests.rewards.RewardType;
import java.util.List;
import java.util.Map;

/** 用真实 ODQ 选择器抽取离线类别夹具；没有伪造服务器、玩家或占位符执行。 */
public final class NativeDrawTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var field = ConfigFactory.class.getDeclaredField("configs");
        field.setAccessible(true);
        Map<Class<? extends IConfigurable>, IConfigurable> configs =
                (Map<Class<? extends IConfigurable>, IConfigurable>) field.get(null);
        configs.put(QuestsPerCategory.class, new QuestsPerCategory(null));
        QuestsPerCategory.getAllSettings().put("a", QuestAmountSetting.from("a", 2));
        QuestsPerCategory.getAllSettings().put("b", QuestAmountSetting.from("b", 1));
        for (String name : List.of("a", "b")) {
            Category category = new Category(name);
            for (int id = 1; id <= 3; id++) {
                category.add(new BasicQuest(id, String.valueOf(id), "fixture", name, List.of(),
                        "BASIC", null, 1, null, "10", new Reward(RewardType.NONE, 0.0, ""),
                        List.of(), List.of(), false, List.of(), List.of()));
            }
            CategoriesLoader.getAllCategories().put(name, category);
        }
        // 固定数量、空权限和空占位符条件允许真实 API 接收 null，不借此测试玩家权限。
        var drawn = QuestsManager.selectRandomQuests(null);
        assert QuestState.complete(QuestsPerCategory.resolveAllFor(null),
                drawn.keySet().stream().map(quest -> quest.getCategoryName()).toList());
        assert drawn.size() == 3;
        assert drawn.values().stream().allMatch(progress -> progress.getAdvancement() == 0
                && !progress.isAchieved() && progress.getRequiredAmount() == 10);
        System.out.println("PASS real ODQ selectRandomQuests: complete 2+1 category draw, fresh progress;"
                + " player permission/PAPI/runtime not tested");
    }
}
