package org.nomiteam.quests;
import com.ordwen.odailyquests.ODailyQuests;
import com.ordwen.odailyquests.quests.player.*;
import com.ordwen.odailyquests.quests.types.AbstractQuest;
import com.ordwen.odailyquests.quests.player.progression.Progression;
import com.ordwen.odailyquests.quests.categories.CategoriesLoader;
import com.ordwen.odailyquests.configuration.essentials.QuestsPerCategory;
import com.ordwen.odailyquests.configuration.functionalities.CommandAliases;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.util.*;
public final class NomiQuestExtras extends BasePlugin {
 protected String commandName(){return "nomiquestreset";}protected double defaultPrice(){return 500;}
 private PluginCommand nativeCommand;private CommandExecutor originalExecutor,guardedExecutor;
 protected void ready(){
  if(!getServer().getPluginManager().isPluginEnabled("ODailyQuests")||!ODailyQuests.INSTANCE.getDescription().getVersion().equals("3.0.2"))throw new IllegalStateException("需要真实 ODailyQuests 3.0.2");
  nativeCommand=Objects.requireNonNull(ODailyQuests.INSTANCE.getCommand("dquests"));originalExecutor=Objects.requireNonNull(nativeCommand.getExecutor());
  guardedExecutor=(sender,command,label,args)->{
   if(sender instanceof Player player&&CommandPolicy.action(args,rerollActions())){player.sendMessage("§e请使用 /nomiquestreset 重抽整组任务，每天一次。");return true;}
   return originalExecutor.onCommand(sender,command,label,args);
  };
  nativeCommand.setExecutor(guardedExecutor);
 }
 @Override public void onDisable(){if(nativeCommand!=null&&nativeCommand.getExecutor()==guardedExecutor)nativeCommand.setExecutor(originalExecutor);}
 private Set<String> rerollActions(){Set<String> actions=new HashSet<>(Set.of("reroll"));CommandAliases.getSubcommandAliases("reroll").forEach(a->actions.add(a.toLowerCase(Locale.ROOT)));return actions;}

