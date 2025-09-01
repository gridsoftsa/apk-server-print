package com.gridpos.puenteimpresora;

import android.app.Application;
import android.util.Log;
import net.posprinter.POSConnect;

/**
 * 🎯 Application class para inicializar SDK 3nStar correctamente
 */
public class PrinterApplication extends Application {
    private static final String TAG = "PrinterApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            // 🎯 Inicializar SDK 3nStar al inicio de la aplicación
            POSConnect.init(this);
            Log.d(TAG, "✅ SDK 3nStar inicializado en Application");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error inicializando SDK 3nStar en Application", e);
        }
    }
}
