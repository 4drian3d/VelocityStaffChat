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

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class VelocityStaffChat implements SimpleCommand {

    @Inject
    @Getter
    private ProxyServer proxy;
    @Inject
    @Getter
    private Logger logger;
    @Inject
    @Getter
    @DataDirectory
    private Path configPath;

    private Toml toml;
    private String messageFormat;
    private String toggleFormat;
    private Set<UUID> toggledPlayers;

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.toml = loadConfig(configPath);
        if (toml == null) {
            logger.warn("Failed to load config.toml. Shutting down.");
            return;
        }

        this.messageFormat = toml.getString("message-format");
        this.toggleFormat = toml.getString("toggle-format");
        this.toggledPlayers = new HashSet<UUID>();

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
        player.sendMessage(color(toggleFormat.replace("{state}", state ? "enabled" : "disabled")));
    }

    private void sendStaffMessage(Player player, ServerConnection server, String message) {
        proxy.getAllPlayers().stream().filter(target -> target.hasPermission("staffchat")).forEach(target -> {
            target.sendMessage(color(messageFormat.replace("{player}", player.getUsername())
                    .replace("{server}", server != null ? server.getServerInfo().getName() : "N/A").replace("{message}", message)));
        });
    }

    private TextComponent color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
