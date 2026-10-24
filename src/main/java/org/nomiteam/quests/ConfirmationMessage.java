package org.nomiteam.quests;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
public final class ConfirmationMessage {
 public static Component create(String command,String description,String token){
  return Component.text(description+" 60秒内点击：")
   .append(Component.text("[确认扣费]").clickEvent(ClickEvent.runCommand("/"+command+" confirm "+token)))
   .append(Component.text("  [取消]").clickEvent(ClickEvent.runCommand("/"+command+" cancel "+token)));
 }
}
