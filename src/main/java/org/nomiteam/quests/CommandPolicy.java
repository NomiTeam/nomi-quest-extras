package org.nomiteam.quests;
import java.util.*;
public final class CommandPolicy {
 public static String base(String s){s=s.toLowerCase(Locale.ROOT);return s.substring(s.lastIndexOf(':')+1);}
 public static boolean action(String[] args,Set<String> actions){return args.length>0&&actions.contains(args[0].toLowerCase(Locale.ROOT));}
 public static boolean block(String message,Set<String> commands,Set<String> actions){
  String[] p=message.replaceFirst("^/", "").strip().split("\\s+");return p.length>=2&&commands.contains(base(p[0]))&&actions.contains(p[1].toLowerCase(Locale.ROOT));
 }
}
