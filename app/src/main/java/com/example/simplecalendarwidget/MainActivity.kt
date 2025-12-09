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
        if (isGranted) {
            statusText.text = getString(R.string.permission_granted)
            grantButton.isEnabled = false
            grantButton.visibility = View.GONE
            lblCalendars.visibility = View.VISIBLE
            calendarContainer.visibility = View.VISIBLE
        } else {
            statusText.text = getString(R.string.permission_required)
            grantButton.isEnabled = true
            grantButton.visibility = View.VISIBLE
            lblCalendars.visibility = View.GONE
            calendarContainer.visibility = View.GONE
        }
    }

    private fun loadCalendars() {
        calendarContainer.removeAllViews()
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val selectedIds = prefs.getStringSet("selected_calendars", null)
        
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
                
                val checkBox = CheckBox(this)
                checkBox.text = name
                checkBox.textSize = 16f
                checkBox.tag = id // Store ID in tag
                checkBox.setPadding(0, 16, 0, 16)
                
                // If first run, check all. Else check if in set.
                checkBox.isChecked = if (isFirstRun) true else selectedIds!!.contains(id)
                
                checkBox.setOnCheckedChangeListener { _, _ ->
                    saveSelectionFromUI()
                }
                
                calendarContainer.addView(checkBox)
            }
        }
        
        // If it was first run, immediately save this "All Checked" state so filtering works correctly
        if (isFirstRun && calendarContainer.childCount > 0) {
            saveSelectionFromUI()
        }
    }

    private fun saveSelectionFromUI() {
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val selectedIds = HashSet<String>()
        
        for (i in 0 until calendarContainer.childCount) {
            val cb = calendarContainer.getChildAt(i) as CheckBox
            if (cb.isChecked) {
                val id = cb.tag as String
                selectedIds.add(id)
            }
        }
        
        prefs.edit().putStringSet("selected_calendars", selectedIds).apply()
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
