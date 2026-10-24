package org.nomiteam.quests;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
/** Per-account durable latest state plus transaction history. Contains private account identifiers. */
public final class Journal implements Transaction.Store {
 private final Path root;
 public Journal(Path root)throws IOException{this.root=root;Files.createDirectories(root);}
 private Path account(String id){return root.resolve(UUID.fromString(id).toString()+".properties");}
 public synchronized Transaction.Record read(String id)throws IOException{
  Path f=account(id);if(!Files.exists(f))return null;Properties p=new Properties();try(var in=Files.newInputStream(f)){p.load(in);}Transaction.Record row=new Transaction.Record(p.getProperty("transaction"),p.getProperty("cycle"),p.getProperty("phase"),Double.parseDouble(p.getProperty("fee")));validate(row);return row;
 }
 public synchronized void write(String id,Transaction.Record r)throws IOException{
  validate(r);account(id);
  Properties p=new Properties();p.setProperty("transaction",r.transaction());p.setProperty("cycle",r.cycle());p.setProperty("phase",r.phase());p.setProperty("fee",Double.toString(r.fee()));p.setProperty("updated",java.time.Instant.now().toString());
  Path temp=Files.createTempFile(root,"intent-",".tmp");
  try{
   try(var out=Files.newOutputStream(temp)){p.store(out,"Nomi transaction journal");}
   try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
   Files.move(temp,account(id),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   try(var dir=FileChannel.open(root,StandardOpenOption.READ)){dir.force(true);}
   Files.copy(account(id),root.resolve(id+"-"+r.transaction()+"-"+r.phase()+".history"),StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(temp);}
 }
 private static void validate(Transaction.Record row){
  if(row.transaction()==null||row.cycle()==null||row.cycle().isBlank()||row.phase()==null)throw new IllegalArgumentException("Missing journal fields");
  UUID.fromString(row.transaction());Transaction.fee(row.fee());
  if(!Set.of("CHARGE_PENDING","PAID","UNPAID","REFUND_PENDING","REFUNDED","REVIEW","REFUNDED_SAVE_UNCONFIRMED","COMPENSATION_UNCONFIRMED","SUCCESS","SUBMITTED").contains(row.phase()))throw new IllegalArgumentException("Unknown journal phase");
 }
 public synchronized List<String> audit()throws IOException{
  List<String> rows=new ArrayList<>();try(var paths=Files.list(root)){for(Path p:paths.filter(f->f.toString().endsWith(".properties")).sorted().toList()){String id=p.getFileName().toString().replace(".properties","");rows.add(id+" "+read(id));}}return rows;
 }
}
