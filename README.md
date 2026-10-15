# CommandBlocker

Paper plugin by **Failxos**. The admin chooses, per world, which commands players cannot run.
Blocked commands are also hidden from the tab-completion / command suggestions.
Chat can be split into groups of worlds.

- Paper 1.21+, Java 21
- Per-world blocked commands, plus `"*"` for rules that apply to every world
- Wildcards: `"tpa*"` blocks `tpa`, `tpaccept`, `tpahere`, ...
- Whitelist mode: block `"*"` and allow only selected commands in a world
- Chat groups: worlds in one group share chat, other worlds do not see it
- Aliases and namespaced variants are covered (blocking `tp` also blocks `teleport` and `minecraft:tp`)
- Customizable message shown to the player
- Players with `commandblocker.bypass` are not affected (default: OP)
- Live reload with `/cb reload`

## Installation

1. Get `CommandBlocker.jar` (build it yourself, or download it from the **Actions** tab on GitHub: open the latest run and take the `CommandBlocker` artifact) and put it into `plugins/`.
2. Start the server once so `plugins/CommandBlocker/config.yml` is generated.
3. Edit the config and run `/cb reload`.

## Configuration

```yaml
blocked-message: "&cThis command is disabled in this world."

chat-groups:
  enabled: true
  groups:
    main:                    # these worlds share one chat
      - world
      - world_nether
      - world_the_end
    lobby:
      - lobby

worlds:
  "*":                       # applies to every world
    blocked-commands: []
    allowed-commands: []
  world:
    blocked-commands:
      - "tpa*"               # tpa, tpaccept, tpahere, ...
      - "sethome"
  lobby:                     # whitelist mode: only these commands work
    blocked-commands:
      - "*"
    allowed-commands:
      - "spawn"
      - "msg"
      - "help"
```

- Commands are written without the leading slash, ideally in quotes.
- World names must match the folder name of the world.
- `allowed-commands` are exceptions and always win over `blocked-commands`.
- `*` matches any characters (`"tpa*"`, `"*home"`, `"*"`).
- Chat groups: players only see chat from worlds in their own group. A world that is in no group has its own separate chat. Private messages (`/msg`) and the console are not affected.

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| `/commandblocker reload` (alias `/cb`) | `commandblocker.admin` | Reloads the config |
| – | `commandblocker.bypass` | Ignores all command restrictions |
| – | `commandblocker.chat.spy` | Sees chat from all worlds (default: nobody) |

## Building

```
mvn clean package
```

The jar is created at `target/CommandBlocker.jar`.

## Limitations

Blocking works on the first word of a command, so a single subcommand
(e.g. `/gamemode creative` only) cannot be blocked separately.

## Author

Failxos
