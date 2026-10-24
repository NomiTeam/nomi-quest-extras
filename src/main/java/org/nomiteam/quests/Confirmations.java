package org.nomiteam.quests;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class Confirmations {
 public record Preview(String token,Object state,double fee,long expires){}
 private final Map<UUID,Preview> pending=new ConcurrentHashMap<>();
 public String issue(UUID player,Object state,double fee,long now){String token=UUID.randomUUID().toString();pending.put(player,new Preview(token,state,fee,now+60_000));return token;}
 public Preview take(UUID player,String token,long now){Preview p=pending.get(player);if(p==null||!p.token().equals(token))return null;if(!pending.remove(player,p)||now>p.expires())return null;return p;}
 public void clear(UUID player){pending.remove(player);}
}
