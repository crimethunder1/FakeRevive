# FakeLeaveAndRevive

A Paper plugin for Minecraft 1.21.4+. When a player dies, the normal death
message is replaced with a fake "left the game" message and the player is
put into spectator mode at their death location instead of seeing the
regular respawn screen. If they actually leave the server in this state, no
quit message is shown either.

An admin can later bring a "faked-out" player back with `/revive`, at which
point they can also be re-equipped with a saved kit. On revive, the player
is disguised under a random fake name and skin — visible to everyone else
in chat, tab list, and nametag — until their next death.

## Features

- Replaces death messages with a fake "left the game" message
- Puts dead players into spectator mode at their death position instead of respawning
- Suppresses the quit message if the player disconnects while faked-out
- `/revive` brings a player back to survival, optionally applying a saved kit
- Automatic disguise on revive: random fake name + matching skin, visible to all other players
- Disguise persists across reconnects until the next death
- Built-in kit system (save/list/apply inventories)
- Multi-language support (German, English, French)

## Requirements

- Paper 1.21.4 or newer
- [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) installed as a separate plugin in the `plugins` folder (this is the only dependency; disguises are implemented entirely through PacketEvents packet interception)
- Persistent internet access to `api.mojang.com` and `sessionserver.mojang.com` (used to keep the fake-identity pool topped up, see below)

## Installation

1. Download or build `fake-leave-and-revive-<version>.jar` (see "Building from source" below).
2. Install [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) in your server's `plugins` folder.
3. Copy the plugin jar into `plugins` as well and (re)start the server.
4. Adjust `plugins/FakeLeaveAndRevive/config.yml` if needed (see "Configuration").

## Commands

All commands require the `fakeleaveandrevive.revive` permission (default: `op`).

| Command | Description |
|---|---|
| `/revive <player\|@a> [kit]` | Revives one faked-out player, or all of them with `@a`. If a kit name is given, the player's inventory is cleared and replaced with that kit. |
| `/undisguise [player]` | Removes the disguise from the given player, or from yourself if no player is specified. |
| `/kit save <name>` | Saves the current inventory (items, armor, offhand) as a server kit. |
| `/kit list` | Lists all saved kits. |

## Configuration

`plugins/FakeLeaveAndRevive/config.yml`:

```yaml
language: de
```

Supported values: `de` (German), `en` (English), `fr` (French).

## Kit System

Kits are stored in `plugins/FakeLeaveAndRevive/kits.yml`. A kit fully
captures the inventory, armor slots, and offhand item at the time it was
saved with `/kit save <name>`, and restores all of them into the correct
slots when applied via `/revive <player> <kit>`.

## How disguises work

When a player is revived, they are assigned a random fake name together
with a fixed, matching skin from a pool of pre-generated identities and
appear under that identity to everyone else — in the tab list, nametag,
and skin. The disguised player themselves gets a chat and action bar
notice showing their assigned fake name. The disguise survives
reconnects and stays active until the player's next death, at which
point it is removed.

The name/skin pairs come from a bundled pool (~200 entries) of names
verified not to belong to any existing Minecraft account, each paired
with the skin of an unrelated, real donor account. After every revive,
the plugin asynchronously fetches a fresh replacement identity from the
Mojang API in the background to keep the pool from running low; this
requires the server to have ongoing internet access to Mojang's APIs.

**Known limitations:**
- A disguised player's own client always renders their real skin in
  third-person view — this is a Minecraft client limitation and cannot
  be worked around without a client-side mod.
- Tab-completion (e.g. `/msg <Tab>`) still shows the player's real
  username, since it operates on actual connected player names rather
  than the visual disguise.

## Building from source

Requirements: JDK 21, Maven.

```
mvn package
```

The built plugin will be at `target/fake-leave-and-revive-<version>.jar`,
ready to be copied into a Paper server's `plugins` folder.

Under JDK 25, the build (including `mvn test`) may fail with
`Cannot load from object array because "this.hashes" is null` — this is a
javac bug unrelated to the plugin code. Workaround: append
`-Dmaven.compiler.fork=true`, e.g. `mvn package -Dmaven.compiler.fork=true`.

### Tests

```
mvn test
```

Tests use [MockBukkit](https://github.com/MockBukkit/MockBukkit) to
simulate the server, players, and events.
