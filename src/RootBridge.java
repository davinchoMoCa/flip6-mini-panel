package local.flip6.minipanel;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Runs via su + app_process. Only explicit navigation actions; no permission changes. */
public class RootBridge {
  static Object manager;
  static Class<?> iface;

  static Object call(String name, Class<?>[] types, Object... args) throws Exception {
    return iface.getMethod(name, types).invoke(manager, args);
  }

  static int number(Object o, String key) throws Exception {
    return o.getClass().getField(key).getInt(o);
  }

  static Object field(Object o, String key) throws Exception {
    return o.getClass().getField(key).get(o);
  }

  static int type(Object root) throws Exception {
    Object config = field(root, "configuration");
    Object wc = field(config, "windowConfiguration");
    return (Integer) wc.getClass().getMethod("getActivityType").invoke(wc);
  }

  static List<?> roots() throws Exception {
    return (List<?>) call("getAllRootTaskInfos", new Class<?>[] {});
  }

  static String command(String... args) throws Exception {
    Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] b = new byte[4096];
    int n;
    try (InputStream in = p.getInputStream()) {
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
    if (p.waitFor() != 0) throw new IOException("No se pudo ejecutar la accion");
    return out.toString("UTF-8");
  }

  static void closed() throws Exception {
    String s = command("/system/bin/dumpsys", "device_state");
    if (!s.contains("mCommittedState=Optional[DeviceState{identifier=0,"))
      throw new Exception("Cierra el telefono para usar el panel exterior");
  }

  public static void main(String[] args) {
    JSONObject result = new JSONObject();
    try {
      iface = Class.forName("android.app.IActivityTaskManager");
      manager =
          Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
      String action = args.length == 0 ? "list" : args[0];
      if (action.equals("nowplaying")) {
        String dump = command("/system/bin/dumpsys", "media_session");
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("metadata: size=\\d+, description=([^\\r\\n]+)")
                .matcher(dump);
        result.put("description", m.find() ? m.group(1) : "");
      } else if (action.equals("list")) {
        JSONArray items = new JSONArray();
        for (Object root : roots()) {
          if (type(root) != 1) continue;
          int id = number(root, "taskId"), display = number(root, "displayId");
          if (display != 0 && display != 1) continue;
          int[] ids = (int[]) field(root, "childTaskIds");
          if (ids.length != 1 || ids[0] != id) continue;
          ComponentName top = (ComponentName) field(root, "topActivity");
          if (top == null || top.getPackageName().equals("local.flip6.minipanel")) continue;
          JSONObject item = new JSONObject();
          item.put("id", id);
          item.put("display", display);
          item.put("package", top.getPackageName());
          items.put(item);
        }
        result.put("items", items);
      } else {
        closed();
        if (action.equals("media")) {
          if (!args[1].matches("play-pause|next|previous"))
            throw new Exception("Control no valido");
          String sessions = command("/system/bin/cmd", "media_session", "list-sessions");
          if (!sessions.contains("package="))
            throw new Exception("Abre Spotify y elige una cancion primero");
          command("/system/bin/cmd", "media_session", "dispatch", args[1]);
        } else if (action.equals("controls")
            || action.equals("volume")
            || action.equals("brightness")) {
          if (action.equals("volume")) {
            if (!args[1].matches("raise|lower")) throw new Exception("Volumen no valido");
            command(
                "/system/bin/cmd", "media_session", "volume", "--stream", "3", "--adj", args[1]);
          }
          if (action.equals("brightness")) {
            if (args[1].equals("auto"))
              command("/system/bin/settings", "put", "system", "sub_screen_brightness_mode", "1");
            else {
              if (!args[1].matches("up|down")) throw new Exception("Brillo no valido");
              int old =
                  Integer.parseInt(
                      command("/system/bin/settings", "get", "system", "sub_screen_brightness")
                          .trim());
              int value = Math.max(20, Math.min(255, old + (args[1].equals("up") ? 25 : -25)));
              command("/system/bin/settings", "put", "system", "sub_screen_brightness_mode", "0");
              command(
                  "/system/bin/settings",
                  "put",
                  "system",
                  "sub_screen_brightness",
                  Integer.toString(value));
            }
          }
          result.put(
              "brightness",
              Integer.parseInt(
                  command("/system/bin/settings", "get", "system", "sub_screen_brightness")
                      .trim()));
          result.put(
              "auto",
              command("/system/bin/settings", "get", "system", "sub_screen_brightness_mode")
                  .trim()
                  .equals("1"));
          String volume =
              command("/system/bin/cmd", "media_session", "volume", "--stream", "3", "--get");
          java.util.regex.Matcher match =
              java.util.regex.Pattern.compile("volume is (\\d+) in range \\[\\d+\\.\\.(\\d+)\\]")
                  .matcher(volume);
          if (match.find()) result.put("volume", match.group(1) + "/" + match.group(2));
        } else if (action.equals("back"))
          command("/system/bin/input", "-d", "1", "keyevent", "KEYCODE_BACK");
        else if (action.equals("home")) {
          boolean found = false;
          for (Object root : roots())
            if (number(root, "displayId") == 1 && type(root) == 2) {
              call("setFocusedRootTask", new Class<?>[] {int.class}, number(root, "taskId"));
              found = true;
              break;
            }
          if (!found) throw new Exception("No se encontro el inicio exterior");
        } else if (action.equals("launch")) {
          String pkg = args[1];
          if (!pkg.matches("[A-Za-z0-9_]+(?:[.][A-Za-z0-9_]+)+"))
            throw new Exception("Aplicacion no valida");
          String resolved =
              command(
                      "/system/bin/cmd",
                      "package",
                      "resolve-activity",
                      "--brief",
                      "--user",
                      "0",
                      "-a",
                      "android.intent.action.MAIN",
                      "-c",
                      "android.intent.category.LAUNCHER",
                      pkg)
                  .trim();
          String[] lines = resolved.split("\n");
          String component = lines[lines.length - 1].trim();
          if (!component.startsWith(pkg + "/") || component.contains(" "))
            throw new Exception("La aplicacion no tiene una pantalla de inicio disponible");
          String output =
              command(
                  "/system/bin/am",
                  "start",
                  "-W",
                  "--user",
                  "0",
                  "--display",
                  "1",
                  "-a",
                  "android.intent.action.MAIN",
                  "-c",
                  "android.intent.category.LAUNCHER",
                  "-f",
                  "0x10200000",
                  "-n",
                  component);
          if (output.contains("Error:") || output.contains("Exception"))
            throw new Exception("Android no pudo abrir esta aplicacion en la exterior");
        } else if (action.equals("switch")) {
          int id = Integer.parseInt(args[1]);
          Object selected = null;
          for (Object root : roots())
            if (number(root, "taskId") == id && type(root) == 1) selected = root;
          if (selected == null) throw new Exception("La aplicacion ya no esta abierta");
          int[] ids = (int[]) field(selected, "childTaskIds");
          if (ids.length != 1 || ids[0] != id)
            throw new Exception("Esta ventana no admite traslado simple");
          call(
              "startActivityFromRecents",
              new Class<?>[] {int.class, Bundle.class},
              id,
              ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
        } else throw new Exception("Accion desconocida");
      }
      result.put("ok", true);
    } catch (Throwable e) {
      Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
      try {
        result.put("ok", false);
        result.put("error", cause.getMessage() == null ? cause.toString() : cause.getMessage());
      } catch (Exception ignored) {
      }
    }
    System.out.println(result.toString());
    System.exit(0);
  }
}
