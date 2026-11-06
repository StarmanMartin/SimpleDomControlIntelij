package com.starmanmartin.sdc.intellij

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.starmanmartin.sdc.intellij.settings.SdcSettingsState
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Thrown when a Django command cannot be run or returns no JSON. */
class SdcCommandException(message: String, val output: String) : RuntimeException(message)

/**
 * Runs `python manage.py sdc_get_controller_infos` / `sdc_get_model_infos`
 * in the SDC-initialized Django project and parses their JSON output.
 *
 * Blocking calls — invoke from a background thread only.
 */
@Service(Service.Level.PROJECT)
class SdcInfoService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(SdcInfoService::class.java)
        private val GSON = Gson()
        private const val TIMEOUT_MS = 60_000
        private const val PYTHON_ENV_FILE = ".sdc_python_env"

        @JvmStatic
        fun getInstance(project: Project): SdcInfoService = project.getService(SdcInfoService::class.java)
    }

    fun fetchSnapshot(): SdcSnapshot {
        val settings = SdcSettingsState.getInstance(project)
        val projectRoot = Path.of(requireNotNull(project.basePath) { "Project has no base path" })
        val workDir = settings.resolveProjectDir(projectRoot)
        requireManagePy(workDir)
        val python = resolvePython(workDir)
        val errors = mutableListOf<String>()
        var controllers: Map<String, List<SdcControllerInfo>> = emptyMap()
        var models: List<SdcModelInfo> = emptyList()

        try {
            val json = runCommand(python, workDir, "sdc_get_controller_infos")
            controllers = GSON.fromJson(json, SdcControllersResult::class.java)?.controllers ?: emptyMap()
        } catch (e: Exception) {
            LOG.warn("sdc_get_controller_infos failed", e)
            errors.add("sdc_get_controller_infos: ${e.message}")
        }
        try {
            val json = runCommand(python, workDir, "sdc_get_model_infos")
            models = GSON.fromJson(json, SdcModelsResult::class.java)?.models ?: emptyList()
        } catch (e: Exception) {
            LOG.warn("sdc_get_model_infos failed", e)
            errors.add("sdc_get_model_infos: ${e.message}")
        }

        if (controllers.isEmpty() && models.isEmpty() && errors.isNotEmpty()) {
            throw SdcCommandException(errors.joinToString("\n\n"), "")
        }
        return SdcSnapshot(controllers, models, errors)
    }

    private fun requireManagePy(workDir: Path) {
        if (!Files.exists(workDir.resolve("manage.py"))) {
            throw SdcCommandException(
                "manage.py not found in '$workDir'. Configure the manage.py directory in Settings > Tools > SimpleDomControl.",
                "",
            )
        }
    }

    /**
     * The interpreter comes from `Assets/.sdc_python_env` (`PYTHON="/path/to/python"`), which
     * sdc_core writes with `sys.executable` whenever Django starts (e.g. `manage.py runserver`).
     */
    private fun resolvePython(workDir: Path): String {
        val envFile = workDir.resolve("Assets").resolve(PYTHON_ENV_FILE)
        val hint = "Run the server once (python manage.py runserver, even if it fails to start) " +
                "with the project's interpreter, so that SDC writes Assets/$PYTHON_ENV_FILE. Then refresh."
        if (!Files.isRegularFile(envFile)) {
            throw SdcCommandException("'$envFile' not found. $hint", "")
        }
        val python = try {
            parsePythonEnv(Files.readAllLines(envFile, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw SdcCommandException("Cannot read '$envFile': ${e.message}. $hint", "")
        }
        if (python.isNullOrBlank()) {
            throw SdcCommandException("'$envFile' does not define PYTHON. $hint", "")
        }
        if (!Files.isExecutable(Path.of(python))) {
            throw SdcCommandException(
                "Python interpreter '$python' from '$envFile' does not exist or is not executable. $hint",
                "",
            )
        }
        return python
    }

    private fun parsePythonEnv(lines: List<String>): String? =
        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.removePrefix("export ").trim() }
            .filter { it.substringBefore('=').trim() == "PYTHON" }
            .map { it.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'").trim() }
            .lastOrNull()

    private fun runCommand(python: String, workDir: Path, command: String): String {
        val commandLine = GeneralCommandLine(python).apply {
            addParameters("manage.py", command)
            withWorkDirectory(workDir.toFile())
            withEnvironment("PYTHONIOENCODING", "utf-8")
            charset = StandardCharsets.UTF_8
        }
        val output = ExecUtil.execAndGetOutput(commandLine, TIMEOUT_MS)
        if (output.exitCode != 0 || output.isTimeout) {
            throw SdcCommandException(
                "'$command' failed with exit code ${output.exitCode}",
                output.stderr.ifBlank { output.stdout },
            )
        }
        return extractJson(output.stdout)
    }

    /**
     * The commands write the JSON document to stdout, but other output (e.g. logging
     * configuration warnings) may precede it. Take the outermost braces block.
     */
    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw SdcCommandException("No JSON found in command output", text)
        }
        return text.substring(start, end + 1)
    }
}
