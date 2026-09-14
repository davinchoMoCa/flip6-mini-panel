package local.flip6.minipanel;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

/** Shared Material roles for both the activity and the cover overlay context. */
final class MaterialUi {
  static int dp(Context c, int n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static int color(Context c, int attr) {
    return MaterialColors.getColor(c, attr, "Mini Panel");
  }

  static final int SURFACE = com.google.android.material.R.attr.colorSurface;
  static final int CONTAINER = com.google.android.material.R.attr.colorSurfaceContainer;
  static final int ON_SURFACE = com.google.android.material.R.attr.colorOnSurface;
  static final int MUTED = com.google.android.material.R.attr.colorOnSurfaceVariant;

  static void type(TextView t, int style) {
    androidx.core.widget.TextViewCompat.setTextAppearance(t, style);
  }

  static MaterialTextView text(Context c, String value, boolean title) {
    MaterialTextView t = new MaterialTextView(c);
    t.setText(value);
    type(
        t,
        title
            ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    t.setTextColor(color(c, title ? ON_SURFACE : MUTED));
    return t;
  }

  static MaterialButton button(Context c, String title, Runnable action) {
    MaterialButton b = new MaterialButton(c);
    b.setText(title);
    b.setAllCaps(false);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setMinHeight(dp(c, 48));
    b.setMinimumHeight(dp(c, 48));
    b.setInsetTop(dp(c, 4));
    b.setInsetBottom(dp(c, 4));
    b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
    b.setCornerRadius(dp(c, 20));
    type(b, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
    tone(b, 1);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  // 0 text, 1 tonal, 2 filled. Disabled and pressed states retain Material feedback.
  static void tone(MaterialButton b, int role) {
    Context c = b.getContext();
    int fg =
        color(
            c,
            role == 2
                ? com.google.android.material.R.attr.colorOnPrimary
                : role == 1
                    ? com.google.android.material.R.attr.colorOnSecondaryContainer
                    : androidx.appcompat.R.attr.colorPrimary);
    int bg =
        role == 2
            ? color(c, androidx.appcompat.R.attr.colorPrimary)
            : role == 1
                ? color(c, com.google.android.material.R.attr.colorSecondaryContainer)
                : Color.TRANSPARENT;
    int on = color(c, ON_SURFACE);
    b.setTextColor(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {(on & 0xffffff) | 0x61000000, fg}));
    b.setIconTint(b.getTextColors());
    b.setBackgroundTintList(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {role == 0 ? 0 : (on & 0xffffff) | 0x1f000000, bg}));
  }

  static void icon(MaterialButton b, int resource, String description) {
    b.setText("");
    b.setIconResource(resource);
    b.setIconSize(dp(b.getContext(), 24));
    b.setIconPadding(0);
    b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    b.setContentDescription(description);
  }

  static void normalize(View view) {
    if (view instanceof TextView
        && !(view instanceof MaterialButton)
        && !(view instanceof android.widget.EditText)) {
      TextView t = (TextView) view;
      boolean title = "heading".equals(t.getTag());
      type(
          t,
          title
              ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
              : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
      t.setTextColor(color(t.getContext(), title ? ON_SURFACE : MUTED));
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) normalize(group.getChildAt(i));
    }
  }

  static void ripple(View v) {
    android.util.TypedValue value = new android.util.TypedValue();
    v.getContext()
        .getTheme()
        .resolveAttribute(android.R.attr.selectableItemBackground, value, true);
    v.setBackgroundResource(value.resourceId);
    v.setFocusable(true);
  }
}
