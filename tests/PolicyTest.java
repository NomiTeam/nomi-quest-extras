package org.nomiteam.quests;

import java.util.Set;

public final class PolicyTest {
    static void check(boolean result) {
        if (!result) {
            throw new AssertionError();
        }
    }

    public static void main(String[] args) {
        check(CommandPolicy.block("/ODailyQuests:DQ\tReRoLl 1", Set.of("dq"), Set.of("reroll")));
        check(CommandPolicy.block("/quests   resetone 1", Set.of("quests"), Set.of("resetone")));
        check(!CommandPolicy.block("/other reroll 1", Set.of("dq"), Set.of("reroll")));
        check(CommandPolicy.action(new String[]{"ReRoLl", "1"}, Set.of("reroll")));
        check(CommandPolicy.action(new String[]{"resetone", "1"}, Set.of("resetone")));
        check(!CommandPolicy.action(new String[]{}, Set.of("reroll")));
        check(!CommandPolicy.action(new String[]{"me"}, Set.of("reroll")));
        System.out.println("PASS namespace, whitespace, aliases");
    }
}
