package com.learnthis

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.learnthis.ui.theme.LearnThisTheme
import com.learnthis.util.NativeBridge

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)

 // Verify native library loads
 try {
 Log.i("MainActivity", "Native version: ${NativeBridge.getNativeVersion()}")
 Log.i("MainActivity", "Native ready: ${NativeBridge.isNativeReady()}")
 } catch (e: UnsatisfiedLinkError) {
 Log.e("MainActivity", "Native library failed to load", e)
 }

 setContent {
 LearnThisTheme {
 MainScreen()
 }
 }
 }
}

@Composable
fun MainScreen() {
 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
 Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
 Text(text = "Learn This")
 }
 }
}
