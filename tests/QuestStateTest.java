package org.nomiteam.quests;
import com.ordwen.odailyquests.quests.player.PlayerQuests;
import java.util.*;
import com.ordwen.odailyquests.quests.types.shared.BasicQuest;
import com.ordwen.odailyquests.quests.player.QuestsManager;
import com.ordwen.odailyquests.quests.player.progression.Progression;
import com.ordwen.odailyquests.rewards.*;
public final class QuestStateTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  PlayerQuests old=new PlayerQuests(123456L,new LinkedHashMap<>());old.setTotalAchievedQuests(71);old.setTotalAchievedQuestsByCategory(new HashMap<>(Map.of("a",12)));old.setAchievedQuests(3);
  PlayerQuests next=QuestState.replacement(old,new LinkedHashMap<>());
  check(next.getTimestamp().equals(old.getTimestamp()));check(next.getTotalAchievedQuests()==71);check(next.getTotalAchievedQuestsByCategory("a")==12);check(next.getRecentlyRolled()==1);check(next.getAchievedQuests()==0);
  next.getTotalAchievedQuestsByCategory().put("a",1);check(old.getTotalAchievedQuestsByCategory("a")==12);
  check(QuestState.complete(Map.of("a",2,"b",1),List.of("a","a","b")));
  check(!QuestState.complete(Map.of("a",2,"b",1),List.of("a","b")));
  check(!QuestState.complete(Map.of("a",2,"b",1),List.of("a","a","a")));
  check(!QuestState.complete(Map.of(),List.of()));
  var snapshot=QuestState.capture(old);check(snapshot.matches(old));old.setTotalAchievedQuests(72);check(!snapshot.matches(old));
  BasicQuest realQuest=new BasicQuest(1,"1","test","a",List.of(),"BASIC",null,1,null,"10",new Reward(RewardType.NONE,0.0,""),List.of(),List.of(),false,List.of(),List.of());
  Progression progress=QuestsManager.createFreshProgression(realQuest);check(progress.getRequiredAmount()==10&&progress.getAdvancement()==0&&!progress.isAchieved());
  old.getQuests().put(realQuest,progress);var progressSnapshot=QuestState.capture(old);check(progressSnapshot.matches(old));progress.increaseAdvancement();check(!progressSnapshot.matches(old));
  var objectSnapshot=QuestState.capture(old);PlayerQuests other=QuestState.replacement(old,old.getQuests());check(!objectSnapshot.matches(other));
  Progression fresh=QuestsManager.createFreshProgression(realQuest);PlayerQuests withFresh=QuestState.replacement(old,Map.of(realQuest,fresh));check(withFresh.getQuests().get(realQuest).getAdvancement()==0);check(progress.getAdvancement()==1);
  System.out.println("PASS real ODQ PlayerQuests: original epoch, lifetime/category counts, zero daily progress, daily quota, full category draw, stale mutation");
 }
}
