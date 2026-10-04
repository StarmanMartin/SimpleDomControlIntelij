# SimpleDomControl IntelliJ

An IntelliJ Platform plugin that adds a **left-side "SDC" tool window** listing all
SimpleDomControl models and controllers of the currently open SDC Django project.

- **Models** — per app: python model file (at the class line), create/edit form classes, and the
  list / detail / form templates.
- **Controllers** — per app: tag name (`<borrow-btn>`), content URL, the `SDCView` python class
  (at the class line), and the JS / SCSS / HTML controller files.

Double-clicking an entry opens the file in the editor at the class definition line.

## How it works

The plugin shells out to the framework's own Django management commands
(see `SimpleDomControl/sdc_core/management/commands/`):

```
python manage.py sdc_get_controller_infos
python manage.py sdc_get_model_infos
```

and renders their JSON output as a tree. Both commands are run with the Django project root as
working directory; they only exist after `python manage.py sdc_init` (the project needs
`Assets/src` and `Assets/libs`).

## Requirements

- PyCharm (developed and compiled against PyCharm Professional 2026.2.3, build 262.*;
  **requires IDE 2026.2 or newer** — classes are JVM 25 bytecode, matching the 2026.2 JBR)
  with an SDC-initialized Django project opened.
- A Python interpreter with Django installed that can run the project's `manage.py`
  (the framework requires Python >= 3.13, Django >= 6.0).

## Configuration

`Settings > Tools > SimpleDomControl`:

- **Python interpreter** — executable used to run the management commands (default: `python3`).
- **manage.py directory** — root of the SDC Django project; leave empty to use the IntelliJ
  project root.

## Build

```bash
./gradlew buildPlugin     # plugin zip in build/distributions/
./gradlew runIde          # sandbox IDE with the plugin installed
```

Install the zip via `Settings > Plugins > ⚙ > Install Plugin from Disk...`.
The plugin only depends on the IntelliJ Platform (`com.intellij.modules.platform`), so the same
zip installs into IntelliJ IDEA, PyCharm Community/Professional, etc.

## Notes

- The tool window refreshes on open and via the refresh button (nothing watches the filesystem,
  so refresh after generating controllers/models with `sdc_cc` / `sdc_new_model`).
- Command errors (e.g. SDC not initialized, wrong interpreter) are shown in the tool window.
