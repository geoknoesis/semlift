package io.semlift

fun liftPlan(block: LiftPlanBuilder.() -> Unit): LiftPlan {
    return LiftPlanBuilder().apply(block).build()
}

class LiftPlanBuilder {
    private var context: ContextSpec? = null
    private val pre = mutableListOf<PreStep>()
    private val post = mutableListOf<PostStep>()
    private val idRules = mutableListOf<IdRule>()
    private var input: InputSpec? = null
    private val imports = mutableListOf<PlanImport>()
    private var metadata: PlanMetadata? = null

    fun contextInline(jsonLd: ByteArray) {
        context = ContextSpec.Inline(jsonLd)
    }

    fun context(uri: String) {
        context = ContextSpec.Resolved(uri)
    }

    fun pre(step: PreStep) {
        pre += step
    }

    fun post(step: PostStep) {
        post += step
    }

    fun idRule(rule: IdRule) {
        idRules += rule
    }

    fun idRules(rules: List<IdRule>) {
        idRules += rules
    }

    fun input(spec: InputSpec) {
        input = spec
    }

    fun metadata(info: PlanMetadata) {
        metadata = info
    }

    fun imports(items: List<PlanImport>) {
        imports += items
    }

    fun import(item: PlanImport) {
        imports += item
    }

    fun build(): LiftPlan {
        return LiftPlan(
            context = requireNotNull(context) { "Context is required" },
            pre = pre.toList(),
            post = post.toList(),
            idRules = idRules.toList(),
            input = input,
            imports = imports.toList(),
            metadata = metadata
        )
    }
}

