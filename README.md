# FakeRevive

A Paper plugin for Minecraft 1.21.4+. When a player dies, the real death
message is replaced with a fake "{name} left the game" broadcast and the
player is held in spectator mode at their death location instead of seeing
the regular respawn screen. An admin can later revive them with `/fr
revive`, at which point they're disguised under a random fake name and
borrowed skin (visible to everyone else in chat, tab list, and nametag)
until their next death.

## Features

* On death: the real death message is replaced by a "{name} left the game"
  broadcast to all players
* A wither spawn sound plays for all players within 48 blocks of the death
* Kill messages show fake names instead of real ones (configurable)
* The player is held in spectator mode at their death location, awaiting
  revive
* On revive: assigned a random fake name and a borrowed skin via
  PacketEvents packet interception (name tag, tab list, and skin; no extra
  entity involved)
* Disguises survive reconnects and stay active until the player's next
  death
* Used fake names are permanently retired and never reassigned

## Commands

All subcommands of `/fr` (alias `/fakerevive`) require the `fakerevive.admin`
permission (default: `op`), except `/fr leave`, which only requires
`fakerevive.leave` (default: everyone).

| Command | Description |
|---|---|
| `/fr revive <player\|@a\|@p\|@r\|team> [kit]` | Revives one faked-out player, all of them (`@a`), nearby ones (`@p`), a random one (`@r`), or a whole team. If a kit name is given, the player is equipped with it. |
| `/fr revive <player> <name> [kit]` | Revives the player under a specific, real Minecraft account's name and skin instead of a randomly generated one. |
| `/fr undisguise [player]` | Removes the disguise from the given player, or from yourself if no player is specified. |
| `/fr disguise <player\|@p\|@r> [kit]` | Manually assigns a random disguise to a player (or nearby/random players), optionally equipping a kit at the same time. |
| `/fr disguise <player> <name> [kit]` | Disguises the player as a specific, real Minecraft account instead of a randomly generated one. |
| `/fr kit save <name>` | Saves the current inventory (items, armor, offhand) as a kit. |
| `/fr kit list` | Lists all saved kits. |
| `/fr kit give <kit> <player>` | Gives a copy of the kit's items to a player's inventory (armor/offhand included); anything that doesn't fit drops on the ground. |
| `/fr kit equip <kit> [player\|team]` | Equips a kit directly into all of a player's slots (or your own), overwriting whatever was there. If a team name is given, equips every online member of that team. |
| `/fr team` | Opens the team management GUI (create/delete teams, assign kits, add/remove members, clear members' inventories). |
| `/fr leave` | Leaves your own team. |
| `/fr reload` | Reloads `config.yml`, the message files, and all kits. |
| `/fr help` | Shows all available commands. |

When adding players to a team from the GUI, you can type one or more names,
or a selector: `@a` (everyone online), `@p` (players near the team manager),
`@r` (one random online player), or `@split` (splits everyone online
roughly in half into the team).

## Configuration

`plugins/FakeRevive/config.yml`:

```yaml
language: de

kill-message:
  enabled: true

kits:
  clear-before-give: false
  clear-before-equip: true
```

* `language`: `de`, `en`, or `fr` (default: `de`)
* `kill-message.enabled`: show kill messages with fake names substituted in (default: `true`)
* `kits.clear-before-give`: clear the target's inventory before `/fr kit give` (default: `false`)
* `kits.clear-before-equip`: clear all of the target's slots before `/fr kit equip` (default: `true`)

## Requirements

* Paper 1.21.4 or newer
* [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) installed as a separate plugin in the `plugins` folder (disguises are implemented entirely through PacketEvents packet interception)
* Persistent internet access to `api.mojang.com` and `sessionserver.mojang.com` (used to keep the fake-identity pool topped up, see below)

## Installation

1. Download and place [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) in your server's `plugins` folder.
2. Place the FakeRevive jar in `plugins` as well.
3. Restart the server.
4. Adjust `plugins/FakeRevive/config.yml` if needed (see "Configuration").

## How disguises work

Around 200 fake identities ship bundled in `names.json`: usernames
verified to not belong to any existing Minecraft account, each paired with
a skin borrowed from an unrelated real donor account. After each revive,
one fresh identity is fetched asynchronously from the Mojang API to keep
the pool topped up (this requires internet access to `api.mojang.com` and
`sessionserver.mojang.com`). Used names are permanently retired: the pool
never reassigns a name that's already been handed out.

**Known limitations:**
* A disguised player's own client always renders their real skin in
  third-person view: this is a Minecraft client limitation and cannot be
  worked around without a client-side mod.
* Tab-completion (e.g. `/msg <Tab>`) still shows real player names, not
  fake names, since it operates on actual connected player names rather
  than the visual disguise.

## Kit system

Kits are stored in `plugins/FakeRevive/kits.yml`.

* `/fr kit give` adds items via `addItem()`: anything that doesn't fit in
  the inventory drops on the ground.
* `/fr kit equip` sets all slots directly: nothing is dropped.

## Building from source

Requirements: JDK 21, Maven.

```
mvn package
```

The built plugin will be at `target/fakerevive-1.0.0.jar`, ready to be
copied into a Paper server's `plugins` folder.

Under JDK 25, the build (including `mvn test`) may fail with
`Cannot load from object array because "this.hashes" is null`: this is a
javac bug unrelated to the plugin code. Workaround: append
`-Dmaven.compiler.fork=true`, e.g. `mvn package -Dmaven.compiler.fork=true`.

### Tests

```
mvn test
```

Tests use [MockBukkit](https://github.com/MockBukkit/MockBukkit) to
simulate the server, players, and events.
