package com.starmanmartin.sdc.intellij

import com.google.gson.annotations.SerializedName

/**
 * JSON shapes produced by the Django management commands
 * `sdc_get_model_infos` and `sdc_get_controller_infos`
 * (see SimpleDomControl/sdc_core/management/commands/).
 */
data class SdcFormInfo(
    val file: String? = null,
    @SerializedName("class") val className: String? = null,
    val line: Int? = null,
)

data class SdcModelInfo(
    val name: String? = null,
    val app: String? = null,
    @SerializedName("model_file") val modelFile: String? = null,
    @SerializedName("model_file_line") val modelFileLine: Int? = null,
    @SerializedName("create_form") val createForm: SdcFormInfo? = null,
    @SerializedName("edit_form") val editForm: SdcFormInfo? = null,
    @SerializedName("html_detail_template") val htmlDetailTemplate: String? = null,
    @SerializedName("html_list_template") val htmlListTemplate: String? = null,
    @SerializedName("html_form_template") val htmlFormTemplate: String? = null,
)

data class SdcControllerInfo(
    @SerializedName("tag_name") val tagName: String? = null,
    val name: String? = null,
    @SerializedName("controller_asset_dir") val controllerAssetDir: String? = null,
    @SerializedName("sdc_view_file") val sdcViewFile: String? = null,
    @SerializedName("sdc_view_file_number") val sdcViewFileNumber: Int? = null,
    val url: String? = null,
    val js: String? = null,
    val scss: String? = null,
    val html: String? = null,
)

data class SdcControllersResult(
    @SerializedName("sdc_controller") val controllers: Map<String, List<SdcControllerInfo>> = emptyMap(),
)

data class SdcModelsResult(
    @SerializedName("sdc_models") val models: List<SdcModelInfo> = emptyList(),
)

/** Combined result of both commands. [errors] holds per-command failures while keeping partial data. */
data class SdcSnapshot(
    val controllers: Map<String, List<SdcControllerInfo>> = emptyMap(),
    val models: List<SdcModelInfo> = emptyList(),
    val errors: List<String> = emptyList(),
)
