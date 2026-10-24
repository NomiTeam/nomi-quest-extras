import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import com.ordwen.odailyquests.quests.player.QuestsManager;
public final class NomiDailyExpansion extends PlaceholderExpansion {
 public String getIdentifier(){return "nomidaily";}
 public String getAuthor(){return "NomiTeam";}
 public String getVersion(){return "1.0.0";}
 public String getRequiredPlugin(){return "ODailyQuests";}
 public String onRequest(OfflinePlayer player,String params){
  if(!params.equals("can_reset"))return null;
  if(player==null||player.getName()==null)return "0";
  var q=QuestsManager.getActiveQuests().get(player.getName());
  return q!=null&&!q.getQuests().isEmpty()&&q.getRecentlyRolled()<1?"1":"0";
 }
}
