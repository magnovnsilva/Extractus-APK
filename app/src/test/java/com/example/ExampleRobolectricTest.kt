package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppManagerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Extractus Apk", appName)
  }

  @Test
  fun `verify apk storage directory exists`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = AppManagerRepository(context)
    val dir = repository.getApkStorageDir()
    assertNotNull(dir)
    assertEquals(true, dir.exists())
  }
}
