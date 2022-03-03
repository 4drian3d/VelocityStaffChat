package me.crypnotic.velocitystaffchat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent.ChatResult;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.command.SimpleCommand;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class VelocityStaffChat implements SimpleCommand {

    @Inject
    private ProxyServer proxy;
    @Inject
    private Logger logger;
    @Inject
    @DataDirectory
    private Path configPath;

    private String messageFormat;
    private String toggleFormat;
    private Set<UUID> toggledPlayers;

    private static final MiniMessage MINIMESSAGE = MiniMessage.miniMessage();

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Toml toml = loadConfig(configPath);
        if (toml == null) {
            logger.warn("Failed to load config.toml. Shutting down.");
            return;
        }

        this.messageFormat = toml.getString("message-format");
        this.toggleFormat = toml.getString("toggle-format");
        this.toggledPlayers = new HashSet<>();

        CommandMeta meta = proxy.getCommandManager().metaBuilder("staffchat")
            .aliases("sc")
            .build();

        proxy.getCommandManager().register(meta, this);
    }

    private Toml loadConfig(Path path) {
        File folder = path.toFile();
        File file = new File(folder, "config.toml");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            try (InputStream input = getClass().getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }
        }

        return new Toml().read(file);
    }

    @Override
    public void execute(final Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (source instanceof Player) {
            Player player = (Player) source;
            if (player.hasPermission("staffchat")) {
                if (args.length == 0) {
                    if (toggledPlayers.contains(player.getUniqueId())) {
                        toggledPlayers.remove(player.getUniqueId());
                        sendToggleMessage(player, false);
                    } else {
                        toggledPlayers.add(player.getUniqueId());
                        sendToggleMessage(player, true);
                    }
                } else {
                    sendStaffMessage(player, player.getCurrentServer().get(), String.join(" ", args));
                }
            } else {
                player.sendMessage(Component.text("Permission denied.").color(NamedTextColor.RED));
            }
        } else {
            source.sendMessage(Component.text("Only players can use this command."));
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!toggledPlayers.contains(player.getUniqueId())) {
            return;
        }

        event.setResult(ChatResult.denied());

        sendStaffMessage(player, player.getCurrentServer().get(), event.getMessage());
    }

    private void sendToggleMessage(Player player, boolean state) {
        player.sendMessage(MINIMESSAGE.deserialize(toggleFormat.replace("{state}", state ? "enabled" : "disabled")));
    }

    private void sendStaffMessage(Player player, ServerConnection server, String message) {
        proxy.getAllPlayers().stream().filter(target -> target.hasPermission("staffchat")).forEach(target -> {
            target.sendMessage(MINIMESSAGE.deserialize(messageFormat.replace("{player}", player.getUsername())
                    .replace("{server}", server != null ? server.getServerInfo().getName() : "N/A").replace("{message}", message)));
        });
    }
}
