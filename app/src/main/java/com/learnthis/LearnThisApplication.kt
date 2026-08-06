package com.learnthis

import android.app.Application
import com.learnthis.di.ServiceLocator

class LearnThisApplication : Application() {
 lateinit var serviceLocator: ServiceLocator
 private set

 override fun onCreate() {
 super.onCreate()
 serviceLocator = ServiceLocator.getInstance(this)
 }
}
