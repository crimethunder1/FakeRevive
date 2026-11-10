# FakeRevive

A plugin for Paper 26.2 (Java 25). When a player dies, the real death
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
  players with `fakerevive.seereal` additionally see the real name as a prefix
* Players with `fakerevive.seereal` see the real name as a prefix in the tab
  list next to the fake name
* `/fr undisguise` and `/fr kit give` accept fake player names as input
* `/fr kit equip <team>` equips a team's own assigned kit to all of its
  members in one command
* `/fr armor on` locks worn armor in place like Curse of Binding, without
  enchanting the items

## Commands

Each subcommand of `/fr` (alias `/fakerevive`) has its own permission node, so
a permissions plugin such as LuckPerms can hand single commands to helpers
without granting full admin rights. `fakerevive.admin` (default: `op`) includes
every command node; `/fr leave` only requires `fakerevive.leave` (default:
everyone). `/fr help` is available to anyone holding at least one of the nodes
and lists only the commands they may use. Tab completion likewise only
suggests permitted subcommands.

| Command | Description |
|---|---|
| `/fr revive <player\|@a\|@p\|@near\|@r\|team> [kit]` | Revives faked-out players. `@a` revives all, `@p` the nearest faked-out player in your world, `@near` all faked-out players within 16 blocks in your world, `@r` random, or all online members of a team. `@p` and `@near` require a player as sender. Kit is optional. |
| `/fr revive <player> <MinecraftName> [kit]` | Revives a player disguised as a specific real Minecraft account (name and skin fetched from Mojang). |
| `/fr undisguise [@a\|player\|fakename]` | Removes a disguise. `@a` removes all active disguises. Accepts both real and fake player names. |
| `/fr disguise <player\|@p\|@near\|@r> [MinecraftName]` | Assigns a random disguise, or a specific real Minecraft account's identity if a name is given. `@p` targets the nearest other player in your world, `@near` all players within 16 blocks in your world (you included), `@r` a random online player. `@p`, `@near` and `@r` skip exempt players. |
| `/fr disguise @a` | Gives every online player a random disguise, skipping exempt players, faked-out players, and players who are already disguised. A `MinecraftName` is not allowed here. See "Exempt players". |
| `/fr exempt add <player>` | Adds an online player, or an offline player who has joined the server before, to the stored exempt list. |
| `/fr exempt remove <player>` | Removes a player from the stored exempt list. Names from `exempt-players` in `config.yml` can only be removed there. |
| `/fr exempt list` | Lists all exempt players, both stored and from `config.yml`. |
| `/fr kit save <name>` | Saves current inventory (items, armor, offhand) as a named kit. |
| `/fr kit list` | Lists all saved kits. |
| `/fr kit delete <name>` | Deletes a saved kit. |
| `/fr kit give <kit> <player\|fakename\|@a\|team>` | Gives kit items to a player's inventory. `@a` gives to all online players, team name gives to all online team members. Overflow drops on the ground. Accepts fake player names. |
| `/fr kit equip <kit> [@a\|player\|team]` | Equips a kit directly into all slots. `@a` equips all online players, team name equips all online members. |
| `/fr kit equip <team>` | Equips the kit assigned to that team (in the team GUI) to all of its online members. A team name takes precedence over an equally named kit. |
| `/fr team` | Opens the team management GUI (create/delete teams, assign kits, add/remove members, clear members' inventories). |
| `/fr armor <on\|off> [player\|team\|@a]` | Locks or unlocks worn armor. Without a target it applies to yourself. A team target covers every member, including offline ones. |
| `/fr armor status [player]` | Shows who currently has an armor lock. |
| `/fr pool [refill]` | Shows how many fake names are still available, how many have been used, and whether a refill is running. `refill` starts one immediately, regardless of `fake-names.auto-refill`, a running backoff after failures, and `fake-names.low-water-mark`. It fetches at most `fake-names.batch-size` identities towards `fake-names.target-pool-size`. Possible replies: "Refill from Mojang started." when a run was started, "A refill is already running." when one is in progress, and "The name pool is already full (target: N)." when there is nothing to fetch. |
| `/fr leave` | Leaves your own team. |
| `/fr reload` | Reloads `config.yml`, the message files, all kits, teams, armor locks, and both exempt lists. |
| `/fr help` | Shows all available commands. |

When adding players to a team from the GUI, you can type one or more names,
or a selector: `@a` (everyone online), `@p` (the nearest other player),
`@near` (everyone within 16 blocks, the team manager included),
`@r` (one random online player), or `@split` (splits everyone online
roughly in half into the team).

**Behavior change:** `@p` used to mean every player within 16 blocks of the
sender, the sender included. It now means the nearest single player, as in
vanilla Minecraft, and never the sender. The old behavior is available as `@near`.

### Permissions

| Node | Grants | Default |
|---|---|---|
| `fakerevive.admin` | All nodes below except `fakerevive.leave` (children: `revive`, `disguise`, `kit`, `team`, `armor`, `pool`, `reload`, `seereal`). | `op` |
| `fakerevive.revive` | `/fr revive` | `op` |
| `fakerevive.disguise` | `/fr disguise`, `/fr undisguise`, and `/fr exempt` | `op` |
| `fakerevive.kit` | `/fr kit` with all subcommands (`save`, `list`, `delete`, `give`, `equip`) | `op` |
| `fakerevive.team` | `/fr team` (team management GUI) | `op` |
| `fakerevive.armor` | `/fr armor`, including `status` | `op` |
| `fakerevive.pool` | `/fr pool`, including `refill` | `op` |
| `fakerevive.reload` | `/fr reload` | `op` |
| `fakerevive.seereal` | Shows the real name of disguised players in chat and in the tab list. Operators have it by default. | `op` |
| `fakerevive.leave` | `/fr leave` | everyone |
| `fakerevive.exempt` | Excludes the holder from `/fr disguise @a`. Not part of `fakerevive.admin`, so operators are not exempt automatically. | `false` |

## Configuration

`plugins/FakeRevive/config.yml`:

```yaml
language: de

kill-message:
  enabled: true

kits:
  clear-before-give: false
  clear-before-equip: true

exempt-players: []

armor:
  notify-blocked: true

fake-names:
  auto-refill: true
  target-pool-size: 60
  low-water-mark: 25
  batch-size: 10
  refill-interval-seconds: 300
  request-spacing-millis: 1200
  max-attempts-per-identity: 25
  reuse-donor-skins: true
```

All options are read from `config.yml`. `/fr reload` applies changes to every
key below without a restart. Missing keys fall back to the defaults listed
here. Numeric values outside the valid range are not rejected: the plugin
silently raises or lowers them to the nearest allowed value.

#### General

* `language`
  * Default: `de`
  * Valid values: `de`, `en`, `fr`
  * Effect: language of all plugin messages. An unknown value falls back to `en`.
* `kill-message.enabled`
  * Default: `true`
  * Valid values: `true`, `false`
  * Effect: when a player dies, the real death message is always replaced by the fake "left the game" broadcast. With `true`, the original death message is additionally broadcast, with real names of disguised players and killers replaced by their fake names. With `false`, only the fake leave broadcast is sent.

#### Kits

* `kits.clear-before-give`
  * Default: `false`
  * Valid values: `true`, `false`
  * Effect: clears the main inventory of each target before `/fr kit give`. Armor and offhand are not touched.
* `kits.clear-before-equip`
  * Default: `true`
  * Valid values: `true`, `false`
  * Effect: clears the whole inventory of each target before `/fr kit equip`, including armor and offhand. This also applies to `/fr kit equip <team>`.

#### Exempt players

* `exempt-players`
  * Default: empty list (`[]`)
  * Valid values: a list of player names
  * Effect: players that `/fr disguise @a`, `@p`, `@near`, and `@r` always skip. Names are matched case-insensitively. Entries here cannot be removed with `/fr exempt remove`. Edit the file and run `/fr reload`. See "Exempt players".

#### Armor lock

* `armor.notify-blocked`
  * Default: `true`
  * Valid values: `true`, `false`
  * Effect: shows an action bar message when a player with an armor lock tries to take a worn piece off. With `false`, the attempt is still blocked, but silently.

#### Fake name pool

These options control the background refill described in "How disguises work".

* `fake-names.auto-refill`
  * Default: `true`
  * Valid values: `true`, `false`
  * Effect: enables the background refill. With `false`, no identities are fetched automatically, neither by the timer nor after a revive or disguise. The pool eventually runs empty, since used names are never reused. `/fr pool refill` is independent of this option: it still fetches identities on demand, ignores a running backoff and `low-water-mark`, and is limited by `batch-size` per run and by `target-pool-size`.
* `fake-names.target-pool-size`
  * Default: `60`
  * Valid range: `1` to `500`
  * Effect: number of unused identities the refill tries to keep ready.
* `fake-names.low-water-mark`
  * Default: `25`
  * Valid range: `0` to `500`, and never higher than `target-pool-size`
  * Effect: a refill only starts when the number of unused identities has dropped to this value or below. A larger value than `target-pool-size` is lowered to `target-pool-size`.
* `fake-names.batch-size`
  * Default: `10`
  * Valid range: `1` to `50`
  * Effect: maximum number of identities fetched in one refill run. A run fetches at most the missing amount up to `target-pool-size`.
* `fake-names.refill-interval-seconds`
  * Default: `300`
  * Valid range: `30` or higher, no upper limit
  * Effect: seconds between two background checks of the pool. Lower values are raised to `30`.
* `fake-names.request-spacing-millis`
  * Default: `1200`
  * Valid range: `200` or higher, no upper limit
  * Effect: pause in milliseconds between two requests to Mojang inside a batch. Lower values are raised to `200`. Low values raise the risk of rate limiting.
* `fake-names.max-attempts-per-identity`
  * Default: `25`
  * Valid range: `1` to `100`
  * Effect: maximum number of candidate names tried to find one free name for a single identity.
* `fake-names.reuse-donor-skins`
  * Default: `true`
  * Valid values: `true`, `false`
  * Effect: with `true`, cached donor skins are reused. With `false`, a fresh donor is searched for every identity. This needs many more API calls.

## Requirements

* Paper 26.2
* Java 25
* [PacketEvents](https://modrinth.com/plugin/packetevents) 2.13.0 or newer, installed as a separate plugin in the `plugins` folder (disguises are implemented entirely through PacketEvents packet interception)
* Persistent internet access to `api.mojang.com` and `sessionserver.mojang.com` (used to keep the fake-identity pool topped up, see below)

## Installation

1. Make sure the server runs Paper 26.2 on Java 25. Minecraft 1.21.x is not supported.
2. Download [PacketEvents](https://modrinth.com/plugin/packetevents) 2.13.0 or newer and place it in your server's `plugins` folder.
3. Place the FakeRevive jar in `plugins` as well.
4. Restart the server.
5. Adjust `plugins/FakeRevive/config.yml` if needed (see "Configuration").

## How disguises work

Around 200 fake identities ship bundled in `names.json`: usernames
verified to not belong to any existing Minecraft account, each paired with
a skin borrowed from an unrelated real donor account. Used names are
permanently retired in `used-fake-names.yml`: the pool never reassigns a
name that has already been handed out.

Because names are never recycled, the pool has to be refilled to keep
working. A background task checks it regularly and, once it has fallen to
`fake-names.low-water-mark`, fetches a batch of fresh identities from the
Mojang API back up towards `fake-names.target-pool-size`. This needs
internet access to `api.mojang.com` and `sessionserver.mojang.com`. If
Mojang is unreachable or rate-limiting, the refill backs off exponentially
(up to 30 minutes) and logs a warning instead of retrying in a tight loop.

If the pool does run dry, revives still go through, the player simply keeps
their real name, and the command sender is told so explicitly. Run
`/fr pool` to see the current counts, or `/fr pool refill` to trigger a
refill by hand.

### Chat and tab list

While a disguise is active, the player's fake identity is used in chat and
in the tab list:

* Chat: the chat event is neither cancelled nor re-sent. The plugin wraps the current chat renderer
  and passes the fake name as the source display name, so the format set by other chat plugins is kept
  and chat bridges and chat reporting keep working. Viewers with `fakerevive.seereal`
  (operators by default, and the console) additionally see the real name as an aqua prefix: `[RealName] ` in front
  of the rendered message. Players who are not disguised are not touched.
* Tab list: everyone sees the fake name. Players with `fakerevive.seereal` see the real name as an
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

## Exempt players

`/fr disguise @a` disguises every online player at once. A player is
skipped if any of these three sources marks them as exempt:

1. **Stored list**: maintained in game with `/fr exempt add <player>` and
   `/fr exempt remove <player>`, saved per player UUID in
   `plugins/FakeRevive/exempt-players.yml`. Entries keep working after a
   name change.
2. **Config list**: the `exempt-players` list in `config.yml`, edited by
   hand. Names are matched case-insensitively. `/fr exempt list` shows these
   entries too, marked with `(config.yml)`, but they can only be removed in
   the file.
3. **Permission**: anyone holding `fakerevive.exempt`. This node defaults to
   `false` and is not a child of `fakerevive.admin`, so it has to be granted
   explicitly.

Both lists are reloaded on startup and with `/fr reload`.

Besides exempt players, `/fr disguise @a` also skips players who are
faked out (dead and waiting in spectator mode for a revive) and players
who are already disguised, so no names are wasted. The sender gets a summary
with the number of disguised, exempt, and already disguised players. If the
name pool runs dry partway through, the remaining players are left as they
are and a single message reports how many were not disguised.

`/fr disguise` with an explicit player name ignores the exempt lists, because the admin names the player directly. `@p`, `@near`, and `@r` respect them like `@a` does: `@p` and `@r` only pick players who are not exempt, and `@near` reports how many players it skipped.

## Kit system

Kits are stored in `plugins/FakeRevive/kits.yml`.

* `/fr kit give` adds items via `addItem()`: anything that doesn't fit in
  the inventory drops on the ground.
* `/fr kit equip` sets all slots directly: nothing is dropped.
* `/fr kit delete` removes a kit permanently.
* `/fr kit give` and `/fr kit equip` accept `@a` (all online players) and
  team names as targets.
* `/fr kit equip <team>` (a single argument) is shorthand for "equip this
  team's own kit to its members". The kit comes from the team's assignment
  in the team GUI, stored in `teams.yml`. If a kit and a team share a name,
  the team wins; use `/fr kit equip <kit> <player>` to target the kit
  explicitly.

## Armor lock

`/fr armor on` makes worn armor behave like it carries Curse of Binding,
but without touching the items: no enchantment is added, so the gear stays
clean and the lock can be lifted again at any time with `/fr armor off`.

* Putting armor **on** stays allowed, only taking a worn piece off is
  blocked. That covers normal and shift clicks, hotbar number swaps,
  offhand swaps, `Q`/`Ctrl+Q` drops, drags across an armor slot, and the
  right-click swap with a piece in hand (the classic elytra/chestplate
  swap).
* Locks are stored per player UUID in `plugins/FakeRevive/armor-lock.yml`,
  so they survive logouts and server restarts.
* Targeting a team locks every member, including offline ones. `@a` only
  covers players currently online.
* On death, locked armor drops as usual, the lock only applies while the
  player is alive, matching vanilla Curse of Binding.

## Building from source

Requirements: JDK 25, Maven.

```
mvn package
```

The built plugin will be at `target/fakerevive-1.3.0.jar`, ready to be
copied into a Paper server's `plugins` folder.

See [CHANGELOG.md](CHANGELOG.md) for the list of changes in each release.

The build (including `mvn test`) may fail with
`Cannot load from object array because "this.hashes" is null`: this is a
javac bug unrelated to the plugin code. Workaround: append
`-Dmaven.compiler.fork=true`, e.g. `mvn package -Dmaven.compiler.fork=true`.

### Regenerating names.json

The generator is a developer tool and lives in the test sources, so it is
not part of the built jar. It checks candidate names against the Mojang API,
pairs each free name with a skin from a real donor account, and writes the
result to `src/main/resources/names.json`. Progress is kept in
`tools-output/`, so an interrupted run can simply be started again.

```
mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.fakerevive.tools.NameListGenerator -Dexec.args="src/main/resources/names.json"
```

Without the Maven exec plugin, compile and run it directly:

```
mvn test-compile
java -cp target/classes:target/test-classes com.fakerevive.tools.NameListGenerator src/main/resources/names.json
```

On Windows, use `;` instead of `:` as the classpath separator.

### Tests

```
mvn test
```

Tests use [MockBukkit](https://github.com/MockBukkit/MockBukkit) to
simulate the server, players, and events.
