package org.nomiteam.quests;
import java.util.*;
import java.util.concurrent.*;
public final class TransactionTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Port implements Transaction.Port {
  boolean valid=true,paid=true,refund=true,applied,failApply,failRollback,throwCharge,uncertainSave; int charges,refunds;
  public boolean compensationUnconfirmed(){return uncertainSave;}
  public boolean valid(){return valid;}
  public boolean charge(double fee){charges++;if(throwCharge)throw new IllegalStateException();return paid;}
  public void apply(){applied=true;if(failApply)throw new IllegalStateException();}
  public boolean rollback(){applied=false;return !failRollback;}
  public boolean refund(double fee){refunds++;return refund;}
 }
 static class Store implements Transaction.Store {
  final Map<String,Transaction.Record> rows=new ConcurrentHashMap<>();boolean fail;
  public Transaction.Record read(String id){return rows.get(id);}
  public void write(String id,Transaction.Record row){if(fail)throw new IllegalStateException();rows.put(id,row);}
 }
 public static void main(String[] args)throws Exception{
  if(args.length==0)main(new String[]{"prepare"});
  for(double fee:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,0.001,1_000_000_001}){try{Transaction.fee(fee);throw new AssertionError();}catch(IllegalArgumentException good){}}
  check(Transaction.fee(0)==0);check(Transaction.fee(500)==500);
  if(args.length>0&&args[0].equals("prepare")){
   Store prep=new Store();Transaction retry=new Transaction(prep);
   Port bad=new Port(){public void prepared(String tx){throw new IllegalStateException("evidence failure before payment");}};
   check(retry.run("prepare","x",100,bad,false)==Transaction.Result.UNPAID);check(bad.charges==0);
   check(retry.run("prepare","x",100,new Port(),false)==Transaction.Result.SUCCESS);
   Port validation=new Port(){public boolean valid(){throw new IllegalStateException("validation before payment");}};
   check(retry.run("validation","x",100,validation,false)==Transaction.Result.UNPAID);
   check(retry.run("validation","x",100,new Port(),false)==Transaction.Result.SUCCESS);
   System.out.println("PASS preparation failure retry without charge");return;
  }
  Store s=new Store();Transaction t=new Transaction(s);Port p=new Port();
  check(t.run("a","day1",500,p,true)==Transaction.Result.SUBMITTED);check(p.charges==1);
  check(t.run("a","day1",500,p,true)==Transaction.Result.BLOCKED);check(p.charges==1);
  check(new Transaction(s).run("a","day1",500,p,true)==Transaction.Result.BLOCKED);
  check(t.run("a","day2",500,p,true)==Transaction.Result.SUBMITTED);
  p=new Port();p.valid=false;check(t.run("b","x",100,p,false)==Transaction.Result.INVALID);check(p.charges==0);
  p=new Port();p.paid=false;check(t.run("c","x",100,p,false)==Transaction.Result.UNPAID);check(!p.applied);
  p=new Port();p.failApply=true;check(t.run("d","x",100,p,false)==Transaction.Result.REFUNDED);check(p.refunds==1&&!p.applied);
  p=new Port();p.failApply=true;p.refund=false;check(t.run("e","x",100,p,false)==Transaction.Result.REVIEW);check(new Transaction(s).run("e","y",100,p,false)==Transaction.Result.BLOCKED);
  p=new Port();p.throwCharge=true;check(t.run("f","x",100,p,false)==Transaction.Result.REVIEW);check(!p.applied&&p.refunds==0);
  p=new Port();p.failApply=true;p.failRollback=true;check(t.run("g","x",100,p,false)==Transaction.Result.REVIEW);check(p.refunds==0);
  Store broken=new Store();broken.fail=true;p=new Port();check(new Transaction(broken).run("h","x",100,p,false)==Transaction.Result.REVIEW);check(p.charges==0);
  Store afterWrite=new Store(){public void write(String id,Transaction.Record row){super.write(id,row);throw new IllegalStateException("write completed then IO failure");}};
  Port noPayment=new Port();Transaction uncertainIntent=new Transaction(afterWrite);
  check(uncertainIntent.run("io","x",100,noPayment,false)==Transaction.Result.REVIEW);check(noPayment.charges==0);
  check(new Transaction(afterWrite).run("io","x",100,noPayment,false)==Transaction.Result.BLOCKED);
  Port rollbackThrows=new Port(){public boolean rollback(){throw new IllegalStateException("rollback failure");}};rollbackThrows.failApply=true;
  check(t.run("rollback-throws","x",100,rollbackThrows,false)==Transaction.Result.REVIEW);check(rollbackThrows.refunds==0);
  Port concurrent=new Port();ExecutorService pool=Executors.newFixedThreadPool(8);List<Future<Transaction.Result>> fs=new ArrayList<>();
  for(int i=0;i<20;i++)fs.add(pool.submit(()->t.run("parallel","day",500,concurrent,true)));
  int ok=0;for(var f:fs)if(f.get()==Transaction.Result.SUBMITTED)ok++;pool.shutdown();check(ok==1&&concurrent.charges==1);
  Port compensate=new Port();compensate.failApply=true;compensate.uncertainSave=true;check(t.run("compensate","x",500,compensate,true)==Transaction.Result.REVIEW);check(compensate.refunds==0);check(s.read("compensate").phase().equals("COMPENSATION_UNCONFIRMED"));
  Port stale=new Port(){public boolean charge(double amount){boolean r=super.charge(amount);valid=false;return r;}};
  check(t.run("stale","x",100,stale,false)==Transaction.Result.REFUNDED);check(!stale.applied&&stale.refunds==1);
  Port refundException=new Port(){public boolean refund(double f){throw new IllegalStateException("deliberate refund exception");}};refundException.failApply=true;
  check(t.run("refund-exception","x",100,refundException,false)==Transaction.Result.REVIEW);
  Port reentrant=new Port(){public void apply(){check(t.run("reentrant","day",100,new Port(),true)==Transaction.Result.BLOCKED);}};
  check(t.run("reentrant","day",100,reentrant,true)==Transaction.Result.SUBMITTED);
  Port zero=new Port();check(t.run("zero","x",0,zero,false)==Transaction.Result.SUCCESS);check(zero.charges==0);
  System.out.println("PASS transaction: fees, validation, charge, rollback/refund, ambiguity, restart, concurrent quota");
 }
}
