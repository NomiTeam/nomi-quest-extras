import com.ordwen.odailyquests.quests.player.PlayerQuests;
import com.ordwen.odailyquests.quests.player.QuestsManager;
import org.bukkit.OfflinePlayer;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Objects;

/** Real ODQ objects and API interface proxy; no replacement API classes. */
public final class ExpansionRegression {
    private static int checks;
    private static final NomiDailyExpansion expansion = new NomiDailyExpansion();
    private static OfflinePlayer player(String name) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
            new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> {
                if (method.getName().equals("getName")) return name;
                throw new AssertionError("Unexpected API call: " + method);
            });
    }
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        equal("nomidaily", expansion.getIdentifier());
        equal("NomiTeam", expansion.getAuthor());
        equal("1.0.0", expansion.getVersion());
        equal("ODailyQuests", expansion.getRequiredPlugin());
        equal(false, expansion.persist());
        OfflinePlayer p = player("RegressionPlayer");
        for (String unknown : new String[]{"loaded", "", "CAN_RESET", "can_reset_extra"})
            equal(null, expansion.onRequest(p, unknown));
        equal("0", expansion.onRequest(null, "can_reset"));
        equal("0", expansion.onRequest(player(null), "can_reset"));
        QuestsManager.getActiveQuests().clear();
        equal("0", expansion.onRequest(p, "can_reset"));
        var quests = new HashMap<com.ordwen.odailyquests.quests.types.AbstractQuest,
                                 com.ordwen.odailyquests.quests.player.progression.Progression>();
        PlayerQuests q = new PlayerQuests(0L, quests);
        QuestsManager.getActiveQuests().put("RegressionPlayer", q);
        equal("0", expansion.onRequest(p, "can_reset"));
        // Only nonemptiness is queried: populate after real constructor initialization.
        quests.put(null, null);
        var counter = PlayerQuests.class.getDeclaredField("recentRerolls");
        counter.setAccessible(true);
        for (int count : new int[]{Integer.MIN_VALUE, -1, 0, 1, 2, Integer.MAX_VALUE}) {
            counter.setInt(q, count);
            equal(count < 1 ? "1" : "0", expansion.onRequest(p, "can_reset"));
            equal(count, q.getRecentlyRolled());
        }
        equal("0", expansion.onRequest(player("regressionplayer"), "can_reset"));
        try {
            expansion.onRequest(p, null);
            throw new AssertionError("Expected legacy null-parameter NPE");
        } catch (NullPointerException expected) { checks++; }
        QuestsManager.getActiveQuests().clear();
        System.out.println("PASS " + checks + " checks");
    }
}
