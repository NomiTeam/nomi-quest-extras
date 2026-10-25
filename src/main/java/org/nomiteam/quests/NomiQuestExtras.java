package org.nomiteam.quests;

import com.ordwen.odailyquests.ODailyQuests;
import com.ordwen.odailyquests.configuration.essentials.QuestsPerCategory;
import com.ordwen.odailyquests.configuration.functionalities.CommandAliases;
import com.ordwen.odailyquests.quests.categories.CategoriesLoader;
import com.ordwen.odailyquests.quests.player.PlayerQuests;
import com.ordwen.odailyquests.quests.player.QuestsManager;
import com.ordwen.odailyquests.quests.player.progression.Progression;
import com.ordwen.odailyquests.quests.types.AbstractQuest;
import com.ordwen.odailyquests.tools.RenewSchedule;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 命令只开确认菜单；整组抽取使用 ODQ API，费用和余额判断归 TrMenu。 */
public final class NomiQuestExtras extends JavaPlugin implements CommandExecutor, Listener {
    private final MenuBridge bridge = new MenuBridge();
    private PluginCommand nativeCommand;
    private CommandExecutor originalExecutor;
    private CommandExecutor guardedExecutor;

    @Override
    public void onEnable() {
        try {
            if (!getServer().getPluginManager().isPluginEnabled("ODailyQuests")
                    || !ODailyQuests.INSTANCE.getDescription().getVersion().equals("3.0.2")) {
                throw new IllegalStateException("需要 ODailyQuests 3.0.2");
            }
            Objects.requireNonNull(getCommand("nomiquestreset")).setExecutor(this);
            getServer().getPluginManager().registerEvents(this, this);
            guardNativeReroll();
            if (!QuestState.calendarSchedule(RenewSchedule.settings())) {
                getLogger().warning("付费重抽暂不可用：ODQ 须为模式1、每日00:00、Asia/Shanghai；不自动修改其配置。");
            }
        } catch (Exception | LinkageError failure) {
            getLogger().log(Level.SEVERE, "初始化失败", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void guardNativeReroll() {
        nativeCommand = Objects.requireNonNull(ODailyQuests.INSTANCE.getCommand("dquests"));
        originalExecutor = Objects.requireNonNull(nativeCommand.getExecutor());
        guardedExecutor = (sender, command, label, args) -> {
            if (sender instanceof Player player && CommandPolicy.action(args, rerollActions())) {
                player.sendMessage("§e请使用 /nomiquestreset 重抽整组任务，每天一次。");
                return true;
            }
            return originalExecutor.onCommand(sender, command, label, args);
        };
        nativeCommand.setExecutor(guardedExecutor);
    }

    @Override
    public void onDisable() {
        bridge.clear();
        if (nativeCommand != null && nativeCommand.getExecutor() == guardedExecutor) {
            nativeCommand.setExecutor(originalExecutor);
        }
    }

    private Set<String> rerollActions() {
        Set<String> actions = new HashSet<>(Set.of("reroll"));
        CommandAliases.getSubcommandAliases("reroll")
                .forEach(alias -> actions.add(alias.toLowerCase(Locale.ROOT)));
        return actions;
    }

    private PlayerQuests active(Player player) {
        return QuestsManager.getActiveQuests().get(player.getName());
    }

    private Map<String, Integer> expected(Player player) {
        return new HashMap<>(QuestsPerCategory.resolveAllFor(player));
    }

    private boolean eligible(Player player) {
        if (!isEnabled() || !player.isOnline() || !player.hasPermission("nomiquestreset.use")
                || !getServer().getPluginManager().isPluginEnabled("ODailyQuests")) {
            return false;
        }
        PlayerQuests quests = active(player);
        // 不检查完成数；未完成、部分完成或全部完成都可刷新整组。
        return QuestState.available(quests, RenewSchedule.settings(), System.currentTimeMillis())
                && !quests.getQuests().isEmpty()
                && quests.getQuests().values().stream().allMatch(Objects::nonNull);
    }

    private boolean completeDraw(Player player, Map<AbstractQuest, Progression> drawn) {
        if (drawn == null || !QuestState.complete(expected(player),
                drawn.keySet().stream().map(AbstractQuest::getCategoryName).toList())) {
            return false;
        }
        if (drawn.values().stream().anyMatch(progress -> progress == null
                || progress.getAdvancement() != 0 || progress.isAchieved())) {
            return false;
        }
        return drawn.keySet().stream().allMatch(quest ->
                CategoriesLoader.getAllCategories().containsKey(quest.getCategoryName())
                && CategoriesLoader.getCategoryByName(quest.getCategoryName()).contains(quest));
    }

    private record State(Player owner, QuestState.Snapshot quests, Map<String, Integer> expected) {}

    private MenuBridge.Port port(Player player) {
        return new MenuBridge.Port() {
            public boolean owned() {
                return Bukkit.isOwnedByCurrentRegion(player);
            }

            public boolean valid() {
                return eligible(player);
            }

            public Object snapshot() {
                return new State(player, QuestState.capture(active(player)), expected(player));
            }

            public boolean matches(Object value) {
                State state = (State) value;
                return state.owner() == player && state.quests().matches(active(player))
                        && state.expected().equals(expected(player));
            }

            public Object draw() {
                Map<AbstractQuest, Progression> drawn = QuestsManager.selectRandomQuests(player);
                return completeDraw(player, drawn) ? QuestState.replacement(active(player), drawn) : null;
            }

            public String apply(Object value) {
                PlayerQuests old;
                PlayerQuests next;
                State before;
                try {
                    old = active(player);
                    next = (PlayerQuests) value;
                    if (!completeDraw(player, next.getQuests())) {
                        return "REJECTED";
                    }
                    before = (State) snapshot();
                } catch (Exception | LinkageError failure) {
                    return "REJECTED";
                }
                // 关闭库存可同步触发第三方回调，必须在回调后再核验状态。
                return QuestState.apply(QuestsManager.getActiveQuests(), player.getName(), old, next,
                        player::closeInventory, () -> valid() && matches(before),
                        quests -> ODailyQuests.INSTANCE.getDatabaseManager().saveProgressionForPlayer(
                                player.getName(), player.getUniqueId().toString(), quests));
            }
        };
    }

    /** 仅准备，不变更任务、不扣费；三个 API 须在本人实体线程顺序调用。 */
    public boolean prepare(Player player) {
        return player != null && bridge.prepare(player.getUniqueId(), port(player), System.currentTimeMillis());
    }

    /** SUCCESS 表示应用且 void 保存调用返回，不代表数据库提交回执。 */
    public String apply(Player player) {
        String result = player == null ? "REJECTED"
                : bridge.apply(player.getUniqueId(), port(player), System.currentTimeMillis());
        if (result.equals("UNCERTAIN")) {
            getLogger().warning("ODQ 重抽副作用或保存结果不确定，请核对；不自动回滚 uuid=" + player.getUniqueId());
        }
        return result;
    }

    public void cancel(Player player) {
        if (player != null && Bukkit.isOwnedByCurrentRegion(player)) {
            bridge.cancel(player.getUniqueId());
        }
    }

    private void open(Player player) {
        player.getScheduler().run(this, task -> {
            if (!player.isOnline() || !isEnabled()) {
                return;
            }
            bridge.cancel(player.getUniqueId());
            if (!player.hasPermission("nomiquestreset.use")) {
                return;
            }
            openMenu(player);
        }, () -> bridge.cancel(player.getUniqueId()));
    }

    /** 只在上方目标玩家实体调度器调用，不转派全局控制台命令。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void openMenu(Player player) {
        var tr = getServer().getPluginManager().getPlugin("TrMenu");
        if (tr == null || !tr.isEnabled()) {
            player.sendMessage("§c菜单暂不可用：TrMenu 未启用。");
            return;
        }
        try {
            ClassLoader loader = tr.getClass().getClassLoader();
            String prefix = "me.arasple.mc.trmenu";
            Class<?> api;
            try {
                api = Class.forName(prefix + ".api.TrMenuAPI", true, loader);
            } catch (ClassNotFoundException absent) {
                prefix = "trplugins.menu";
                api = Class.forName(prefix + ".api.TrMenuAPI", true, loader);
            }
            Object menu = api.getMethod("getMenuById", String.class)
                    .invoke(api.getField("INSTANCE").get(null), "DailyQuestReset");
            if (menu == null) {
                player.sendMessage("§c菜单未配置：DailyQuestReset");
                return;
            }
            Class<? extends Enum> reason = (Class<? extends Enum>) Class.forName(
                    prefix + ".api.event.MenuOpenEvent$Reason", true, loader);
            menu.getClass().getMethod("open", Player.class, Integer.class, reason, Consumer.class)
                    .invoke(menu, player, null, Enum.valueOf(reason, "BINDING_COMMANDS"),
                            (Consumer<Object>) ignored -> {});
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            getLogger().log(Level.WARNING, "打开 DailyQuestReset 菜单失败", failure);
            player.sendMessage("§c菜单打开失败，请联系管理员。");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 0) {
                open(player);
            } else {
                player.sendMessage("§e请使用 /nomiquestreset 打开确认菜单。");
            }
            return true;
        }
        if (sender instanceof ConsoleCommandSender && args.length == 1
                && args[0].matches("[A-Za-z0-9_]{1,16}")) {
            Player player = getServer().getPlayerExact(args[0]);
            if (player != null) {
                open(player);
            } else {
                sender.sendMessage("玩家不在线。");
            }
            return true;
        }
        sender.sendMessage("用法：dailyquestreset <在线玩家>");
        return true;
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cancel(player);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        bridge.cancel(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockSingle(PlayerCommandPreprocessEvent event) {
        Set<String> bases = new HashSet<>(Set.of("dq", "dquests", "quests", "dailyquests"));
        PluginCommand command = ODailyQuests.INSTANCE.getCommand("dquests");
        if (command != null) {
            bases.add(CommandPolicy.base(command.getName()));
            command.getAliases().forEach(alias -> bases.add(CommandPolicy.base(alias)));
        }
        if (CommandPolicy.block(event.getMessage(), bases, rerollActions())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§e请使用 /nomiquestreset 重抽整组任务，每天一次。");
        }
    }
}
