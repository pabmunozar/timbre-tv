package cl.pabmunozar.timbretv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showBanner() {
        if (!Settings.canDrawOverlays(this) || ringing) {
            return;
        }
        ringing = true;

        // Tarjeta: fondo oscuro translúcido, esquinas redondeadas, borde sutil
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF014161B);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x26FFFFFF);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(bg);
        card.setPadding(dp(16), dp(14), dp(26), dp(14));
        card.setElevation(dp(6));

        // Campana vectorial (estilo Material, dibujada con Path) en un círculo azul
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF2E5AAC);
        View bell = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(Paint.Style.FILL);
                float s = Math.min(getWidth(), getHeight()) / 24f;
                canvas.save();
                canvas.translate(getWidth() / 2f - 12f * s, getHeight() / 2f - 12f * s);
                canvas.scale(s, s);
                Path p = new Path();
                p.moveTo(12f, 4.6f);
                p.cubicTo(8.6f, 4.6f, 6.2f, 7.3f, 6.2f, 11.3f);
                p.lineTo(5.1f, 16.4f);
                p.lineTo(18.9f, 16.4f);
                p.lineTo(17.8f, 11.3f);
                p.cubicTo(17.8f, 7.3f, 15.4f, 4.6f, 12f, 4.6f);
                p.close();
                p.addCircle(12f, 3.6f, 1.5f, Path.Direction.CW);
                p.addCircle(12f, 19.3f, 1.7f, Path.Direction.CW);
                canvas.drawPath(p, paint);
                canvas.restore();
            }
        };
        LinearLayout.LayoutParams bellLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        bellLp.setMarginEnd(dp(16));
        card.addView(bell, bellLp);

        // Título + hora
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Alguien en la puerta");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView sub = new TextView(this);
        sub.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        sub.setTextColor(0x99FFFFFF);
        sub.setTextSize(14);
        col.addView(title);
        col.addView(sub);
        card.addView(col);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(24);
        lp.y = dp(24);

        // Entrada deslizante desde la derecha
        card.setAlpha(0f);
        card.setTranslationX(dp(80));
        try {
            wm.addView(card, lp);
        } catch (Exception e) {
            ringing = false;
            return;
        }
        card.animate().alpha(1f).translationX(0)
                .setDuration(320).setInterpolator(new DecelerateInterpolator()).start();

        // Sin sonido: pedir audio focus pausa el video en curso. El ding-dong lo pone el Nest.

        // Salida deslizante a los 6 s
        handler.postDelayed(() -> card.animate().alpha(0f).translationX(dp(80))
                .setDuration(260).withEndAction(() -> {
                    try {
                        wm.removeView(card);
                    } catch (Exception ignored) {
                    }
                    ringing = false;
                }).start(), 6000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }
}
