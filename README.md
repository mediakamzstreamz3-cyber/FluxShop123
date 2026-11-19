# DonutShop

DonutSMP-style shop for Paper 1.21.11 (Java 21).

## Build
    mvn package
The jar is `target/DonutShop.jar`. Put it in `plugins/`. You also need **Vault** and an economy plugin (e.g. EssentialsX) for the money shops.

## Commands
- `/shop` - main menu, `/shop <menu>` - open one directly, `/shop reload`
- `/shardshop` - shard shop
- `/shards` - your own balance (everyone)
- `/shards <player>`, `/shards give|take|set <player> <amount>`, `/shop reload` - operators only (donutshop.admin)

## Shop controls
Left click = buy, Shift+Left = buy a stack of 64, Right click = sell, Shift+Right = sell all.

## Editing
Everything lives in `plugins/DonutShop/menus/*.yml` (one file per menu, file name = menu id). Prices are in each file. Run `/shop reload` after editing.
Item format: `- {material: STONE, amount: 1, buy: 10, sell: 2}`
Enchants: `enchants: {sharpness: 5, mending: 1}`. Potions: `potion: LEAPING`.
`commands: ["..."]` runs console commands on purchase instead of giving an item (`%player%` = buyer). Use this for SmartSpawners and crate keys in the shard shop.

## Shards and scoreboard
Shards are a built-in currency stored in `shards.yml`. The sidebar scoreboard (money, shards, kills, deaths, playtime) is configured in `config.yml`; set `scoreboard.enabled: false` if another scoreboard plugin is in use.

## GitHub
Pushing to GitHub builds the jar automatically (`.github/workflows/build.yml`). Open the repo's **Actions** tab, click the latest run, and download the **DonutShop** artifact at the bottom. Unzip it to get `DonutShop.jar`.
