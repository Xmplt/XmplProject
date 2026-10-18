package me.xmpl.lib.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Serializer;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.xmpl.lib.XmplLib;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class PacketTeamGlowManager {

    private static volatile BiFunction<Player, Player, ChatColor> relationColorResolver;
    private static boolean listenerRegistered = false;

    // Отслеживаем созданные виртуальные команды: viewerUUID -> (targetNameLower -> teamName)
    private static final Map<UUID, Map<String, String>> activeVirtualTeams = new ConcurrentHashMap<>();

    private PacketTeamGlowManager() {}

    public static void registerRelationResolver(BiFunction<Player, Player, ChatColor> resolver) {
        relationColorResolver = resolver;
        ensurePacketListenersRegistered();
    }

    public static void unregisterRelationResolver() {
        relationColorResolver = null;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            clearVirtualTeamsForViewer(viewer);
        }
        activeVirtualTeams.clear();
    }

    private static synchronized void ensurePacketListenersRegistered() {
        if (listenerRegistered) return;
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return;

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // 1. Удерживаем флаг свечения (0x40) в пакетах ENTITY_METADATA (не слетает при нажатии Shift)
        pm.addPacketListener(new PacketAdapter(
                XmplLib.getInstance(),
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.ENTITY_METADATA
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                BiFunction<Player, Player, ChatColor> resolver = relationColorResolver;
                if (resolver == null) return;

                Player viewer = event.getPlayer();
                if (viewer == null) return;

                PacketContainer packet = event.getPacket();
                int entityId = packet.getIntegers().read(0);
                if (entityId == viewer.getEntityId()) return;

                Player target = findPlayerByEntityId(viewer.getWorld().getPlayers(), entityId);
                if (target == null) return;

                ChatColor glowColor = resolver.apply(viewer, target);
                if (glowColor == null) return;

                List<WrappedWatchableObject> watchables = packet.getWatchableCollectionModifier().read(0);
                if (watchables == null) return;

                boolean foundIndexZero = false;
                for (WrappedWatchableObject obj : watchables) {
                    if (obj.getIndex() == 0 && obj.getValue() instanceof Byte) {
                        byte flags = (Byte) obj.getValue();
                        obj.setValue((byte) (flags | 0x40));
                        foundIndexZero = true;
                        break;
                    }
                }

                if (!foundIndexZero) {
                    try {
                        byte currentFlags = WrappedDataWatcher.getEntityWatcher(target).getByte(0);
                        WrappedWatchableObject added = new WrappedWatchableObject(
                                new WrappedDataWatcher.WrappedDataWatcherObject(0, Registry.get(Byte.class)),
                                (byte) (currentFlags | 0x40)
                        );
                        watchables.add(added);
                    } catch (Exception ignored) {}
                }
                packet.getWatchableCollectionModifier().write(0, watchables);
            }
        });

        // 2. Если сторонний плагин отправляет SCOREBOARD_TEAM (mode 0 или 3) и перекидывает target в свою тиму,
        // на следующий тик возвращаем target в нашу виртуальную тиму для viewer
        pm.addPacketListener(new PacketAdapter(
                XmplLib.getInstance(),
                ListenerPriority.MONITOR,
                PacketType.Play.Server.SCOREBOARD_TEAM
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                BiFunction<Player, Player, ChatColor> resolver = relationColorResolver;
                if (resolver == null) return;

                Player viewer = event.getPlayer();
                if (viewer == null) return;

                PacketContainer packet = event.getPacket();
                String teamName = packet.getStrings().read(0);
                if (teamName == null || teamName.startsWith("xcl_")) {
                    return;
                }

                int mode = packet.getIntegers().read(1);
                if (mode != 0 && mode != 3) return;

                @SuppressWarnings("unchecked")
                Collection<String> entries = (Collection<String>) packet.getSpecificModifier(Collection.class).read(0);
                if (entries == null || entries.isEmpty()) return;

                for (String entryName : entries) {
                    Player target = Bukkit.getPlayerExact(entryName);
                    if (target != null && !target.equals(viewer)) {
                        ChatColor glowColor = resolver.apply(viewer, target);
                        if (glowColor != null) {
                            Bukkit.getScheduler().runTask(XmplLib.getInstance(), () -> {
                                if (viewer.isOnline() && target.isOnline()) {
                                    applyGlowStateForPair(viewer, target, glowColor);
                                }
                            });
                        }
                    }
                }
            }
        });

        listenerRegistered = true;
    }

    public static void refreshViewerGlows(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        BiFunction<Player, Player, ChatColor> resolver = relationColorResolver;

        for (Player target : viewer.getWorld().getPlayers()) {
            if (target.equals(viewer)) continue;
            ChatColor glowColor = (resolver != null) ? resolver.apply(viewer, target) : null;
            applyGlowStateForPair(viewer, target, glowColor);
        }
    }

    public static void refreshAllForPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        refreshViewerGlows(player);
        BiFunction<Player, Player, ChatColor> resolver = relationColorResolver;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player)) continue;
            ChatColor glowColor = (resolver != null) ? resolver.apply(other, player) : null;
            applyGlowStateForPair(other, player, glowColor);
        }
    }

    public static void applyGlowStateForPair(Player viewer, Player target, ChatColor glowColor) {
        if (viewer == null || target == null || !viewer.isOnline() || !target.isOnline()) return;
        if (viewer.equals(target)) return;

        if (glowColor != null) {
            sendVirtualTeamColorPacket(viewer, target, glowColor);
            sendMetadataGlowFlag(viewer, target, true);
        } else {
            removeVirtualTeamForTarget(viewer, target.getName());
            if (!target.isGlowing()) {
                sendMetadataGlowFlag(viewer, target, false);
            }
        }
    }

    private static void sendMetadataGlowFlag(Player viewer, Player target, boolean glowing) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return;
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, target.getEntityId());

            WrappedDataWatcher watcher = new WrappedDataWatcher();
            Serializer serializer = Registry.get(Byte.class);

            byte currentMask = WrappedDataWatcher.getEntityWatcher(target).getByte(0);
            if (glowing) {
                currentMask |= 0x40;
            } else {
                currentMask &= ~0x40;
            }

            watcher.setEntity(target);
            watcher.setObject(0, serializer, currentMask);
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
            pm.sendServerPacket(viewer, packet);
        } catch (Exception ignored) {}
    }

    /**
     * Создает или обновляет виртуальную команду для пары (viewer, target).
     * Секрет 1.12.2: префикс "§a§r" (для зеленого) или "§e§r" (для желтого) заставляет
     * RenderLivingBase#getTeamColor окрасить контур свечения в зеленый/желтый,
     * а символ §r (RESET) сбрасывает цвет перед рендером букв ника над головой, поэтому ник остается белым!
     */
    private static void sendVirtualTeamColorPacket(Player viewer, Player target, ChatColor color) {
        try {
            Map<String, String> viewerMap = activeVirtualTeams.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
            String targetKey = target.getName().toLowerCase();
            String teamName = "xcl_" + target.getEntityId();
            if (teamName.length() > 16) {
                teamName = teamName.substring(0, 16);
            }

            // §<цвет> задает цвет контура свечения, а §r сбрасывает цвет текста ника над головой в стандартный белый!
            String glowPrefix = ChatColor.COLOR_CHAR + "" + color.getChar() + ChatColor.RESET;
            int nmsColorId = chatColorToNmsId(color);

            String existingTeam = viewerMap.get(targetKey);
            if (existingTeam != null && !existingTeam.equals(teamName)) {
                sendRawTeamPacket(viewer, existingTeam, 1, "", "", 15, Collections.emptyList());
                existingTeam = null;
            }

            if (existingTeam == null) {
                // Создаем виртуальную команду и добавляем игрока (mode = 0)
                sendRawTeamPacket(viewer, teamName, 0, glowPrefix, "", nmsColorId, Collections.singletonList(target.getName()));
                viewerMap.put(targetKey, teamName);
            } else {
                // Обновляем цвет и префикс существующей команды (mode = 2) и перепривязываем игрока (mode = 3)
                sendRawTeamPacket(viewer, teamName, 2, glowPrefix, "", nmsColorId, Collections.emptyList());
                sendRawTeamPacket(viewer, teamName, 3, "", "", nmsColorId, Collections.singletonList(target.getName()));
            }
        } catch (Exception ignored) {}
    }

    private static void removeVirtualTeamForTarget(Player viewer, String targetName) {
        Map<String, String> viewerMap = activeVirtualTeams.get(viewer.getUniqueId());
        if (viewerMap == null) return;
        String teamName = viewerMap.remove(targetName.toLowerCase());
        if (teamName != null) {
            sendRawTeamPacket(viewer, teamName, 1, "", "", 15, Collections.emptyList());
        }
    }

    public static void clearVirtualTeamsForViewer(Player viewer) {
        if (viewer == null) return;
        Map<String, String> viewerMap = activeVirtualTeams.remove(viewer.getUniqueId());
        if (viewerMap == null) return;
        for (String teamName : viewerMap.values()) {
            sendRawTeamPacket(viewer, teamName, 1, "", "", 15, Collections.emptyList());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void sendRawTeamPacket(Player viewer, String teamName, int mode, String prefix, String suffix,
                                          int colorId, Collection<String> players) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutScoreboardTeam");
            Class<?> basePacketClass = Class.forName("net.minecraft.server." + version + ".Packet");
            Object packet = packetClass.getConstructor().newInstance();

            setField(packetClass, packet, "a", teamName);
            setField(packetClass, packet, "i", mode);

            if (mode == 0 || mode == 2) {
                setField(packetClass, packet, "b", teamName);
                setField(packetClass, packet, "c", prefix);
                setField(packetClass, packet, "d", suffix);
                setField(packetClass, packet, "e", "always");
                setField(packetClass, packet, "f", "always");
                setField(packetClass, packet, "g", colorId);
                setField(packetClass, packet, "j", 3);
            }
            if (mode == 0 || mode == 3 || mode == 4) {
                Field hField = packetClass.getDeclaredField("h");
                hField.setAccessible(true);
                Collection col = (Collection) hField.get(packet);
                col.addAll(players);
            }

            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object conn = handle.getClass().getField("playerConnection").get(handle);
            Method sendPacket = conn.getClass().getMethod("sendPacket", basePacketClass);
            sendPacket.invoke(conn, packet);
        } catch (Exception ignored) {}
    }

    private static void setField(Class<?> clazz, Object instance, String fieldName, Object value) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(instance, value);
    }

    private static int chatColorToNmsId(ChatColor color) {
        if (color == null) return 15;
        switch (color) {
            case BLACK: return 0;
            case DARK_BLUE: return 1;
            case DARK_GREEN: return 2;
            case DARK_AQUA: return 3;
            case DARK_RED: return 4;
            case DARK_PURPLE: return 5;
            case GOLD: return 6;
            case GRAY: return 7;
            case DARK_GRAY: return 8;
            case BLUE: return 9;
            case GREEN: return 10;
            case AQUA: return 11;
            case RED: return 12;
            case LIGHT_PURPLE: return 13;
            case YELLOW: return 14;
            case WHITE:
            default: return 15;
        }
    }

    private static Player findPlayerByEntityId(List<Player> players, int entityId) {
        for (Player p : players) {
            if (p.getEntityId() == entityId) return p;
        }
        return null;
    }
}