package dev.shutterclick.core

import org.junit.Test
import kotlin.test.*

class PhotoShutterTest {
    private val photo = ShutterCandidate("camera:shutter", "Take photo", true, true, true, 100, 200, 180, 280)
    @Test fun selectsTheUniqueVisiblePhotoAction() { assertEquals(photo, PhotoShutter.select(listOf(photo))) }
    @Test fun videoActionWithSameResourceIdIsRejected() {
        assertNull(PhotoShutter.select(listOf(photo.copy(description = "Start video"))))
    }
    @Test fun genericShutterLabelDoesNotProvePhotoMode() {
        assertNull(PhotoShutter.select(listOf(photo.copy(description = "Shutter"))))
    }
    @Test fun duplicatedOrUnavailableControlsAreRejected() {
        assertNull(PhotoShutter.select(listOf(photo, photo.copy(left = 300, right = 380))))
        assertNull(PhotoShutter.select(listOf(photo.copy(visible = false))))
        assertNull(PhotoShutter.select(listOf(photo.copy(enabled = false))))
        assertNull(PhotoShutter.select(listOf(photo.copy(clickable = false))))
        assertNull(PhotoShutter.select(listOf(photo.copy(right = 100))))
    }
}
