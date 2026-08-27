// file: ZMRCommand.java
// description: Executor and tab completer for the /zmzonereset (alias /zmr) command. Handles all subcommands.

package dev.zm.zonereset.command;

import dev.zm.zonereset.api.ZoneManagerAPI;
import dev.zm.zonereset.api.zone.Zone;
import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.gui.menus.ZoneMainMenu;
import dev.zm.zonereset.gui.menus.ZonesListMenu;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.lang.TimeFormatter;
import dev.zm.zonereset.reset.ResetManagerImpl;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.selection.PlayerSelection;
import dev.zm.zonereset.selection.PlayerSelectionStore;
import dev.zm.zonereset.selection.WandManager;
import dev.zm.zonereset.snapshot.SnapshotCapture;
import dev.zm.zonereset.zone.ZoneBounds;
import dev.zm.zonereset.zone.ZoneImpl;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import dev.zm.zonereset.zone.ZoneRepository;
import dev.zm.zonereset.zone.ZoneStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class ZMRCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "zmzonereset.admin";
    private static final String PERM_USE = "zmzonereset.use";

    private final ZoneManagerImpl zoneManager;
    private final ZoneRepository zoneRepository;
    private final ResetManagerImpl resetManager;
    private final SnapshotCapture snapshotCapture;
    private final LanguageManager langManager;
    private final WandManager wandManager;
    private final PlayerSelectionStore selectionStore;
    private final ZoneLogger logger;
    private final JavaPlugin plugin;
    private final ZoneScheduler scheduler;

    private final File globalSpawnFile;
    private Location globalSpawn;

    public ZMRCommand(ZoneManagerAPI zoneManager,
            ZoneRepository zoneRepository,
            ResetManagerImpl resetManager,
            SnapshotCapture snapshotCapture,
            LanguageManager langManager,
            WandManager wandManager,
            PlayerSelectionStore selectionStore,
            ZoneLogger logger,
            JavaPlugin plugin,
            ZoneScheduler scheduler) {
        this.zoneManager = (ZoneManagerImpl) zoneManager;
        this.zoneRepository = zoneRepository;
        this.resetManager = resetManager;
        this.snapshotCapture = snapshotCapture;
        this.langManager = langManager;
        this.wandManager = wandManager;
        this.selectionStore = selectionStore;
        this.logger = logger;
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.globalSpawnFile = new File(plugin.getDataFolder(), "global-spawn.yml");
        loadGlobalSpawn();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "wand" -> cmdWand(sender);
            case "create" -> cmdCreate(sender, args);
            case "remove" -> cmdRemove(sender, args);
            case "edit" -> cmdEdit(sender, args);
            case "info" -> cmdInfo(sender, args);
            case "list" -> cmdList(sender);
            case "enable" -> cmdEnable(sender, args);
            case "disable" -> cmdDisable(sender, args);
            case "reset" -> cmdReset(sender, args);
            case "capture" -> cmdCapture(sender, args);
            case "setspawn" -> cmdSetSpawn(sender, args);
            case "clearspawn" -> cmdClearSpawn(sender, args);
            case "spawn" -> cmdSpawn(sender, args);
            case "reload" -> cmdReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void cmdWand(CommandSender sender) {
        if (!checkPlayer(sender))
            return;
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        Player player = (Player) sender;
        wandManager.giveWand(player);
        player.sendMessage(langManager.getMessage("wand.given"));
    }

    private void cmdCreate(CommandSender sender, String[] args) {
        if (!checkPlayer(sender))
            return;
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sender.sendMessage(langManager.getMessage("general.unknown-command"));
            return;
        }
        Player player = (Player) sender;
        String id = args[1];

        if (zoneManager.hasZone(id)) {
            player.sendMessage(langManager.getMessage("general.zone-already-exists", "zone", id));
            return;
        }

        PlayerSelection sel = selectionStore.getSelection(player.getUniqueId());
        if (!sel.isComplete()) {
            player.sendMessage(langManager.getMessage("wand.incomplete"));
            return;
        }

        Location pos1 = sel.getPos1();
        Location pos2 = sel.getPos2();

        if (!pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage(langManager.getMessage("wand.different-worlds"));
            return;
        }

        World world = pos1.getWorld();
        ZoneBounds bounds = new ZoneBounds(
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());

        ZoneImpl zone = ZoneImpl.builder(id, world.getUID(), bounds).build();
        try {
            zoneManager.registerZone(zone);
        } catch (IllegalArgumentException e) {
            player.sendMessage(langManager.getMessage("general.zone-already-exists", "zone", id));
            return;
        }

        saveZoneAsync(zone);

        selectionStore.clearSelection(player.getUniqueId());
        player.sendMessage(langManager.getMessage("zones.created", "zone", id));
    }

    private void cmdRemove(CommandSender sender, String[] args) {
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String id = args[1];
        if (!ensureZoneExists(sender, id))
            return;

        zoneManager.unregisterZone(id);

        scheduler.runAsync(() -> {
            try {
                zoneRepository.deleteZone(id);
            } catch (Exception e) {
                logger.error("Error borrando zona '{}' del disco: {}", id, e.getMessage());
            }
        });

        sender.sendMessage(langManager.getMessage("zones.removed", "zone", id));
    }

    private void cmdEdit(CommandSender sender, String[] args) {
        if (!checkPlayer(sender))
            return;
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        Player player = (Player) sender;

        if (args.length >= 2) {
            String id = args[1];
            if (!ensureZoneExists(sender, id))
                return;
            Zone zone = zoneManager.getZone(id).get();
            new ZoneMainMenu(player, zone, zoneManager, zoneRepository, langManager, scheduler).open();
        } else {
            new ZonesListMenu(player, zoneManager, zoneRepository, langManager, scheduler).open();
        }
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (!checkPerm(sender, PERM_USE))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String id = args[1];
        if (!ensureZoneExists(sender, id))
            return;

        Zone zone = zoneManager.getZone(id).get();
        ZoneBounds bounds = zone.getBounds();

        long interval = zone.getResetIntervalTicks();
        String timeStr = interval <= 0
                ? "N/A"
                : TimeFormatter.format(interval * 50L, 3);

        World world = Bukkit.getWorld(zone.getWorldUID());
        String worldName = world != null ? world.getName() : zone.getWorldUID().toString();

        sender.sendMessage("§6§l» §eZona: §f" + zone.getId());
        sender.sendMessage("§7Mundo: §f" + worldName);
        sender.sendMessage("§7Estado: §f" + zone.getStatus().name());
        sender.sendMessage("§7Estrategia: §f" + zone.getResetStrategy().name());
        sender.sendMessage("§7Habilitada: §f" + zone.isEnabled());
        sender.sendMessage("§7Intervalo: §f" + timeStr);
        sender.sendMessage("§7Min: §f" + bounds.getMinX() + ", " + bounds.getMinY() + ", " + bounds.getMinZ());
        sender.sendMessage("§7Max: §f" + bounds.getMaxX() + ", " + bounds.getMaxY() + ", " + bounds.getMaxZ());
        sender.sendMessage("§7Snapshot válido: §f" + zone.hasValidSnapshot());
    }

    private void cmdList(CommandSender sender) {
        if (!checkPerm(sender, PERM_USE))
            return;
        Collection<Zone> zones = zoneManager.getAllZones();
        if (zones.isEmpty()) {
            sender.sendMessage("§7No hay zonas registradas.");
            return;
        }
        sender.sendMessage("§6§l» §eZonas (§f" + zones.size() + "§e):");
        for (Zone z : zones) {
            String status = z.isEnabled() ? "§a" : "§c";
            sender.sendMessage("  §7- " + status + z.getId() + " §8[" + z.getResetStrategy() + "] §7Snap:"
                    + (z.hasValidSnapshot() ? "§a✓" : "§c✗"));
        }
    }

    private void cmdEnable(CommandSender sender, String[] args) {
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String id = args[1];
        if (!ensureZoneExists(sender, id))
            return;
        try {
            zoneManager.enableZone(id);
            sender.sendMessage(langManager.getMessage("zones.enabled", "zone", id));
            zoneManager.getZoneImpl(id).ifPresent(this::saveZoneAsync);
        } catch (IllegalStateException e) {
            sender.sendMessage("§c" + e.getMessage());
        }
    }

    private void cmdDisable(CommandSender sender, String[] args) {
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String id = args[1];
        if (!ensureZoneExists(sender, id))
            return;
        zoneManager.disableZone(id);
        sender.sendMessage(langManager.getMessage("zones.disabled", "zone", id));
        zoneManager.getZoneImpl(id).ifPresent(this::saveZoneAsync);
    }

    private void cmdReset(CommandSender sender, String[] args) {
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String id = args[1];
        if (!ensureZoneExists(sender, id))
            return;

        Zone zone = zoneManager.getZone(id).get();
        try {
            resetManager.requestReset(zone);
            sender.sendMessage(langManager.getMessage("zones.reset-started", "zone", id));
        } catch (Exception e) {
            sender.sendMessage("§cError iniciando reset: " + e.getMessage());
        }
    }

    private void cmdCapture(CommandSender sender, String[] args) {
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        String id = args[1];
        if (!ensureZoneExists(sender, id))
            return;

        zoneManager.getZoneImpl(id).ifPresent(zone -> {
            World world = Bukkit.getWorld(zone.getWorldUID());
            if (world == null) {
                sender.sendMessage(langManager.getMessage("general.zone-not-found", "zone", id));
                return;
            }

            sender.sendMessage(langManager.getMessage("zones.capture-started", "zone", id));

            snapshotCapture.beginCapture(zone, world, progress -> {
                // progress ignored here
            }).thenRun(() -> {
                String blocks = String.valueOf(zone.getBounds().getVolume());
                String msg = langManager.getMessage("zones.capture-done", "zone", id, "blocks", blocks);

                if (sender instanceof Player player && player.isOnline()) {
                    scheduler.runSync(() -> player.sendMessage(msg));
                } else {
                    logger.info("Snapshot capturado para zona '{}'.", id);
                }
            }).exceptionally(ex -> {
                String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                String msg = langManager.getMessage("zones.capture-failed", "zone", id, "error", cause);
                logger.error("Error capturando snapshot de '{}': {}", ex, id, cause);

                if (sender instanceof Player player && player.isOnline()) {
                    scheduler.runSync(() -> player.sendMessage(msg));
                }
                return null;
            });
        });
    }

    private void cmdSetSpawn(CommandSender sender, String[] args) {
        if (!checkPlayer(sender))
            return;
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        Player player = (Player) sender;
        String target = args[1].toLowerCase(Locale.ROOT);
        Location loc = player.getLocation();

        if (target.equals("global")) {
            globalSpawn = loc;
            saveGlobalSpawn();
            player.sendMessage(langManager.getMessage("general.global-spawn-set"));
        } else {
            if (!ensureZoneExists(sender, target))
                return;
            zoneManager.getZoneImpl(target).ifPresent(zone -> {
                zone.setSpawn(loc);
                saveZoneAsync(zone);
            });
            player.sendMessage(langManager.getMessage("zones.spawn-set", "zone", target));
        }
    }

    private void cmdClearSpawn(CommandSender sender, String[] args) {
        if (!checkPerm(sender, PERM_ADMIN))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String target = args[1].toLowerCase(Locale.ROOT);
        if (target.equals("global")) {
            globalSpawn = null;
            saveGlobalSpawn();
            sender.sendMessage(langManager.getMessage("general.global-spawn-clear"));
        } else {
            if (!ensureZoneExists(sender, target))
                return;
            zoneManager.getZoneImpl(target).ifPresent(zone -> {
                zone.setSpawn(null);
                saveZoneAsync(zone);
            });
            sender.sendMessage(langManager.getMessage("zones.spawn-clear", "zone", target));
        }
    }

    private void cmdSpawn(CommandSender sender, String[] args) {
        if (!checkPlayer(sender))
            return;
        if (!checkPerm(sender, PERM_USE))
            return;
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        Player player = (Player) sender;
        String target = args[1].toLowerCase(Locale.ROOT);

        Location dest = null;
        if (target.equals("global")) {
            dest = globalSpawn;
        } else {
            if (!ensureZoneExists(sender, target))
                return;
            Zone zone = zoneManager.getZone(target).get();
            dest = zone.getSpawn();
            if (dest == null)
                dest = globalSpawn;
            if (dest == null)
                dest = safeCenter(zone);
        }

        if (dest == null) {
            player.sendMessage("§cNo hay spawn configurado.");
            return;
        }

        player.teleport(dest);
        player.sendMessage(langManager.getMessage("zones.teleported", "zone", target));
    }

    private void cmdReload(CommandSender sender) {
        if (!checkPerm(sender, PERM_ADMIN))
            return;

        dev.zm.zonereset.zMZoneReset mainPlugin = (dev.zm.zonereset.zMZoneReset) plugin;
        mainPlugin.reloadPlugin(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("wand", "create", "remove", "edit", "info",
                    "list", "enable", "disable", "reset", "capture",
                    "setspawn", "clearspawn", "spawn", "reload");
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (Set.of("remove", "edit", "info", "enable", "disable", "reset", "capture").contains(sub)) {
                List<String> ids = zoneManager.getAllZones().stream()
                        .map(Zone::getId).collect(Collectors.toList());
                return filter(ids, args[1]);
            }
            if (Set.of("setspawn", "clearspawn", "spawn").contains(sub)) {
                List<String> targets = new ArrayList<>();
                targets.add("global");
                zoneManager.getAllZones().stream().map(Zone::getId).forEach(targets::add);
                return filter(targets, args[1]);
            }
        }

        return List.of();
    }

    public void saveZoneAsync(ZoneImpl zone) {
        scheduler.runAsync(() -> {
            try {
                zoneRepository.saveZone(zone);
                logger.debug("Zona '{}' guardada en disco.", zone.getId());
            } catch (IOException e) {
                logger.error("Error guardando zona '{}': {}", e, zone.getId(), e.getMessage());
            }
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l» §ezMZoneReset — Comandos:");
        sender.sendMessage("§7/zmr wand §8— Obtener varita de selección");
        sender.sendMessage("§7/zmr create <id> §8— Crear zona con la selección actual");
        sender.sendMessage("§7/zmr remove <id> §8— Eliminar zona");
        sender.sendMessage("§7/zmr edit [id] §8— Abrir GUI de edición");
        sender.sendMessage("§7/zmr info <id> §8— Ver información de una zona");
        sender.sendMessage("§7/zmr list §8— Listar zonas");
        sender.sendMessage("§7/zmr enable/disable <id> §8— Habilitar/deshabilitar zona");
        sender.sendMessage("§7/zmr reset <id> §8— Forzar reset");
        sender.sendMessage("§7/zmr capture <id> §8— Capturar snapshot");
        sender.sendMessage("§7/zmr setspawn global|<zone> §8— Establecer spawn");
        sender.sendMessage("§7/zmr clearspawn global|<zone> §8— Limpiar spawn");
        sender.sendMessage("§7/zmr spawn global|<zone> §8— Teleportarse al spawn");
        sender.sendMessage("§7/zmr reload §8— Recargar configuración e idiomas");
    }

    private boolean checkPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(langManager.getMessage("general.player-only"));
            return false;
        }
        return true;
    }

    private boolean checkPerm(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(langManager.getMessage("general.no-permission"));
            return false;
        }
        return true;
    }

    private boolean ensureZoneExists(CommandSender sender, String id) {
        if (!zoneManager.hasZone(id)) {
            sender.sendMessage(langManager.getMessage("general.zone-not-found", "zone", id));
            return false;
        }
        return true;
    }

    private Location safeCenter(Zone zone) {
        World world = Bukkit.getWorld(zone.getWorldUID());
        if (world == null)
            return null;
        ZoneBounds b = zone.getBounds();
        int cx = (b.getMinX() + b.getMaxX()) / 2;
        int cz = (b.getMinZ() + b.getMaxZ()) / 2;
        int cy = world.getHighestBlockYAt(cx, cz) + 1;
        return new Location(world, cx + 0.5, cy, cz + 0.5);
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return list.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private void loadGlobalSpawn() {
        if (!globalSpawnFile.exists())
            return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(globalSpawnFile);
            String worldName = cfg.getString("world");
            if (worldName == null)
                return;
            World world = Bukkit.getWorld(worldName);
            if (world == null)
                return;
            globalSpawn = new Location(world,
                    cfg.getDouble("x"), cfg.getDouble("y"), cfg.getDouble("z"),
                    (float) cfg.getDouble("yaw"), (float) cfg.getDouble("pitch"));
        } catch (Exception e) {
            logger.warn("No se pudo cargar el spawn global: {}", e.getMessage());
        }
    }

    private void saveGlobalSpawn() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (globalSpawn != null && globalSpawn.getWorld() != null) {
            cfg.set("world", globalSpawn.getWorld().getName());
            cfg.set("x", globalSpawn.getX());
            cfg.set("y", globalSpawn.getY());
            cfg.set("z", globalSpawn.getZ());
            cfg.set("yaw", globalSpawn.getYaw());
            cfg.set("pitch", globalSpawn.getPitch());
        }
        try {
            cfg.save(globalSpawnFile);
        } catch (Exception e) {
            logger.error("Error guardando spawn global: {}", e, e.getMessage());
        }
    }
}