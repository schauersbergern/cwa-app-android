package de.rki.coronawarnapp.ccl.configuration.common

import com.fasterxml.jackson.databind.node.TextNode
import com.upokecenter.cbor.CBORObject
import de.rki.coronawarnapp.ccl.configuration.model.CclConfiguration
import de.rki.coronawarnapp.ccl.configuration.model.FunctionDefinition
import de.rki.coronawarnapp.ccl.configuration.model.FunctionParameter
import de.rki.coronawarnapp.ccl.configuration.model.JsonFunctionsDescriptor
import de.rki.coronawarnapp.util.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import org.joda.time.Instant
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CclConfigurationParserTest : BaseTest() {

    private val mapper = SerializationModule.jacksonBaseMapper
    private val parser = CclConfigurationParser(objectMapper = mapper)

    private val json = """
        [
          {
            "Identifier": "CCL-DE-0001",
            "Type": "CCLConfiguration",
            "Country": "DE",
            "Version": "1.0.0",
            "SchemaVersion": "1.0.0",
            "Engine": "JsonFunctions",
            "EngineVersion": "1.0.0",
            "ValidFrom": "2021-10-07T00:00:00Z",
            "ValidTo": "2030-06-01T00:00:00Z",
            "Logic": {
              "JfnDescriptors": [
                {
                  "name": "greet",
                  "definition": {
                    "parameters": [
                      {
                        "name": "greeting",
                        "default": "Hello"
                      },
                      {
                        "name": "name"
                      }
                    ],
                    "logic": [
                      {
                        "return": [
                          {
                            "concatenate": [
                              {
                                "var": "greeting"
                              },
                              " ",
                              {
                                "var": "name"
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
          }
        ]
    """.trimIndent()

    @Test
    fun `check ccl config parsing`() {
        val param1 = FunctionParameter(
            name = "greeting",
            default = TextNode.valueOf("Hello")
        )

        val param2 = FunctionParameter(
            name = "name"
        )

        val jfnLogic = mapper.readTree(
            """
            {
              "return": [
                {
                  "concatenate": [
                    {
                      "var": "greeting"
                    },
                    " ",
                    {
                      "var": "name"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val jfnDescriptor = JsonFunctionsDescriptor(
            name = "greet",
            definition = FunctionDefinition(
                parameters = listOf(param1, param2),
                logic = listOf(jfnLogic)
            )
        )

        with(parser.parseCClConfigurationsJson(json = json).first()) {
            identifier shouldBe "CCL-DE-0001"
            type shouldBe CclConfiguration.Type.CCL_CONFIGURATION
            country shouldBe "DE"
            version shouldBe "1.0.0"
            schemaVersion shouldBe "1.0.0"
            engine shouldBe "JsonFunctions"
            engineVersion shouldBe "1.0.0"
            validFrom shouldBe Instant.parse("2021-10-07T00:00:00Z")
            validTo shouldBe Instant.parse("2030-06-01T00:00:00Z")
            logic shouldBe CclConfiguration.Logic(jfnDescriptors = listOf(jfnDescriptor))
        }
    }

    @Test
    fun `parser creates the same output for raw data and json`() {
        val rawData = CBORObject.FromJSONString(json).EncodeToBytes()

        with(parser) {
            parseCClConfigurations(rawData = rawData) shouldBe parseCClConfigurationsJson(json = json)
        }
    }
}
