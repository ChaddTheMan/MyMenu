# Changelog

All notable changes to MyMenu are recorded here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

Write entries for server owners, in plain language, one line per user-visible change. No
internal class names, no package paths, no implementation detail.

---

## [Unreleased]

### Added

- Menu items can run commands, send messages, open other menus, go back, close, and pause
  between steps.

### Changed

### Fixed

---

## [2.0.0] — unreleased

A complete rewrite for modern Paper servers. **Nothing from 1.0.4.3 carries over**: old
commands, old menu files, and old configuration are all incompatible. Treat this as a new
plugin that happens to share a name.

### Added
- MySQL storage as an alternative to YAML files, selectable in the config
- Menus can open other menus directly, with a back action and navigation history
- Different actions for left-click, right-click, shift-click and other click types
- Per-item permissions, so one menu can show different items to different players
- Per-item cooldowns, click sounds and a glow effect
- Delays between actions in a sequence
- Commands can run as the player with temporarily granted permissions, without opping them
- Hopper, dispenser and dropper menus alongside chests
- PlaceholderAPI support when it is installed
- All messages moved into `messages.yml` for editing and translation
- Automatic backups of the menu file (at most one every five minutes by default, ten kept),
  plus a permanent backup of every deleted menu
- Tab completion and typed arguments on every command

### Changed
- Requires Paper 26.2 or newer, running on Java 25
- All commands are now subcommands of `/mymenu` (aliased `/mm`), replacing the thirteen
  separate `/mm…` commands
- Binding an item now captures the whole item, including its name, lore and enchantments
- Menus stay open after a click unless the item is set to close them
- A mistake in the menu file now skips only the broken menu or item and names where it is,
  instead of stopping every menu from loading; in-game editing pauses until it is fixed
- Edits are saved a couple of seconds after you make them, so a burst of changes is written once
- Items with special data such as enchantments or custom heads are stored in an encoded form;
  plain items stay readable in the menu file
- Item names and lore are no longer shown in italics unless the text asks for it
- An item that can no longer be loaded shows as a barrier instead of breaking the menu, and the
  server log names the menu and slot
- Menu items are placed by dragging a real item into the slot, rather than typing a
  material name
- Statistics now use bStats; the old MCStats service no longer exists
- The update checker reports new versions and no longer downloads or installs anything

### Removed
- Support for Spigot, CraftBukkit and Minecraft versions before the current release
- Migration from MyMenu 1.x menu and configuration files
