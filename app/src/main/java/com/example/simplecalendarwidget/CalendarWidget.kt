package com.example.simplecalendarwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    // 1. Set Date Header
    val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val currentDate = dateFormat.format(Date())
    views.setTextViewText(R.id.widget_date_header, currentDate)
    
    // Header Click -> Open Calendar
    val headerIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val headerPendingIntent = PendingIntent.getActivity(
        context,
        0,
        headerIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_date_header, headerPendingIntent)

    // Set up the List Adapter
    val intent = Intent(context, WidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    views.setRemoteAdapter(R.id.widget_list_view, intent)

    // Template for item clicks
    val clickIntentTemplate = Intent(Intent.ACTION_VIEW).apply {
        // Explicitly set the package to avoid ambiguity
        setPackage(context.packageName)
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    
    val clickPendingIntent = PendingIntent.getActivity(
        context,
        1, // Request code
        clickIntentTemplate,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    views.setPendingIntentTemplate(R.id.widget_list_view, clickPendingIntent)
    
    appWidgetManager.updateAppWidget(appWidgetId, views)
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
}