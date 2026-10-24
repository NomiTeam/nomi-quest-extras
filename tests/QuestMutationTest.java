package org.nomiteam.quests;
import java.util.*;
public final class QuestMutationTest {
 public static void main(String[] args){
  for(boolean failOld:new boolean[]{false,true}){
   Object old=new Object(),next=new Object();Map<String,Object> active=new HashMap<>();active.put("p",old);int[] saves={0},money={0,0};
   QuestMutation<Object> mutation=new QuestMutation<>(active,"p",old,next,value->{saves[0]++;if(value==next||failOld)throw new IllegalStateException("save request failure after possible side effect");});
   Map<String,Transaction.Record> rows=new HashMap<>();Transaction.Store store=new Transaction.Store(){public Transaction.Record read(String id){return rows.get(id);}public void write(String id,Transaction.Record row){rows.put(id,row);}};
   Transaction.Port port=new Transaction.Port(){public boolean valid(){return true;}public boolean charge(double f){money[0]++;return true;}public void apply(){mutation.apply();}public boolean rollback(){return mutation.rollback();}public boolean compensationUnconfirmed(){return mutation.unconfirmed();}public boolean refund(double f){money[1]++;return true;}};
   Transaction t=new Transaction(store);assert t.run("p","day",500,port,true)==Transaction.Result.REVIEW;
   assert money[0]==1&&money[1]==0&&saves[0]==2&&active.get("p")==old;
   assert new Transaction(store).run("p","day2",500,port,true)==Transaction.Result.BLOCKED;
   assert money[0]==1&&saves[0]==2;
  }
  Object old=new Object(),next=new Object();Map<String,Object> active=new HashMap<>();active.put("p",old);
  QuestMutation<Object> pending=new QuestMutation<>(active,"p",old,next,v->{});pending.apply();assert pending.unconfirmed();assert !pending.rollback();assert active.get("p")==old;
  System.out.println("PASS production quest mutation: next/old save throws, void async return never confirms restoration, no refund/reapply on restart");
 }
}
