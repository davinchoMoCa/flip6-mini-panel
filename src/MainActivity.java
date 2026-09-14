package local.flip6.minipanel;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {
  final Handler h = new Handler(Looper.getMainLooper());
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  LinearLayout page, grid;
  boolean active, busy, appsPage, heldApp;
  TextInputEditText searchField;
  ScrollView appScroll;

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(0xff111512);
    getWindow().setNavigationBarColor(0xff111512);
    render();
  }

  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    render();
  }

  protected void onResume() {
    super.onResume();
    active = true;
    catalog();
  }

  protected void onPause() {
    active = false;
    super.onPause();
  }

  protected void onDestroy() {
    worker.shutdownNow();
    super.onDestroy();
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    MaterialUi.type(
        t,
        size >= 17
            ? com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
            : size <= 12
                ? com.google.android.material.R.style.TextAppearance_Material3_LabelMedium
                : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    t.setTextColor(MaterialUi.color(this, color == -1 ? MaterialUi.ON_SURFACE : MaterialUi.MUTED));
    return t;
  }

  LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  void base() {
    View focus = getCurrentFocus();
    if (focus != null)
      getSystemService(android.view.inputmethod.InputMethodManager.class)
          .hideSoftInputFromWindow(focus.getWindowToken(), 0);
    ScrollView scroll = new AppScroll();
    appScroll = scroll;
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(MaterialUi.color(this, MaterialUi.SURFACE));
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        scroll,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
          v.setPadding(0, 0, 0, bars.bottom);
          return insets;
        });
    page = col();
    page.setPadding(dp(16), dp(8), dp(16), dp(24));
    scroll.addView(page);
    setContentView(scroll);
  }

  MaterialButton button(String title, Runnable action) {
    return MaterialUi.button(this, title, action);
  }

  MaterialCardView card(View child) {
    MaterialCardView c = new MaterialCardView(this);
    c.setRadius(dp(12));
    c.setCardBackgroundColor(MaterialUi.color(this, MaterialUi.CONTAINER));
    c.setStrokeWidth(0);
    c.setCardElevation(0);
    c.addView(child);
    return c;
  }

  void cell(LinearLayout row, View v, int height) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(height), 1);
    lp.setMargins(dp(4), dp(4), dp(4), dp(4));
    row.addView(v, lp);
  }

  void render() {
    getSharedPreferences("panel", 0).edit().remove("open_catalog_once").apply();
    catalog();
  }

  void appCell(LinearLayout row, String pkg, String label) {
    LinearLayout item = col();
    item.setGravity(Gravity.CENTER);
    try {
      ImageView icon = new ImageView(this);
      icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
      item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
    } catch (Exception ignored) {
    }
    TextView name = text(label, 12, -1);
    name.setGravity(Gravity.CENTER);
    name.setSingleLine();
    name.setEllipsize(TextUtils.TruncateAt.END);
    item.addView(name);
    MaterialCardView tile = card(item);
    tile.setContentDescription("Abrir " + label);
    tile.setClickable(true);
    tile.setOnClickListener(v -> launch(pkg));
    markFavorite(tile, pkg, label);
    tile.setOnLongClickListener(
        v -> {
          heldApp = true;
          Set<String> favorites =
              new HashSet<>(
                  getSharedPreferences("panel", 0)
                      .getStringSet("favorites", Collections.emptySet()));
          boolean added = favorites.add(pkg);
          if (!added) favorites.remove(pkg);
          getSharedPreferences("panel", 0).edit().putStringSet("favorites", favorites).apply();
          markFavorite(tile, pkg, label);
          tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
          notice(added ? label + " añadida a favoritas" : label + " quitada de favoritas");
          return true;
        });
    cell(row, tile, 56);
  }

  void notice(String message) {
    com.google.android.material.snackbar.Snackbar.make(
            page,
            message == null ? "No se pudo completar" : message,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        .show();
  }

  void launch(String pkg) {
    if (pkg.equals(getPackageName())) {
      render();
      return;
    }
    run("launch " + pkg);
  }

  void run(String action) {
    if (busy) {
      notice("Un momento…");
      return;
    }
    busy = true;
    worker.execute(
        () -> {
          try {
            RootClient.run(this, action);
            h.post(
                () -> {
                  busy = false;
                });
          } catch (Exception e) {
            h.post(
                () -> {
                  busy = false;
                  notice(e.getMessage());
                });
          }
        });
  }

  static class App {
    String pkg, label;

    App(String p, String l) {
      pkg = p;
      label = l;
    }
  }

  void catalog() {
    appsPage = true;
    base();
    LinearLayout top = new LinearLayout(this);
    page.addView(top);
    cell(top, button("← Música", () -> run("home")), 48);
    TextView title = text("Aplicaciones", 17, -1);
    title.setGravity(Gravity.CENTER);
    cell(top, title, 48);
    TextInputLayout searchBox =
        new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    searchBox.setHintEnabled(false);
    searchBox.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);
    searchBox.setBoxCornerRadii(dp(28), dp(28), dp(28), dp(28));
    searchBox.setBoxStrokeWidth(0);
    searchBox.setBoxStrokeWidthFocused(0);
    searchBox.setBoxBackgroundColor(MaterialUi.color(this, MaterialUi.CONTAINER));
    searchBox.setStartIconDrawable(R.drawable.ic_search);
    searchBox.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
    TextInputEditText search = new TextInputEditText(searchBox.getContext());
    searchField = search;
    search.setSingleLine();
    search.setHint("Buscar aplicaciones");
    search.setContentDescription("Buscar aplicaciones instaladas");
    search.setPadding(dp(16), dp(12), dp(16), dp(12));
    searchBox.addView(search, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(56));
    searchLp.topMargin = dp(8);
    searchLp.bottomMargin = dp(12);
    page.addView(searchBox, searchLp);
    grid = col();
    page.addView(grid);
    ArrayList<App> apps = new ArrayList<>();
    HashSet<String> seen = new HashSet<>();
    PackageManager pm = getPackageManager();
    for (ResolveInfo r :
        pm.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
      if (r.activityInfo.enabled
          && r.activityInfo.applicationInfo.enabled
          && seen.add(r.activityInfo.packageName))
        apps.add(new App(r.activityInfo.packageName, r.loadLabel(pm).toString()));
    apps.sort((a, b) -> java.text.Collator.getInstance().compare(a.label, b.label));
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            fill(apps, s.toString());
          }

          public void afterTextChanged(Editable e) {}
        });
    fill(apps, "");
  }

  void fill(ArrayList<App> apps, String query) {
    Set<String> favorites =
        getSharedPreferences("panel", 0).getStringSet("favorites", Collections.emptySet());
    ArrayList<App> ordered = new ArrayList<>(apps);
    ordered.sort(
        (a, b) -> {
          int favorite = Boolean.compare(favorites.contains(b.pkg), favorites.contains(a.pkg));
          return favorite != 0
              ? favorite
              : java.text.Collator.getInstance().compare(a.label, b.label);
        });
    grid.removeAllViews();
    LinearLayout row = null;
    int i = 0;
    for (App a : ordered) {
      if (!a.label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
      if (i % 3 == 0) {
        row = new LinearLayout(this);
        grid.addView(row);
      }
      appCell(row, a.pkg, a.label);
      i++;
    }
    if (i == 0) grid.addView(text("Sin coincidencias", 14, -1));
    else
      while (i % 3 != 0) {
        cell(row, new View(this), 52);
        i++;
      }
  }

  // Return to the cover home task instead of finishing into an inner-screen task.
  @android.annotation.SuppressLint("MissingSuperCall")
  @Override
  public void onBackPressed() {
    backGesture();
  }

  void markFavorite(MaterialCardView tile, String pkg, String label) {
    boolean favorite =
        getSharedPreferences("panel", 0)
            .getStringSet("favorites", Collections.emptySet())
            .contains(pkg);
    tile.setStrokeWidth(favorite ? dp(2) : 0);
    tile.setStrokeColor(MaterialUi.color(this, androidx.appcompat.R.attr.colorPrimary));
    tile.setContentDescription(
        "Abrir "
            + label
            + (favorite ? ", favorita" : "")
            + ". Mantén pulsada para cambiar favorito");
  }

  void backGesture() {
    androidx.core.view.WindowInsetsCompat insets =
        androidx.core.view.ViewCompat.getRootWindowInsets(page);
    if ((insets != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
        || (searchField.hasFocus()
            && getSystemService(android.view.inputmethod.InputMethodManager.class)
                .isAcceptingText())) {
      run("back");
      page.setFocusableInTouchMode(true);
      page.requestFocus();
      searchField.clearFocus();
      return;
    }
    run("home");
  }

  void showSearch() {
    appScroll.smoothScrollTo(0, 0);
    searchField.requestFocus();
    searchField.postDelayed(
        () -> {
          if (active)
            getSystemService(android.view.inputmethod.InputMethodManager.class)
                .showSoftInput(
                    searchField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        },
        180);
  }

  class AppScroll extends ScrollView {
    float startX, startY;
    int mode;
    boolean atTop, editStart, multiple;
    long began;

    AppScroll() {
      super(MainActivity.this);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
      int action = e.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        startX = e.getX();
        startY = e.getY();
        mode = 0;
        multiple = false;
        heldApp = false;
        began = e.getEventTime();
        atTop = !canScrollVertically(-1);
        android.graphics.Rect r = new android.graphics.Rect();
        editStart =
            searchField != null
                && searchField.getGlobalVisibleRect(r)
                && r.contains((int) e.getRawX(), (int) e.getRawY());
      }
      if (action == MotionEvent.ACTION_POINTER_DOWN) {
        multiple = true;
        mode = -1;
      }
      if (action == MotionEvent.ACTION_MOVE && !multiple && !heldApp && mode == 0) {
        float dx = e.getX() - startX, dy = e.getY() - startY;
        if (dx > dp(24) && dx > Math.abs(dy) * 1.8f && !editStart) {
          mode = 1;
          return true;
        }
        if (Math.abs(dy) > dp(24)) {
          if (dy > 0 && atTop && !editStart && dy > Math.abs(dx) * 1.8f) {
            mode = 2;
            return true;
          }
          mode = -1;
        } else if (dx < -dp(24)) mode = -1;
      }
      if (mode > 0) return true;
      return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
      if (mode == 0 && e.getActionMasked() == MotionEvent.ACTION_MOVE && !multiple && !heldApp) {
        float dx = e.getX() - startX, dy = e.getY() - startY;
        if (dx > dp(24) && dx > Math.abs(dy) * 1.8f && !editStart) mode = 1;
        else if (dy > dp(24) && dy > Math.abs(dx) * 1.8f && atTop && !editStart) mode = 2;
        else if (Math.abs(dy) > dp(24)) mode = -1;
      }
      if (mode > 0) {
        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) multiple = true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
          float dx = e.getX() - startX, dy = e.getY() - startY;
          int gesture = mode;
          mode = 0;
          if (!multiple && !heldApp && e.getEventTime() - began < 1600) {
            if (gesture == 1 && dx > dp(64) && dx > Math.abs(dy) * 1.8f) backGesture();
            if (gesture == 2 && dy > dp(56) && dy > Math.abs(dx) * 1.8f) showSearch();
          }
        } else if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) mode = 0;
        return true;
      }
      return super.onTouchEvent(e);
    }
  }
}
