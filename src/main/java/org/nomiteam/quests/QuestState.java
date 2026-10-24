package org.nomiteam.quests;
import com.ordwen.odailyquests.quests.player.PlayerQuests;
import com.ordwen.odailyquests.quests.player.progression.Progression;
import com.ordwen.odailyquests.quests.types.AbstractQuest;
import java.util.*;
public final class QuestState {
 public static PlayerQuests replacement(PlayerQuests old,Map<AbstractQuest,Progression> drawn){
  PlayerQuests next=new PlayerQuests(old.getTimestamp(),new LinkedHashMap<>(drawn));next.setTotalAchievedQuests(old.getTotalAchievedQuests());next.setTotalAchievedQuestsByCategory(new HashMap<>(old.getTotalAchievedQuestsByCategory()));next.setRecentRerolls(1);return next;
 }
 public static boolean complete(Map<String,Integer> expected,List<String> actual){
  Map<String,Integer> positive=new HashMap<>();expected.forEach((k,v)->{if(v>0)positive.put(k,v);});Map<String,Integer> counts=new HashMap<>();actual.forEach(k->counts.merge(k,1,Integer::sum));return !positive.isEmpty()&&positive.equals(counts);
 }
 public record Progress(Progression identity,int advancement,boolean achieved,int required,int index,double reward){}
 public record Snapshot(PlayerQuests identity,Long timestamp,int total,int achieved,int rerolls,Map<String,Integer> categories,Map<AbstractQuest,Progress> quests){
  public boolean matches(PlayerQuests current){return current==identity&&equals(capture(current));}
 }
 public static Snapshot capture(PlayerQuests q){
  Map<AbstractQuest,Progress> entries=new LinkedHashMap<>();q.getQuests().forEach((k,p)->entries.put(k,new Progress(p,p.getAdvancement(),p.isAchieved(),p.getRequiredAmount(),p.getSelectedRequiredIndex(),p.getRewardAmount())));
  return new Snapshot(q,q.getTimestamp(),q.getTotalAchievedQuests(),q.getAchievedQuests(),q.getRecentlyRolled(),new HashMap<>(q.getTotalAchievedQuestsByCategory()),entries);
 }
}
