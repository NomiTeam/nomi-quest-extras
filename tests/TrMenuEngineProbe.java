package org.nomiteam.quests;

/** 真实 JAR 的离线链接探针；缺少运行依赖时明确失败，不用替身解析器冒充。 */
public final class TrMenuEngineProbe {
    public static void main(String[] args) throws Exception {
        Class<?> helper = Class.forName("me.arasple.mc.trmenu.taboolib.expansion.JexlHelperKt");
        Object compiler = helper.getMethod("getDefaultJexlCompiler").invoke(null);
        Object script = compiler.getClass().getMethod("compileToScript", String.class)
                .invoke(compiler, "1 + 1");
        Object result = script.getClass().getMethod("eval", java.util.Map.class)
                .invoke(script, java.util.Map.of());
        if (!Integer.valueOf(2).equals(result)) {
            throw new AssertionError("Unexpected actual JEXL engine result: " + result);
        }
        System.out.println("PASS real JEXL constant expression; player/menu execution still untested");
    }
}
