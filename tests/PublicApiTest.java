package org.nomiteam.quests;

import java.lang.reflect.Modifier;
import org.bukkit.entity.Player;

public final class PublicApiTest {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("org.nomiteam.quests.NomiQuestExtras", false,
                PublicApiTest.class.getClassLoader());
        for (String name : new String[]{"prepare", "apply", "cancel"}) {
            var method = type.getDeclaredMethod(name, Player.class);
            assert Modifier.isPublic(method.getModifiers());
            assert method.getReturnType() == switch (name) {
                case "prepare" -> boolean.class;
                case "apply" -> String.class;
                default -> void.class;
            };
        }
        assert org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", org.bukkit.entity.Entity.class)
                .getReturnType() == boolean.class;
        System.out.println("PASS exact public Player API signatures and real Paper entity ownership API");
    }
}
