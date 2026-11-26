package cl.pabmunozar.timbretv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class RingService extends Service {
    private static final int PORT = 8089;
    private final Handler handler = new Handler();
    private Thread serverThread;
    private WindowManager wm;
    private boolean ringing = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotif());
        if (serverThread == null || !serverThread.isAlive()) {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            serverThread = new Thread(this::serve);
            serverThread.start();
        }
        return START_STICKY;
    }

    private Notification buildNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    "timbre", "Timbre", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "timbre")
                : new Notification.Builder(this);
        return b.setContentTitle("Timbre TV")
                .setContentText("Escuchando puerto " + PORT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .build();
    }

    private void serve() {
        try {
            ServerSocket ss = new ServerSocket(PORT);
            while (true) {
                final Socket s = ss.accept();
                new Thread(() -> handle(s)).start();
            }
        } catch (Exception ignored) {
        }
    }

    private void handle(Socket s) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line = in.readLine();
            boolean ring = line != null && line.contains("/ring");
            int contentLength = 0;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String low = line.toLowerCase();
                if (low.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            if (contentLength > 0) {
                char[] body = new char[contentLength];
                in.read(body);
            }
            OutputStream os = s.getOutputStream();
            byte[] resp = ring
                    ? "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".getBytes()
                    : "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes();
            os.write(resp);
            os.close();
            s.close();
            if (ring) {
                handler.post(this::showBanner);
            }
        } catch (Exception ignored) {
        }
    }

    private void showBanner() {
        if (!Settings.canDrawOverlays(this) || ringing) {
            return;
        }
        ringing = true;
        TextView tv = new TextView(this);
        tv.setText("\uD83D\uDD0D Alguien en la puerta");
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(34);
        tv.setBackgroundColor(0xE0104090);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(48, 32, 48, 32);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = 64;
        lp.y = 64;
        try {
            wm.addView(tv, lp);
        } catch (Exception e) {
            ringing = false;
            return;
        }
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            r.play();
        } catch (Exception ignored) {
        }
        handler.postDelayed(() -> {
            try {
                wm.removeView(tv);
            } catch (Exception ignored) {
            }
            ringing = false;
        }, 6000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }
}
