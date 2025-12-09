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
import android.os.SystemClock
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
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

// Data class specifically for this widget
data class SingleEvent(val id: Long, val title: String, val begin: Long)

private fun getNextEvent(context: Context): SingleEvent? {
    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val selectedCalendars = prefs.getStringSet(Constants.PREF_SELECTED_CALENDARS, null)
    val shouldFilter = selectedCalendars != null

    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.CALENDAR_ID
    )

    val now = System.currentTimeMillis()
    val startRange = now + 60000
    val endRange = now + 31536000000

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startRange)
    ContentUris.appendId(builder, endRange)
    val uri = builder.build()
    
    val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

    var event: SingleEvent? = null
    val cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
    
    cursor?.use {
        while (it.moveToNext()) {
            val calId = it.getLong(5).toString()
            if (shouldFilter && !selectedCalendars!!.contains(calId)) continue

            val id = it.getLong(0)
            val title = it.getString(1) ?: "No Title"
            val begin = it.getLong(2)
            
            event = SingleEvent(id, title, begin)
            break
        }
    }
    return event
}

internal fun updateNextEventWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_next_event)

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        views.setTextViewText(R.id.next_event_title, "Permission Required")
        views.setViewVisibility(R.id.next_event_countdown, View.GONE)
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.next_event_title, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        return
    }

    val nextEvent = getNextEvent(context)

    if (nextEvent != null) {
        views.setTextViewText(R.id.next_event_title, nextEvent.title)
        
        val sdf = SimpleDateFormat("EEE, HH:mm", Locale.getDefault())
        views.setTextViewText(R.id.next_event_time, sdf.format(Date(nextEvent.begin)))
        
        val timeSinceBoot = SystemClock.elapsedRealtime()
        val currentTime = System.currentTimeMillis()
        val timeDelta = nextEvent.begin - currentTime
        val triggerTime = timeSinceBoot + timeDelta

        views.setChronometer(R.id.next_event_countdown, triggerTime, null, true)
        views.setChronometerCountDown(R.id.next_event_countdown, true)
        views.setViewVisibility(R.id.next_event_countdown, View.VISIBLE)
        
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
