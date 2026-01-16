package io.semlift

interface SemanticLifter {
    suspend fun lift(
        input: InputSource,
        plan: LiftPlan,
        options: LiftOptions = LiftOptions()
    ): LiftResult
}

