# NoTick

NoTick is an entity ticking optimization mod that can significantly improve server performance with a simple philosophy:

If a sheep is 12 chunks away and no one can see it, should it still tick?

Usually, no. Many distant entities do not affect player experience, so NoTick skips their ticking to reduce server load. In practice this has low gameplay impact, but it can change expected behavior for some AFK farms.

NoTick also integrates with both FTB Chunks and Open Parties and Claims. If you want entities to keep ticking normally while you are far away, claim those chunks.

Because this is an invasive optimization, NoTick includes extensive configuration controls. You can disable optimization for specific entities, mods, items, raid behavior, dimensions, and more. A default whitelist is included so critical entities (such as the Ender Dragon, Ghasts, and other gameplay-sensitive entities) continue ticking as expected.

## Commands

Commands require operator permission level 2.

- `/notick` or `/notick status` shows what optimization is doing, the safe player range, active chunk protection, and claim integration status.
- `/notick here` explains the current dimension/chunk protections and whether distant entities there can be skipped.
- `/notick reload` reloads the NoTick config from disk and clears runtime caches.
- `/notick help` lists available commands.

## Supported Builds

- Fabric 1.20.1
- Forge 1.20.1
- Fabric 1.21.1
- NeoForge 1.21.1
- NeoForge 26.1.2
- NeoForge 26.2

## Notes for Packs

NoTick works without extra dependencies. FTB Chunks and Open Parties and Claims are optional integrations used to keep claimed chunks protected from tick skipping.

For large modpacks, whitelist important boss mobs, scripted entities, fake-player-style automation, or whole mod IDs if a mod depends on distant entities ticking normally.
