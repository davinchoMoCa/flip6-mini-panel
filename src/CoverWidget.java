package local.flip6.minipanel;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;

/** RemoteViews page for Samsung's cover widget host. */
public class CoverWidget extends AppWidgetProvider {
  public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
    for (int id : ids) update(context, manager, id, "");
  }

  static void update(Context c, AppWidgetManager manager, int id, String message) {
    RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.cover_widget);
    v.setTextViewText(R.id.widget_status, message.isEmpty() ? "Música y aplicaciones" : message);
    bind(c, v, R.id.widget_open, "open");
    bind(c, v, R.id.widget_spotify, "spotify");
    bind(c, v, R.id.widget_apps, "apps");
    bind(c, v, R.id.widget_previous, "previous");
    bind(c, v, R.id.widget_play, "play");
    bind(c, v, R.id.widget_next, "next");
    bind(c, v, R.id.widget_lower, "lower");
    bind(c, v, R.id.widget_raise, "raise");
    manager.updateAppWidget(id, v);
  }

  static void bind(Context c, RemoteViews v, int view, String action) {
    Intent i = new Intent(c, CoverWidget.class).setAction(c.getPackageName() + ".widget." + action);
    v.setOnClickPendingIntent(
        view,
        PendingIntent.getBroadcast(
            c, view, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
  }

  @Override
  public void onReceive(Context c, Intent i) {
    super.onReceive(c, i);
    String a = i.getAction(), prefix = c.getPackageName() + ".widget.";
    if (a == null || !a.startsWith(prefix)) return;
    String command;
    switch (a.substring(prefix.length())) {
      case "open":
      case "apps":
        command = "launch local.flip6.minipanel";
        break;
      case "spotify":
        command = "launch com.spotify.music";
        break;
      case "previous":
        command = "media previous";
        break;
      case "play":
        command = "media play-pause";
        break;
      case "next":
        command = "media next";
        break;
      case "lower":
        command = "volume lower";
        break;
      case "raise":
        command = "volume raise";
        break;
      default:
        return;
    }
    if (a.endsWith(".apps"))
      c.getSharedPreferences("panel", 0).edit().putBoolean("open_catalog_once", true).apply();
    final PendingResult pending = goAsync();
    new Thread(
            () -> {
              try {
                RootClient.run(c, command);
              } catch (Exception e) {
                AppWidgetManager m = AppWidgetManager.getInstance(c);
                for (int id : m.getAppWidgetIds(new ComponentName(c, CoverWidget.class)))
                  update(c, m, id, "No se pudo completar. Abre el panel.");
              } finally {
                pending.finish();
              }
            },
            "CoverWidgetAction")
        .start();
  }
}
