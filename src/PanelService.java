package local.flip6.minipanel;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import org.json.*;

public class PanelService extends android.accessibilityservice.AccessibilityService
    implements DisplayManager.DisplayListener {
  Handler main = new Handler(Looper.getMainLooper());
  ExecutorService executor = Executors.newSingleThreadExecutor();
  DisplayManager dm;
  Context cover;
  WindowManager wm;
  View window;
  boolean expanded = false, left = true;
  boolean busy = false;
  boolean catalogMode = false;
  TextView status;

  // Keep only the cover overlay awake; never wake a display the user switched off.
  boolean spotifyPlaying = false, watchStarted = false, destroyed = false;
  final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor();
  final Runnable playbackWatch =
      () -> {
        if (destroyed) return;
        if (!getSharedPreferences("panel", 0).getBoolean("enabled", false)) {
          applyPlaybackAwake(false);
          main.postDelayed(this.playbackWatch, 10000);
          return;
        }
        playbackExecutor.execute(
            () -> {
              boolean playing = false;
              try {
                playing = RootClient.run(this, "spotifyplaying").optBoolean("playing", false);
              } catch (Exception ignored) {
              }
              final boolean value = playing;
              main.post(
                  () -> {
                    if (destroyed) return;
                    applyPlaybackAwake(value);
                    main.postDelayed(this.playbackWatch, 10000);
                  });
            });
      };

  void startPlaybackWatch() {
    if (!watchStarted) {
      watchStarted = true;
      main.post(playbackWatch);
    }
  }

  void applyPlaybackAwake(boolean playing) {
    spotifyPlaying = playing;
    if (window == null || wm == null) return;
    WindowManager.LayoutParams p = (WindowManager.LayoutParams) window.getLayoutParams();
    int flags = p.flags;
    if (expanded || playing) p.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    else p.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    if (flags != p.flags)
      try {
        wm.updateViewLayout(window, p);
      } catch (Exception ignored) {
      }
  }

  int dp(int n) {
    return Math.round(n * cover.getResources().getDisplayMetrics().density);
  }

  public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent e) {}

  public void onInterrupt() {}

  SharedPreferences.OnSharedPreferenceChangeListener listener =
      (prefs, key) -> {
        if ("enabled".equals(key)) {
          if (prefs.getBoolean("enabled", false)) attach();
          else remove();
        }
      };

  public void onServiceConnected() {
    if (!getSharedPreferences("panel", 0).contains("enabled"))
      getSharedPreferences("panel", 0).edit().putBoolean("enabled", true).apply();
    dm = getSystemService(DisplayManager.class);
    dm.registerDisplayListener(this, main);
    left = getSharedPreferences("panel", 0).getBoolean("left", true);
    getSharedPreferences("panel", 0).registerOnSharedPreferenceChangeListener(listener);
    if (getSharedPreferences("panel", 0).getBoolean("enabled", false)) attach();
  }

  void attach() {
    if (!getSharedPreferences("panel", 0).getBoolean("enabled", false)) return;
    Display d = dm.getDisplay(1);
    if (d == null) return;
    if (cover == null) {
      cover =
          new android.view.ContextThemeWrapper(
              createWindowContext(d, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null),
              R.style.Theme_MiniPanel);
      wm = cover.getSystemService(WindowManager.class);
    }
    if (window == null) bubble();
    startPlaybackWatch();
  }

  void remove() {
    if (window != null) {
      try {
        wm.removeView(window);
      } catch (Exception ignored) {
      }
      window = null;
    }
  }

  void show(View v, int width, int height) {
    remove();
    View host = v;
    if (expanded) {
      MaterialUi.normalize(v);
      v.setClickable(true);
      com.google.android.material.card.MaterialCardView surface =
          new com.google.android.material.card.MaterialCardView(cover);
      surface.setRadius(dp(28));
      surface.setCardBackgroundColor(MaterialUi.color(cover, MaterialUi.CONTAINER));
      surface.setStrokeWidth(0);
      surface.setCardElevation(dp(3));
      surface.setClickable(true);
      int available = cover.getResources().getDisplayMetrics().heightPixels - dp(32);
      if (height < 0) {
        v.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        height = Math.min(v.getMeasuredHeight(), available);
        ScrollView sc = new ScrollView(cover);
        sc.setFillViewport(true);
        sc.addView(v);
        surface.addView(sc);
      } else {
        height = Math.min(height, available);
        surface.addView(v, new FrameLayout.LayoutParams(-1, -1));
      }
      v = surface;
      FrameLayout shade = new FrameLayout(cover);
      shade.setBackgroundColor(0x52000000);
      shade.setOnClickListener(outside -> bubble());
      FrameLayout.LayoutParams inner =
          new FrameLayout.LayoutParams(
              width, height, Gravity.TOP | (left ? Gravity.LEFT : Gravity.RIGHT));
      inner.topMargin = dp(16);
      inner.leftMargin = dp(8);
      inner.rightMargin = dp(8);
      shade.addView(v, inner);
      host = shade;
    }
    window = host;
    WindowManager.LayoutParams p =
        new WindowManager.LayoutParams(
            expanded ? -1 : width,
            expanded ? -1 : height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (catalogMode ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                | ((expanded || spotifyPlaying)
                    ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    : 0),
            PixelFormat.TRANSLUCENT);
    p.gravity = Gravity.TOP | (left ? Gravity.LEFT : Gravity.RIGHT);
    p.x = expanded ? 0 : dp(6);
    p.y = expanded ? 0 : dp(80);
    p.softInputMode =
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
    p.setTitle("Mini Panel exterior");
    wm.addView(host, p);
    if (expanded) {
      v.setTranslationX(left ? -width : width);
      v.animate().translationX(0).setDuration(180).start();
    }
  }

  void bubble() {
    catalogMode = false;
    expanded = false;
    FrameLayout b = new FrameLayout(cover);
    b.setContentDescription("Desliza hacia el centro para abrir Mini Panel");
    b.setClickable(true);
    b.setBackgroundColor(0x01000000);
    b.setSystemGestureExclusionRects(
        java.util.Collections.singletonList(new android.graphics.Rect(0, 0, dp(24), dp(104))));
    View bar = new View(cover);
    GradientDrawable pill = new GradientDrawable();
    pill.setColor(MaterialUi.color(cover, MaterialUi.CONTAINER));
    pill.setStroke(dp(1), MaterialUi.color(cover, com.google.android.material.R.attr.colorOutline));
    pill.setCornerRadius(dp(4));
    bar.setBackground(pill);
    FrameLayout.LayoutParams handle =
        new FrameLayout.LayoutParams(
            dp(6), dp(84), Gravity.CENTER_VERTICAL | (left ? Gravity.LEFT : Gravity.RIGHT));
    b.addView(bar, handle);
    show(b, dp(24), dp(104));
    b.setOnClickListener(v -> menu());
    b.setOnLongClickListener(
        v -> {
          left = !left;
          getSharedPreferences("panel", 0).edit().putBoolean("left", left).apply();
          bubble();
          return true;
        });
    b.setOnTouchListener(
        new View.OnTouchListener() {
          float startX, startY;
          boolean moved, swiped, held;
          int pointer;
          final int slop = ViewConfiguration.get(cover).getScaledTouchSlop();
          final Runnable hold =
              () -> {
                if (window == b && !moved) {
                  held = true;
                  b.setPressed(false);
                  b.performLongClick();
                }
              };

          public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
              case MotionEvent.ACTION_DOWN:
                startX = e.getRawX();
                startY = e.getRawY();
                pointer = e.getPointerId(0);
                moved = false;
                swiped = false;
                held = false;
                b.setPressed(true);
                main.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                return true;
              case MotionEvent.ACTION_MOVE:
                if (held || e.getPointerId(0) != pointer) return true;
                float dx = e.getRawX() - startX, dy = e.getRawY() - startY;
                if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                  moved = true;
                  main.removeCallbacks(hold);
                  b.setPressed(false);
                }
                if ((left ? dx : -dx) >= dp(24) && Math.abs(dx) > Math.abs(dy) * 1.5f)
                  swiped = true;
                return true;
              case MotionEvent.ACTION_UP:
                main.removeCallbacks(hold);
                b.setPressed(false);
                if (!held && window == b) {
                  if (swiped) menu();
                  else if (!moved) b.performClick();
                }
                return true;
              case MotionEvent.ACTION_POINTER_DOWN:
              case MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(hold);
                held = true;
                b.setPressed(false);
                return true;
            }
            return true;
          }
        });
  }

  class EdgeLayout extends LinearLayout {
    float x, y;
    boolean closing;

    EdgeLayout() {
      super(cover);
    }

    public boolean onInterceptTouchEvent(MotionEvent e) {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        x = e.getX();
        y = e.getY();
        closing = false;
      }
      if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
        float dx = e.getX() - x, dy = e.getY() - y;
        if ((left ? -dx : dx) > dp(24) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
          closing = true;
          return true;
        }
      }
      return super.onInterceptTouchEvent(e);
    }

    public boolean onTouchEvent(MotionEvent e) {
      if (closing) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
          bubble();
        }
        return true;
      }
      return super.onTouchEvent(e);
    }
  }

  LinearLayout layout(String heading) {
    LinearLayout box = new EdgeLayout();
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(16), dp(12), dp(16), dp(12));
    TextView h = MaterialUi.text(cover, "", false);
    h.setText(heading);
    h.setTag("heading");
    h.setPadding(0, 0, 0, dp(8));
    h.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
    h.setTextSize(16);
    if (!heading.isEmpty()) box.addView(h);
    return box;
  }

  void button(LinearLayout box, String text, Runnable action) {
    com.google.android.material.button.MaterialButton b =
        MaterialUi.button(
            cover,
            text,
            () -> {
              if (!busy) action.run();
            });
    MaterialUi.tone(b, 0);
    b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
    box.addView(b, lp);
  }

  void navigation() {
    catalogMode = false;
    expanded = true;
    LinearLayout box = layout("Navegación");
    button(box, "Atrás", () -> navigate("back"));
    button(box, "Inicio (reloj)", () -> navigate("home"));
    button(box, "Todas las apps", () -> allApps());
    button(box, "Apps abiertas", () -> tasks(false));
    button(box, "Cerrar apps", () -> closeApps());
    button(box, "Rescatar de la interna", () -> tasks(true));
    button(box, "Volver a música", () -> menu());
    show(box, dp(280), -2);
  }

  void menu() {
    catalogMode = false;
    expanded = true;
    LinearLayout box = layout("");
    TextView info = MaterialUi.text(cover, "", false);
    info.setText("Control de música");
    info.setTag("heading");
    info.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
    info.setTextSize(15);
    info.setGravity(Gravity.CENTER);
    box.addView(info, new LinearLayout.LayoutParams(-1, dp(30)));
    LinearLayout transport = new LinearLayout(cover);
    box.addView(transport);
    musicButton(transport, "|◀", "Canción anterior", () -> mediaAction("previous", info));
    musicButton(transport, "▶ / Ⅱ", "Reproducir o pausar", () -> mediaAction("play-pause", info));
    musicButton(transport, "▶|", "Canción siguiente", () -> mediaAction("next", info));
    TextView volume = MaterialUi.text(cover, "", false);
    volume.setText("Volumen multimedia");
    volume.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
    volume.setTextSize(12);
    volume.setGravity(Gravity.CENTER);
    box.addView(volume, new LinearLayout.LayoutParams(-1, dp(24)));
    LinearLayout audio = new LinearLayout(cover);
    box.addView(audio);
    musicButton(audio, "−", "Bajar volumen multimedia", () -> musicVolume("lower", volume));
    musicButton(audio, "+", "Subir volumen multimedia", () -> musicVolume("raise", volume));
    button(
        box,
        "Escritorio",
        () -> {
          bubble();
          run("launch local.flip6.minipanel", r -> {}, e -> error(e));
        });
    button(box, "Cerrar apps", () -> closeApps());
    button(box, "Brillo y controles", () -> controls());
    button(box, "Apps y navegación", () -> navigation());
    show(box, dp(280), -2);
  }

  void musicButton(LinearLayout row, String label, String description, Runnable action) {
    com.google.android.material.button.MaterialButton b =
        MaterialUi.button(
            cover,
            label,
            () -> {
              if (!busy) action.run();
            });
    b.setContentDescription(description);
    if (description.equals("Canción anterior"))
      MaterialUi.icon(b, R.drawable.ic_previous, description);
    else if (description.equals("Canción siguiente"))
      MaterialUi.icon(b, R.drawable.ic_next, description);
    else if (description.equals("Reproducir o pausar")) {
      MaterialUi.icon(b, R.drawable.ic_play_pause, description);
      MaterialUi.tone(b, 2);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
    lp.setMargins(dp(2), 0, dp(2), 0);
    row.addView(b, lp);
  }

  void mediaAction(String key, TextView info) {
    info.setText("Enviando...");
    run(
        "media " + key,
        r -> {
          if (info.isAttachedToWindow()) info.setText("Control de música");
          info.setTag("heading");
        },
        e -> {
          if (info.isAttachedToWindow()) info.setText(e);
        });
  }

  void musicVolume(String direction, TextView info) {
    run(
        "volume " + direction,
        r -> {
          if (info.isAttachedToWindow())
            info.setText("Volumen multimedia · " + r.optString("volume", "—"));
        },
        e -> {
          if (info.isAttachedToWindow()) info.setText(e);
        });
  }

  void controls() {
    catalogMode = false;
    expanded = true;
    LinearLayout box = layout("Controles rápidos");
    TextView info = MaterialUi.text(cover, "", false);
    info.setText("Consultando...");
    info.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
    info.setTextSize(13);
    box.addView(info);
    TextView hint = MaterialUi.text(cover, "", false);
    hint.setText("Volumen multimedia");
    hint.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
    hint.setTextSize(12);
    box.addView(hint);
    LinearLayout audio = new LinearLayout(cover);
    box.addView(audio);
    quickButton(audio, "−", () -> adjustControl("volume lower", info));
    quickButton(audio, "+", () -> adjustControl("volume raise", info));
    TextView light = MaterialUi.text(cover, "", false);
    light.setText("Brillo exterior · −/+ cambia a manual");
    light.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
    light.setTextSize(12);
    box.addView(light);
    LinearLayout brightness = new LinearLayout(cover);
    box.addView(brightness);
    quickButton(brightness, "−", () -> adjustControl("brightness down", info));
    quickButton(brightness, "+", () -> adjustControl("brightness up", info));
    button(box, "Brillo automático", () -> adjustControl("brightness auto", info));
    button(box, "Volver", () -> menu());
    show(box, dp(280), -2);
    adjustControl("controls", info);
  }

  void quickButton(LinearLayout row, String label, Runnable action) {
    musicButton(row, label, label.equals("−") ? "Disminuir" : "Aumentar", action);
  }

  void adjustControl(String action, TextView info) {
    info.setText("Aplicando...");
    run(
        action,
        r -> {
          if (!info.isAttachedToWindow()) return;
          info.setText(
              "Volumen "
                  + r.optString("volume", "—")
                  + " · Brillo "
                  + (r.optBoolean("auto")
                      ? "auto"
                      : Math.round(r.optInt("brightness") * 100f / 255) + "%"));
        },
        e -> {
          if (info.isAttachedToWindow()) info.setText("No se pudo: " + e);
        });
  }

  void navigate(String action) {
    bubble();
    run(action, r -> {}, e -> error(e));
  }

  interface Done {
    void call(JSONObject r);
  }

  interface Error {
    void call(String s);
  }

  void run(String command, Done done, Error error) {
    if (busy) return;
    busy = true;
    executor.execute(
        () -> {
          try {
            JSONObject r = RootClient.run(this, command);
            main.post(
                () -> {
                  busy = false;
                  done.call(r);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  busy = false;
                  error.call(e.getMessage());
                });
          }
        });
  }

  void error(String text) {
    expanded = true;
    LinearLayout box = layout("No se pudo completar");
    TextView t = MaterialUi.text(cover, "", false);
    t.setText(text);
    t.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
    t.setTextSize(14);
    box.addView(t);
    button(box, "Volver", () -> menu());
    show(box, dp(270), -2);
  }

  void closeApps() {
    catalogMode = false;
    expanded = true;
    LinearLayout loading = layout("Cerrar apps");
    TextView hint = MaterialUi.text(cover, "Consultando ventanas...", false);
    loading.addView(hint);
    show(loading, dp(280), -2);
    final View origin = window;
    run(
        "list",
        r -> {
          if (window != origin) return;
          try {
            LinearLayout box = layout("Cerrar apps");
            TextView info =
                MaterialUi.text(
                    cover,
                    "Cierra ventanas como en Recientes. La música puede seguir en segundo plano.",
                    false);
            info.setTextSize(12);
            box.addView(info);
            ScrollView scroll = new ScrollView(cover);
            LinearLayout list = new LinearLayout(cover);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list);
            box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            JSONArray items = r.getJSONArray("items");
            int count = 0;
            for (int i = 0; i < items.length(); i++) {
              JSONObject item = items.getJSONObject(i);
              String pkg = item.getString("package");
              if (pkg.equals("com.android.systemui")) continue;
              final int id = item.getInt("id");
              String label = pkg;
              try {
                label =
                    getPackageManager()
                        .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                        .toString();
              } catch (Exception ignored) {
              }
              LinearLayout row = new LinearLayout(cover);
              row.setGravity(Gravity.CENTER_VERTICAL);
              list.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
              TextView title =
                  MaterialUi.text(
                      cover,
                      label + (item.getInt("display") == 1 ? "\nExterna" : "\nInterna"),
                      false);
              title.setTextSize(14);
              title.setMaxLines(2);
              title.setEllipsize(android.text.TextUtils.TruncateAt.END);
              row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
              com.google.android.material.button.MaterialButton close =
                  MaterialUi.button(cover, "Cerrar", () -> {});
              MaterialUi.tone(close, 0);
              close.setContentDescription("Cerrar " + label);
              row.addView(close, new LinearLayout.LayoutParams(dp(88), dp(48)));
              close.setOnClickListener(
                  v -> {
                    if (busy) return;
                    final View view = window;
                    close.setEnabled(false);
                    run(
                        "close " + id,
                        x -> {
                          if (window == view) closeApps();
                        },
                        e -> {
                          if (window == view) {
                            close.setEnabled(true);
                            info.setText(e);
                          }
                        });
                  });
              count++;
            }
            if (count == 0)
              list.addView(MaterialUi.text(cover, "No hay ventanas de apps para cerrar.", false));
            button(box, "Actualizar", () -> closeApps());
            button(box, "Volver", () -> menu());
            show(box, dp(280), cover.getResources().getDisplayMetrics().heightPixels - dp(32));
          } catch (Exception e) {
            error(e.getMessage());
          }
        },
        e -> {
          if (window == origin) error(e);
        });
  }

  void tasks(boolean internalOnly) {
    final View origin = window;
    run(
        "list",
        r -> {
          if (window != origin || !expanded) return;
          try {
            LinearLayout box =
                layout(internalOnly ? "Traer desde la interna" : "Aplicaciones abiertas");
            ScrollView scroll = new ScrollView(cover);
            LinearLayout list = new LinearLayout(cover);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list);
            box.addView(scroll, new LinearLayout.LayoutParams(-1, dp(145)));
            JSONArray items = r.getJSONArray("items");
            int count = 0;
            for (int i = 0; i < items.length(); i++) {
              JSONObject item = items.getJSONObject(i);
              int display = item.getInt("display"), id = item.getInt("id");
              if (internalOnly && display != 0) continue;
              String pkg = item.getString("package"), label = pkg;
              try {
                label =
                    getPackageManager()
                        .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                        .toString();
              } catch (Exception ignored) {
              }
              final int task = id;
              button(
                  list,
                  label + (display == 0 ? " · interna" : " · exterior"),
                  () -> {
                    bubble();
                    run("switch " + task, x -> {}, e -> error(e));
                  });
              count++;
            }
            if (count == 0) {
              TextView empty = MaterialUi.text(cover, "", false);
              empty.setText(
                  internalOnly
                      ? "No hay aplicaciones que traer."
                      : "No hay aplicaciones abiertas.");
              empty.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
              list.addView(empty);
            }
            button(box, "Volver", () -> menu());
            show(box, dp(280), -2);
          } catch (Exception e) {
            error(e.getMessage());
          }
        },
        e -> error(e));
  }

  static class AppEntry {
    String pkg, label;
    android.graphics.drawable.Drawable icon;

    AppEntry(String p, String l, android.graphics.drawable.Drawable i) {
      pkg = p;
      label = l;
      icon = i;
    }
  }

  void allApps() {
    expanded = true;
    catalogMode = true;
    android.content.pm.PackageManager pm = getPackageManager();
    java.util.ArrayList<AppEntry> apps = new java.util.ArrayList<>();
    java.util.HashSet<String> seen = new java.util.HashSet<>();
    Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
      String pkg = ri.activityInfo.packageName;
      if (ri.activityInfo.enabled && ri.activityInfo.applicationInfo.enabled && seen.add(pkg))
        apps.add(new AppEntry(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm)));
    }
    apps.sort((a, b) -> java.text.Collator.getInstance().compare(a.label, b.label));
    LinearLayout box = layout("");
    com.google.android.material.textfield.TextInputLayout searchBox =
        new com.google.android.material.textfield.TextInputLayout(
            cover, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    searchBox.setHint("Buscar apps");
    searchBox.setEndIconMode(
        com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT);
    com.google.android.material.textfield.TextInputEditText search =
        new com.google.android.material.textfield.TextInputEditText(searchBox.getContext());
    search.setSingleLine(true);
    search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    searchBox.addView(search, new LinearLayout.LayoutParams(-1, -2));
    box.addView(searchBox, new LinearLayout.LayoutParams(-1, -2));
    com.google.android.material.button.MaterialButtonToggleGroup tabs =
        new com.google.android.material.button.MaterialButtonToggleGroup(cover);
    tabs.setSingleSelection(true);
    tabs.setSelectionRequired(true);
    LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(-1, dp(48));
    tabLp.topMargin = dp(8);
    tabLp.bottomMargin = dp(8);
    box.addView(tabs, tabLp);
    com.google.android.material.button.MaterialButton
        all = MaterialUi.button(cover, "Todas", () -> {}),
        stars = MaterialUi.button(cover, "Favoritas", () -> {});
    all.setId(View.generateViewId());
    stars.setId(View.generateViewId());
    for (com.google.android.material.button.MaterialButton tab :
        new com.google.android.material.button.MaterialButton[] {all, stars}) {
      tab.setCheckable(true);
      tab.setStrokeWidth(dp(1));
      tab.setStrokeColor(
          android.content.res.ColorStateList.valueOf(
              MaterialUi.color(cover, com.google.android.material.R.attr.colorOutline)));
      tabs.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
    }
    tabs.check(all.getId());
    ScrollView scroll = new ScrollView(cover);
    LinearLayout list = new LinearLayout(cover);
    list.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(list);
    box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    java.util.Set<String> favorites =
        new java.util.HashSet<>(
            getSharedPreferences("panel", 0)
                .getStringSet("favorites", java.util.Collections.emptySet()));
    boolean[] onlyFavorites = {false};
    Runnable[] render = new Runnable[1];
    render[0] =
        () -> {
          list.removeAllViews();
          String term = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
          MaterialUi.tone(all, onlyFavorites[0] ? 0 : 1);
          MaterialUi.tone(stars, onlyFavorites[0] ? 1 : 0);
          int count = 0;
          for (AppEntry app : apps) {
            if (onlyFavorites[0] && !favorites.contains(app.pkg)) continue;
            if (!app.label.toLowerCase(java.util.Locale.ROOT).contains(term)
                && !app.pkg.toLowerCase(java.util.Locale.ROOT).contains(term)) continue;
            count++;
            LinearLayout row = new LinearLayout(cover);
            row.setGravity(Gravity.CENTER_VERTICAL);
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
            LinearLayout open = new LinearLayout(cover);
            open.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(open, new LinearLayout.LayoutParams(0, -1, 1));
            ImageView icon = new ImageView(cover);
            icon.setImageDrawable(app.icon);
            open.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = MaterialUi.text(cover, "", false);
            label.setText(app.label);
            label.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
            label.setTextSize(14);
            label.setMaxLines(2);
            label.setPadding(dp(8), 0, 0, 0);
            open.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialUi.ripple(open);
            open.setContentDescription("Abrir " + app.label);
            open.setOnClickListener(
                v -> {
                  if (busy) return;
                  ((android.view.inputmethod.InputMethodManager)
                          cover.getSystemService(INPUT_METHOD_SERVICE))
                      .hideSoftInputFromWindow(search.getWindowToken(), 0);
                  bubble();
                  run("launch " + app.pkg, r -> {}, e -> error(e));
                });
            com.google.android.material.button.MaterialButton star =
                MaterialUi.button(cover, "", () -> {});
            MaterialUi.tone(star, 0);
            star.setText(favorites.contains(app.pkg) ? "★" : "☆");
            star.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
            star.setTextSize(22);
            star.setPadding(0, 0, 0, 0);
            star.setTextColor(MaterialUi.color(cover, androidx.appcompat.R.attr.colorPrimary));
            star.setContentDescription(
                (favorites.contains(app.pkg) ? "Quitar favorito " : "Marcar favorito ")
                    + app.label);
            row.addView(star, new LinearLayout.LayoutParams(dp(48), -1));
            star.setOnClickListener(
                v -> {
                  if (!favorites.remove(app.pkg)) favorites.add(app.pkg);
                  getSharedPreferences("panel", 0)
                      .edit()
                      .putStringSet("favorites", new java.util.HashSet<>(favorites))
                      .apply();
                  render[0].run();
                });
          }
          MaterialUi.normalize(list);
          if (count == 0) {
            TextView empty = MaterialUi.text(cover, "", false);
            empty.setText(
                onlyFavorites[0]
                    ? "Marca ☆ en Todas para guardar favoritas."
                    : "No se encontraron apps.");
            empty.setTextColor(MaterialUi.color(cover, MaterialUi.ON_SURFACE));
            list.addView(empty);
          }
        };
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            render[0].run();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    tabs.addOnButtonCheckedListener(
        (group, id, checked) -> {
          if (checked) {
            onlyFavorites[0] = id == stars.getId();
            render[0].run();
          }
        });
    button(box, "Volver", () -> menu());
    render[0].run();
    show(box, dp(285), cover.getResources().getDisplayMetrics().heightPixels - dp(32));
  }

  public void onDisplayAdded(int id) {
    if (id == 1) attach();
  }

  public void onDisplayRemoved(int id) {
    if (id == 1) {
      remove();
      cover = null;
      wm = null;
    }
  }

  public void onDisplayChanged(int id) {
    if (id == 1
        && dm.getDisplay(1) != null
        && dm.getDisplay(1).getState() == Display.STATE_OFF
        && expanded) bubble();
  }

  public void onDestroy() {
    destroyed = true;
    main.removeCallbacks(playbackWatch);
    playbackExecutor.shutdownNow();
    getSharedPreferences("panel", 0).unregisterOnSharedPreferenceChangeListener(listener);
    if (dm != null) dm.unregisterDisplayListener(this);
    remove();
    executor.shutdownNow();
    super.onDestroy();
  }
}
