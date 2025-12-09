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

    override fun onCreate() { }

    override fun onDataSetChanged() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            eventList = listOf(CalendarEvent(-1, "Permission Needed", 0, 0, false))
            return
        }

        try {
            val rawEvents = fetchEvents()
            eventList = if (rawEvents.isEmpty()) emptyList() else groupEventsByDay(rawEvents)
        } catch (e: Exception) {
            Log.e("WidgetService", "Error fetching data", e)
            eventList = listOf(CalendarEvent(-1, "Error loading events", 0, 0, false))
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
        val eventYear = eventCal.get(Calendar.YEAR)
        val nowYear = now.get(Calendar.YEAR)
        val eventDay = eventCal.get(Calendar.DAY_OF_YEAR)
        val nowDay = now.get(Calendar.DAY_OF_YEAR)
        
        return when {
            eventYear == nowYear && eventDay == nowDay -> "Today"
            eventYear == nowYear && eventDay == nowDay + 1 -> "Tomorrow"
            else -> sdfDay.format(eventCal.time)
        }
    }

    private fun fetchEvents(): List<CalendarEvent> {
        val events = ArrayList<CalendarEvent>()
        
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
                    
                    if (shouldFilter && !selectedCalendars!!.contains(calId)) {
                        continue
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
            Log.e("WidgetService", "Query Error", e)
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
        
        if (item.isHeader) {
            val views = RemoteViews(context.packageName, R.layout.widget_header)
            views.setTextViewText(R.id.header_text, item.headerText)
            return views
        }
        
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        views.setTextViewText(R.id.event_title, item.title)
        
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeText = if (item.allDay) "All Day" else sdfTime.format(Date(item.begin))
        views.setTextViewText(R.id.event_time, timeText)

        // Progress Logic
        val now = System.currentTimeMillis()
        var progress = 0
        
        if (!item.allDay && item.end > item.begin) {
            when {
                now >= item.end -> progress = 100
                now <= item.begin -> progress = 0
                else -> {
                    val totalDuration = item.end - item.begin
                    val elapsedTime = now - item.begin
                    progress = ((elapsedTime.toDouble() / totalDuration.toDouble()) * 100).toInt()
                }
            }
        }
        
        views.setProgressBar(R.id.event_progress, 100, progress, false)

        return views
    }
    
    override fun getViewTypeCount(): Int { return 2 }
    override fun getLoadingView(): RemoteViews? { return null }
    override fun getItemId(position: Int): Long { return eventList[position].id }
    override fun hasStableIds(): Boolean { return false }
}