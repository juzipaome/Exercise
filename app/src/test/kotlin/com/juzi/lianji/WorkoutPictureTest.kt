package com.juzi.lianji

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutPictureTest {
    @Test fun notification_picture_decodes_large_and_small_images_without_fallback() {
        for (size in listOf(768,96)) {
            val source=Bitmap.createBitmap(size,size/2,Bitmap.Config.ARGB_8888)
            source.eraseColor(Color.RED)
            val bytes=ByteArrayOutputStream().apply { source.compress(Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
            val picture=decodeWorkoutPicture { bytes.inputStream() }!!
            assertEquals(minOf(size,192),picture.width)
            assertEquals(minOf(size,192)/2,picture.height)
            assertEquals(Color.RED,picture.getPixel(picture.width/2,picture.height/2))
            assertEquals(Color.TRANSPARENT,picture.getPixel(0,0))
        }
        assertNull(decodeWorkoutPicture { byteArrayOf(1,2,3).inputStream() })
    }
}
