package dev.shutterclick.core

data class ShutterCandidate(
    val id: String, val description: String, val visible: Boolean, val enabled: Boolean,
    val clickable: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int
)

/** A resource ID alone cannot distinguish photo from video. Fail closed on unknown labels. */
object PhotoShutter {
    private val labels = setOf("take photo", "take picture", "capture photo")
    fun select(candidates: List<ShutterCandidate>): ShutterCandidate? =
        candidates.filter {
            it.visible && it.enabled && it.clickable &&
                it.right > it.left && it.bottom > it.top &&
                it.description.trim().lowercase() in labels
        }.singleOrNull()
}
