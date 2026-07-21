# Permissions

All permissions are declared in `plugin.yml`. `orthodoxicons.use` is granted to
everyone by default; administrative nodes default to operators only.

| Permission              | Default | Description                                                        |
|-------------------------|---------|--------------------------------------------------------------------|
| `orthodoxicons.use`     | `true`  | Open the browser, search, view info, get random icons, see stats.  |
| `orthodoxicons.place`   | `true`  | Place and remove icons on walls.                                   |
| `orthodoxicons.admin`   | `op`    | Umbrella node; grants all admin sub-nodes and provider listing.    |
| `orthodoxicons.reload`  | `op`    | Run `/icons reload`.                                               |
| `orthodoxicons.update`  | `op`    | Run `/icons update` to fetch new/changed icons.                    |
| `orthodoxicons.give`    | `op`    | Run `/icons give <player> <id> [amount]`.                          |
| `orthodoxicons.cache`   | `op`    | Run `/icons cache <info\|clean\|clear>`.                           |
| `orthodoxicons.debug`   | `op`    | Toggle debug logging with `/icons debug`.                          |

## Command → permission mapping

| Command             | Required permission     |
|---------------------|-------------------------|
| `/icons`            | `orthodoxicons.use`     |
| `/icons browse`     | `orthodoxicons.use`     |
| `/icons search`     | `orthodoxicons.use`     |
| `/icons random`     | `orthodoxicons.use`     |
| `/icons info`       | `orthodoxicons.use`     |
| `/icons stats`      | `orthodoxicons.use`     |
| `/icons version`    | *(none)*                |
| `/icons give`       | `orthodoxicons.give`    |
| `/icons reload`     | `orthodoxicons.reload`  |
| `/icons update`     | `orthodoxicons.update`  |
| `/icons cache`      | `orthodoxicons.cache`   |
| `/icons provider`   | `orthodoxicons.admin`   |
| `/icons debug`      | `orthodoxicons.debug`   |

## Inheritance

`orthodoxicons.admin` is configured with `children` in `plugin.yml` so granting
it automatically grants `reload`, `update`, `give`, `cache`, and `debug`.
