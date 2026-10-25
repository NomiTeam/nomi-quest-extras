#!/usr/bin/env python3
"""Real JAR bytecode checks. These are not server, payment or engine execution tests."""
from pathlib import Path
import subprocess
import sys
import unittest

ODQ = Path(sys.argv.pop(1))
TRMENU = Path(sys.argv.pop(1))


def bytecode(jar, name):
    return subprocess.check_output(["javap", "-p", "-c", "-classpath", str(jar), name], text=True)


class NativeCapabilities(unittest.TestCase):
    def test_odq_reroll_is_single_and_admin_skips_quota_increment(self):
        code = bytecode(ODQ, "com.ordwen.odailyquests.quests.player.PlayerQuests")
        reroll = code.split("public boolean rerollQuest(", 1)[1].split(
            "private boolean isRerollAllowedProgression", 1)[0]
        self.assertIn("create", code.lower())
        self.assertIn("getRandomQuestForPlayer", reroll)
        self.assertIn("addRecentReroll", reroll)
        self.assertIn("iload_3", reroll)
        print("ODQ: native indexed reroll and admin quota bypass confirmed")

    def test_admin_reset_rebuilds_period_and_clears_counter(self):
        reset = bytecode(ODQ, "com.ordwen.odailyquests.commands.admin.handlers.ResetCommand")
        loader = bytecode(ODQ, "com.ordwen.odailyquests.quests.player.progression.QuestLoaderUtils")
        method = reset.split("public void quests(", 1)[1].split("private void resetTotal", 1)[0]
        self.assertIn("loadNewPlayerQuests", method)
        self.assertNotIn("getRecentlyRolled", method)
        self.assertNotIn("getScheduler", method)
        self.assertIn("Calendar.getTimeInMillis", loader)
        self.assertIn("PlayerQuests.setRecentRerolls", loader)
        print("ODQ: native reset is a full draw, new period, zero rerolls, no entity scheduling")

    def test_jexl_copies_context_before_setting_viewer(self):
        code = bytecode(TRMENU, "me.arasple.mc.trmenu.module.internal.script.jexl.JexlAgent")
        method = code.split("public final java.lang.Object eval-", 1)[1].split(
            "public static java.lang.Object", 1)[0]
        self.assertLess(method.index("Maps.newHashMap"), method.index("String player"))
        self.assertIn("MenuSession.getViewer", method)
        self.assertIn("JexlCompiledScript.eval", method)
        print("TrMenu: JEXL context copy precedes per-call viewer binding (bytecode only)")

    def test_native_money_drops_economy_response(self):
        hook = bytecode(TRMENU, "me.arasple.mc.trmenu.module.internal.hook.impl.HookVault")
        take = hook.split("public final void takeMoney(", 1)[1].split(
            "public final void addMoney", 1)[0]
        self.assertIn("Economy.withdrawPlayer", take)
        self.assertIn("pop", take)
        self.assertNotIn("transactionSuccess", take)
        print("TrMenu: native take-money does not expose a debit success receipt")


if __name__ == "__main__":
    unittest.main()
