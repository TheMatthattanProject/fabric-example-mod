# Fabric Example Mod

## HAMMER (Orbital Weapon)

Adds a `HAMMER Designator` item. Hold right-click to designate a target (reticle + ground marker), then release to trigger a server-driven orbital strike sequence.

**Dev command**

- `/hammer fire <x> <y> <z>` (requires permission level 2)
- `/hammer_preview [<x> <y> <z>]` (plays full sequence with no damage)

**Config (JVM system properties)**

- `-Dmodid.hammer.blockDamage=true|false`
- `-Dmodid.hammer.blockDamageMode=none|scorch|crust`
- `-Dmodid.hammer.fireSpread=true|false`
- `-Dmodid.hammer.maxRadius=<blocks>`
- `-Dmodid.hammer.maxDuration=<ticks>`
- `-Dmodid.hammer.acquireTicks=<ticks> -Dmodid.hammer.alignTicks=<ticks> -Dmodid.hammer.warningTicks=<ticks> -Dmodid.hammer.afterTicks=<ticks>`
- `-Dmodid.hammer.cooldownTicks=<ticks>`
- Client VFX: `-Dmodid.hammer.clientFx=true|false` + `clientFx*` toggles/budgets
- Optional ammo: `-Dmodid.hammer.ammoItem=minecraft:nether_star -Dmodid.hammer.ammoCost=1`

## Setup

For setup instructions please see the [fabric documentation page](https://docs.fabricmc.net/develop/getting-started/setting-up) that relates to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
