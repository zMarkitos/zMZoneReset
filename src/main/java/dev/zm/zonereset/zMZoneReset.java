package dev.zm.zonereset;

import dev.zm.zonereset.command.ZMRCommand;
import dev.zm.zonereset.config.ConfigManager;
import dev.zm.zonereset.core.logging.ZoneLogger;
import dev.zm.zonereset.core.performance.PerformanceMonitor;
import dev.zm.zonereset.diff.DiffStore;
import dev.zm.zonereset.diff.DiffTracker;
import dev.zm.zonereset.gui.MenuListener;
import dev.zm.zonereset.integration.IntegrationManager;
import dev.zm.zonereset.lang.LanguageManager;
import dev.zm.zonereset.listener.BlockChangeListener;
import dev.zm.zonereset.listener.ExplosionListener;
import dev.zm.zonereset.listener.WorldListener;
import dev.zm.zonereset.selection.SelectionListener;
import dev.zm.zonereset.integration.AdminNotificationListener;
import dev.zm.zonereset.integration.VersionChecker;
import dev.zm.zonereset.reset.ResetEngine;
import dev.zm.zonereset.reset.ResetManagerImpl;
import dev.zm.zonereset.reset.ResetQueue;
import dev.zm.zonereset.reset.recovery.ResetStateStore;
import dev.zm.zonereset.reset.strategy.DiffStrategyHandler;
import dev.zm.zonereset.reset.strategy.EntityCleaner;
import dev.zm.zonereset.reset.strategy.SnapshotStrategyHandler;
import dev.zm.zonereset.scheduler.SchedulerFactory;
import dev.zm.zonereset.scheduler.ZoneScheduler;
import dev.zm.zonereset.selection.PlayerSelectionStore;
import dev.zm.zonereset.selection.WandManager;
import dev.zm.zonereset.snapshot.BlockEntitySerializer;
import dev.zm.zonereset.snapshot.SnapshotCapture;
import dev.zm.zonereset.storage.AtomicFileWriter;
import dev.zm.zonereset.storage.BinarySnapshotStorage;
import dev.zm.zonereset.storage.StorageLayout;
import dev.zm.zonereset.storage.ZstdCompressionCodec;
import dev.zm.zonereset.lang.ColorParser;
import dev.zm.zonereset.zone.ZoneManagerImpl;
import dev.zm.zonereset.zone.ZoneRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class zMZoneReset extends JavaPlugin {

        private static zMZoneReset instance;

        public static zMZoneReset getInstance() {
                if (instance == null) {
                        throw new IllegalStateException(
                                        "zMZoneReset is not enabled. Do not call getInstance() before onEnable().");
                }
                return instance;
        }

        private ZoneLogger zoneLogger;
        private ZoneScheduler scheduler;

        private ConfigManager configManager;
        private BinarySnapshotStorage snapshotStorage;
        private ZoneRepository zoneRepository;
        private ZoneManagerImpl zoneManager;
        private DiffStore diffStore;
        private DiffTracker diffTracker;
        private ResetEngine resetEngine;
        private ResetManagerImpl resetManager;
        private SnapshotCapture snapshotCapture;
        private IntegrationManager integrationManager;
        private ResetStateStore stateStore;
        private LanguageManager langManager;
        private WandManager wandManager;
        private PlayerSelectionStore selectionStore;
        private Instant startupInstant;

        @Override
        public void onEnable() {
                startupInstant = Instant.now();
                instance = this;

                zoneLogger = new ZoneLogger(this);

                printBanner();

                scheduler = SchedulerFactory.create(this);
                zoneLogger.info("Scheduler: {} mode active.",
                                SchedulerFactory.isFolia() ? "Folia" : "Paper");

                configManager = new ConfigManager(this);
                configManager.load();
                zoneLogger.setDebugEnabled(configManager.isDebugMode());
                zoneLogger.debug("Debug mode enabled.");

                StorageLayout layout = new StorageLayout(this);
                snapshotStorage = new BinarySnapshotStorage(
                                layout,
                                ZstdCompressionCodec.createOrFallback(zoneLogger),
                                new AtomicFileWriter(zoneLogger),
                                zoneLogger);

                dev.zm.zonereset.zone.ZoneValidator validator = new dev.zm.zonereset.zone.ZoneValidator(zoneLogger);
                zoneRepository = new ZoneRepository(this, zoneLogger, validator);
                zoneRepository.initialize();

                dev.zm.zonereset.zone.index.ChunkZoneIndex index = new dev.zm.zonereset.zone.index.ChunkZoneIndex();
                zoneManager = new ZoneManagerImpl(index, zoneLogger);

                zoneRepository.loadAllZones().forEach(zoneManager::registerZone);

                diffStore = new DiffStore();
                BlockEntitySerializer entitySerializer = new BlockEntitySerializer(zoneLogger);
                diffTracker = new DiffTracker(diffStore, entitySerializer, zoneLogger);

                snapshotCapture = new SnapshotCapture(
                                scheduler,
                                snapshotStorage,
                                entitySerializer,
                                zoneManager,
                                zoneLogger);

                PerformanceMonitor perfMonitor = new PerformanceMonitor();
                ResetQueue queue = new ResetQueue();
                stateStore = new ResetStateStore(getDataFolder(), zoneLogger);

                EntityCleaner entityCleaner = new EntityCleaner(scheduler);
                DiffStrategyHandler diffHandler = new DiffStrategyHandler(
                                diffStore,
                                scheduler,
                                entitySerializer,
                                entityCleaner,
                                zoneLogger);

                SnapshotStrategyHandler snapHandler = new SnapshotStrategyHandler(
                                snapshotStorage,
                                scheduler,
                                entitySerializer,
                                entityCleaner,
                                zoneLogger);

                resetEngine = new ResetEngine(
                                queue,
                                scheduler,
                                zoneLogger,
                                diffHandler,
                                snapHandler,
                                stateStore);

                dev.zm.zonereset.reset.strategy.AutoStrategySelector autoSelector = new dev.zm.zonereset.reset.strategy.AutoStrategySelector(
                                diffTracker);

                resetManager = new ResetManagerImpl(
                                queue,
                                resetEngine,
                                zoneLogger,
                                autoSelector);

                List<String> pendingResets = stateStore.getPendingResets();
                for (String zoneId : pendingResets) {
                        zoneManager.getZone(zoneId).ifPresent(zone -> {
                                zoneLogger.warn("Recovering pending reset for zone '{}'", zoneId);
                                try {
                                        resetManager.requestReset(zone);
                                } catch (Exception e) {
                                        zoneLogger.error(
                                                        "Could not resume reset for zone '{}': {}",
                                                        e,
                                                        zoneId,
                                                        e.getMessage());
                                }
                        });
                }

                Bukkit.getPluginManager().registerEvents(
                                new BlockChangeListener(zoneManager, diffTracker), this);
                Bukkit.getPluginManager().registerEvents(
                                new ExplosionListener(zoneManager, diffTracker), this);
                Bukkit.getPluginManager().registerEvents(
                                new WorldListener(zoneManager), this);

                langManager = new LanguageManager(this, zoneLogger);
                langManager.load(
                                configManager.getConfig() != null
                                                ? configManager.getConfig().getLanguage()
                                                : "ES");

                Bukkit.getPluginManager().registerEvents(
                                new dev.zm.zonereset.listener.ResetEventListener(this, scheduler, langManager), this);

                wandManager = new WandManager(this, langManager);
                selectionStore = new PlayerSelectionStore();

                Bukkit.getPluginManager().registerEvents(
                                new SelectionListener(wandManager, selectionStore, langManager), this);

                Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
                Bukkit.getPluginManager()
                                .registerEvents(new dev.zm.zonereset.gui.listener.ChatInputListener(zoneManager,
                                                zoneRepository, langManager, scheduler, zoneLogger), this);

                ZMRCommand zmrCommand = new ZMRCommand(
                                zoneManager,
                                zoneRepository,
                                resetManager,
                                snapshotCapture,
                                langManager,
                                wandManager,
                                selectionStore,
                                zoneLogger,
                                this,
                                scheduler);

                getCommand("zmzonereset").setExecutor(zmrCommand);
                getCommand("zmzonereset").setTabCompleter(zmrCommand);

                integrationManager = new IntegrationManager(
                                zoneLogger,
                                zoneManager,
                                configManager,
                                getPluginMeta().getVersion());

                integrationManager.init();
                new VersionChecker(this, zoneLogger, langManager).checkAsync();
                Bukkit.getPluginManager().registerEvents(new AdminNotificationListener(langManager), this);
                try {
                        new dev.zm.zonereset.integration.PluginMetrics(this, 33670);
                } catch (Throwable ignored) {
                }

                dev.zm.zonereset.scheduler.ZoneTimerTask timerTask = new dev.zm.zonereset.scheduler.ZoneTimerTask(
                                zoneManager,
                                resetManager,
                                langManager,
                                configManager::getConfig);

                scheduler.runTaskTimer(timerTask, 20L, 20L);

                printStartupMessage();
        }

        @Override
        public void onDisable() {
                Instant shutdownInstant = Instant.now();
                printShutdownMessage(Duration
                                .between(startupInstant != null ? startupInstant : shutdownInstant, shutdownInstant)
                                .toMillis());

                if (resetEngine != null) {
                        resetEngine.shutdown();
                }

                if (zoneManager != null && zoneRepository != null) {
                        zoneManager.getAllZones().forEach(z -> {
                                try {
                                        zoneRepository.saveZone(
                                                        (dev.zm.zonereset.zone.ZoneImpl) z);
                                } catch (Exception e) {
                                        zoneLogger.error(
                                                        "Error saving zone {}: {}",
                                                        e,
                                                        z.getId(),
                                                        e.getMessage());
                                }
                        });
                }

                if (diffStore != null) {
                        diffStore.clearAll();
                }

                if (scheduler != null) {
                        scheduler.shutdown();
                }

                instance = null;
        }

        public void reloadPlugin(org.bukkit.command.CommandSender sender) {
                try {
                        configManager.load();
                        zoneLogger.setDebugEnabled(configManager.isDebugMode());

                        langManager.load(
                                        configManager.getConfig() != null
                                                        ? configManager.getConfig().getLanguage()
                                                        : "ES");

                        if (sender != null) {
                                sender.sendMessage(langManager.getMessage("general.reload"));
                        }

                        zoneLogger.info(
                                        "Plugin reloaded by {}.",
                                        sender != null ? sender.getName() : "console");

                } catch (Exception e) {
                        zoneLogger.error("Error during reload: {}", e, e.getMessage());

                        if (sender != null) {
                                sender.sendMessage("§cError during reload: " + e.getMessage());
                        }
                }
        }

        public ZoneLogger getZoneLogger() {
                return zoneLogger;
        }

        public ZoneScheduler getZoneScheduler() {
                return scheduler;
        }

        private void printBanner() {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                console.sendMessage(
                                ColorParser.parse(
                                                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                console.sendMessage(
                                ColorParser.parse("<gold><bold>zMZoneReset</bold></gold> <gray>v"
                                                + getPluginMeta().getVersion()
                                                + " <dark_gray>by <white>" + getAuthor()));
                console.sendMessage(ColorParser
                                .parse("<dark_gray>› <gray>Automatic zone restoration for Paper/Folia servers"));
                console.sendMessage(
                                ColorParser.parse(
                                                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        }

        private void printStartupMessage() {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                long ms = startupInstant == null ? 0L : Duration.between(startupInstant, Instant.now()).toMillis();
                int zoneCount = zoneManager != null ? zoneManager.getZoneCount() : 0;
                int pending = stateStore != null ? stateStore.getPendingResets().size() : 0;
                String schedulerMode = SchedulerFactory.isFolia() ? "<green>Folia" : "<yellow>Paper";
                String papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null ? "<green>Hooked"
                                : "<red>Not found";
                String debugMode = configManager.isDebugMode() ? "<green>Enabled" : "<red>Disabled";

                console.sendMessage(ColorParser.parse(""));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>Zones loaded: <gold>" + zoneCount));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>Pending resets: <gold>" + pending));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>Scheduler: " + schedulerMode));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>PlaceholderAPI: " + papi));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>Debug mode: " + debugMode));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>Compression: <gold>ZSTD/GZIP"));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>Language: <gold>"
                                + (langManager != null ? configManager.getConfig().getLanguage() : "N/A")));
                console.sendMessage(ColorParser
                                .parse("<green>[OK] <white>Plugin enabled successfully <gray>(" + ms + "ms)"));
                console.sendMessage(
                                ColorParser.parse(
                                                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        }

        private void printShutdownMessage(long ms) {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                int zoneCount = zoneManager != null ? zoneManager.getZoneCount() : 0;
                console.sendMessage(
                                ColorParser.parse(
                                                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                console.sendMessage(ColorParser.parse("<red><bold>zMZoneReset</bold></red> <gray>is shutting down..."));
                console.sendMessage(ColorParser.parse("<dark_gray>  <gray>Zones saved: <gold>" + zoneCount));
                console.sendMessage(
                                ColorParser.parse("<red>X <white>Plugin disabled successfully <gray>(" + ms + "ms)"));
                console.sendMessage(
                                ColorParser.parse(
                                                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        }

        private String getAuthor() {
                return getDescription().getAuthors().isEmpty() ? "zMarkitos_" : getDescription().getAuthors().get(0);
        }
}