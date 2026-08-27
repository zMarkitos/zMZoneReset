# zMZoneReset

> Automatic zone reset plugin for Paper and Folia servers.
> No WorldEdit or FAWE required.

---

## Installation

1. Drop `zMZoneReset-X.X.X.jar` into your server's `plugins/` folder.
2. Start the server once so the plugin can generate its folders.
3. Edit `plugins/zMZoneReset/config.yml` to fit your needs.
4. Set the language in `settings.language` (`EN` or `ES`).
5. Restart or reload with `/zmr reload`.

---

## Configuration (`config.yml`)

### `settings`
| Key | Description | Default |
|---|---|---|
| `debug` | Enables diagnostic logs in console | `false` |
| `language` | Language file without `.yml` (`EN` or `ES`) | `"EN"` |
| `timer-format` | Time format in messages and placeholders (1=clock, 2=short, 3=long) | `3` |

### `performance`
| Key | Description | Default |
|---|---|---|
| `adaptive` | Adjusts workload based on server load | `true` |
| `max-time-per-tick-ms` | Max work time per tick (ms) | `4` |
| `min-time-per-tick-ms` | Minimum time under stress | `1` |
| `target-tps` | Target TPS to reduce workload | `19.0` |
| `max-concurrent-resets` | Max number of zones resetting simultaneously | `1` |

### `reset`
| Key | Description | Default |
|---|---|---|
| `strategy` | Default strategy for new zones (`AUTO`/`DIFF`/`SNAPSHOT`) | `AUTO` |
| `auto.diff-threshold` | % of change for AUTO to use SNAPSHOT instead of DIFF | `30` |

### `storage`
| Key | Description | Default |
|---|---|---|
| `compression` | Snapshot compression (`ZSTD` with fallback to `GZIP`) | `ZSTD` |

### `security`
| Key | Description | Default |
|---|---|---|
| `players.action-during-reset` | Action for players inside the zone during reset (`ALLOW`/`WARN`/`WARN_AND_BLOCK`/`TELEPORT`) | `WARN_AND_BLOCK` |
| `block-interactions-during-reset` | Blocks interactions during reset | `true` |
| `entity-cleanup.items` | Removes dropped items | `true` |
| `entity-cleanup.tnt` | Removes activated TNT | `true` |
| `entity-cleanup.projectiles` | Removes projectiles | `true` |
| `entity-cleanup.armor-stands` | Removes armor stands | `false` |
| `entity-cleanup.falling-blocks` | Removes falling blocks | `true` |
| `entity-cleanup.custom` | Extra list of EntityType to remove | `[]` |

### `countdown-announcements`
Global announcements sent to **all** online players before a zone resets.

| Key | Description | Default |
|---|---|---|
| `enabled` | Enables countdown announcements | `true` |
| `announce-before-seconds` | List of remaining seconds at which a notice is sent (e.g. `[60, 30, 10]`) | `[60, 30, 10]` |
| `last-countdown-seconds` | Last N seconds with a notice every second (1-by-1 countdown) | `5` |
| `title.enabled` | Shows a title in addition to the chat message | `true` |
| `title.fade-in` | Title fade-in ticks | `5` |
| `title.stay` | Title stay ticks | `30` |
| `title.fade-out` | Title fade-out ticks | `5` |
| `sounds.countdown` | Sound on each notice | `BLOCK_NOTE_BLOCK_PLING` |
| `sounds.final` | Sound on the final second | `BLOCK_NOTE_BLOCK_BASS` |

Example announcement flow (default config, 5-minute zone):
```
[4m 00s remaining] → chat message + title
[30s remaining]    → chat message + title
[10s remaining]    → chat message + title
[5s, 4s, 3s, 2s, 1s] → second-by-second countdown
[0s] → reset executes
```

Messages and titles are customized in the language file under the `countdown:` section.

### `reset-notifications`
Notifications sent to players **inside the zone** while it is resetting.

