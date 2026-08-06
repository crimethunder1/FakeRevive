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
* Chat messages from disguised players show their fake name to everyone;
  operators additionally see the real name as a prefix
* Operators see the real name as a prefix in the tab list next to the fake
  name
* `/fr undisguise` and `/fr kit give` accept fake player names as input

## Commands

All subcommands of `/fr` (alias `/fakerevive`) require the `fakerevive.admin`
permission (default: `op`), except `/fr leave`, which only requires
`fakerevive.leave` (default: everyone).

| Command | Description |
|---|---|
| `/fr revive <player\|@a\|@p\|@r\|team> [kit]` | Revives faked-out players. `@a` revives all, `@p` nearby (16 blocks), `@r` random, or all online members of a team. Kit is optional. |
| `/fr revive <player> <MinecraftName> [kit]` | Revives a player disguised as a specific real Minecraft account (name and skin fetched from Mojang). |
| `/fr undisguise [@a\|player\|fakename]` | Removes a disguise. `@a` removes all active disguises. Accepts both real and fake player names. |
| `/fr disguise <player\|@p\|@r> [MinecraftName]` | Assigns a random disguise, or a specific real Minecraft account's identity if a name is given. `@p` targets nearby players (16 blocks), `@r` a random online player. |
| `/fr kit save <name>` | Saves current inventory (items, armor, offhand) as a named kit. |
| `/fr kit list` | Lists all saved kits. |
| `/fr kit delete <name>` | Deletes a saved kit. |
| `/fr kit give <kit> <player\|fakename\|@a\|team>` | Gives kit items to a player's inventory. `@a` gives to all online players, team name gives to all online team members. Overflow drops on the ground. Accepts fake player names. |
| `/fr kit equip <kit> [@a\|player\|team]` | Equips a kit directly into all slots. `@a` equips all online players, team name equips all online members. |
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

### Chat and tab list

While a disguise is active, the player's fake identity is used in chat and
in the tab list:

* Chat: everyone sees `<FakeName> <message>`. Operators additionally see
  the real name as a prefix: `[RealName] <FakeName> <message>`.
* Tab list: everyone sees the fake name. Operators see the real name as an
  aqua prefix in front of it: `[RealName] FakeName`.

Because fake names are shown this way, `/fr undisguise` and
`/fr kit give` also accept a fake name where a player name is expected.

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
* `/fr kit delete` removes a kit permanently.
* `/fr kit give` and `/fr kit equip` accept `@a` (all online players) and
  team names as targets.

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
