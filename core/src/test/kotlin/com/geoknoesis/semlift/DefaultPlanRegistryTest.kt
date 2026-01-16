package io.semlift

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultPlanRegistryTest {
    @Test
    fun `registers and resolves local plan`() {
        val registry = DefaultPlanRegistry()
        val plan = LiftPlan(context = ContextSpec.Inline("""{ "@context": {} }""".toByteArray()))
        registry.registerPlan("sample", plan)

        val resolved = registry.resolvePlan("sample")
        assertThat(resolved).isEqualTo(plan)
    }

    @Test
    fun `resolves local plan via provider id`() = kotlinx.coroutines.runBlocking {
        val registry = DefaultPlanRegistry()
        val plan = LiftPlan(context = ContextSpec.Inline("""{ "@context": {} }""".toByteArray()))
        registry.registerPlan("sample", plan)

        val resolved = registry.resolve(DefaultPlanRegistry.LOCAL_PROVIDER_ID, "sample")
        assertThat(resolved).isEqualTo(plan)
    }
}

