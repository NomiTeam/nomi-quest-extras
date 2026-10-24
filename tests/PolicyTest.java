package org.nomiteam.quests;
import java.util.*;
public final class PolicyTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  Confirmations c=new Confirmations();UUID a=UUID.randomUUID(),b=UUID.randomUUID();Object state=new Object();
  String token=c.issue(a,state,100,10);check(c.take(b,token,11)==null);check(c.take(a,token,11).state()==state);check(c.take(a,token,12)==null);
  token=c.issue(a,state,100,10);check(c.take(a,token,71_000)==null);
  String old=c.issue(a,state,100,10);String next=c.issue(a,state,100,11);check(c.take(a,old,12)==null);check(c.take(a,next,12)!=null);
  check(CommandPolicy.block("/ODailyQuests:DQ\tReRoLl 1",Set.of("dq"),Set.of("reroll")));
  check(CommandPolicy.block("/quests   resetone 1",Set.of("quests"),Set.of("resetone")));
  check(!CommandPolicy.block("/other reroll 1",Set.of("dq"),Set.of("reroll")));
  check(CommandPolicy.action(new String[]{"ReRoLl","1"},Set.of("reroll")));
  check(CommandPolicy.action(new String[]{"resetone","1"},Set.of("resetone")));
  check(!CommandPolicy.action(new String[]{},Set.of("reroll")));
  check(!CommandPolicy.action(new String[]{"me"},Set.of("reroll")));
  System.out.println("PASS confirmation identity, expiry, replay, replacement; namespace, whitespace, aliases");
 }
}
