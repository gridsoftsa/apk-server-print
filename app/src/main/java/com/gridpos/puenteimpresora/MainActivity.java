package com.gridpos.puenteimpresora;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PORT = 12345;
    
    private PrintServer server;
    private UsbPrinterManager usbPrinterManager;
    private TextView statusText;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        statusText = findViewById(R.id.statusText);
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Inicializar el manejador USB
        usbPrinterManager = new UsbPrinterManager(this);
        
        // Iniciar el servidor HTTP
        startHttpServer();
        
        // Mostrar mensaje de bienvenida
        showToast("Aplicación Puente iniciada");
    }

    private void startHttpServer() {
        try {
            server = new PrintServer();
            server.start();
            
            updateStatus("✅ Servicio de impresión activo en puerto " + PORT + "\n\nEsperando solicitudes de impresión...");
            Log.i(TAG, "Servidor HTTP iniciado en el puerto " + PORT);
            
        } catch (IOException e) {
            Log.e(TAG, "Error al iniciar el servidor HTTP", e);
            updateStatus("❌ Error al iniciar el servicio de impresión\n\n" + e.getMessage());
            showToast("Error al iniciar el servidor");
        }
    }

    private void updateStatus(String message) {
        mainHandler.post(() -> statusText.setText(message));
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    // Clase interna para el servidor HTTP
    private class PrintServer extends NanoHTTPD {
        
        public PrintServer() {
            super(PORT);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Log.d(TAG, "Solicitud recibida: " + session.getMethod() + " " + session.getUri());
            
            // Configurar headers CORS para permitir peticiones desde el navegador
            Response response;
            
            if (Method.OPTIONS.equals(session.getMethod())) {
                // Manejar preflight CORS
                response = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
                
            } else if (Method.POST.equals(session.getMethod()) && "/print".equalsIgnoreCase(session.getUri())) {
                // Manejar solicitud de impresión
                response = handlePrintRequest(session);
                
            } else {
                // Otras rutas
                response = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", 
                    "Ruta no encontrada. Use POST /print para imprimir.");
            }
            
            // Agregar headers CORS
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            response.addHeader("Access-Control-Allow-Headers", "Content-Type");
            
            return response;
        }

        private Response handlePrintRequest(IHTTPSession session) {
            try {
                updateStatus("📄 Procesando solicitud de impresión...");
                
                // Leer el cuerpo de la petición
                HashMap<String, String> files = new HashMap<>();
                session.parseBody(files);
                
                String base64Image = null;
                
                // Intentar obtener los datos del cuerpo
                if (files.containsKey("postData")) {
                    base64Image = files.get("postData");
                } else {
                    // Si no hay postData, intentar leer directamente del cuerpo
                    HashMap<String, String> params = new HashMap<>();
                    session.parseBody(params);
                    base64Image = params.get("data");
                }

                if (base64Image == null || base64Image.trim().isEmpty()) {
                    updateStatus("❌ Error: No se recibió contenido para imprimir");
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", 
                        "No se recibió contenido para imprimir.");
                }

                Log.d(TAG, "Datos Base64 recibidos, longitud: " + base64Image.length());

                // Limpiar el string Base64 (remover prefijos si existen)
                base64Image = cleanBase64String(base64Image);

                // Decodificar la imagen
                byte[] decodedBytes;
                try {
                    decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Error decodificando Base64", e);
                    updateStatus("❌ Error: Datos Base64 inválidos");
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", 
                        "Error: Los datos Base64 no son válidos.");
                }

                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                if (bitmap != null) {
                    // Procesar la impresión en un hilo separado
                    new Thread(() -> printImageAsync(bitmap)).start();
                    
                    updateStatus("✅ Imagen recibida y enviada a impresora\n\nEsperando siguiente solicitud...");
                    return newFixedLengthResponse("Imagen enviada a la impresora exitosamente.");
                    
                } else {
                    Log.e(TAG, "No se pudo decodificar la imagen");
                    updateStatus("❌ Error: No se pudo procesar la imagen");
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", 
                        "Error: No se pudo procesar la imagen recibida.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error procesando la petición de impresión", e);
                updateStatus("❌ Error interno: " + e.getMessage());
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", 
                    "Error interno del servidor: " + e.getMessage());
            }
        }

        private String cleanBase64String(String base64) {
            // Remover prefijos comunes de datos URI
            if (base64.startsWith("data:image/")) {
                int commaIndex = base64.indexOf(',');
                if (commaIndex > 0) {
                    base64 = base64.substring(commaIndex + 1);
                }
            }
            
            // Remover espacios en blanco y caracteres de nueva línea
            return base64.replaceAll("\\s", "");
        }
    }

    private void printImageAsync(Bitmap bitmap) {
        try {
            updateStatus("🔌 Conectando con la impresora...");
            
            if (!usbPrinterManager.connectToPrinter()) {
                updateStatus("❌ Error: Impresora no conectada\n\nVerifica:\n• Cable USB OTG conectado\n• Impresora encendida\n• Permisos USB otorgados");
                showToast("Error: Impresora no conectada");
                return;
            }
            
            updateStatus("🖨️ Imprimiendo...");
            
            // Convertir imagen a formato ESC/POS
            byte[] printData = EscPosImageConverter.bitmapToEscPos(bitmap);
            
            if (printData.length == 0) {
                updateStatus("❌ Error al convertir imagen para impresión");
                showToast("Error al procesar la imagen");
                return;
            }
            
            // Enviar datos a la impresora
            usbPrinterManager.printBytes(printData);
            
            updateStatus("✅ Impresión completada exitosamente!\n\nEsperando siguiente solicitud...");
            showToast("Impresión enviada correctamente");
            
            Log.i(TAG, "Impresión completada exitosamente");
            
        } catch (IOException e) {
            Log.e(TAG, "Error al imprimir", e);
            updateStatus("❌ Error de impresión: " + e.getMessage());
            showToast("Error al enviar datos a la impresora");
            
        } catch (Exception e) {
            Log.e(TAG, "Error inesperado durante la impresión", e);
            updateStatus("❌ Error inesperado: " + e.getMessage());
            showToast("Error inesperado durante la impresión");
            
        } finally {
            usbPrinterManager.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Detener el servidor
        if (server != null) {
            server.stop();
            Log.i(TAG, "Servidor HTTP detenido");
        }
        
        // Limpiar recursos USB
        if (usbPrinterManager != null) {
            usbPrinterManager.cleanup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Mantener el servidor activo incluso cuando la app está en background
    }
}
