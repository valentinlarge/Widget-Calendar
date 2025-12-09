package com.example.simplecalendarwidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NextEventWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateNextEventWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateNextEventWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_next_event)

    // 1. Check Permission
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        views.setTextViewText(R.id.next_event_title, "Permission Required")
        views.setViewVisibility(R.id.next_event_countdown, View.GONE)
        // Click to open app to ask perm
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.next_event_title, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        return
    }

    // 2. Fetch Next Event (Logic simplified here for single item)
    val nextEvent = getNextEvent(context)

    if (nextEvent != null) {
        views.setTextViewText(R.id.next_event_title, nextEvent.title)
        
        val sdf = SimpleDateFormat("EEE, HH:mm", Locale.getDefault())
        views.setTextViewText(R.id.next_event_time, sdf.format(Date(nextEvent.begin)))

        // 3. Configure Countdown
        // Chronometer needs a base time to count from/to.
        // For Countdown: Set Base = Event Start Time.
        // But Chronometer uses SystemClock.elapsedRealtime() (boot time), not System.currentTimeMillis() (UTC).
        // We must convert.
        
        val timeSinceBoot = SystemClock.elapsedRealtime()
        val currentTime = System.currentTimeMillis()
        val timeDelta = nextEvent.begin - currentTime
        val triggerTime = timeSinceBoot + timeDelta

        views.setChronometer(R.id.next_event_countdown, triggerTime, null, true) // base, format, unused
        views.setChronometerCountDown(R.id.next_event_countdown, true)
        views.setViewVisibility(R.id.next_event_countdown, View.VISIBLE)
        
        // Click to open Calendar event
        val intent = Intent(Intent.ACTION_VIEW).apply {
             data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, nextEvent.id)
             flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, nextEvent.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.next_event_title, pendingIntent)

    } else {
        views.setTextViewText(R.id.next_event_title, "No upcoming events")
        views.setTextViewText(R.id.next_event_time, "")
        views.setViewVisibility(R.id.next_event_countdown, View.GONE)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

// Data class specifically for this widget
data class SingleEvent(val id: Long, val title: String, val begin: Long)

private fun getNextEvent(context: Context): SingleEvent? {
    // Read Settings
    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    val selectedCalendars = prefs.getStringSet("selected_calendars", null)
    val shouldFilter = selectedCalendars != null

    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.CALENDAR_ID // Needed for filter
    )

    // Look from Now + 1 minute (to be safe and strictly future) to +1 Year
    // This strictly excludes any event that has already started.
    val now = System.currentTimeMillis()
    val startRange = now + 60000 // 1 minute in the future
    val endRange = now + 31536000000 // 1 year

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startRange)
    ContentUris.appendId(builder, endRange)
    val uri = builder.build()
    
    // Sort by Begin ASC
    val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

    var event: SingleEvent? = null
    val cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
    
    cursor?.use {
        while (it.moveToNext()) {
            val calId = it.getLong(5).toString()
            
            // FILTER CHECK
            if (shouldFilter && !selectedCalendars!!.contains(calId)) {
                continue // Skip this event as it's from a hidden calendar
            }

            // Found the first valid event!
            val id = it.getLong(0)
            val title = it.getString(1) ?: "No Title"
            val begin = it.getLong(2)
            
            event = SingleEvent(id, title, begin)
            break // We only want the first one, so break immediately
        }
    }
    return event
}
