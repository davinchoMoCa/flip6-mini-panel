package local.flip6.minipanel;

import android.content.Context;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

public class RootClient {
  public static JSONObject run(Context context, String action) throws Exception {
    if (!action.matches(
        "nowplaying|media (play-pause|next|previous)|controls|volume (raise|lower)|brightness"
            + " (up|down|auto)|list|home|back|switch [0-9]+|launch"
            + " [A-Za-z0-9_]+(?:[.][A-Za-z0-9_]+)+"))
      throw new Exception("Accion no valida");
    String apk = context.getApplicationInfo().sourceDir;
    String cmd =
        "CLASSPATH='"
            + apk.replace("'", "'\\''")
            + "' /system/bin/app_process /system/bin local.flip6.minipanel.RootBridge "
            + action;
    Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
    ExecutorService reader = Executors.newSingleThreadExecutor();
    Future<String> output =
        reader.submit(
            () -> {
              ByteArrayOutputStream b = new ByteArrayOutputStream();
              byte[] buf = new byte[4096];
              int n;
              try (InputStream in = p.getInputStream()) {
                while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
              }
              return b.toString("UTF-8");
            });
    try {
      String raw = output.get(30, TimeUnit.SECONDS);
      for (String line : raw.split("\n"))
        if (line.startsWith("{")) {
          JSONObject r = new JSONObject(line);
          if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "Accion fallida"));
          return r;
        }
      throw new Exception("Autoriza Mini Panel en Magisk y vuelve a probar");
    } finally {
      p.destroy();
      reader.shutdownNow();
    }
  }
}
