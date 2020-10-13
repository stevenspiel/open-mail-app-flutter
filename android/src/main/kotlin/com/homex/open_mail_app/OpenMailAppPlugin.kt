package com.homex.open_mail_app

import android.content.Context
import android.content.Intent
import android.content.pm.LabeledIntent
import android.net.Uri
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat.startActivity
import com.google.gson.Gson
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

class OpenMailAppPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var applicationContext: Context

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Although getFlutterEngine is deprecated we still need to use it for
        // apps not updated to Flutter Android v2 embedding
        channel = MethodChannel(flutterPluginBinding.flutterEngine.dartExecutor, "open_mail_app")
        channel.setMethodCallHandler(this)
        init(flutterPluginBinding.applicationContext)
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "open_mail_app")
            val plugin = OpenMailAppPlugin()
            channel.setMethodCallHandler(plugin)
            plugin.init(registrar.context())
        }
    }

    fun init(context: Context) {
        applicationContext = context
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        if (call.method == "openMailApp") {
            val opened = emailAppIntent(call.argument("to")!!)

            if (opened) {
                result.success(true)
            } else {
                result.success(false)
            }
        } else if (call.method == "openSpecificMailApp" && call.hasArgument("name")) {
            // for android, we can run the same thing for openMailApp and openSpecificMailApp.
            // Let the system determine which email app to open
            val opened = emailAppIntent(call.argument("to")!!)

            if (opened) {
                result.success(true)
            } else {
                result.success(false)
            }
        } else if (call.method == "getMainApps") {
            val apps = getInstalledMailApps()
            val appsJson = Gson().toJson(apps)
            result.success(appsJson)
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun emailAppIntent(to: String?): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:") // only email apps should handle this

        if (to != null) {
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        }

        return if (intent.resolveActivity(applicationContext.packageManager) != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            applicationContext.startActivity(intent)

            true
        } else {
            false
        }
    }

    private fun specificEmailAppIntent(name: String, to: String?): Boolean {
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", to, null))
        val packageManager = applicationContext.packageManager

        val activitiesHandlingEmails = packageManager.queryIntentActivities(emailIntent, 0)
        val activityHandlingEmail = activitiesHandlingEmails.firstOrNull {
            it.loadLabel(packageManager) == name
        } ?: return false

        val firstEmailPackageName = activityHandlingEmail.activityInfo.packageName
        val emailInboxIntent = packageManager.getLaunchIntentForPackage(firstEmailPackageName)
                ?: return false

        emailInboxIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (to != null) {
            val recipients: Array<String> = arrayOf(to)
            emailInboxIntent.putExtra(Intent.EXTRA_EMAIL, recipients)
        }

        applicationContext.startActivity(emailInboxIntent)
        return true
    }

    private fun getInstalledMailApps(): List<App> {
        val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager
        val activitiesHandlingEmails = packageManager.queryIntentActivities(emailIntent, 0)

        return if (activitiesHandlingEmails.isNotEmpty()) {
            val mailApps = mutableListOf<App>()
            for (i in 0 until activitiesHandlingEmails.size) {
                val activityHandlingEmail = activitiesHandlingEmails[i]
                mailApps.add(App(activityHandlingEmail.loadLabel(packageManager).toString()))
            }
            mailApps
        } else {
            emptyList()
        }
    }
}

data class App(val name: String)
