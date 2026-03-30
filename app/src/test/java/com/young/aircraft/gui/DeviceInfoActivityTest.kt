package com.young.aircraft.gui

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceInfoActivityTest {

    @Test
    fun `activity launches and displays static info`() {
        // Set mock build values
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 7")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "14")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)

        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvModel = activity.findViewById<TextView>(R.id.tv_device_model)
                val tvAndroid = activity.findViewById<TextView>(R.id.tv_android_version)

                assertEquals("GOOGLE Pixel 7", tvModel.text.toString())
                assertTrue(tvAndroid.text.toString().contains("Android 14"))
                assertTrue(tvAndroid.text.toString().contains("API 34"))
            }
        }
    }

    @Test
    fun `back button finishes activity`() {
        ActivityScenario.launch(DeviceInfoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val btnBack = activity.findViewById<View>(R.id.btn_back)
                btnBack.performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }
}
