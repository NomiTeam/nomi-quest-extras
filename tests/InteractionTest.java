package org.nomiteam.quests;
import net.kyori.adventure.text.event.ClickEvent;
public class InteractionTest {
 public static void main(String[] args){
  var message=ConfirmationMessage.create("nomi:test","fee 100","nonce");
  assert message.children().get(0).clickEvent().equals(ClickEvent.runCommand("/nomi:test confirm nonce"));
  assert message.children().get(1).clickEvent().equals(ClickEvent.runCommand("/nomi:test cancel nonce"));
  Confirmations c=new Confirmations();var owner=java.util.UUID.randomUUID();String token=c.issue(owner,"x",100,0);
  assert c.take(java.util.UUID.randomUUID(),token,1)==null;
  assert c.take(owner,token,1)!=null;assert c.take(owner,token,2)==null;
  System.out.println("PASS clickable confirm/cancel commands, owner and replay");
 }
}
