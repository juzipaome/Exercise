package com.juzi.lianji

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** UI tests own their in-memory database; never start the real importer or notification workers. */
class RegressionTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
