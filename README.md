# NgaRecipes 2.0

Paper 1.21.x plugin: a Java port of the Skript "Recipe Showcase" plus real crafting recipes.

## Build
    mvn package          # -> target/NgaRecipes.jar  (needs JDK 21)
Drop the jar in `plugins/` (replace the old NgaRecipes.jar; old `recipes.yml` format is not read).

## Commands
| Command | Permission | What it does |
|---|---|---|
| `/recipes` (`/recipe`, `/ngarecipes`) | `recipes.use` (default: everyone) | Paged showcase (45 per page) -> click an icon to view the 3x3 layout, prev/next recipe |
| `/recipes reload` | `recipes.admin` | Re-read config.yml + recipes.yml |
| `/addrecipe` | `recipes.admin` | Editor: drag ingredients into the 3x3 grid, the result into the result slot, click SAVE |
| `/editrecipe <id\|item>` | `recipes.admin` | Same editor, pre-filled. e.g. `/editrecipe apple` finds the recipe that outputs an apple (if several match you get a clickable list) |
| `/delrecipe <id\|item>` | `recipes.admin` | Confirm/cancel GUI, same lookup |

The editor has a **Mode** button (crafting table / item frame). Per recipe you choose:
* **Working** - also a real, craftable shaped recipe
* **Showcase only** - only displayed in `/recipes`, not craftable

The choice is saved per recipe (`working:` in recipes.yml; old recipes default to Working). `/recipes` and the viewer show each recipe's mode.

Editor items are always handed back on close (Save, Cancel, Esc). When editing, the pre-filled
items are display-only copies and disappear on close, so editing never duplicates items.

## config.yml
* `register-crafting-recipes` - master switch. If true, recipes set to *Working* are registered as real shaped crafting recipes (exact item match). If false, nothing is craftable regardless of per-recipe mode.
* `titles.*` - GUI titles, MiniMessage format.

Recipes are stored in `plugins/NgaRecipes/recipes.yml`.
