package com.muditsahni.documentstore.model.entity.document.type

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InvoiceSerializationTest {
    private val objectMapper = ObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        registerModule(KotlinModule.Builder().build())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Test
    fun `test direct deserialization`() {
        val jsonString = """
            {
              "invoice": {
                "invoice_number": "GC/24-25/3099",
                "billing_date": "22-04-24",
                "place_of_supply": "Delhi",
                "currency_code": "INR",
                "customer": {
                  "name": "SOLIDUM AND STARS GUILD LLP",
                  "billing_address": "GROUND KH/1595/1596/1597/1598/1482, FATEHPURI BERI, NEW DELHI-110074, MOB-882711980",
                  "gst_number": "07AEGFS0831M1ZU"
                },
                "vendor": {
                  "name": "GOYAL & CO.",
                  "address": "1876-A, GURUDWARA ROAD KOTLA MUBARAKPUR NEW DELHI-110003",
                  "gst_number": "07AAVPG1823A1Z1",
                  "bank_details": [
                    {
                      "bank_name": "Kotak Mahindra Bank Current A/c",
                      "account_number": "8111504127",
                      "branch_address": "DEFENCE COLONY, NEW DELHI",
                      "ifsc": "KKBK0004520"
                    }
                  ]
                },
                "billed_amount": {
                  "sub_total": 2692,
                  "total": 3177,
                  "amount_in_words": "INR Three Thousand One Hundred Seventy Seven Only"
                },
                "line_items": [
                  {
                    "description": "Universal Drawer Cupboard Lock 3099 Godrej",
                    "hsn_sac": "8301",
                    "quantity": {
                      "value": 5,
                      "unit": "Pcs."
                    },
                    "rate": 315,
                    "amount": 1575,
                    "taxes": [
                      {
                        "category": "CGST",
                        "rate": 9,
                        "amount": 141.75
                      },
                      {
                        "category": "SGST",
                        "rate": 9,
                        "amount": 141.75
                      }
                    ]
                  },
                  {
                    "description": "Tower Bolt 6\" M",
                    "hsn_sac": "8302",
                    "quantity": {
                      "value": 3,
                      "unit": "Pcs."
                    },
                    "rate": 108,
                    "amount": 324,
                    "taxes": [
                      {
                        "category": "CGST",
                        "rate": 9,
                        "amount": 87.48
                      },
                      {
                        "category": "SGST",
                        "rate": 9,
                        "amount": 87.48
                      }
                    ]
                  },
                  {
                    "description": "Handle 12\" H",
                    "hsn_sac": "8302",
                    "quantity": {
                      "value": 3,
                      "unit": "Pcs."
                    },
                    "rate": 216,
                    "amount": 648
                  },
                  {
                    "description": "Screw 19*6 GYP",
                    "hsn_sac": "7318",
                    "quantity": {
                      "value": 1,
                      "unit": "Pkt"
                    },
                    "rate": 145,
                    "amount": 145,
                    "taxes": [
                      {
                        "category": "CGST",
                        "rate": 9,
                        "amount": 13.05
                      },
                      {
                        "category": "SGST",
                        "rate": 9,
                        "amount": 13.05
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val result = objectMapper.readValue(jsonString, InvoiceWrapper::class.java)

        assertEquals("GC/24-25/3099", result.invoice.invoiceNumber)
        assertEquals("SOLIDUM AND STARS GUILD LLP", result.invoice.customer?.name)
        assertEquals(4, result.invoice.lineItems.size)
        assertEquals(13.05, result.invoice.lineItems[3].taxes[1].amount)
    }
}
