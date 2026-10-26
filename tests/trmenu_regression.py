"""Source regression and real backend ABI checks; does not start a server."""
import pathlib
import subprocess
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR = pathlib.Path(sys.argv.pop(1))
SOURCE = (ROOT / 'src/main/java/org/nomiteam/quests/NomiQuestExtras.java').read_text()


class TrMenuRegression(unittest.TestCase):
    def test_entity_entry_uses_api_without_console_or_global_dispatch(self):
        opening = SOURCE.split('private void open(Player player)', 1)[1].split('public boolean onCommand', 1)[0]
        self.assertIn('player.getScheduler().run(this,', opening)
        self.assertIn('openMenu(player);', opening)
        self.assertNotIn('dispatchCommand', SOURCE)
        self.assertNotIn('getConsoleSender', SOURCE)
        self.assertNotIn('getGlobalRegionScheduler', SOURCE)
        self.assertLess(opening.index('bridge.cancel'), opening.index('openMenu(player);'))
        self.assertLess(opening.index('hasPermission'), opening.index('openMenu(player);'))

    def test_exact_adapter_contract(self):
        for fragment in [
            '"me.arasple.mc.trmenu"', '"trplugins.menu"',
            'getField("INSTANCE")', 'getMethod("getMenuById", String.class)',
            '"DailyQuestReset"', 'Player.class, Integer.class, reason, Consumer.class',
            '"BINDING_COMMANDS"', 'menu, player, null,',
            'if (menu == null)', 'tr == null || !tr.isEnabled()',
            'ReflectiveOperationException | LinkageError | RuntimeException',
        ]:
            self.assertIn(fragment, SOURCE)

    def test_quota_callback_abi(self):
        prefix = 'me.arasple.mc.trmenu'
        result = subprocess.run(['javap', '-classpath', str(JAR),
            prefix + '.module.display.MenuSession',
            prefix + '.module.display.Menu',
            prefix + '.module.display.icon.Icon',
            prefix + '.module.display.icon.IconProperty',
            prefix + '.api.receptacle.ReceptacleClickType'],
            text=True, capture_output=True, check=True)
        for signature in [' SESSIONS;', 'getViewer();', 'getAgent();', 'isViewing();',
                          'getIcon(java.lang.String);',
                          'getProperty(' + prefix + '.module.display.MenuSession);',
                          'handleClick(' + prefix + '.api.receptacle.ReceptacleClickType, ' + prefix + '.module.display.MenuSession);',
                          ' LEFT;']:
            self.assertIn(signature, result.stdout)
        self.assertIn('quota.acquire(request.player, request.day, request.token)', SOURCE)
        self.assertIn('request.arm(System.currentTimeMillis())', SOURCE)
        self.assertIn('quotaRequests.remove(request.player, request)', SOURCE)

    def test_real_3125_backend_signatures(self):
        prefix = 'me.arasple.mc.trmenu'
        result = subprocess.run([
            'javap', '-classpath', str(JAR), prefix + '.api.TrMenuAPI',
            prefix + '.module.display.Menu', prefix + '.api.event.MenuOpenEvent$Reason',
        ], text=True, capture_output=True, check=True)
        print(result.stdout)
        for signature in [
            f'public static final {prefix}.api.TrMenuAPI INSTANCE;',
            f'public final {prefix}.module.display.Menu getMenuById(java.lang.String);',
            f'public final void open(org.bukkit.entity.Player, java.lang.Integer, {prefix}.api.event.MenuOpenEvent$Reason, java.util.function.Consumer<{prefix}.module.display.MenuSession>);',
            f'public static final {prefix}.api.event.MenuOpenEvent$Reason BINDING_COMMANDS;',
        ]:
            self.assertIn(signature, result.stdout)


if __name__ == '__main__':
    unittest.main()
