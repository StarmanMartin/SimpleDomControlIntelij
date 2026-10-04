# AGENTS.md — SimpleDomControlIntelij

IntelliJ Platform plugin giving SDC projects a left tool window with models/controllers.
The parent repo's `AGENTS.md` and `DOCUMENTATION.md` (one level up) describe the framework.

## Data source (verified against SimpleDomControl 0.159.0)

- Data comes from `python manage.py sdc_get_controller_infos` / `sdc_get_model_infos`, run with
  cwd = Django project root. Both print a single JSON document to **stdout** (Django log noise goes
  to stderr); parse defensively (outermost `{...}`), because stdout may carry other lines.
- `sdc_get_controller_infos` fails with exit code 3 unless `Assets/src` and `Assets/libs` exist
  (i.e. only after `sdc_init`). `manage.py` must exist in the working directory.
- Controller JSON: `{"sdc_controller": {app: [{tag_name, name, controller_asset_dir, sdc_view_file,
  sdc_view_file_number, url, js?, scss?, html?}]}}` — file fields are omitted when missing.
- Model JSON: `{"sdc_models": [{name, app, model_file, model_file_line,
  create_form/edit_form: {file, class, line}?, html_detail_template?, html_list_template?,
  html_form_template?}]}` — template values are resolved absolute paths (strings), present only
  when the model declares them and the template loads.

## Environment gotchas

- The `SimpleDomControl` poetry venv in this workspace is **empty** (no Django); the system
  `python3` (asdf 3.13) has Django 6.0.6 and runs the commands fine. Default interpreter `python3`.
- Stack versions are pinned in `build.gradle.kts`: IntelliJ Platform Gradle Plugin 2.19.0,
  Kotlin 2.4.20 (2.4.21 is RC-only on the Gradle portal), Gradle wrapper 9.8.0, dev platform
  `pycharmProfessional("2026.2.3")` (the user's installed Toolbox PyCharm 2026.2.3, build
  262.*, JBR **25**), sinceBuild 262, **no untilBuild** (open-ended so it keeps installing
  after IDE updates).
- **Do not pin** `java { source/targetCompatibility }` or `kotlin { compilerOptions { jvmTarget } }`
  — the platform plugin drives the JVM target from the dev platform's JBR (25 for 2026.2);
  pinning 21 caused "Inconsistent JVM Target Compatibility" (compileJava 21 vs compileKotlin 25).
  Bytecode 25 means the plugin requires IDE 2026.2+; dropping to an older dev platform would
  need the 21 bytecode pinned consistently (foojay resolver in `settings.gradle.kts` provisions
  toolchain JDKs).
- `GRADLE_USER_HOME` may point elsewhere; builds were done with
  `GRADLE_USER_HOME=/tmp/opencode/gradle-home`. First build downloads ~1 GB (the IDE) + JDK 21.
- `buildSearchableOptions` is disabled in `build.gradle.kts` (it launches the IDE).
- `./gradlew build` does **not** produce the distributable in plugin 2.x — use `./gradlew
  buildPlugin` → `build/distributions/SimpleDomControlIntelij-<v>.zip`.
- Gson is bundled with the IntelliJ Platform — no extra dependency for JSON parsing.

## IntelliJ 2024.2 API gotchas (all hit during the first compile)

- `DefaultMutableTreeNode` is in `javax.swing.tree`, **not** `javax.swing` (unhelpful cascade of
  unresolved references everywhere else in the file when the import is wrong).
- Java `void` methods called from Kotlin return `Unit` — `GeneralCommandLine.addParameters(...)`
  is **not** chainable; use `apply { ... }` and chain only the `withXxx(...)` methods (those return
  the command line).
- `ProcessOutput` has no `isSuccess`; check `exitCode != 0 || isTimeout`.
- `Task.Backgroundable.onError(Exception)` is deprecated → use `onFinished()` (EDT, always called)
  and handle both result and error there. `onSuccess()` alone misses errors.
- Services looked up with `project.getService(...)` must be registered — either `<projectService>`
  in `plugin.xml` or a **light service** (`@Service(Service.Level.PROJECT)`). `SdcInfoService`
  lacked both and crashed at runtime with `getService(...) must not be null` on the first tool
  window load (Kotlin deref of the null platform-type result).
- `XmlSerializer.copyInto` does not exist; copy persisted fields manually in `loadState`.
- `javap` is not installed here; to check platform API/icon constants, read the class file with
  python (`zipfile` + regex over `lib/app-client.jar`, e.g. `com/intellij/icons/AllIcons$Nodes.class`).
  Verified icon names: `AllIcons.Nodes.Tag`, `AllIcons.General.Web`, `Nodes.Class/Module/Folder`,
  `FileTypes.Text/Html/JavaScript/Css`, `General.Information`. There is **no** `Nodes.Htmltag` or
  `Nodes.Link`.
- Content of `sdc_get_model_infos` for a model *without* forms/templates omits those keys — Gson
  data classes use nullable fields with defaults throughout.
