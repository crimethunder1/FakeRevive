# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.3.0] (2026-11-10)

### Changed

**Breaking change: FakeRevive now requires Paper 26.2, Java 25 and PacketEvents 2.13.0 or newer. Minecraft 1.21.x is no longer supported.**

**Behavior changes. These affect existing setups, please review them before updating.**

* **`@p` now means the nearest single player.** This applies to `/fr revive`, `/fr disguise` and the player prompt of the team GUI. The previous behavior (all players within 16 blocks) is now available as `@near`.
* **`/fr disguise @p`, `@near` and `@r` skip exempt players**, like `@a` does.
* **Showing the real name before the fake name (tab list and chat) now requires the permission `fakerevive.seereal`.** It no longer depends on being an operator. The default is `op`, and it is a child of `fakerevive.admin`.
* **Kit and team names are restricted** to letters, digits, underscore and hyphen, 1 to 32 characters. Existing names that contain a dot or other special characters are not migrated. Rename them by hand in `kits.yml` and `teams.yml`.
* **`/fr pool refill` always starts a refill**, regardless of `auto-refill`. It reports when a refill is already running or when the pool is full.
* Chat from disguised players is no longer cancelled and resent. It is rewritten through a chat renderer, so chat plugins keep their format.

### Added

* Join, leave and advancement messages of disguised players show the fake name, including in hover and click events.
* Players who died and are waiting for a revive stay in that state across restarts. The state is stored in `faked-out.yml`, and the join message is suppressed for these players.
* Malformed YAML files are saved as `.broken` instead of being overwritten. Files are written atomically.
* New messages in German, English and French.

### Fixed

* Disguises could get out of sync between the server and the clients after a failed switch.
* Revive and disguise with a player name now report invalid names and network errors instead of failing silently. The kit is still applied.
* Broken kit entries no longer abort `equip` and `give` partway through.
* Write errors while saving the assigned fake names no longer abort a revive.
* Dragging items into the team GUI is blocked. Kit selection uses stored data instead of the display name.
* `/fr disguise @near` with an empty name pool now reports the number of undisguised players correctly and only once.

### Internal

* The code was restructured without further behavior changes.
* The configuration is fully documented in the README.

## [1.2.0]

* Initial changelog, earlier releases not documented.
