package io.semlift

interface DecoderRegistry {
    fun decoderFor(input: InputSource, options: LiftOptions): SyntaxDecoder
}

class DefaultDecoderRegistry(
    private val apiRegistry: ApiProtocolRegistry = DefaultApiProtocolRegistry(),
    private val resolver: ResourceResolver = DefaultResourceResolver()
) : DecoderRegistry {
    override fun decoderFor(input: InputSource, options: LiftOptions): SyntaxDecoder {
        return when (input) {
            is InputSource.Json -> JsonDecoder()
            is InputSource.Xml -> XmlToJsonDecoder(JacksonSupport.xmlMapper)
            is InputSource.Csv -> CsvToJsonDecoder(options.csvInferTypes, JacksonSupport.csvMapper)
            is InputSource.Jdbc -> JdbcToJsonDecoder(options.jdbcFetchSize, JacksonSupport.jsonMapper)
            is InputSource.Api -> ApiProtocolDecoder(apiRegistry, resolver)
            is InputSource.Spark -> error("Spark input requires Spark module decoder registry.")
        }
    }
}

