package org.nomiteam.quests;
import java.util.*;
public final class KnownUnpaidTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static class Store extends TransactionTest.Store {
  String phase="CHARGE_PENDING";boolean after,broken=true; final List<Transaction.Record> writes=new ArrayList<>();
  public void write(String id,Transaction.Record row){
   if(broken&&row.phase().equals(phase)){if(after)super.write(id,row);throw new IllegalStateException("injected "+phase+" after="+after);}
   super.write(id,row);writes.add(row);
  }
 }
 public static void main(String[] args)throws Exception{
  for(boolean prior:new boolean[]{false,true})for(boolean after:new boolean[]{false,true})for(String phase:new String[]{"CHARGE_PENDING","UNPAID"}){
   Store s=new Store();if(prior)s.rows.put("a",new Transaction.Record(UUID.randomUUID().toString(),"old","UNPAID",50));s.after=after;s.phase=phase;Transaction t=new Transaction(s);TransactionTest.Port p=new TransactionTest.Port();p.paid=false;
   check(t.run("a","day",100,p,false)==Transaction.Result.REVIEW);check(p.charges==(phase.equals("UNPAID")?1:0));
   Transaction.Record original=s.read("a");
   var evidence=t.knownUnpaidAudit().get("a");check(evidence!=null);
   check(evidence.reason().equals(phase.equals("UNPAID")?"CHARGE_REJECTED":"CHARGE_NOT_CALLED"));
   check(t.knownUnpaidAudit().size()==1);
   // Recovery write failure, including persist-then-throw, must not permit another charge.
   s.phase="UNPAID";check(t.run("a","day",100,p,false)!=Transaction.Result.SUCCESS);check(p.charges==(phase.equals("UNPAID")?1:0));
   s.broken=false;TransactionTest.Port retry=new TransactionTest.Port();
   check(t.run("a","day",100,retry,false)==Transaction.Result.SUCCESS);check(retry.charges==1);check(t.knownUnpaidAudit().isEmpty());
   Transaction.Record unpaid=s.writes.stream().filter(x->x.phase().equals("UNPAID")).findFirst().orElseThrow();
   check(unpaid.transaction().equals(evidence.intent().transaction()));
  }
  for(int change=0;change<5;change++){
   Store s=new Store();s.after=true;Transaction t=new Transaction(s);check(t.run("a","day",100,new TransactionTest.Port(),false)==Transaction.Result.REVIEW);
   Transaction.Record pending=s.read("a");Transaction.Record changed=new Transaction.Record(change==0?UUID.randomUUID().toString():pending.transaction(),change==2?"changed":"day",change==1?"PAID":change==4?"UNPAID":"CHARGE_PENDING",change>=3?200:100);
   s.rows.put("a",changed);s.broken=false;TransactionTest.Port p=new TransactionTest.Port();
   check(t.run("a","day",100,p,false)==Transaction.Result.BLOCKED);check(s.read("a").equals(changed)&&p.charges==0);
   check(t.run("other","day",100,new TransactionTest.Port(),false)==Transaction.Result.SUCCESS);
  }
  Store restart=new Store();restart.after=true;check(new Transaction(restart).run("a","day",100,new TransactionTest.Port(),false)==Transaction.Result.REVIEW);restart.broken=false;
  check(new Transaction(restart).run("a","day",100,new TransactionTest.Port(),false)==Transaction.Result.BLOCKED);
  for(boolean refund:new boolean[]{false,true}){
   Store s=new Store();s.broken=false;Transaction t=new Transaction(s);TransactionTest.Port p=new TransactionTest.Port(){public boolean refund(double f){throw new IllegalStateException("refund unknown");}};
   p.throwCharge=!refund;p.failApply=refund;
   check(t.run("a","day",100,p,false)==Transaction.Result.REVIEW);
   check(t.run("a","day",100,new TransactionTest.Port(),false)==Transaction.Result.BLOCKED);
  }
  // Exercise the production Journal: recovery must create the old tx UNPAID history.
  java.nio.file.Path root=java.nio.file.Files.createTempDirectory("known-unpaid-journal-");
  try{
   Journal journal=new Journal(root);String id=UUID.randomUUID().toString();
   boolean[] broken={true};
   Transaction.Store faulty=new Transaction.Store(){
    public Transaction.Record read(String key)throws Exception{return journal.read(key);}
    public void write(String key,Transaction.Record row)throws Exception{
     journal.write(key,row);if(broken[0])throw new java.io.IOException("real journal persisted then caller failed");
    }
   };
   Transaction t=new Transaction(faulty);TransactionTest.Port p=new TransactionTest.Port();
   check(t.run(id,"day",100,p,false)==Transaction.Result.REVIEW);check(p.charges==0);
   Transaction.Record pending=new Journal(root).read(id);
   check(new Transaction(new Journal(root)).run(id,"day",100,p,false)==Transaction.Result.BLOCKED);
   // A recovery UNPAID that persisted then threw still leaves this process locked.
   check(t.run(id,"day",100,p,false)==Transaction.Result.REVIEW);check(p.charges==0&&!t.knownUnpaidAudit().isEmpty());
   broken[0]=false;check(t.run(id,"day",100,p,false)==Transaction.Result.SUCCESS);check(p.charges==1);
   check(java.nio.file.Files.exists(root.resolve(id+"-"+pending.transaction()+"-UNPAID.history")));
   check(new Journal(root).read(id).phase().equals("SUCCESS"));
  }finally{try(var files=java.nio.file.Files.walk(root)){for(var path:files.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}}
  System.out.println("PASS known-unpaid recovery, failed persistence, transaction guards, restart and unknown money locks");
 }
}
