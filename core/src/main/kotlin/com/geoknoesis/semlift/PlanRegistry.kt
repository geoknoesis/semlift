package io.semlift

interface PlanProvider {
    val id: String
    suspend fun resolve(identifier: String): LiftPlan
}

interface PlanResolver {
    suspend fun resolve(providerId: String, identifier: String): LiftPlan
}

class PlanRegistry(
    private val providers: List<PlanProvider>
) : PlanResolver {
    override suspend fun resolve(providerId: String, identifier: String): LiftPlan {
        val provider = providers.firstOrNull { it.id == providerId }
            ?: error("Unknown plan provider: $providerId")
        return provider.resolve(identifier)
    }
}

class DefaultPlanRegistry(
    providers: List<PlanProvider> = emptyList()
) : PlanResolver {
    private val providerMap = mutableMapOf<String, PlanProvider>()
    private val planMap = mutableMapOf<String, LiftPlan>()

    init {
        providers.forEach { registerProvider(it) }
    }

    fun registerProvider(provider: PlanProvider) {
        providerMap[provider.id] = provider
    }

    fun registerPlan(id: String, plan: LiftPlan) {
        planMap[id] = plan
    }

    override suspend fun resolve(providerId: String, identifier: String): LiftPlan {
        if (providerId == LOCAL_PROVIDER_ID) {
            return resolvePlan(identifier)
        }
        val provider = providerMap[providerId] ?: error("Unknown plan provider: $providerId")
        return provider.resolve(identifier)
    }

    fun resolvePlan(id: String): LiftPlan {
        return planMap[id] ?: error("Unknown plan id: $id")
    }

    companion object {
        const val LOCAL_PROVIDER_ID = "local"
    }
}

class CompositePlanRegistry(
    private val registries: List<PlanResolver>
) : PlanResolver {
    override suspend fun resolve(providerId: String, identifier: String): LiftPlan {
        var lastError: Throwable? = null
        for (registry in registries) {
            try {
                return registry.resolve(providerId, identifier)
            } catch (err: IllegalStateException) {
                lastError = err
            }
        }
        throw lastError ?: error("Unknown plan provider: $providerId")
    }
}

