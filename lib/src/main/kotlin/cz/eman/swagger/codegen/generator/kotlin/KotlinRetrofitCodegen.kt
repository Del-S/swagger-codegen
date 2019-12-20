package cz.eman.swagger.codegen.generator.kotlin

import cz.eman.swagger.codegen.language.GENERATE_INFRASTRUCTURE_API
import cz.eman.swagger.codegen.language.INFRASTRUCTURE_CLI
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.parser.util.SchemaTypeUtil
import org.gradle.internal.impldep.com.esotericsoftware.minlog.Log
import org.openapitools.codegen.*
import org.openapitools.codegen.languages.AbstractKotlinCodegen
import org.openapitools.codegen.languages.KotlinClientCodegen
import java.io.File
import java.util.stream.Stream

/**
 * @author eMan s.r.o. (vaclav.souhrada@eman.cz)
 */
open class KotlinRetrofitCodegen : AbstractKotlinCodegen() {

    private var collectionType = CollectionType.ARRAY.value
    private var dateLib = DateLibrary.JAVA8.value
    private val numberDataTypes = arrayOf("kotlin.Short", "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double")

    companion object {
        const val RETROFIT2 = "retrofit2"

        const val DATE_LIBRARY = "dateLibrary"
        const val CLASS_API_SUFFIX = "Service"
        const val COLLECTION_TYPE = "collectionType"

        const val VENDOR_EXTENSION_BASE_NAME_LITERAL = "x-base-name-literal"
    }

    enum class DateLibrary constructor(val value: String) {
        STRING("string"),
        THREETENBP("threetenbp"),
        JAVA8("java8"),
        MILLIS("millis")
    }

    enum class CollectionType(val value: String) {
        ARRAY("array"), LIST("list");
    }

    enum class GenerateApiType constructor(val value: String) {
        INFRASTRUCTURE("infrastructure"),
        API("api")
    }

    enum class DtoSuffix constructor(val value: String) {
        DTO("dto"),
        DEFAULT("default")
    }

    /**
     * Constructs an instance of `KotlinRetrofitCodegen`.
     */
    init {
        enumPropertyNaming = CodegenConstants.ENUM_PROPERTY_NAMING_TYPE.camelCase
        initArtifact()
        initTemplates()
        initSettings()
        initLibraries()
    }

    override fun getTag(): CodegenType {
        return CodegenType.OTHER
    }

    override fun getName(): String {
        return "kotlin-retrofit-client"
    }

    override fun getHelp(): String {
        return "Generates a Kotlin Retrofit2 classes."
    }

    override fun toModelFilename(name: String): String {
        return toModelName(name)
    }

    override fun toModelName(name: String): String {
        val modelName = super.toModelName(name)
        return if (modelName.startsWith("kotlin.") || modelName.startsWith("java.")) {
            modelName
        } else {
            "$modelNamePrefix$modelName$modelNameSuffix"
        }
    }

    override fun toApiName(name: String?): String {
        return super.toApiName(name) + CLASS_API_SUFFIX
    }