 private PlayerQuests active(Player p){return QuestsManager.getActiveQuests().get(p.getName());}
 private Map<String,Integer> expected(Player p){return new HashMap<>(QuestsPerCategory.resolveAllFor(p));}
 private boolean loaded(PlayerQuests q){return q!=null&&q.getTimestamp()!=null&&!q.getQuests().isEmpty()&&q.getQuests().values().stream().allMatch(Objects::nonNull);}
 private boolean eligible(Player p,PlayerQuests q){return p.isOnline()&&economyReady()&&getServer().getPluginManager().isPluginEnabled("ODailyQuests")&&p.hasPermission(permission())&&loaded(q)&&q.getRecentlyRolled()<1;}
 protected void request(Player p,String[] args){
  if(cancel(p,args))return;
  if(args.length==0){PlayerQuests q=active(p);if(!eligible(p,q)){p.sendMessage("§c任务未加载或今日重抽额度已用完，未扣费。");return;}
   String token=confirmations.issue(p.getUniqueId(),QuestState.capture(q),price,System.currentTimeMillis());confirmation(p,"重抽全部每日任务费用："+price+"，进度归零，每日一次。",token);return;}
  if(args.length!=2||!args[0].equalsIgnoreCase("confirm")){p.sendMessage("§c仅能重抽自己的任务，请使用 /nomiquestreset。");return;}
  var preview=confirmations.take(p.getUniqueId(),args[1],System.currentTimeMillis());if(preview==null){p.sendMessage("§c确认已过期或已使用，请重新打开。");return;}
  execute(p,(QuestState.Snapshot)preview.state(),preview.fee());
 }
 protected boolean console(CommandSender s,String[] args){
  if(args.length!=1||!args[0].matches("[A-Za-z0-9_]{1,16}")){s.sendMessage("用法：dailyquestreset <在线玩家>；费用由插件扣取。只读：nomiquestreset audit | selftest");return true;}
  Player p=getServer().getPlayerExact(args[0]);if(p==null){s.sendMessage("玩家不在线，未执行。");return true;}
  schedule(p,()->{if(p.hasPermission(permission()))request(p,new String[0]);else p.sendMessage("§c你没有使用此功能的权限。");});s.sendMessage("已排入玩家实体调度以生成本人确认，未直接扣费；实际结果请查事务审计。");return true;
 }
 private void execute(Player p,QuestState.Snapshot snapshot,double cost){
  PlayerQuests old=snapshot.identity();if(!eligible(p,old)||!snapshot.matches(active(p))||cost!=price){p.sendMessage("§c任务或费用已变化，请重新确认，未扣费。");return;}
  Map<String,Integer> expected=expected(p);
  Map<AbstractQuest,Progression> drawn=QuestsManager.selectRandomQuests(p);
  if(drawn==null||!QuestState.complete(expected,drawn.keySet().stream().map(AbstractQuest::getCategoryName).toList())||drawn.values().stream().anyMatch(v->v==null||v.getAdvancement()!=0||v.isAchieved())||drawn.keySet().stream().anyMatch(q->!CategoriesLoader.getAllCategories().containsKey(q.getCategoryName())||!CategoriesLoader.getCategoryByName(q.getCategoryName()).contains(q))){p.sendMessage("§c无法抽取完整任务，本次未扣费、未消耗次数。");return;}
  PlayerQuests next=QuestState.replacement(old,drawn);
  Transaction.Result outcome=transactions.run(p.getUniqueId().toString(),old.getTimestamp().toString(),cost,new Transaction.Port(){
   final QuestMutation<PlayerQuests> mutation=new QuestMutation<>(QuestsManager.getActiveQuests(),p.getName(),old,next,this::save);
   public void prepared(String tx)throws Exception{
    Properties proof=new Properties();proof.setProperty("player",p.getUniqueId().toString());proof.setProperty("name",p.getName());proof.setProperty("fee",Double.toString(cost));proof.setProperty("initiator","player-confirmation");
    describe(proof,"before",old);describe(proof,"after",next);
    Evidence.save(getDataFolder().toPath().resolve("evidence"),tx,proof);
   }
   public boolean valid(){return eligible(p,old)&&snapshot.matches(active(p))&&expected.equals(expected(p))&&cost==price;}
   public boolean charge(double f){return NomiQuestExtras.this.charge(p,f);}
   public void apply(){
    // Invalidate the old ODQ inventory before the identity swap; old task objects must not be confirmed again.
    p.closeInventory();
    mutation.apply();
   }
   public boolean rollback(){return mutation.rollback();}
   public boolean compensationUnconfirmed(){return mutation.unconfirmed();}
   public boolean refund(double f){return NomiQuestExtras.this.refund(p,f);}
   private void save(PlayerQuests value){ODailyQuests.INSTANCE.getDatabaseManager().saveProgressionForPlayer(p.getName(),p.getUniqueId().toString(),value);}
  },true);
  result(p,outcome);
 }
 private static void describe(Properties proof,String prefix,PlayerQuests q){
  proof.setProperty(prefix+".timestamp",q.getTimestamp().toString());proof.setProperty(prefix+".total",Integer.toString(q.getTotalAchievedQuests()));proof.setProperty(prefix+".achieved",Integer.toString(q.getAchievedQuests()));proof.setProperty(prefix+".rerolls",Integer.toString(q.getRecentlyRolled()));
  q.getTotalAchievedQuestsByCategory().forEach((k,v)->proof.setProperty(prefix+".categoryTotal."+k,v.toString()));
  int i=0;for(var entry:q.getQuests().entrySet()){
   String key=prefix+".quest."+(i++);AbstractQuest quest=entry.getKey();Progression progress=entry.getValue();
   proof.setProperty(key+".category",quest.getCategoryName());proof.setProperty(key+".file",quest.getFileIndex());proof.setProperty(key+".index",Integer.toString(quest.getQuestIndex()));
   proof.setProperty(key+".advancement",Integer.toString(progress.getAdvancement()));proof.setProperty(key+".achieved",Boolean.toString(progress.isAchieved()));proof.setProperty(key+".required",Integer.toString(progress.getRequiredAmount()));proof.setProperty(key+".selectedIndex",Integer.toString(progress.getSelectedRequiredIndex()));proof.setProperty(key+".reward",Double.toString(progress.getRewardAmount()));
  }
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockSingle(PlayerCommandPreprocessEvent e){
  Set<String> bases=new HashSet<>(Set.of("dq","dquests","quests","dailyquests"));
  PluginCommand cmd=ODailyQuests.INSTANCE.getCommand("dquests");if(cmd!=null){bases.add(CommandPolicy.base(cmd.getName()));cmd.getAliases().forEach(a->bases.add(CommandPolicy.base(a)));}
  if(CommandPolicy.block(e.getMessage(),bases,rerollActions())){e.setCancelled(true);e.getPlayer().sendMessage("§e请使用 /nomiquestreset 重抽整组任务，每天一次。");}
 }
}
