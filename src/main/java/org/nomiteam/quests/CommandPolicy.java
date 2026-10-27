package org.nomiteam.quests;

import java.util.Locale;
import java.util.Set;

public final class CommandPolicy {
    private CommandPolicy() {}

    public static String base(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.substring(normalized.lastIndexOf(':') + 1);
    }

    public static boolean action(String[] args, Set<String> actions) {
        return args.length > 0 && actions.contains(args[0].toLowerCase(Locale.ROOT));
    }

    public static boolean block(String message, Set<String> commands, Set<String> actions) {
        String[] parts = message.replaceFirst("^/", "").strip().split("\\s+");
        return parts.length >= 2 && commands.contains(base(parts[0]))
                && actions.contains(parts[1].toLowerCase(Locale.ROOT));
    }
}
