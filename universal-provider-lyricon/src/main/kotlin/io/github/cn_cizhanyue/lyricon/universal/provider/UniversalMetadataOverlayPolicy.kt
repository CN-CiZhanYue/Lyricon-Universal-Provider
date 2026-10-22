package io.github.cn_cizhanyue.lyricon.universal.provider

internal object UniversalMetadataOverlayPolicy {
    private const val UNIVERSAL_REMARK = "[CLLUniversal]"
    private const val UNIVERSAL_PROVIDER =
        "io.github.cn_cizhanyue.lyricon.universal.provider"

    fun shouldPreserveExisting(existingLyricInfo: String?, force: Boolean): Boolean {
        if (force || existingLyricInfo == null) return false
        return !existingLyricInfo.contains(UNIVERSAL_REMARK) &&
            !existingLyricInfo.contains(UNIVERSAL_PROVIDER)
    }
}
