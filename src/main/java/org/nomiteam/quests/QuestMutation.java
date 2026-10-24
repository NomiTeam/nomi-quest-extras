package org.nomiteam.quests;
import java.util.Map;
import java.util.function.Consumer;
/** A void save request has no durable commit acknowledgement, including on normal return. */
public final class QuestMutation<T> {
 private final Map<String,T> active;private final String key;private final T old,next;private final Consumer<T> save;
 private boolean replaced,saveAttempted;
 public QuestMutation(Map<String,T> active,String key,T old,T next,Consumer<T> save){this.active=active;this.key=key;this.old=old;this.next=next;this.save=save;}
 public void apply(){if(!active.replace(key,old,next))throw new IllegalStateException("任务对象已变化");replaced=true;saveAttempted=true;save.accept(next);}
 public boolean rollback(){
  if(!replaced)return true;
  if(!active.replace(key,next,old))return false;
  if(saveAttempted){save.accept(old);return false;}
  return true;
 }
 public boolean unconfirmed(){return saveAttempted;}
}