    override fun processOpts() {
        super.processOpts()

        if (additionalProperties.containsKey(DATE_LIBRARY)) {
            setDateLibrary(additionalProperties[DATE_LIBRARY].toString())
        }

        when (dateLib) {
            DateLibrary.THREETENBP.value -> {
                additionalProperties[DateLibrary.THREETENBP.value] = true
                typeMapping["date"] = "LocalDate"
                typeMapping["DateTime"] = "LocalDateTime"
                importMapping["LocalDate"] = "org.threeten.bp.LocalDate"
                importMapping["LocalDateTime"] = "org.threeten.bp.LocalDateTime"
                defaultIncludes.add("org.threeten.bp.LocalDateTime")
            }
            DateLibrary.STRING.value -> {
                typeMapping["date-time"] = "kotlin.String"
                typeMapping["date"] = "kotlin.String"
                typeMapping["Date"] = "kotlin.String"
                typeMapping["DateTime"] = "kotlin.String"
            }
            DateLibrary.JAVA8.value -> additionalProperties[DateLibrary.JAVA8.value] = true
            DateLibrary.MILLIS.value -> {
                typeMapping["date-time"] = "kotlin.Long"
                typeMapping["date"] = "kotlin.String"
                typeMapping["Date"] = "kotlin.String"
                typeMapping["DateTime"] = "kotlin.Long"
            }
        }

        supportingFiles.add(SupportingFile("README.mustache", "", "README.md"))
        supportingFiles.add(SupportingFile("build.gradle.mustache", "", "build.gradle"))
        supportingFiles.add(SupportingFile("settings.gradle.mustache", "", "settings.gradle"))

        var generateInfrastructure = true
        if (additionalProperties.containsKey(GENERATE_INFRASTRUCTURE_API)) {
            generateInfrastructure = additionalProperties[GENERATE_INFRASTRUCTURE_API].toString() == "true"
        }

        if (generateInfrastructure) {
            val infrastructureFolder =
                (sourceFolder + File.separator + packageName + File.separator + "infrastructure").replace(".", "/")
            //supportingFiles.add(SupportingFile("infrastructure/ApiClient.kt.mustache", infrastructureFolder, "ApiClient.kt"))
            supportingFiles.add(
                SupportingFile(
                    "infrastructure/ApiAbstractions.kt.mustache",
                    infrastructureFolder,
                    "ApiAbstractions.kt"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "infrastructure/ApiInfrastructureResponse.kt.mustache",
                    infrastructureFolder,
                    "ApiInfrastructureResponse.kt"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "infrastructure/ApplicationDelegates.kt.mustache",
                    infrastructureFolder,
                    "ApplicationDelegates.kt"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "infrastructure/RequestConfig.kt.mustache",
                    infrastructureFolder,
                    "RequestConfig.kt"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "infrastructure/RequestMethod.kt.mustache",
                    infrastructureFolder,
                    "RequestMethod.kt"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "infrastructure/ResponseExtensions.kt.mustache",
                    infrastructureFolder,
                    "ResponseExtensions.kt"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "infrastructure/Serializer.kt.mustache",
                    infrastructureFolder,
                    "Serializer.kt"
                )
            )
            supportingFiles.add(SupportingFile("infrastructure/Errors.kt.mustache", infrastructureFolder, "Errors.kt"))
        }

        if (additionalProperties.containsKey(COLLECTION_TYPE)) {
            setCollectionType(additionalProperties[COLLECTION_TYPE].toString())
        }

        if (CollectionType.LIST.value == collectionType) {
            typeMapping["array"] = "kotlin.collections.List"
            typeMapping["list"] = "kotlin.collections.List"
            additionalProperties["isList"] = true
        }
    }

    override fun fromModel(name: String?, schema: Schema<*>?): CodegenModel {
        fixSchemaType(schema)
        fixEmptyType(schema)
        return super.fromModel(name, schema)
    }

    /**
     * Enum number values are not escaped to String.
     *
     * @since 1.1.0
     */
    override fun toEnumValue(value: String, datatype: String): String {
        return if (datatype in numberDataTypes) {
            value
        } else {
            "\"" + escapeText(value) + "\""
        }
    }

    /**
     * Enum number names are unified with Java code generation.
     *
     * @since 1.1.0
     */
    override fun toEnumVarName(value: String?, datatype: String?): String {
        var name = super.toEnumVarName(value, datatype)
        if (datatype in numberDataTypes) {
            name = "NUMBER$name"
            name = name.replace("-".toRegex(), "MINUS_")
            name = name.replace("\\+".toRegex(), "PLUS_")
            name = name.replace("\\.".toRegex(), "_DOT_")
        }
        return name
    }

    override fun postProcessModels(objs: Map<String?, Any?>): Map<String?, Any?>? {
        val objects = super.postProcessModels(objs)
        val models = objs["models"] as List<*>? ?: emptyList<Any>()
        for (model in models) {
            val mo = model as Map<*, *>
            (mo["model"] as CodegenModel?)?.let {
                // escape the variable base name for use as a string literal
                Stream.of(
                    it.vars,
                    it.allVars,
                    it.optionalVars,
                    it.requiredVars,
                    it.readOnlyVars,
                    it.readWriteVars,
                    it.parentVars
                ).flatMap { obj: List<CodegenProperty> -> obj.stream() }.forEach { property ->
                    property.vendorExtensions[VENDOR_EXTENSION_BASE_NAME_LITERAL] =
                        property.baseName.replace("$", "\\$")
                }
            }
        }
        return objects
    }

    override fun postProcessOperationsWithModels(
        objs: Map<String?, Any?>,
        allModels: List<Any?>?
    ): Map<String, Any>? {
        super.postProcessOperationsWithModels(objs, allModels)
        val operations = objs["operations"] as Map<String, Any>?
        if (operations != null) {
            (operations["operation"] as List<*>?)?.forEach { operation ->
                if (operation is CodegenOperation && operation.hasConsumes == java.lang.Boolean.TRUE) {
                    if (isMultipartType(operation.consumes)) {
                        operation.isMultipart = java.lang.Boolean.TRUE
                    }
                }
            }
        }
        return operations
    }

    private fun isMultipartType(consumes: List<Map<String, String>>): Boolean {
        val firstType = consumes[0]
        return "multipart/form-data" == firstType["mediaType"]
    }

    private fun initArtifact() {
        artifactId = "kotlin-retrofit-client"
        packageName = "cz.eman.swagger"
    }

