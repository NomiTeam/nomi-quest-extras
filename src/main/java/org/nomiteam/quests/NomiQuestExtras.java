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
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, DurableQuota.Request> quotaRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService quotaWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private DurableQuota.Store quota;
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
            saveDefaultConfig();
            String url = getConfig().getString("quota.jdbc-url", "");
            String user = getConfig().getString("quota.username", "");
            String password = getConfig().getString("quota.password", "");
            String driver = getConfig().getString("quota.jdbc-driver-class", "");
            if (!url.isBlank() && !driver.isBlank()) Class.forName(driver, true, getClass().getClassLoader());
            if (!url.isBlank()) quota = new DurableQuota.Jdbc(() -> java.sql.DriverManager.getConnection(url, user, password));
            else getLogger().warning("独立共享日限未配置：收费入口关闭。");
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
        quotaRequests.keySet().forEach(this::cancelRequest);
        quotaWorker.shutdown();
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
        return QuestState.current(quests, RenewSchedule.settings(), System.currentTimeMillis())
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

    /** Click entry; returns immediately. Only the entity callback can continue TrMenu. */
    public void begin(Player player, Object session) {
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player) || quota == null
                || !eligible(player) || !sessionValid(player, session)) return;
        var request = new DurableQuota.Request(player.getUniqueId(), System.currentTimeMillis());
        if (quotaRequests.putIfAbsent(player.getUniqueId(), request) != null) return;
        try {
            quotaWorker.execute(() -> {
                try {
                    if (!quota.acquire(request.player, request.day, request.token)) {
                        quotaRequests.remove(request.player, request); return;
                    }
                    request.acquired();
                    if (request.cancelled()) { release(request); return; }
                    var scheduled = player.getScheduler().run(this, task -> {
                        if (quotaRequests.get(request.player) != request || !isEnabled()
                                || !request.ready(System.currentTimeMillis()) || !eligible(player)
                                || !sessionValid(player, session)) {
                            cancelRequest(request); return;
                        }
                        try {
                            continueMenu(session);
                        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                            getLogger().warning("TrMenu 资格回调失败；不会自动重放。");
                        } finally {
                            cancelRequest(request);
                        }
                    }, () -> cancelRequest(request));
                    if (scheduled == null) cancelRequest(request);
                } catch (Exception | LinkageError failure) {
                    // INSERT/connection outcome may be unknown: never delete or replay.
                    quotaRequests.remove(request.player, request);
                    request.cancel();
                    getLogger().warning("独立日限数据库失败，未进入收费链；不自动重试。");
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            cancelRequest(request);
        }
    }

    private boolean sessionValid(Player player, Object session) {
        try {
            if (session == null || session.getClass().getMethod("getViewer").invoke(session) != player
                    || session.getClass().getMethod("getAgent").invoke(session) != player
                    || !Boolean.TRUE.equals(session.getClass().getMethod("isViewing").invoke(session))) return false;
            Object sessions = session.getClass().getField("SESSIONS").get(null);
            if (((Map<?, ?>) sessions).get(player.getUniqueId()) != session) return false;
            Object menu = session.getClass().getMethod("getMenu").invoke(session);
            return "DailyQuestReset".equals(menu.getClass().getMethod("getId").invoke(menu));
        } catch (ReflectiveOperationException | RuntimeException failure) { return false; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void continueMenu(Object session) throws ReflectiveOperationException {
        ClassLoader loader = session.getClass().getClassLoader();
        String prefix = session.getClass().getName().substring(0, session.getClass().getName().indexOf(".module.display.MenuSession"));
        Class<? extends Enum> click = (Class<? extends Enum>) Class.forName(prefix + ".api.receptacle.ReceptacleClickType", true, loader);
        Object menu = session.getClass().getMethod("getMenu").invoke(session);
        Object icon = menu.getClass().getMethod("getIcon", String.class).invoke(menu, "quota_continue");
        Object property = icon.getClass().getMethod("getProperty", session.getClass()).invoke(icon, session);
        property.getClass().getMethod("handleClick", click, session.getClass())
                .invoke(property, Enum.valueOf(click, "LEFT"), session);
    }

    private void release(DurableQuota.Request request) {
        try { quota.release(request.player, request.day, request.token); }
        catch (Exception failure) { getLogger().warning("日限撤销失败，保留占位，不重试收费。"); }
    }

    private void cancelRequest(DurableQuota.Request request) {
        quotaRequests.remove(request.player, request);
        if (request.cancel()) {
            try { quotaWorker.execute(() -> release(request)); }
            catch (java.util.concurrent.RejectedExecutionException stopped) { /* retain */ }
        }
    }

    private void cancelRequest(java.util.UUID id) {
        DurableQuota.Request request = quotaRequests.remove(id);
        if (request != null && request.cancel()) {
            try { quotaWorker.execute(() -> release(request)); }
            catch (java.util.concurrent.RejectedExecutionException stopped) { /* safely retain quota */ }
        }
    }

    /** Hidden continuation only: validation and irreversible quota arming BEFORE TrMenu debit. */
    public boolean prepare(Player player) {
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) return false;
        var request = quotaRequests.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (request == null || !request.ready(now) || !bridge.prepare(player.getUniqueId(), port(player), now)) return false;
        if (!request.arm(System.currentTimeMillis())) { bridge.cancel(player.getUniqueId()); return false; }
        return true;
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
            cancelRequest(player.getUniqueId());
        }
    }

    private void open(Player player) {
        player.getScheduler().run(this, task -> {
            if (!player.isOnline() || !isEnabled()) {
                return;
            }
            bridge.cancel(player.getUniqueId());
            cancelRequest(player.getUniqueId());
            if (!player.hasPermission("nomiquestreset.use")) {
                return;
            }
            openMenu(player);
        }, () -> { bridge.cancel(player.getUniqueId()); cancelRequest(player.getUniqueId()); });
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
        cancelRequest(event.getPlayer().getUniqueId());
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
