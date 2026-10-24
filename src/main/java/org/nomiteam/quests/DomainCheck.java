package org.nomiteam.quests;
import java.util.*;
public final class DomainCheck {
 private static void require(boolean b){if(!b)throw new IllegalStateException("domain assertion failed");}
 public static void run(){
  Map<String,Transaction.Record> data=new HashMap<>();Transaction tx=new Transaction(new Transaction.Store(){public Transaction.Record read(String id){return data.get(id);}public void write(String id,Transaction.Record r){data.put(id,r);}});
  int[] movements={0,0};
  Transaction.Port port=new Transaction.Port(){public boolean valid(){return true;}public boolean charge(double f){movements[0]++;return true;}public void apply(){throw new IllegalStateException("selftest deliberate application failure");}public boolean rollback(){return true;}public boolean refund(double f){movements[1]++;return true;}};
  require(tx.run("test","cycle",100,port,false)==Transaction.Result.REFUNDED);require(movements[0]==1&&movements[1]==1);
  Transaction.Port success=new Transaction.Port(){public boolean valid(){return true;}public boolean charge(double f){return true;}public void apply(){}public boolean rollback(){return true;}public boolean refund(double f){return true;}};
  require(tx.run("quota","cycle",500,success,true)==Transaction.Result.SUBMITTED);require(tx.run("quota","cycle",500,success,true)==Transaction.Result.BLOCKED);
  Transaction.Port refused=new Transaction.Port(){public boolean valid(){return true;}public boolean charge(double f){return false;}public void apply(){throw new AssertionError();}public boolean rollback(){throw new AssertionError();}public boolean refund(double f){throw new AssertionError();}};
  require(tx.run("refused","cycle",100,refused,false)==Transaction.Result.UNPAID);
  Confirmations c=new Confirmations();UUID id=UUID.randomUUID();String token=c.issue(id,"state",100,0);require(c.take(id,token,1)!=null);require(c.take(id,token,2)==null);
 }
}
