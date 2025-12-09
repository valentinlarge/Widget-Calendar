package com.example.simplecalendarwidget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarRemoteViewsFactory(this.applicationContext)
    }
}

class CalendarRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    data class CalendarEvent(
        val id: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val isHeader: Boolean = false,
        val headerText: String = ""
    )

    private var eventList: List<CalendarEvent> = ArrayList()

    override fun onCreate() {
    }

    override fun onDataSetChanged() {
        // 1. Check Permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            eventList = listOf(CalendarEvent(-1, "Permission Needed", 0, 0, false))
            return
        }

        // 2. Try Fetching and Grouping
        try {
            val rawEvents = fetchEvents()
            if (rawEvents.isEmpty()) {
                eventList = emptyList() 
            } else {
                eventList = groupEventsByDay(rawEvents)
            }
        } catch (e: Exception) {
            eventList = listOf(CalendarEvent(-1, "Error: ${e.message}", 0, 0, false))
        }
    }
    
    private fun groupEventsByDay(events: List<CalendarEvent>): List<CalendarEvent> {
        val groupedList = ArrayList<CalendarEvent>()
        val cal = Calendar.getInstance()
        val now = Calendar.getInstance()
        var lastDayOfYear = -1
        
        for (event in events) {
            cal.timeInMillis = event.begin
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val year = cal.get(Calendar.YEAR)
            
            // If day changed (or first item), insert a Header
            if (dayOfYear != lastDayOfYear) {
                val headerTitle = getHeaderTitle(cal, now)
                groupedList.add(CalendarEvent(-1, "", event.begin, 0, false, true, headerTitle))
                lastDayOfYear = dayOfYear
            }
            groupedList.add(event)
        }
        return groupedList
    }

    private fun getHeaderTitle(eventCal: Calendar, now: Calendar): String {
        val sdfDay = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        
        return when {
            eventCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            eventCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
            
            eventCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            eventCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) + 1 -> "Tomorrow"
            
            else -> sdfDay.format(eventCal.time)
        }
    }

    private fun fetchEvents(): List<CalendarEvent> {
        val events = ArrayList<CalendarEvent>()
        
        // Read Settings
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val selectedCalendars = prefs.getStringSet("selected_calendars", null)
        
        // If selectedCalendars is NOT null, we must filter. 
        // If it is null, it means MainActivity hasn't saved anything yet, so we assume "Show All".
        val shouldFilter = selectedCalendars != null

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID // Needed for filtering
        )

        // Range: Now - 1 Hour to Now + 30 Days
        val now = System.currentTimeMillis()
        val startRange = now - 3600000 
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(Calendar.DAY_OF_YEAR, 30)
        val endOfRange = calendar.timeInMillis
        
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startRange)
        ContentUris.appendId(builder, endOfRange)
        val uri = builder.build()
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(5).toString()
                    
                    // FILTERING LOGIC
                    if (shouldFilter && !selectedCalendars!!.contains(calId)) {
                        continue // Skip this event
                    }

                    val id = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "No Title"
                    val begin = cursor.getLong(2)
                    val end = cursor.getLong(3)
                    val allDay = cursor.getInt(4) == 1
                    events.add(CalendarEvent(id, title, begin, end, allDay))
                }
            }
        } catch (e: Exception) {
            Log.e("WidgetService", "Error", e)
        } finally {
            cursor?.close()
        }
        return events
    }

    override fun onDestroy() { eventList = emptyList() }
    override fun getCount(): Int { return eventList.size }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= eventList.size) return RemoteViews(context.packageName, R.layout.widget_item)

        val item = eventList[position]
        
        // --- RENDER HEADER ---
        if (item.isHeader) {
            val views = RemoteViews(context.packageName, R.layout.widget_header)
            views.setTextViewText(R.id.header_text, item.headerText)
            // Headers are not clickable usually
            return views
        }
        
        // --- RENDER EVENT ---
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        views.setTextViewText(R.id.event_title, item.title)
        
        // Time logic
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeText = if (item.allDay) "All Day" else sdfTime.format(Date(item.begin))
        views.setTextViewText(R.id.event_time, timeText)

        // Progress Logic
        val now = System.currentTimeMillis()
        var progress = 0
        
        if (!item.allDay && item.end > item.begin) {
            when {
                now >= item.end -> progress = 100 // Event finished
                now <= item.begin -> progress = 0 // Event not started
                else -> {
                    // Event in progress
                    val totalDuration = item.end - item.begin
                    val elapsedTime = now - item.begin
                    progress = ((elapsedTime.toDouble() / totalDuration.toDouble()) * 100).toInt()
                }
            }
        }
        
        views.setProgressBar(R.id.event_progress, 100, progress, false)

        return views
    }
    
    // We have 2 types of views now (Header + Item)
    override fun getViewTypeCount(): Int {
        return 2
    }
    
    // ... (Keep other methods)
    override fun getLoadingView(): RemoteViews? { return null }
    override fun getItemId(position: Int): Long { return eventList[position].id }
    override fun hasStableIds(): Boolean { return false } // IDs might conflict with headers using -1
}