package io.github.cn_cizhanyue.lyricon.universal.provider

object PlayerBindingPolicy {
    val SPECIAL_BLOCKED = setOf(
        "cn.kuwo.player"
    )

    val BLOCKED: Set<String> = SPECIAL_BLOCKED

    fun isBlocked(packageName: String): Boolean = packageName in BLOCKED

    fun blockReason(packageName: String): String? = when (packageName) {
        in SPECIAL_BLOCKED -> "禁用（需专项适配）"
        else -> null
    }

    fun sanitize(packages: Set<String>): Set<String> = packages.filterNot(::isBlocked).toSet()
}
