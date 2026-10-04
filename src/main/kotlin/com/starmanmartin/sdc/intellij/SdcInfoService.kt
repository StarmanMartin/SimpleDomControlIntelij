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

        @JvmStatic
        fun getInstance(project: Project): SdcInfoService = project.getService(SdcInfoService::class.java)
    }

    fun fetchSnapshot(): SdcSnapshot {
        val settings = SdcSettingsState.getInstance(project)
        val projectRoot = Path.of(requireNotNull(project.basePath) { "Project has no base path" })
        val workDir = settings.resolveProjectDir(projectRoot)
        val python = settings.pythonInterpreter.ifBlank { "python3" }
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

    private fun runCommand(python: String, workDir: Path, command: String): String {
        if (!Files.exists(workDir.resolve("manage.py"))) {
            throw SdcCommandException(
                "manage.py not found in '$workDir'. Configure the manage.py directory in Settings > Tools > SimpleDomControl.",
                "",
            )
        }
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
