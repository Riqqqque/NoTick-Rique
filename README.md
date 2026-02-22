# NoTick

## What Does This Do?
NoTick is an entity ticking optimization mod that can significantly improve server performance with a simple philosophy:

If a sheep is 12 chunks away and no one can see it, should it still tick?

Usually, no. Many distant entities do not affect player experience, so NoTick skips their ticking to reduce server load. In practice this has low gameplay impact, but it can change expected behavior for some AFK farms.

NoTick also integrates with both FTB Chunks and Open Parties and Claims. If you want entities to keep ticking normally while you are far away, just claim those chunks.

Because this is an invasive optimization, NoTick includes extensive configuration controls. You can disable optimization for specific entities, mods, items, raid behavior, dimensions, and more. A default whitelist is included so critical entities (such as the Ender Dragon, Ghasts, and other gameplay-sensitive entities) continue ticking as expected.
