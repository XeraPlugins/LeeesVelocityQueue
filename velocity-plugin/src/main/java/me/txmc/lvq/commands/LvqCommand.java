package me.txmc.lvq.commands;


import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.Plugin;
import lombok.RequiredArgsConstructor;
import me.txmc.lvq.Main;
import me.txmc.lvq.Reloadable;

import java.util.List;

import static me.txmc.lvq.util.MessageUtil.sendMessage;

@RequiredArgsConstructor
public class LvqCommand implements SimpleCommand {
    private final Main plugin;
    @Override
    public void execute(Invocation invocation) {
        if (!hasPermission(invocation)) {
            sendMessage(invocation.source(), "&cNo Permission");
            sendMessage(invocation.source(), versionString());
            return;
        }
        if (invocation.arguments().length == 0) {
            sendMessage(invocation.source(), versionString());
            return;
        }
        switch (invocation.arguments()[0]) {
            case "reload" -> {
                try {
                    plugin.loadConfig();
                    plugin.getReloadables().forEach(Reloadable::reloadConfig);
                    sendMessage(invocation.source(), "&b[&r&aLVQ&b]&r&a Reloaded successfully!");
                } catch (Throwable t) {
                    sendMessage(invocation.source(), "&b[&r&aLVQ&b]&r&c Failed to reload config, Please check the console for more details");
                    plugin.getLogger().atError().setCause(t).log("Failed to load config");
                }
            }
            case "version" -> sendMessage(invocation.source(), versionString());
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("lvq.admin");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!hasPermission(invocation)) return List.of();
        return List.of("version", "reload");
    }
    private String versionString() {
        return String.format("&b[&r&aLVQ&b]&r&6 Version&r&a %s&r&6 by&r&a 254n_m", plugin.getClass().getAnnotation(Plugin.class).version());
    }
}
