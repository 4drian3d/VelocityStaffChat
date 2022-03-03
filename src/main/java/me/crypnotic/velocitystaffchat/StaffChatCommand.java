package me.crypnotic.velocitystaffchat;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.Component;

public class StaffChatCommand implements SimpleCommand {
    private final VelocityStaffChat plugin;
    protected StaffChatCommand(VelocityStaffChat plugin){
        this.plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (source instanceof Player) {
            Player player = (Player) source;
            if (args.length == 0) {
                if (plugin.toggledPlayers.remove(player.getUniqueId())) {
                    plugin.sendToggleMessage(player, false);
                } else {
                    plugin.toggledPlayers.add(player.getUniqueId());
                    plugin.sendToggleMessage(player, true);
                }
            } else {
                plugin.sendStaffMessage(player, String.join(" ", args));
            }
        } else {
            source.sendMessage(Component.text("Only players can use this command."));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation){
        return invocation.source().hasPermission("staffchat");
    }
}
