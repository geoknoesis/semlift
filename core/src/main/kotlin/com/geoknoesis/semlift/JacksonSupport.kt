package io.semlift

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JacksonSupport {
    val jsonMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(KotlinModule.Builder().build())

    val xmlMapper: XmlMapper = XmlMapper(
        JacksonXmlModule().apply {
            setXMLTextElementName("#text")
        }
    ).apply {
        registerModule(KotlinModule.Builder().build())
    }

    val csvMapper: CsvMapper = CsvMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }
}

