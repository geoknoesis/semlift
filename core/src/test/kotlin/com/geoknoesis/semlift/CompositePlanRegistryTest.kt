package io.semlift

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompositePlanRegistryTest {
    @Test
    fun `resolves plan from first matching registry`() = kotlinx.coroutines.runBlocking {
        val plan = LiftPlan(context = ContextSpec.Inline("""{ "@context": {} }""".toByteArray()))
        val first = DefaultPlanRegistry().apply {
            registerPlan("example", plan)
        }
        val second = DefaultPlanRegistry()

        val composite = CompositePlanRegistry(listOf(first, second))

        val resolved = composite.resolve(DefaultPlanRegistry.LOCAL_PROVIDER_ID, "example")
        assertThat(resolved).isEqualTo(plan)
    }
}

