package org.example.project.data.settings

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.data.firestore.FirestoreRestClient
import org.example.project.model.BudgetPlan

class UserSettingsStoreTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun store(reply: String) = UserSettingsStore(
        FirestoreRestClient(
            projectId = { "p" },
            idToken = { "t" },
            http = HttpClient(MockEngine { request ->
                requests += request
                respond(reply, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
        )
    )

    @Test
    fun loadsOnePlanPerCutOffDocument() = runTest {
        val plans = store(
            """{"documents":[
               |{"name":"x/users/u1/budgets/2026-09-1","fields":{"total":{"doubleValue":20000},"Food":{"doubleValue":8000},"updatedAt":{"integerValue":"1"}}},
               |{"name":"x/users/u1/budgets/2026-09-2","fields":{"total":{"integerValue":"0"},"Bills":{"integerValue":"5000"},"updatedAt":{"integerValue":"2"}}}
               |]}""".trimMargin()
        ).loadBudgetPlans("u1")
        assertEquals(BudgetPlan(total = 20_000.0, byBucket = mapOf("Food" to 8_000.0)), plans["2026-09-1"])
        assertEquals(BudgetPlan(total = 0.0, byBucket = mapOf("Bills" to 5_000.0)), plans["2026-09-2"])
        assertTrue(requests.single().url.toString().contains("/documents/users/u1/budgets"))
    }

    @Test
    fun savesToTheCutOffsOwnDocument() = runTest {
        store("{}").saveBudgetPlan("u1", "2026-09-2", BudgetPlan(total = 15_000.0, byBucket = mapOf("Food" to 8_000.0)))
        val request = requests.single()
        assertTrue(request.url.toString().endsWith("/documents/users/u1/budgets/2026-09-2"))
        val body = (request.body as TextContent).text
        assertTrue(""""total":{"doubleValue":15000.0}""" in body, body)
        assertTrue(""""Food":{"doubleValue":8000.0}""" in body, body)
        assertTrue(""""updatedAt":{"integerValue"""" in body, body)
    }

    @Test
    fun legacyMonthlyBudgetIsStillReadableForTheFirstSuggestion() = runTest {
        val legacy = store(
            """{"fields":{"totalMonthly":{"doubleValue":40000},"Food":{"doubleValue":8000},"updatedAt":{"integerValue":"1"}}}"""
        ).loadLegacyMonthlyPlan("u1")
        assertEquals(BudgetPlan(total = 40_000.0, byBucket = mapOf("Food" to 8_000.0)), legacy)
    }
}
