package org.nomiteam.quests;
import java.util.concurrent.ConcurrentHashMap;
/** Durable intent precedes external money movement. No claim of cross-service atomicity. */
public final class Transaction {
 public enum Result {SUCCESS,SUBMITTED,INVALID,UNPAID,REFUNDED,REVIEW,BLOCKED}
 public record Record(String transaction,String cycle,String phase,double fee){}
 public interface Store {Record read(String id)throws Exception;void write(String id,Record row)throws Exception;}
 public interface Port {boolean valid();boolean charge(double fee);void apply();boolean rollback();boolean refund(double fee);default boolean compensationUnconfirmed(){return false;}default void prepared(String transaction)throws Exception{}}
 private final Store store;private final java.util.Set<String> busy=ConcurrentHashMap.newKeySet();
 private final java.util.Set<String> unsafe=ConcurrentHashMap.newKeySet();
 // Process-local proof is about the payment boundary, never about whether IO persisted.
 public record UnpaidEvidence(Record intent,Record previous,String reason){}
 private final java.util.Map<String,UnpaidEvidence> knownUnpaid=new ConcurrentHashMap<>();
 public java.util.Map<String,UnpaidEvidence> knownUnpaidAudit(){return java.util.Map.copyOf(knownUnpaid);}
 private void rememberUnpaid(String id,Record intent,Record previous,String reason){
  knownUnpaid.put(id,new UnpaidEvidence(intent,previous,reason));unsafe.add(id);
  System.err.println("KNOWN_UNPAID id="+id+" evidence="+knownUnpaid.get(id));
 }
 private boolean recoverUnpaid(String id)throws Exception{
  UnpaidEvidence evidence=knownUnpaid.get(id);if(evidence==null)return false;
  // Journal reads/writes use this same monitor. Compare the entire record, including phase.
  synchronized(store){
   Record current=store.read(id),intent=evidence.intent();
   Record unpaid=new Record(intent.transaction(),intent.cycle(),"UNPAID",intent.fee());
   if(!java.util.Objects.equals(current,evidence.previous())&&!intent.equals(current)&&!unpaid.equals(current))return false;
   // Always rewrite, even after a previous write persisted then threw: only normal
   // return confirms completion of the Store durability/history contract.
   store.write(id,unpaid);
   knownUnpaid.remove(id,evidence);unsafe.remove(id);return true;
  }
 }
 public Transaction(Store store){this.store=store;}
 public static double fee(double value){if(!Double.isFinite(value)||value<0||value>1_000_000_000||Math.abs(value*100-Math.rint(value*100))>0.000001)throw new IllegalArgumentException("费用必须为0至10亿且最多两位小数");return value;}
 public Result run(String id,String cycle,double amount,Port port,boolean asynchronous){
  fee(amount);if(!busy.add(id))return Result.BLOCKED;
  String tx=java.util.UUID.randomUUID().toString();
  try{
   if(unsafe.contains(id)&&!recoverUnpaid(id))return Result.BLOCKED;
   Record old=store.read(id);
   if(old!=null && (!java.util.Set.of("SUCCESS","SUBMITTED","UNPAID","REFUNDED").contains(old.phase()) || (old.phase().equals("SUBMITTED")&&old.cycle().equals(cycle))))return Result.BLOCKED;
   // No external payment can have started during validation/evidence preparation.
   try{if(!port.valid())return Result.INVALID;port.prepared(tx);}catch(Exception preparation){preparation.printStackTrace();return Result.UNPAID;}
   Record intent=new Record(tx,cycle,"CHARGE_PENDING",amount);
   try{store.write(id,intent);}catch(Exception failure){
    rememberUnpaid(id,intent,old,"CHARGE_NOT_CALLED");throw failure;
   }
   boolean charged=false;
   if(amount>0){
    try{charged=port.charge(amount);}catch(Exception e){e.printStackTrace();return review(id,tx,cycle,amount);}
    if(!charged){
     try{write(id,tx,cycle,"UNPAID",amount);}catch(Exception failure){
      rememberUnpaid(id,intent,intent,"CHARGE_REJECTED");throw failure;
     }
     return Result.UNPAID;
    }
   }
   try{
    write(id,tx,cycle,"PAID",amount);
    if(!port.valid())throw new IllegalStateException("stale request");
    port.apply();
   }catch(Exception e){
    e.printStackTrace();boolean rolled=false;try{rolled=port.rollback();}catch(Exception failure){failure.printStackTrace();}
    if(port.compensationUnconfirmed()){unsafe.add(id);write(id,tx,cycle,"COMPENSATION_UNCONFIRMED",amount);return Result.REVIEW;}
    if(!rolled)return review(id,tx,cycle,amount);
    write(id,tx,cycle,"REFUND_PENDING",amount);
    boolean refunded=!charged;try{if(charged)refunded=port.refund(amount);}catch(Exception failure){failure.printStackTrace();}
    if(!refunded)return review(id,tx,cycle,amount);
    write(id,tx,cycle,"REFUNDED",amount);return Result.REFUNDED;
   }
   String phase=asynchronous?"SUBMITTED":"SUCCESS";
   write(id,tx,cycle,phase,amount);return asynchronous?Result.SUBMITTED:Result.SUCCESS;
  }catch(Exception e){e.printStackTrace();unsafe.add(id);return Result.REVIEW;}finally{busy.remove(id);}
 }
 private void write(String id,String tx,String cycle,String phase,double fee)throws Exception{store.write(id,new Record(tx,cycle,phase,fee));}
 private Result review(String id,String tx,String cycle,double fee)throws Exception{unsafe.add(id);write(id,tx,cycle,"REVIEW",fee);return Result.REVIEW;}
}
