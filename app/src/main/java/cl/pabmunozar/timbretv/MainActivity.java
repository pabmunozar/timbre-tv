package cl.pabmunozar.timbretv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Timbre TV activo\nEscuchando en puerto 8089");
        tv.setTextSize(28);
        setContentView(tv);
        startForegroundService(new Intent(this, RingService.class));
    }
}