| Key | Description | Default |
|---|---|---|
| `enabled` | Enables reset notifications | `true` |
| `warning-interval-seconds` | Seconds between reminders while resetting | `60` |
| `title.title` | Title (supports MiniMessage) | `<red><bold>Zone Reset</bold></red>` |
| `title.subtitle` | Subtitle with `{zone}` | `<yellow>{zone}</yellow> <gray>is resetting</gray>` |
| `sounds.start` | Sound when the reset starts | `BLOCK_NOTE_BLOCK_PLING` |
| `sounds.warning` | Sound on each reminder | `BLOCK_NOTE_BLOCK_BELL` |
| `sounds.complete` | Sound when the reset completes | `ENTITY_PLAYER_LEVELUP` |

### `teleport-on-reset`
Controls behavior when teleporting players out of the zone.

| Key | Description | Default |
|---|---|---|
| `use-zone-spawn-if-set` | Uses the zone's spawn if configured | `true` |
| `search-surface` | Searches for safe ground nearby | `true` |
| `safety-radius` | Safe-surface search radius | `3` |

### `progress-bar`
Controls the progress bar for the `%zmzonereset_progress_<zone>%` placeholder.

| Key | Description | Default |
|---|---|---|
| `length` | Number of characters in the bar | `20` |
| `filled-char` | Character for the filled portion | `█` |
| `empty-char` | Character for the empty portion | `░` |
| `filled-color` | Hex color for the filled portion (e.g. `#55FF55`) | `#55FF55` |
| `empty-color` | Hex color for the empty portion | `#555555` |
| `border-left` | Left border (supports `&` codes) | `&8[` |
| `border-right` | Right border | `&8]` |

The bar shows **elapsed progress** since the last reset:
`0% = just reset`, `100% = about to reset`.

---

## Languages

Language files are located in `plugins/zMZoneReset/lang/`.

Included files:
- `Lang_EN.yml` — English
- `Lang_ES.yml` — Spanish

To add a language, copy any file, rename it to `Lang_XX.yml`, and translate it.
Then set `settings.language: XX` in the config.

### Language file sections
| Section | Description |
|---|---|
| `general` | General messages (permissions, errors, reload) |
| `wand` | Selection wand messages |
| `zones` | Zone management and reset broadcast messages |
| `gui` | Text for all GUI menus |
| `countdown` | Global countdown messages and titles |

---

## Commands

Main aliases: `/zmr`, `/zmzonereset`, `/zonereset`

| Command | Description | Permission |
|---|---|---|
| `/zmr` | Opens the main zones menu | `zmzonereset.use` |
| `/zmr wand` | Gives the selection wand | `zmzonereset.admin` |
| `/zmr create <id>` | Creates a zone from the current selection | `zmzonereset.admin` |
| `/zmr remove <id>` | Removes a zone | `zmzonereset.admin` |
| `/zmr edit [id]` | Opens the zone edit GUI | `zmzonereset.admin` |
| `/zmr info <id>` | Shows zone information | `zmzonereset.use` |
| `/zmr list` | Lists all zones | `zmzonereset.use` |
| `/zmr enable <id>` | Enables a zone | `zmzonereset.admin` |
| `/zmr disable <id>` | Disables a zone | `zmzonereset.admin` |
| `/zmr reset <id>` | Forces a zone reset | `zmzonereset.reset` |
| `/zmr capture <id>` | Captures the current state as a snapshot | `zmzonereset.admin` |
| `/zmr setspawn global\|<zone>` | Sets global or zone spawn | `zmzonereset.admin` |
| `/zmr clearspawn global\|<zone>` | Clears global or zone spawn | `zmzonereset.admin` |
| `/zmr spawn global\|<zone>` | Teleports to spawn | `zmzonereset.use` |
| `/zmr reload` | Reloads config and language | `zmzonereset.reload` |

---

## Permissions

| Permission | Description |
|---|---|
| `zmzonereset.*` | All permissions |
| `zmzonereset.admin` | Zone management, capture, reset, editing, wand |
| `zmzonereset.reset` | Force resets manually |
| `zmzonereset.reload` | Reload the plugin |
| `zmzonereset.use` | Basic access (info, list, spawn) |

---

## Placeholders (PlaceholderAPI)

Requires PlaceholderAPI to be installed.

