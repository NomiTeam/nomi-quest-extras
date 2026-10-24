package org.nomiteam.quests;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
public final class Evidence {
 public static void save(Path directory,String transaction,Properties p)throws java.io.IOException{
  Files.createDirectories(directory);Path file=directory.resolve(UUID.fromString(transaction).toString()+".properties");
  try(var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){p.store(out,"Private transaction evidence; not a database commit receipt");}
  try(var channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}
  try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
 }
}
