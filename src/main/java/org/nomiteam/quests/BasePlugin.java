package org.nomiteam.quests;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import net.milkbowl.vault.economy.Economy;
import java.util.*;
public abstract class BasePlugin extends JavaPlugin implements CommandExecutor,Listener {
 protected Economy economy;protected Journal journal;protected Transaction transactions;
 protected final Confirmations confirmations=new Confirmations();protected double price;
 protected abstract String commandName();protected abstract double defaultPrice();protected abstract void request(Player p,String[] args);
 @Override public void onEnable(){
  try{
   getConfig().addDefault("price",defaultPrice());getConfig().options().copyDefaults(true);saveConfig();price=Transaction.fee(getConfig().getDouble("price"));
   var registration=getServer().getServicesManager().getRegistration(Economy.class);
   if(registration==null)throw new IllegalStateException("Vault 经济服务缺失");economy=registration.getProvider();
   journal=new Journal(getDataFolder().toPath().resolve("transactions"));transactions=new Transaction(journal);
   Objects.requireNonNull(getCommand(commandName())).setExecutor(this);getServer().getPluginManager().registerEvents(this,this);
   ready();
  }catch(Exception|LinkageError e){getLogger().log(java.util.logging.Level.SEVERE,"依赖或初始化失败，插件已停用",e);getServer().getPluginManager().disablePlugin(this);}
 }
 protected void ready(){}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(sender instanceof ConsoleCommandSender){
   if(args.length==1&&args[0].equalsIgnoreCase("audit")){try{transactions.knownUnpaidAudit().forEach((id,evidence)->sender.sendMessage("本进程未扣款恢复证据 "+id+" "+evidence));var rows=journal.audit();sender.sendMessage("本地事务审计（SUBMITTED 并非数据库提交确认），记录数="+rows.size());rows.forEach(sender::sendMessage);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"审计失败",e);}return true;}
   if(args.length==1&&args[0].equalsIgnoreCase("selftest")){try{DomainCheck.run();sender.sendMessage("领域自检通过：实际执行验证、支付失败、补偿、重复请求断言；未触碰玩家数据。");}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"领域自检失败",e);sender.sendMessage("领域自检失败，请查看日志。");}return true;}
   return console(sender,args);
  }
  if(!(sender instanceof Player p)){sender.sendMessage("仅玩家或本机控制台可以使用。");return true;}
  if(!p.hasPermission(permission())){p.sendMessage("§c你没有使用此功能的权限。");return true;}
  schedule(p,()->{if(!economyReady()){p.sendMessage("§c经济服务不可用，未执行交易，请联系管理员。");return;}request(p,args);});return true;
 }
 protected String permission(){return commandName()+".use";}
 protected boolean console(CommandSender s,String[] args){s.sendMessage("控制台只读命令："+commandName()+" audit | selftest");return true;}
 protected void schedule(Player p,Runnable r){p.getScheduler().run(this,t->{if(p.isOnline())try{r.run();}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"玩家请求异常 uuid="+p.getUniqueId(),e);p.sendMessage("§c操作异常，请联系管理员核对事务日志，不要重复付款。");}},null);}
 protected boolean economyReady(){var current=getServer().getServicesManager().getRegistration(Economy.class);return current!=null&&current.getProvider()==economy&&economy.isEnabled();}
 protected boolean charge(Player p,double amount){
  if(!economyReady())return false;
  if(!economy.has(p,amount))return false;
  var r=economy.withdrawPlayer(p,amount);if(!r.transactionSuccess())getLogger().warning("扣款拒绝 uuid="+p.getUniqueId()+" error="+r.errorMessage);return r.transactionSuccess();
 }
 protected boolean refund(Player p,double amount){var r=economy.depositPlayer(p,amount);if(!r.transactionSuccess())getLogger().severe("退款失败 uuid="+p.getUniqueId()+" amount="+amount+" error="+r.errorMessage);return r.transactionSuccess();}
 protected void result(Player p,Transaction.Result r){
  if(r==Transaction.Result.REVIEW)getLogger().severe("需要人工核对事务 uuid="+p.getUniqueId()+"，请执行控制台 audit，禁止盲目重试或退款");
  p.sendMessage(switch(r){
   case SUCCESS->"§a购买成功。";
   case SUBMITTED->"§e已重抽全部任务并提交保存请求；保存结果需核验，本日不可再次重抽。";
   case INVALID->"§c确认已过期或业务条件已变化，未扣费，请重新打开。";
   case UNPAID->"§c准备失败或扣款被拒绝，本次未扣费；可重新打开确认。";
   case REFUNDED->"§c操作失败，已回滚并退还费用，未消耗额度。";
   case REVIEW->"§c交易结果异常，已锁定自动重试；请联系管理员核对或补偿，不能视为成功。";
   case BLOCKED->"§c今日额度已使用、交易正在处理或有待核对记录，本次未重复扣款。";
  });
 }
 protected void confirmation(Player p,String description,String token){p.sendMessage(ConfirmationMessage.create(commandName(),description,token));}
 protected boolean cancel(Player p,String[] args){
  if(args.length==2&&args[0].equalsIgnoreCase("cancel")){confirmations.take(p.getUniqueId(),args[1],System.currentTimeMillis());p.sendMessage("§e已取消此确认，未扣费。");return true;}return false;
 }
 @EventHandler public void quit(PlayerQuitEvent e){confirmations.clear(e.getPlayer().getUniqueId());}
}