| Placeholder | Description | Example value |
|---|---|---|
| `%zmzonereset_status_<zone>%` | Zone status | `READY` / `RESETTING` / `DISABLED` |
| `%zmzonereset_strategy_<zone>%` | Reset strategy | `AUTO` / `DIFF` / `SNAPSHOT` |
| `%zmzonereset_time_<zone>%` | Formatted remaining time | `01m 30s` |
| `%zmzonereset_progress_<zone>%` | Colored progress bar | `[████████░░░░░░░░░░]` |
| `%zmzonereset_progress_raw_<zone>%` | Integer percentage (0–100) | `42` |

### The progress bar

The progress bar shows how much time has elapsed since the last reset.
It is fully configured in the `progress-bar` section of `config.yml`.

```yaml
progress-bar:
  length: 20
  filled-char: "█"
  empty-char: "░"
  filled-color: "#55FF55"   # Green hex
  empty-color: "#555555"    # Gray hex
  border-left: "&8["
  border-right: "&8]"
```

Usage example for ScoreboardPlugin, TAB, etc.:
```
Zone Arena: %zmzonereset_progress_arena%  (%zmzonereset_progress_raw_arena%%)
Time:       %zmzonereset_time_arena%
```

---

## GUI Menus

The plugin includes 4 menus accessible via `/zmr edit <zone>`:

| Menu | Description |
|---|---|
| **ZonesListMenu** | Paginated list of all zones |
| **ZoneMainMenu** | Access to Settings, Info, and Teleport |
| **ZoneSettingsMenu** | Full zone configuration (status, strategy, interval, blocking, spawn, action, titles, messages, capture) |
| **ZoneInfoMenu** | Detailed information (world, bounds, status, strategy, snapshot, interval) |

---

## Reset Strategies

| Strategy | Description |
|---|---|
| `AUTO` | The plugin automatically decides between DIFF and SNAPSHOT based on the change threshold |
| `DIFF` | Only restores blocks that changed (faster, lower disk usage) |
| `SNAPSHOT` | Restores the entire zone from the saved snapshot (more thorough) |

---

## Actions During Reset

| Action | Description |
|---|---|
| `ALLOW` | Players inside can keep interacting |
| `WARN` | They receive a warning message but are not blocked |
| `WARN_AND_BLOCK` | They receive a warning and their interactions are blocked |
| `TELEPORT` | They are teleported out of the zone before the reset starts |

---

## Automatic Reset Flow

```
ZoneTimerTask (every 1s)
    ↓ remainingTicks -= 20
    ↓ remainingSeconds ≤ threshold? → global countdown broadcast
    ↓ remainingTicks ≤ 0?
        → ResetManagerImpl.requestReset(zone)
            → ResetQueue.enqueue(job)
            → ResetEngine.poke()
                → processNext() [sync]
                    → zone.setStatus(RESETTING)
                    → handler.prepareAsync() → executeReset() → verifyAndCleanup()
                    → zone.setStatus(READY)
                    → zone.resetTimer()         ← timer restarted cleanly
```

---

## PlaceholderAPI Integration

The plugin registers automatically if PlaceholderAPI is installed.
The expansion identifier is `zmzonereset`.

---

## Technical Notes

- Compatible with **Paper** and **Folia** via scheduler abstraction.
- ZSTD is optional at runtime; if not present, the plugin falls back to GZIP automatically.
- Snapshots are stored in `plugins/zMZoneReset/snapshots/`.
- Pending resets are automatically recovered when the server restarts.
- Countdown announcements are **global** (all players, regardless of location).
- `reset-notifications` only go to players **inside the zone** while it resets.

---

## How to Test

1. Start the server and verify the plugin loads without errors.
2. Join as an admin.
3. Run `/zmr wand` and select 2 points.
4. Run `/zmr create testzone`.
5. Run `/zmr capture testzone`.
6. Set a short interval with `/zmr edit testzone` → Settings → Interval.
7. Wait and watch for countdown announcements in chat.
8. Verify the zone resets and the timer starts fresh correctly.
9. Run `/zmr reload` and verify the config reloads.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.#   z M Z o n e R e s e t  
 