    private fun initTemplates() {
        outputFolder = "generated-code" + File.separator + "kotlin-retrofit-client"
        modelTemplateFiles["model.mustache"] = ".kt"
        apiTemplateFiles["api.mustache"] = ".kt"
        // TODO parameter if use api with header param or not
        //apiTemplateFiles["api_without_header.mustache"] = ".kt"
        modelDocTemplateFiles["model_doc.mustache"] = ".md"
        apiDocTemplateFiles["api_doc.mustache"] = ".md"
        templateDir = "kotlin-retrofit-client"
        embeddedTemplateDir = templateDir
        apiPackage = "$packageName.api"
        modelPackage = "$packageName.model"
    }

    private fun initSettings() {
        initSettingsDateLibrary()
        initSettingsInfrastructure()
        initSettingsCollectionType()
    }

    private fun initSettingsDateLibrary() {
        val dateLibrary = CliOption(DATE_LIBRARY, "Option. Date library to use")
        val dateOptions = HashMap<String, String>()
        dateOptions[DateLibrary.THREETENBP.value] = "Threetenbp"
        dateOptions[DateLibrary.STRING.value] = "String"
        dateOptions[DateLibrary.JAVA8.value] = "Java 8 native JSR310"
        dateOptions[DateLibrary.MILLIS.value] = "Date Time as Long"
        dateLibrary.enum = dateOptions
        cliOptions.add(dateLibrary)
    }

    private fun initSettingsInfrastructure() {
        val infrastructureCli = CliOption(INFRASTRUCTURE_CLI, "Option to add infrastructure package")
        val infraOptions = HashMap<String, String>()
        infraOptions[GenerateApiType.INFRASTRUCTURE.value] = "Generate Infrastructure API"
        infraOptions[GenerateApiType.API.value] = "Generate API"
        infrastructureCli.enum = infraOptions
        cliOptions.add(infrastructureCli)
    }

    private fun initSettingsCollectionType() {
        val collectionType = CliOption(COLLECTION_TYPE, "Option. Collection type to use")
        val collectionOptions: MutableMap<String, String> = java.util.HashMap()
        collectionOptions[CollectionType.ARRAY.value] = "kotlin.Array"
        collectionOptions[CollectionType.LIST.value] = "kotlin.collections.List"
        collectionType.enum = collectionOptions
        collectionType.default = this.collectionType
        cliOptions.add(collectionType)
    }

    private fun initLibraries() {
        supportedLibraries[RETROFIT2] =
            "[DEFAULT] Platform: Retrofit2. HTTP client: OkHttp 3.2.0+ (Android 2.3+ and Java 7+). JSON processing: Moshi 1.5.0+."

        val libraryOption = CliOption(CodegenConstants.LIBRARY, "Library template (sub-template) to use")
        libraryOption.enum = supportedLibraries
        libraryOption.default = RETROFIT2
        cliOptions.add(libraryOption)
        setLibrary(RETROFIT2)
    }

    private fun setDateLibrary(library: String) {
        this.dateLib = library
    }

    private fun setCollectionType(collectionType: String) {
        this.collectionType = collectionType
    }

    //    /**
//     * Kotlin data classes cannot be without value. This functions adds ignore value to the class
//     * to make sure it compiles. Make sure to check the schema definition.
//     *
//     * @param schema to be checked
//     * @since 1.1.0
//     */
//    private fun fixEmptyType(schema: Schema<*>?) {
//        schema?.let {
//            if (it !is ArraySchema && it !is MapSchema && it !is ComposedSchema && (it.properties == null || it.properties.isEmpty())) {
//                it.properties = java.util.HashMap<String, Schema<String>>().apply { put("ignore", StringSchema().apply { description("No values defined for this class. Please check schema definition for this class.") }) }.toMap()
//            }
//        }
//    }

    /**
     * Fixes schemas that do not have set type. Not having a type would make them empty data classes
     * that will not pass compilation.
     *
     * TODO: add option to cast empty data class as String (json)
     *
     * @param schema to be checked
     * @since 1.1.0
     */
    private fun fixEmptyType(schema: Schema<*>?) {
        schema?.let {
            if (it !is ArraySchema && it !is MapSchema && it !is ComposedSchema && (it.type == null || it.type.isEmpty())) {
                it.type = "string"
            }
        }
    }

    /**
     * Fixes type for schema. There is an issue where type [SchemaTypeUtil.INTEGER_TYPE] with format
     * [SchemaTypeUtil.INTEGER32_FORMAT] is wrongly represented as [SchemaTypeUtil.NUMBER_TYPE].
     *
     * @since 1.1.0
     */
    private fun fixSchemaType(schema: Schema<*>?) {
        schema?.let {
            if (it is IntegerSchema && it.type == SchemaTypeUtil.NUMBER_TYPE && it.format == SchemaTypeUtil.INTEGER32_FORMAT) {
                it.type = SchemaTypeUtil.INTEGER_TYPE
            }
        }
    }

}