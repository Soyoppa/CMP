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
    fun loadSeparatesOverallTotalFromBuckets() = runTest {
        val plan = store(
            """{"fields":{"totalMonthly":{"doubleValue":40000},"Food":{"doubleValue":8000},
               |"Bills":{"integerValue":"5000"},"updatedAt":{"integerValue":"1"}}}""".trimMargin()
        ).loadBudgetPlan("u1")
        assertEquals(40_000.0, plan.totalMonthly)
        assertEquals(mapOf("Food" to 8_000.0, "Bills" to 5_000.0), plan.byBucket)
    }

    @Test
    fun saveWritesTotalAlongsideBuckets() = runTest {
        store("{}").saveBudgetPlan("u1", BudgetPlan(totalMonthly = 30_000.0, byBucket = mapOf("Food" to 8_000.0)))
        val body = (requests.single().body as TextContent).text
        assertTrue(""""totalMonthly":{"doubleValue":30000.0}""" in body, body)
        assertTrue(""""Food":{"doubleValue":8000.0}""" in body, body)
        assertTrue(""""updatedAt":{"integerValue"""" in body, body)
    }
}
