package com.example.appcloner

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * App Cloner (Shortcut Edition)
 *
 * Lists every launchable app on the device. Tapping one opens a dialog
 * where you can rename the shortcut and pick an icon (the app's own
 * icon, or one of several built-in presets) before it's pinned to the
 * home screen.
 *
 * Note: this creates a shortcut to an existing app under a custom
 * name/icon — not a sandboxed second instance of the app (true
 * dual-account virtualization is out of scope).
 */
class MainActivity : AppCompatActivity() {

    private data class AppInfo(val label: String, val packageName: String)
    private data class IconOption(val isOriginal: Boolean, val resId: Int = 0)

    private val presetIcons = listOf(
        R.drawable.ic_preset_1,
        R.drawable.ic_preset_2,
        R.drawable.ic_preset_3,
        R.drawable.ic_preset_4,
        R.drawable.ic_preset_5,
        R.drawable.ic_preset_6,
        R.drawable.ic_preset_7,
        R.drawable.ic_preset_8
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // PackageManager.MATCH_ALL together with the <queries> declaration
        // in the manifest ensures every launchable app is returned, not
        // just a filtered subset (Android 11+ package visibility).
        val resolvedApps = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName } // don't list ourselves
            .sortedBy { it.label.lowercase() }

        val listView = findViewById<ListView>(R.id.appListView)
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            resolvedApps.map { it.label }
        )

        listView.setOnItemClickListener { _, _, position, _ ->
            showCustomizeDialog(resolvedApps[position])
        }
    }

    private fun showCustomizeDialog(app: AppInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_customize, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.shortcutNameInput)
        val grid = dialogView.findViewById<GridView>(R.id.iconGrid)
        nameInput.setText(app.label)

        val options = mutableListOf(IconOption(isOriginal = true)).apply {
            addAll(presetIcons.map { IconOption(isOriginal = false, resId = it) })
        }

        var selectedIndex = 0

        val adapter = object : BaseAdapter() {
            override fun getCount() = options.size
            override fun getItem(position: Int) = options[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val cell = convertView ?: layoutInflater.inflate(R.layout.grid_icon_item, parent, false)
                val img = cell.findViewById<ImageView>(R.id.iconImage)
                val option = options[position]

                if (option.isOriginal) {
                    val appIcon = try {
                        packageManager.getApplicationIcon(app.packageName)
                    } catch (e: Exception) {
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_launcher)
                    }
                    img.setImageDrawable(appIcon)
                } else {
                    img.setImageResource(option.resId)
                }

                img.setBackgroundResource(
                    if (position == selectedIndex) R.drawable.icon_cell_bg_selected
                    else R.drawable.icon_cell_bg_normal
                )
                return cell
            }
        }
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            adapter.notifyDataSetChanged()
        }

        AlertDialog.Builder(this)
            .setTitle("Customize shortcut")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val chosenName = nameInput.text.toString().trim().ifEmpty { app.label }
                val chosen = options[selectedIndex]
                val iconBitmap = if (chosen.isOriginal) {
                    appIconToBitmap(app.packageName)
                } else {
                    drawableResToBitmap(chosen.resId)
                }
                createHomeScreenShortcut(app, chosenName, iconBitmap)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createHomeScreenShortcut(app: AppInfo, displayName: String, iconBitmap: Bitmap) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "Can't launch ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.action = Intent.ACTION_MAIN
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val icon = Icon.createWithBitmap(iconBitmap)
                // Unique id per creation so you can make several differently
                // named/iconed shortcuts for the same app.
                val shortcutId = "clone_${app.packageName}_${System.currentTimeMillis()}"
                val shortcut = ShortcutInfo.Builder(this, shortcutId)
                    .setShortLabel(displayName.take(10))
                    .setLongLabel(displayName)
                    .setIcon(icon)
                    .setIntent(launchIntent)
                    .build()
                shortcutManager.requestPinShortcut(shortcut, null)
            } else {
                Toast.makeText(
                    this,
                    "Pinned shortcuts not supported on this launcher",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        } else {
            @Suppress("DEPRECATION")
            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName)
                putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap)
            }
            sendBroadcast(addIntent)
        }

        Toast.makeText(this, "Shortcut requested for $displayName", Toast.LENGTH_SHORT).show()
    }

    private fun appIconToBitmap(packageName: String): Bitmap {
        val drawable = packageManager.getApplicationIcon(packageName)
        return drawableToBitmap(drawable)
    }

    private fun drawableResToBitmap(resId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, resId)!!
        return drawableToBitmap(drawable)
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
