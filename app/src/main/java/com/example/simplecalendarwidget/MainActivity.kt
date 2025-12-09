package com.example.simplecalendarwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                updateUI(true)
                loadCalendars()
                sendWidgetRefresh()
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_LONG).show()
            } else {
                updateUI(false)
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            }
        }

    private lateinit var statusText: TextView
    private lateinit var grantButton: Button
    private lateinit var calendarContainer: LinearLayout
    private lateinit var lblCalendars: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        grantButton = findViewById(R.id.grant_button)
        calendarContainer = findViewById(R.id.calendar_list_container)
        lblCalendars = findViewById(R.id.lbl_calendars)

        grantButton.setOnClickListener {
            checkAndRequestPermission()
        }

        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED -> {
                updateUI(true)
                loadCalendars()
                sendWidgetRefresh()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    private fun updateUI(isGranted: Boolean) {
        val visibility = if (isGranted) View.VISIBLE else View.GONE
        
        if (isGranted) {
            statusText.text = getString(R.string.permission_granted)
            grantButton.visibility = View.GONE
        } else {
            statusText.text = getString(R.string.permission_required)
            grantButton.isEnabled = true
            grantButton.visibility = View.VISIBLE
        }
        
        lblCalendars.visibility = visibility
        calendarContainer.visibility = visibility
    }

    private fun loadCalendars() {
        calendarContainer.removeAllViews()
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedIds = prefs.getStringSet(Constants.PREF_SELECTED_CALENDARS, null)
        
        // If null, it means first run, so select all by default.
        val isFirstRun = selectedIds == null

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0).toString()
                val name = it.getString(1) ?: "Unknown"
                
                val checkBox = CheckBox(this).apply {
                    text = name
                    textSize = 16f
                    tag = id
                    setPadding(0, 16, 0, 16)
                    isChecked = if (isFirstRun) true else selectedIds!!.contains(id)
                    setOnCheckedChangeListener { _, _ -> saveSelectionFromUI() }
                }
                
                calendarContainer.addView(checkBox)
            }
        }
        
        if (isFirstRun && calendarContainer.childCount > 0) {
            saveSelectionFromUI()
        }
    }

    private fun saveSelectionFromUI() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedIds = HashSet<String>()
        
        for (i in 0 until calendarContainer.childCount) {
            val cb = calendarContainer.getChildAt(i) as CheckBox
            if (cb.isChecked) {
                selectedIds.add(cb.tag as String)
            }
        }
        
        prefs.edit().putStringSet(Constants.PREF_SELECTED_CALENDARS, selectedIds).apply()
        sendWidgetRefresh()
    }

    private fun sendWidgetRefresh() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        
        // Refresh List Widget
        val listWidget = ComponentName(this, CalendarWidget::class.java)
        val listIds = appWidgetManager.getAppWidgetIds(listWidget)
        if (listIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(listIds, R.id.widget_list_view)
        }
        
        // Refresh Next Event Widget
        val nextWidget = ComponentName(this, NextEventWidget::class.java)
        val nextIds = appWidgetManager.getAppWidgetIds(nextWidget)
        if (nextIds.isNotEmpty()) {
            val intent = Intent(this, NextEventWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, nextIds)
            sendBroadcast(intent)
        }
    }
}
