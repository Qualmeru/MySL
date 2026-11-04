package se.mybus.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class DeparturesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeparturesWidget()

    // Called when a widget is added (and on resize); start the 15-minute updates and fetch now.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdates.schedule(context)
        WidgetUpdates.refreshNow(context)
    }

    // The last widget was removed.
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdates.cancel(context)
    }
}
