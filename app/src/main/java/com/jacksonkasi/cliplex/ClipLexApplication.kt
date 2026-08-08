package com.jacksonkasi.cliplex

import android.app.Application
import com.jacksonkasi.cliplex.di.ServiceLocator

class ClipLexApplication : Application() {
 lateinit var serviceLocator: ServiceLocator
 private set

 override fun onCreate() {
 super.onCreate()
 serviceLocator = ServiceLocator.getInstance(this)
 }
}
