package org.nomiteam.quests;
import java.nio.file.*;
import java.util.*;
public final class JournalTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("nomi-journal-test-");
  try{
   String id=UUID.randomUUID().toString(),tx=UUID.randomUUID().toString();Journal journal=new Journal(root);
   Transaction.Record before=new Transaction.Record(tx,"123","CHARGE_PENDING",500);journal.write(id,before);check(new Journal(root).read(id).equals(before));
   journal.write(id,new Transaction.Record(tx,"123","SUBMITTED",500));check(Files.exists(root.resolve(id+"-"+tx+"-CHARGE_PENDING.history")));check(new Journal(root).audit().size()==1);
   journal.write(id,new Transaction.Record(tx,"123","COMPENSATION_UNCONFIRMED",500));
   Transaction.Port blocked=new Transaction.Port(){public boolean valid(){throw new AssertionError();}public boolean charge(double f){throw new AssertionError();}public void apply(){throw new AssertionError();}public boolean rollback(){throw new AssertionError();}public boolean refund(double f){throw new AssertionError();}};
   check(new Transaction(new Journal(root)).run(id,"next",500,blocked,true)==Transaction.Result.BLOCKED);
   try{journal.write(id,new Transaction.Record("invalid","123","SUCCESS",Double.NaN));throw new AssertionError("accepted corrupt journal record");}catch(IllegalArgumentException good){}
   Files.writeString(root.resolve(id+".properties"),"transaction=broken\ncycle=123\nphase=SUCCESS\nfee=NaN\n");
   try{new Journal(root).read(id);throw new AssertionError("accepted corrupt stored record");}catch(IllegalArgumentException good){}
   System.out.println("PASS real filesystem: durable reload, phase evidence, audit, corrupt journal fail-closed");
  }finally{try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
}
