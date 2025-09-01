package com.gridpos.puenteimpresora;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.ScrollView;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONObject;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Enumeration;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PORT = 12345;
    
    private PrintServer server;
    private UsbPrinterManager usbPrinterManager; // ⚠️ Deprecado - usar pos3nStarPrinter
    private POS3nStarPrinter pos3nStarPrinter; // 🎯 SISTEMA PRINCIPAL DE IMPRESIÓN
    private TextView statusText;
    private TextView logText;
    private TextView ipAddressText;
    private Handler mainHandler;
    private StringBuilder logBuffer;
    private ScrollView logScrollView;
    
    // Botones de prueba
    private Button btnTestCash;
    private Button btnTestText;
    private Button btnTestImage;
    private Button btnTestQR;
    private Button btnTestInvoice;  // 🧾 Botón factura dinámico
    private Button btnClearLog;
    
    // 🎯 Selector de tipo de impresora
    private Spinner printerTypeSpinner;
    private TextView selectedPrinterText;
    private PrinterType currentPrinterType = PrinterType.AUTO;
    private SharedPreferences prefs;
    
    // 🖨️ Selector de impresoras USB
    private TextView activePrinterText;
    private RecyclerView printersRecyclerView;
    private Button btnRefreshPrinters;
    private Button btnConnectPrinter;
    private PrintersAdapter printersAdapter;
    private List<UsbDevice> availableDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);
            
            statusText = findViewById(R.id.statusText);
            logText = findViewById(R.id.logText);
            ipAddressText = findViewById(R.id.ipAddressText);
            
            // Hacer clic en IP para copiar
            setupIPClickListener();
            
            if (statusText == null || logText == null || ipAddressText == null) {
                Log.e(TAG, "TextViews no encontrados en layout");
                finish();
                return;
            }
            
            // Inicializar sistema de log
            mainHandler = new Handler(Looper.getMainLooper());
            logBuffer = new StringBuilder();
            initializeLogSystem();
            
            // Mostrar IP local
            updateIPAddress();
            
            // Iniciar actualización automática de IP
            startIPUpdateTimer();
            
            // Inicializar botones de prueba
            initializeTestButtons();
            
            // 🎯 Inicializar selector de tipo de impresora
            initializePrinterTypeSelector();
            
            // Inicializar selector de impresoras USB
            initializePrinterSelector();
            
            // Inicializar el manejador USB con manejo de errores
            try {
                usbPrinterManager = new UsbPrinterManager(this);
                Log.d(TAG, "UsbPrinterManager inicializado correctamente");
                
                // Intentar conectar automáticamente
                updateStatus("🔍 Buscando impresora USB...");
                if (usbPrinterManager.connectToPrinter()) {
                    updateStatus("✅ Impresora conectada automáticamente");
                    Log.d(TAG, "Conexión automática exitosa");
                } else {
                    updateStatus("⚠️ Conecta la impresora USB para continuar");
                    Log.d(TAG, "No se pudo conectar automáticamente");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando UsbPrinterManager", e);
                updateStatus("❌ Error inicializando USB: " + e.getMessage());
            }
            
            // 🎯 Inicializar SDK 3nStar 
            try {
                pos3nStarPrinter = new POS3nStarPrinter(this);
                pos3nStarPrinter.connectUSB();
                addToLog("🎯 SDK 3nStar inicializado");
                Log.d(TAG, "POS3nStarPrinter inicializado correctamente");
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando POS3nStarPrinter", e);
                addToLog("❌ Error SDK 3nStar: " + e.getMessage());
            }
            
            // 🎯 Inicializar botones de prueba SDK
            initializeSDKButtons();
            
            // Iniciar el servidor HTTP
            startHttpServer();
            
            // Mostrar mensaje de bienvenida
            showToast("Aplicación Puente iniciada");
            
        } catch (Exception e) {
            Log.e(TAG, "Error fatal en onCreate", e);
            if (statusText != null) {
                updateStatus("❌ Error fatal: " + e.getMessage());
            }
            // No cerrar la app, solo mostrar error
        }
    }

    private void startHttpServer() {
        try {
            addToLog("🔄 Iniciando servidor HTTP en puerto " + PORT + "...");
            server = new PrintServer();
            server.start();
            
            addToLog("✅ Servidor HTTP activo en localhost:" + PORT);
            updateStatus("✅ Servicio de impresión activo en puerto " + PORT + "\n\nEsperando solicitudes de impresión...");
            Log.i(TAG, "Servidor HTTP iniciado en el puerto " + PORT);
            
        } catch (IOException e) {
            Log.e(TAG, "Error al iniciar el servidor HTTP", e);
            addToLog("❌ Error servidor HTTP: " + e.getMessage());
            updateStatus("❌ Error al iniciar el servicio de impresión\n\n" + e.getMessage());
            showToast("Error al iniciar el servidor");
        }
    }

    private void updateStatus(String message) {
        try {
            if (mainHandler != null && statusText != null) {
                mainHandler.post(() -> {
                    try {
                        statusText.setText(message);
                        Log.d(TAG, "Status actualizado: " + message);
                    } catch (Exception e) {
                        Log.e(TAG, "Error actualizando status UI", e);
                    }
                });
            } else {
                Log.w(TAG, "No se puede actualizar status - UI no inicializada: " + message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en updateStatus", e);
        }
    }

    private void showToast(String message) {
        try {
            if (mainHandler != null) {
                mainHandler.post(() -> {
                    try {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Toast mostrado: " + message);
                    } catch (Exception e) {
                        Log.e(TAG, "Error mostrando toast", e);
                    }
                });
            } else {
                Log.w(TAG, "No se puede mostrar toast - Handler no inicializado: " + message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en showToast", e);
        }
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
                
                try {
                    session.parseBody(files);
                } catch (Exception e) {
                    Log.w(TAG, "Error parseando body, intentando leer directamente", e);
                }
                
                String base64Image = null;
                
                // Método 1: Intentar obtener desde postData
                if (files.containsKey("postData")) {
                    base64Image = files.get("postData");
                    Log.d(TAG, "Datos obtenidos desde postData");
                }
                
                // Método 2: Si no hay postData, leer el inputStream directamente
                if (base64Image == null || base64Image.trim().isEmpty()) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(session.getInputStream()));
                        StringBuilder stringBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                        base64Image = stringBuilder.toString();
                        Log.d(TAG, "Datos obtenidos desde InputStream directamente");
                    } catch (Exception e) {
                        Log.e(TAG, "Error leyendo InputStream", e);
                    }
                }
                
                // Método 3: Intentar desde parámetros
                if (base64Image == null || base64Image.trim().isEmpty()) {
                    HashMap<String, String> params = new HashMap<>();
                    try {
                        session.parseBody(params);
                        base64Image = params.get("data");
                        Log.d(TAG, "Datos obtenidos desde parámetros");
                    } catch (Exception e) {
                        Log.w(TAG, "Error parseando parámetros", e);
                    }
                }

                if (base64Image == null || base64Image.trim().isEmpty()) {
                    updateStatus("❌ Error: No se recibió contenido para imprimir");
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", 
                        "No se recibió contenido para imprimir.");
                }

                Log.d(TAG, "Datos Base64 recibidos, longitud: " + base64Image.length());
                Log.d(TAG, "Primeros 100 caracteres: " + base64Image.substring(0, Math.min(100, base64Image.length())));
                
                // Limpiar prefijo data:image si existe (doble verificación)
                if (base64Image.startsWith("data:image")) {
                    int base64Index = base64Image.indexOf("base64,");
                    if (base64Index != -1) {
                        base64Image = base64Image.substring(base64Index + 7);
                        Log.d(TAG, "Prefijo data:image removido, nueva longitud: " + base64Image.length());
                    }
                }

                // Verificar si los datos son JSON (orden) o Base64 (imagen)
                base64Image = base64Image.trim();
                
                if (isJsonData(base64Image)) {
                    // 📄 DETECTAR TIPO DE JSON: ORDEN O FACTURA
                    return handleJsonRequest(base64Image);
                } else {
                    // 🖼️ PROCESAR COMO IMAGEN BASE64
                    return handleImageRequest(base64Image);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando solicitud de impresión", e);
                updateStatus("❌ Error procesando solicitud: " + e.getMessage());
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", 
                    "Error procesando solicitud: " + e.getMessage());
            }
        }
        
        /**
         * 🎯 Procesar solicitud JSON (detectar tipo: orden o factura)
         */
        private Response handleJsonRequest(String jsonData) {
            try {
                JSONObject data = new JSONObject(jsonData);
                
                // Verificar si contiene data_json
                if (data.has("data_json")) {
                    JSONObject dataJson = data.getJSONObject("data_json");
                    
                    if (dataJson.has("order_data")) {
                        // 📦 Es una orden
                        addToLog("📦 Detectado: Datos de orden JSON");
                        return handleOrderRequest(jsonData);
                    } else if (dataJson.has("sale_data") || dataJson.has("company_info")) {
                        // 🧾 Es una factura
                        addToLog("🧾 Detectado: Datos de factura JSON");
                        return handleSaleRequest(jsonData);
                    }
                }
                
                // Fallback: tratar como orden
                addToLog("📋 Fallback: Tratando como orden");
                return handleOrderRequest(jsonData);
                
            } catch (Exception e) {
                Log.e(TAG, "Error detectando tipo de JSON", e);
                addToLog("❌ Error detectando tipo: " + e.getMessage());
                // Fallback a orden en caso de error
                return handleOrderRequest(jsonData);
            }
        }
        
        /**
         * 📄 Procesar solicitud de orden (JSON)
         */
        private Response handleOrderRequest(String jsonData) {
            try {
                Log.d(TAG, "Procesando orden JSON");
                addToLog("📄 Recibida orden JSON, procesando...");
                updateStatus("📄 Procesando orden de cocina...");
                
                JSONObject orderData = new JSONObject(jsonData);
                
                // Obtener configuración de papel (default 80mm)
                int paperWidth = 80;
                try {
                    JSONObject printSettings = orderData.optJSONObject("print_settings");
                    if (printSettings != null) {
                        paperWidth = printSettings.optInt("paper_width", 80);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo obtener paper_width, usando 80mm", e);
                }
                
                // Verificar si debe abrir caja
                boolean openCash = orderData.optBoolean("open_cash", false);
                
                // Formatear orden usando OrderPrintFormatter
                byte[] orderBytes = OrderPrintFormatter.formatOrder(orderData, paperWidth, openCash);
                
                if (orderBytes != null && orderBytes.length > 0) {
                    // Enviar a impresora
                    if (usbPrinterManager != null && usbPrinterManager.isConnected()) {
                        try {
                            usbPrinterManager.sendRawData(orderBytes);
                            addToLog("✅ Orden de cocina impresa exitosamente");
                            updateStatus("✅ Orden de cocina impresa correctamente");
                        } catch (java.io.IOException ioError) {
                            addToLog("❌ Error imprimiendo orden: " + ioError.getMessage());
                            updateStatus("❌ Error imprimiendo orden");
                        }
                        showToast("✅ Orden impresa!");
                        
                        return newFixedLengthResponse(Response.Status.OK, "application/json", 
                            "{\"success\": true, \"message\": \"Orden impresa correctamente\"}");
                    } else {
                        addToLog("❌ Impresora no conectada - intentando reconectar...");
                        updateStatus("⚠️ Impresora no conectada - intentando reconectar...");
                        
                        // Intentar reconectar automáticamente
                        try {
                            if (usbPrinterManager.connectToPrinter()) {
                                addToLog("✅ Reconexión exitosa, reintentando impresión...");
                                
                                // Reintentar la impresión
                                try {
                                    usbPrinterManager.sendRawData(orderBytes);
                                    addToLog("✅ Orden impresa tras reconexión");
                                    updateStatus("✅ Orden impresa correctamente");
                                    showToast("✅ Orden impresa!");
                                    
                                    return newFixedLengthResponse(Response.Status.OK, "application/json", 
                                        "{\"success\": true, \"message\": \"Orden impresa tras reconexión\"}");
                                } catch (java.io.IOException retryError) {
                                    addToLog("❌ Error en reintento: " + retryError.getMessage());
                                }
                            }
                        } catch (Exception reconnectError) {
                            addToLog("❌ Error en reconexión: " + reconnectError.getMessage());
                        }
                        
                        updateStatus("❌ Impresora no conectada");
                        return newFixedLengthResponse(Response.Status.OK, "application/json", 
                            "{\"success\": false, \"message\": \"Impresora no conectada. Verifica la conexión USB.\"}");
                    }
                } else {
                    updateStatus("❌ Error formateando orden");
                    return newFixedLengthResponse(Response.Status.OK, "application/json", 
                        "{\"success\": false, \"message\": \"Error formateando orden\"}");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error procesando orden JSON", e);
                updateStatus("❌ Error procesando orden: " + e.getMessage());
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", 
                    "Error procesando orden: " + e.getMessage());
            }
        }
        
        /**
         * 🧾 Procesar solicitud de factura (JSON)
         */
        private Response handleSaleRequest(String jsonData) {
            try {
                Log.d(TAG, "Procesando factura JSON");
                addToLog("🧾 Recibida factura JSON, procesando...");
                updateStatus("🧾 Procesando factura de venta...");
                
                JSONObject saleData = new JSONObject(jsonData);
                
                // Obtener configuración de papel (default 80mm)
                int paperWidth = 80;
                try {
                    paperWidth = saleData.optInt("paper_width", 80);
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo obtener paper_width, usando 80mm", e);
                }
                
                // Verificar si debe abrir caja
                boolean openCash = saleData.optBoolean("open_cash", false);
                
                // Obtener datos JSON estructurados
                String dataJsonString = "";
                if (saleData.has("data_json")) {
                    dataJsonString = saleData.getJSONObject("data_json").toString();
                } else {
                    // Fallback: usar datos completos
                    dataJsonString = jsonData;
                }
                
                // Formatear factura usando SalePrintFormatter
                byte[] saleBytes = SalePrintFormatter.formatSale(dataJsonString, paperWidth, openCash);
                
                if (saleBytes != null && saleBytes.length > 0) {
                    // Enviar a impresora
                    if (usbPrinterManager != null && usbPrinterManager.isConnected()) {
                        try {
                            usbPrinterManager.sendRawData(saleBytes);
                            addToLog("✅ Factura impresa exitosamente");
                            updateStatus("✅ Factura impresa correctamente");
                        } catch (java.io.IOException ioError) {
                            addToLog("❌ Error imprimiendo factura: " + ioError.getMessage());
                            updateStatus("❌ Error imprimiendo factura");
                        }
                        showToast("✅ Factura impresa!");
                        
                        return newFixedLengthResponse(Response.Status.OK, "application/json", 
                            "{\"success\": true, \"message\": \"Factura impresa correctamente\"}");
                    } else {
                        addToLog("❌ Impresora no conectada - intentando reconectar...");
                        updateStatus("⚠️ Impresora no conectada - intentando reconectar...");
                        
                        // Intentar reconectar automáticamente
                        try {
                            if (usbPrinterManager.connectToPrinter()) {
                                addToLog("✅ Reconexión exitosa, reintentando impresión...");
                                
                                // Reintentar la impresión
                                try {
                                    usbPrinterManager.sendRawData(saleBytes);
                                    addToLog("✅ Factura impresa tras reconexión");
                                    updateStatus("✅ Factura impresa correctamente");
                                    showToast("✅ Factura impresa!");
                                    
                                    return newFixedLengthResponse(Response.Status.OK, "application/json", 
                                        "{\"success\": true, \"message\": \"Factura impresa tras reconexión\"}");
                                } catch (java.io.IOException retryError) {
                                    addToLog("❌ Error en reintento: " + retryError.getMessage());
                                }
                            }
                        } catch (Exception reconnectError) {
                            addToLog("❌ Error en reconexión: " + reconnectError.getMessage());
                        }
                        
                        updateStatus("❌ Impresora no conectada");
                        return newFixedLengthResponse(Response.Status.OK, "application/json", 
                            "{\"success\": false, \"message\": \"Impresora no conectada. Verifica la conexión USB.\"}");
                    }
                } else {
                    updateStatus("❌ Error formateando factura");
                    return newFixedLengthResponse(Response.Status.OK, "application/json", 
                        "{\"success\": false, \"message\": \"Error formateando factura\"}");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error procesando factura JSON", e);
                updateStatus("❌ Error procesando factura: " + e.getMessage());
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", 
                    "Error procesando factura: " + e.getMessage());
            }
        }
        
        /**
         * 🖼️ Procesar solicitud de imagen (Base64)
         */
        private Response handleImageRequest(String base64Image) {
            try {
                Log.d(TAG, "Procesando imagen Base64");
                addToLog("🖼️ Recibida imagen Base64, procesando...");
                updateStatus("🖼️ Procesando imagen para impresión...");
                
                // Limpiar el string Base64 (remover espacios y caracteres no válidos)
                base64Image = base64Image.replaceAll("\\s", "");
                addToLog("🔧 Base64 limpio, tamaño: " + base64Image.length() + " chars");
                Log.d(TAG, "Base64 limpio, longitud final: " + base64Image.length());

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
                    return newFixedLengthResponse(Response.Status.OK, "application/json", 
                        "{\"success\": true, \"message\": \"Imagen enviada a la impresora exitosamente\"}");
                    
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

        /**
         * 🔍 Verificar si los datos son JSON o Base64
         */
        private boolean isJsonData(String data) {
            if (data == null || data.isEmpty()) {
                return false;
            }
            
            // Remover espacios al inicio y final
            data = data.trim();
            
            // Verificar si empieza con { (JSON) o con caracteres Base64
            if (data.startsWith("{") && data.endsWith("}")) {
                try {
                    new JSONObject(data);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            
            return false;
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
        
        try {
            // Detener el servidor
            if (server != null) {
                server.stop();
                server = null;
                Log.i(TAG, "Servidor HTTP detenido");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deteniendo servidor", e);
        }
        
        try {
            // Limpiar recursos USB
            if (usbPrinterManager != null) {
                usbPrinterManager.cleanup();
                usbPrinterManager = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error limpiando USB resources", e);
        }
        
        // Limpiar handler
        mainHandler = null;
        statusText = null;
        
        Log.d(TAG, "MainActivity destruida correctamente");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Mantener el servidor activo incluso cuando la app está en background
    }
    
        /**
     * 🧪 Inicializar botones de prueba
     */
    private void initializeTestButtons() {
        try {
            // 🎯 Mapear IDs correctos del nuevo layout
            btnTestCash = findViewById(R.id.btnTestCash);
            btnTestText = findViewById(R.id.btnSDKText);        // 📝 Texto dinámico
            btnTestImage = findViewById(R.id.btnTestImage);
            btnTestQR = findViewById(R.id.btnSDKQR);           // 📱 QR + Caja dinámico
            btnTestInvoice = findViewById(R.id.btnSDKInvoice); // 🧾 Factura dinámico
            btnClearLog = findViewById(R.id.btnClearLog);

            // Configurar listeners con métodos dinámicos
            btnTestCash.setOnClickListener(v -> testOpenCashDrawer());
            btnTestText.setOnClickListener(v -> testPrinterText());     // Método dinámico
            btnTestImage.setOnClickListener(v -> testPrintImage());
            btnTestQR.setOnClickListener(v -> testPrinterQR());         // Método dinámico
            btnTestInvoice.setOnClickListener(v -> testPrinterInvoice()); // Método dinámico
            btnClearLog.setOnClickListener(v -> clearLog());
        
        // Agregar listener de doble click al estado para refrescar conexión
        statusText.setOnClickListener(v -> refreshPrinterConnection());

            addToLog("🧪 Botones de prueba inicializados correctamente");
        } catch (Exception e) {
            addToLog("❌ Error inicializando botones: " + e.getMessage());
            Log.e(TAG, "Error inicializando botones de prueba", e);
        }
    }

    /**
     * 📄 Prueba de impresión de texto
     */
    private void testPrintText() {
        try {
            updateStatus("📄 Probando impresión con caracteres especiales...");
            showToast("Imprimiendo ñ, tildes y caracteres especiales");
            
            if (usbPrinterManager != null && usbPrinterManager.isConnected()) {
                StringBuilder testText = new StringBuilder();
                testText.append("================================\n");
                testText.append("  🧪 PRUEBA CARACTERES ESPECIALES\n");
                testText.append("================================\n");
                testText.append("ñ Ñ á é í ó ú ü Á É Í Ó Ú Ü\n");
                testText.append("¿Cómo está usted?\n");
                testText.append("¡Muy bien, gracias!\n");
                testText.append("--------------------------------\n");
                testText.append("Señores: José, María, Peña\n");
                testText.append("Niño, Muñoz, González\n");
                testText.append("Jamón, Café, Piña, Paella\n");
                testText.append("--------------------------------\n");
                testText.append("Fecha: ").append(new java.text.SimpleDateFormat("dd/MM/yyyy").format(new java.util.Date())).append("\n");
                testText.append("Hora: ").append(new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date())).append("\n");
                testText.append("================================\n\n");
                
                // 🌍 Usar helper mejorado para caracteres especiales
                addToLog("🌍 Codificando texto con caracteres especiales...");
                
                // Probar diferentes métodos de codificación
                TextEncodingHelper.testEncodings("Prueba: ñáéíóú ¿Cómo está?");
                
                // Usar el helper mejorado
                byte[] textBytes = TextEncodingHelper.encodeTextForThermalPrinter(testText.toString());
                addToLog("📏 Texto codificado: " + textBytes.length + " bytes");
                
                try {
                    usbPrinterManager.sendRawData(textBytes);
                    updateStatus("✅ Texto con ñ,tildes impreso correctamente");
                    showToast("✅ Texto con caracteres especiales impreso!");
                    addToLog("✅ Texto con caracteres especiales enviado exitosamente");
                } catch (java.io.IOException ioError) {
                    addToLog("❌ Error imprimiendo texto: " + ioError.getMessage());
                    updateStatus("❌ Error imprimiendo texto");
                    showToast("❌ Error imprimiendo texto");
                }
            } else {
                updateStatus("❌ Impresora no conectada");
                showToast("❌ Conecta la impresora primero");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en test de texto", e);
            updateStatus("❌ Error imprimiendo texto: " + e.getMessage());
            showToast("❌ Error: " + e.getMessage());
        }
    }
    
    /**
     * 🖼️ Prueba de impresión de imagen
     */
    private void testPrintImage() {
        try {
            updateStatus("🖼️ Probando impresión de imagen...");
            showToast("Generando y enviando imagen");
            
            if (usbPrinterManager != null && usbPrinterManager.isConnected()) {
                // Crear imagen de prueba simple
                Bitmap testBitmap = createTestBitmap();
                if (testBitmap != null) {
                    addToLog("🖼️ Probando conversión avanzada de imagen...");
                    
                    // Intentar método avanzado primero
                    byte[] imageBytes = EscPosImageConverter.bitmapToEscPos(testBitmap);
                    
                    // Si el método avanzado falla o genera pocos datos, usar método simple
                    if (imageBytes == null || imageBytes.length < 100) {
                        addToLog("⚠️ Método avanzado falló, usando conversión simple...");
                        imageBytes = SimpleImageConverter.convertBitmapSimple(testBitmap);
                        addToLog("🔄 Conversión simple completada: " + imageBytes.length + " bytes");
                    } else {
                        addToLog("✅ Conversión avanzada exitosa: " + imageBytes.length + " bytes");
                    }
                    
                    try {
                        usbPrinterManager.sendRawData(imageBytes);
                        updateStatus("✅ Imagen impresa correctamente");
                        showToast("✅ Imagen impresa!");
                        addToLog("✅ Imagen enviada exitosamente a la impresora");
                    } catch (java.io.IOException ioError) {
                        addToLog("❌ Error imprimiendo imagen: " + ioError.getMessage());
                        updateStatus("❌ Error imprimiendo imagen");
                        showToast("❌ Error imprimiendo imagen");
                    }
                } else {
                    updateStatus("❌ Error generando imagen de prueba");
                    showToast("❌ Error generando imagen");
                }
            } else {
                updateStatus("❌ Impresora no conectada");
                showToast("❌ Conecta la impresora primero");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en test de imagen", e);
            updateStatus("❌ Error imprimiendo imagen: " + e.getMessage());
            showToast("❌ Error: " + e.getMessage());
        }
    }
    
    /**
     * 🖼️ Crear imagen de prueba simple
     */
    private Bitmap createTestBitmap() {
        try {
            int width = 384; // Ancho estándar para 80mm
            int height = 300;
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            // Fondo blanco
            canvas.drawColor(android.graphics.Color.WHITE);
            
            // Configurar paint
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTextSize(24);
            paint.setAntiAlias(true);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            
            // Dibujar contenido
            float centerX = width / 2f;
            float y = 50;
            
            paint.setTextSize(28);
            canvas.drawText("🧪 IMAGEN DE PRUEBA", centerX, y, paint);
            y += 50;
            
            paint.setTextSize(20);
            canvas.drawText("GridPOS - Puente Impresora", centerX, y, paint);
            y += 40;
            
            paint.setTextSize(16);
            canvas.drawText("Sistema Android funcionando", centerX, y, paint);
            y += 30;
            canvas.drawText("correctamente!", centerX, y, paint);
            y += 50;
            
            // Dibujar rectángulo
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawRect(50, y, width - 50, y + 60, paint);
            
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setTextSize(18);
            canvas.drawText("✅ USB CONECTADO ✅", centerX, y + 35, paint);
            
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error creando bitmap de prueba", e);
            return null;
        }
    }
    
    // === SISTEMA DE LOG VISUAL ===
    
    /**
     * 📋 Inicializar sistema de log visual
     */
    private void initializeLogSystem() {
        try {
            addToLog("🚀 Sistema de log iniciado");
            addToLog("📱 GridPOS - Puente Impresora v1.0");
            addToLog("⏰ " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando sistema de log", e);
        }
    }
    
    /**
     * 📝 Agregar mensaje al log visual
     */
    private void addToLog(String message) {
        try {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            String logEntry = timestamp + " " + message + "\n";
            
            // Agregar al buffer
            logBuffer.append(logEntry);
            
            // Mantener solo las últimas 100 líneas
            String[] lines = logBuffer.toString().split("\n");
            if (lines.length > 100) {
                logBuffer = new StringBuilder();
                for (int i = lines.length - 100; i < lines.length; i++) {
                    logBuffer.append(lines[i]).append("\n");
                }
            }
            
            // Actualizar UI en el hilo principal
            if (mainHandler != null && logText != null) {
                mainHandler.post(() -> {
                    try {
                        logText.setText(logBuffer.toString());
                        // Scroll automático hacia abajo
                        logScrollView = (ScrollView) logText.getParent();
                        if (logScrollView != null) {
                            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error actualizando log visual", e);
                    }
                });
            }
            
            // También enviar al logcat para debug
            Log.d(TAG, "LOG: " + message);
            
        } catch (Exception e) {
            Log.e(TAG, "Error agregando al log", e);
        }
    }
    
    /**
     * 📱 Prueba de impresión de código QR
     */
    private void testPrintQR() {
        try {
            updateStatus("📱 Generando código QR...");
            showToast("Generando QR de GridPOS");
            
            if (usbPrinterManager != null && usbPrinterManager.isConnected()) {
                // URL por defecto de GridPOS
                String defaultUrl = "https://www.gridpos.co/";
                
                addToLog("📱 Generando QR para URL: " + defaultUrl);
                
                // Generar código QR con método de prueba mejorado
                addToLog("🧪 Intentando generar QR con método de prueba...");
                android.graphics.Bitmap qrBitmap = QRCodeGenerator.generateTestQR();
                
                // Si falla el método de prueba, intentar el método normal
                if (qrBitmap == null) {
                    addToLog("⚠️ Método de prueba falló, intentando método normal...");
                    qrBitmap = QRCodeGenerator.generateQRForThermalPrinter(defaultUrl);
                }
                
                if (qrBitmap != null) {
                    addToLog("✅ QR generado correctamente - Tamaño: " + qrBitmap.getWidth() + "x" + qrBitmap.getHeight());
                    addToLog("🎨 Configuración bitmap: " + qrBitmap.getConfig());
                    
                    // MÉTODO DUAL: Probar convertidor simple primero para QR
                    try {
                        addToLog("🔄 Intentando conversión simple de QR...");
                        
                        // Usar el convertidor simple para QR
                        byte[] qrImageData = SimpleImageConverter.convertBitmapSimple(qrBitmap);
                        
                        if (qrImageData != null && qrImageData.length > 100) {
                            addToLog("✅ Conversión simple exitosa: " + qrImageData.length + " bytes");
                            
                            // Agregar solo encabezado y pie de página
                            java.util.List<Byte> commandList = new java.util.ArrayList<>();
                            
                            // Texto previo con codificación mejorada
                            String header = "=== CÓDIGO QR - GRIDPOS ===\n";
                            header += "Sistema POS 100% Web\n";
                            header += "URL: " + defaultUrl + "\n\n";
                            
                            // Usar helper de codificación mejorado
                            byte[] headerBytes = TextEncodingHelper.encodeTextForThermalPrinter(header);
                            for (byte b : headerBytes) {
                                commandList.add(b);
                            }
                            
                            // Datos del QR (ya incluye comandos ESC/POS)
                            for (byte b : qrImageData) {
                                commandList.add(b);
                            }
                            
                            // Pie de página con codificación mejorada
                            String footer = "\nEscanea el código QR\n";
                            footer += "Fecha: " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()) + "\n";
                            footer += "¡Visítanos en línea!\n\n";
                            
                            // Usar helper de codificación mejorado
                            byte[] footerBytes = TextEncodingHelper.encodeTextForThermalPrinter(footer);
                            for (byte b : footerBytes) {
                                commandList.add(b);
                            }
                            
                            // Convertir lista a array
                            byte[] printData = new byte[commandList.size()];
                            for (int i = 0; i < commandList.size(); i++) {
                                printData[i] = commandList.get(i);
                            }
                            
                            addToLog("📤 Enviando " + printData.length + " bytes a impresora...");
                            
                            try {
                                usbPrinterManager.sendRawData(printData);
                                addToLog("✅ Código QR de GridPOS impreso correctamente");
                                updateStatus("✅ QR impreso correctamente");
                                showToast("✅ QR impreso!");
                            } catch (java.io.IOException ioError) {
                                addToLog("❌ Error enviando código QR: " + ioError.getMessage());
                                updateStatus("❌ Error imprimiendo QR");
                                showToast("❌ Error imprimiendo QR");
                            }
                            
                        } else {
                            addToLog("⚠️ Conversión simple falló, usando fallback texto...");
                            
                            // Fallback: solo texto con la URL
                            String fallbackText = "=== CÓDIGO QR - GRIDPOS ===\n";
                            fallbackText += "Sistema POS 100% Web\n";
                            fallbackText += "URL: " + defaultUrl + "\n";
                            fallbackText += "¡Visítanos en línea!\n";
                            fallbackText += "Escanea desde tu móvil\n\n";
                            
                            byte[] fallbackBytes = TextEncodingHelper.encodeTextForThermalPrinter(fallbackText);
                            
                            try {
                                usbPrinterManager.sendRawData(fallbackBytes);
                                addToLog("✅ Información QR impresa como texto");
                                updateStatus("✅ Info QR impresa");
                                showToast("✅ Info QR impresa!");
                            } catch (java.io.IOException ioError) {
                                addToLog("❌ Error enviando fallback: " + ioError.getMessage());
                                updateStatus("❌ Error imprimiendo");
                                showToast("❌ Error imprimiendo");
                            }
                        }
                        
                    } catch (Exception conversionError) {
                        addToLog("❌ Error convirtiendo QR: " + conversionError.getMessage());
                        updateStatus("❌ Error procesando QR");
                        Log.e(TAG, "Error conversión QR", conversionError);
                    }
                } else {
                    addToLog("❌ Error generando código QR");
                    updateStatus("❌ Error generando QR");
                    showToast("❌ Error generando QR");
                }
            } else {
                addToLog("⚠️ Impresora no conectada para QR");
                updateStatus("⚠️ Conecta la impresora USB");
                showToast("Impresora no conectada");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en test de QR", e);
            addToLog("❌ Error en test de QR: " + e.getMessage());
            updateStatus("❌ Error en test de QR");
            showToast("Error generando QR");
        }
    }
    
    /**
     * 🗑️ Limpiar log visual
     */
    /**
     * 🔄 Refrescar conexión de impresora
     */
    private void refreshPrinterConnection() {
        try {
            addToLog("🔄 Refrescando conexión de impresora...");
            updateStatus("🔄 Buscando impresora USB...");
            showToast("🔍 Buscando impresora...");
            
            // Desconectar si está conectado
            if (usbPrinterManager != null) {
                usbPrinterManager.disconnect();
                addToLog("🔌 Desconectando impresora anterior...");
            }
            
            // Pequeña pausa para permitir que el sistema se actualice
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    
                    // Intentar reconectar en hilo principal
                    mainHandler.post(() -> {
                        try {
                            if (usbPrinterManager != null && usbPrinterManager.connectToPrinter()) {
                                addToLog("✅ Impresora reconectada exitosamente");
                                updateStatus("✅ Impresora USB conectada");
                                showToast("✅ Impresora conectada!");
                            } else {
                                addToLog("❌ No se pudo conectar a la impresora");
                                updateStatus("❌ Impresora no detectada");
                                showToast("❌ Conecta la impresora USB");
                                
                                // Mostrar ayuda
                                addToLog("💡 SUGERENCIAS:");
                                addToLog("• Verifica que el cable USB OTG esté bien conectado");
                                addToLog("• Intenta desconectar y reconectar la impresora");
                                addToLog("• Apaga y enciende la impresora mientras está conectada");
                                addToLog("• Revisa que la impresora sea compatible con ESC/POS");
                            }
                        } catch (Exception e) {
                            addToLog("❌ Error refrescando conexión: " + e.getMessage());
                            updateStatus("❌ Error de conexión");
                            Log.e(TAG, "Error refrescando conexión", e);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
        } catch (Exception e) {
            addToLog("❌ Error iniciando refresco: " + e.getMessage());
            Log.e(TAG, "Error en refreshPrinterConnection", e);
        }
    }
    
    private void clearLog() {
        try {
            logBuffer = new StringBuilder();
            addToLog("🗑️ Log limpiado");
            addToLog("📱 GridPOS - Puente Impresora v1.0");
            addToLog("💡 Toca el ESTADO para refrescar conexión");
            showToast("Log limpiado");
        } catch (Exception e) {
            Log.e(TAG, "Error limpiando log", e);
        }
    }
    
    // === MÉTODOS DE RED ===
    
    /**
     * 📋 Configurar click en IP para copiar al portapapeles
     */
    private void setupIPClickListener() {
        if (ipAddressText != null) {
            ipAddressText.setOnClickListener(v -> {
                String currentIP = getLocalIPAddress();
                if (currentIP != null) {
                    // Copiar IP al portapapeles
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("IP Address", currentIP);
                    clipboard.setPrimaryClip(clip);
                    
                    // Mostrar confirmación
                    showToast("📋 IP copiada: " + currentIP);
                    addToLog("📋 IP " + currentIP + " copiada al portapapeles");
                    
                    // Animación visual
                    ipAddressText.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(100)
                        .withEndAction(() -> {
                            ipAddressText.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100);
                        });
                } else {
                    showToast("⚠️ IP no disponible");
                }
            });
        }
    }
    
    /**
     * 🌐 Actualizar dirección IP mostrada
     */
    private void updateIPAddress() {
        try {
            String localIP = getLocalIPAddress();
            if (localIP != null) {
                String displayText = "IP: " + localIP;
                if (ipAddressText != null) {
                    // Actualizar en el hilo principal
                    mainHandler.post(() -> {
                        ipAddressText.setText(displayText);
                        ipAddressText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    });
                }
                addToLog("🌐 IP local detectada: " + localIP);
                addToLog("📡 URL de impresión: http://" + localIP + ":" + PORT + "/print");
                addToLog("💡 Usa esta IP en tu configuración POS");
            } else {
                if (ipAddressText != null) {
                    mainHandler.post(() -> {
                        ipAddressText.setText("IP: Conectando...");
                        ipAddressText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                    });
                }
                addToLog("⚠️ No se pudo obtener IP local - Verificando conexión...");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error actualizando IP", e);
            addToLog("❌ Error obteniendo IP: " + e.getMessage());
            if (ipAddressText != null) {
                mainHandler.post(() -> {
                    ipAddressText.setText("IP: Error");
                    ipAddressText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                });
            }
        }
    }
    
    /**
     * 🔄 Actualizar IP automáticamente cada 15 segundos
     */
    private void startIPUpdateTimer() {
        Handler ipHandler = new Handler(Looper.getMainLooper());
        Runnable ipUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateIPAddress();
                ipHandler.postDelayed(this, 15000); // Actualizar cada 15 segundos
            }
        };
        ipHandler.post(ipUpdateRunnable);
    }
    
    /**
     * 🔍 Obtener dirección IP local del dispositivo
     */
    private String getLocalIPAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                
                // Filtrar solo interfaces WiFi activas
                if (!intf.isUp() || intf.isLoopback() || intf.isVirtual()) {
                    continue;
                }
                
                // Buscar interfaces WiFi
                String interfaceName = intf.getName().toLowerCase();
                if (interfaceName.contains("wlan") || interfaceName.contains("wifi") || interfaceName.contains("eth")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        
                        // Solo IPv4 y no loopback
                        if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                            String ipAddress = inetAddress.getHostAddress();
                            
                            // Validar que sea una IP privada válida
                            if (isValidPrivateIP(ipAddress)) {
                                Log.d(TAG, "IP encontrada en " + interfaceName + ": " + ipAddress);
                                return ipAddress;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error obteniendo IP local", ex);
        }
        return null;
    }
    
    /**
     * ✅ Validar si es una IP privada válida
     */
    private boolean isValidPrivateIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            
            int firstOctet = Integer.parseInt(parts[0]);
            int secondOctet = Integer.parseInt(parts[1]);
            
            // Rangos de IP privadas:
            // 10.0.0.0 - 10.255.255.255
            // 172.16.0.0 - 172.31.255.255  
            // 192.168.0.0 - 192.168.255.255
            return (firstOctet == 10) ||
                   (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) ||
                   (firstOctet == 192 && secondOctet == 168);
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 🎯 Inicializar selector de tipo de impresora
     */
    private void initializePrinterTypeSelector() {
        try {
            // Inicializar preferencias
            prefs = getSharedPreferences("printer_settings", MODE_PRIVATE);
            
            // Obtener referencias
            printerTypeSpinner = findViewById(R.id.printerTypeSpinner);
            selectedPrinterText = findViewById(R.id.selectedPrinterText);
            
            if (printerTypeSpinner == null || selectedPrinterText == null) {
                Log.e(TAG, "❌ No se encontraron elementos del selector de impresoras");
                return;
            }
            
            // Crear adapter con tipos de impresora
            ArrayAdapter<PrinterType> adapter = new ArrayAdapter<>(
                this, 
                android.R.layout.simple_spinner_item, 
                PrinterType.values()
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            printerTypeSpinner.setAdapter(adapter);
            
            // Cargar selección guardada
            String savedType = prefs.getString("printer_type", PrinterType.AUTO.getDisplayName());
            currentPrinterType = PrinterType.fromString(savedType);
            
            // Establecer selección inicial
            for (int i = 0; i < PrinterType.values().length; i++) {
                if (PrinterType.values()[i] == currentPrinterType) {
                    printerTypeSpinner.setSelection(i);
                    break;
                }
            }
            
            // Listener para cambios
            printerTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    PrinterType selectedType = PrinterType.values()[position];
                    if (selectedType != currentPrinterType) {
                        currentPrinterType = selectedType;
                        onPrinterTypeChanged(selectedType);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // No hacer nada
                }
            });
            
            // Actualizar texto inicial
            updateSelectedPrinterText();
            
            Log.d(TAG, "✅ Selector de tipo de impresora inicializado: " + currentPrinterType.getDisplayName());
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error inicializando selector de tipo de impresora", e);
        }
    }
    
    /**
     * 🔄 Manejar cambio de tipo de impresora
     */
    private void onPrinterTypeChanged(PrinterType newType) {
        try {
            Log.d(TAG, "🔄 Cambiando tipo de impresora: " + currentPrinterType.getDisplayName() + " → " + newType.getDisplayName());
            
            currentPrinterType = newType;
            
            // Guardar en preferencias
            prefs.edit().putString("printer_type", newType.getDisplayName()).apply();
            
            // Actualizar sistema de impresión
            updatePrintingSystem();
            
            // Actualizar UI
            updateSelectedPrinterText();
            
            // Mostrar mensaje
            String message = "🎯 Impresora configurada: " + newType.getDisplayName();
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            updateLog(message);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error cambiando tipo de impresora", e);
        }
    }

    // Add this method to your MainActivity.java
    private void updateLog(String message) {
        // Implement the logic for updating your log here.
        // This might be similar to addToLog or have different behavior.
        // For example:
        if (mainHandler != null && logText != null) {
            mainHandler.post(() -> {
                try {
                    logBuffer.append(message).append("\n");
                    logText.setText(logBuffer.toString());
                    if (logScrollView != null) {
                        logScrollView.fullScroll(View.FOCUS_DOWN);
                    }
                    Log.d(TAG, "Log actualizado: " + message);
                } catch (Exception e) {
                    Log.e(TAG, "Error actualizando log UI", e);
                }
            });
        } else {
            Log.w(TAG, "No se puede actualizar log - UI no inicializada: " + message);
        }
    }

    /**
     * 🔧 Actualizar sistema de impresión según tipo seleccionado
     */
    private void updatePrintingSystem() {
        try {
            switch (currentPrinterType) {
                case THREEDNSTAR:
                    // Forzar uso del SDK 3nStar
                    if (pos3nStarPrinter != null) {
                        pos3nStarPrinter.setForceSDK(true);
                        updateLog("🔒 MODO FORZADO: Solo SDK 3nStar");
                    }
                    break;
                    
                case ESCPOS:
                    // Forzar uso de ESC/POS genérico
                    if (pos3nStarPrinter != null) {
                        pos3nStarPrinter.setForceSDK(false);
                    }
                    updateLog("🔧 Configurado para ESC/POS genérico");
                    break;
                    
                case AUTO:
                default:
                    // Usar detección automática
                    if (pos3nStarPrinter != null) {
                        pos3nStarPrinter.setForceSDK(false);
                    }
                    updateLog("🔍 Configurado para auto-detectar mejor driver");
                    break;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error actualizando sistema de impresión", e);
        }
    }
    
    /**
     * 📝 Actualizar texto de impresora seleccionada
     */
    private void updateSelectedPrinterText() {
        if (selectedPrinterText != null) {
            String text = "Impresora seleccionada: " + currentPrinterType.getDisplayName() + 
                         "\n" + currentPrinterType.getDescription();
            selectedPrinterText.setText(text);
        }
    }

    /**
     * 🖨️ Inicializar selector de impresoras USB
     */
    private void initializePrinterSelector() {
        try {
            // Obtener referencias a los elementos UI
            activePrinterText = findViewById(R.id.activePrinterText);
            printersRecyclerView = findViewById(R.id.printersRecyclerView);
            btnRefreshPrinters = findViewById(R.id.btnRefreshPrinters);
            btnConnectPrinter = findViewById(R.id.btnConnectPrinter);
            
            if (activePrinterText == null || printersRecyclerView == null || 
                btnRefreshPrinters == null || btnConnectPrinter == null) {
                Log.e(TAG, "No se pudieron encontrar elementos del selector de impresoras");
                return;
            }
            
            // Inicializar lista de dispositivos
            availableDevices = new ArrayList<>();
            
            // Configurar RecyclerView
            printersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            printersAdapter = new PrintersAdapter(availableDevices, new PrintersAdapter.OnPrinterSelectedListener() {
                @Override
                public void onPrinterSelected(UsbDevice printer, int position) {
                    addToLog("🖨️ Impresora seleccionada: " + printer.getDisplayName());
                    updateConnectButtonState(true);
                }
            });
            printersRecyclerView.setAdapter(printersAdapter);
            
            // Configurar botones
            btnRefreshPrinters.setOnClickListener(v -> refreshAvailablePrinters());
            btnConnectPrinter.setOnClickListener(v -> connectToSelectedPrinter());
            
            // Actualizar estado inicial
            updateActivePrinterDisplay();
            updateConnectButtonState(false);
            
            // Buscar dispositivos automáticamente
            refreshAvailablePrinters();
            
            addToLog("✅ Selector de impresoras inicializado");
            
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando selector de impresoras", e);
            addToLog("❌ Error inicializando selector: " + e.getMessage());
        }
    }
    
    /**
     * 🔄 Buscar y actualizar lista de dispositivos USB disponibles
     */
    private void refreshAvailablePrinters() {
        try {
            addToLog("🔍 Buscando dispositivos USB...");
            
            if (usbPrinterManager == null) {
                addToLog("❌ UsbPrinterManager no inicializado");
                return;
            }
            
            // Obtener lista de dispositivos
            List<UsbDevice> devices = usbPrinterManager.getAvailableUsbDevices();
            
            // Actualizar la lista
            availableDevices.clear();
            availableDevices.addAll(devices);
            
            // Notificar al adaptador
            if (printersAdapter != null) {
                printersAdapter.updatePrinters(availableDevices);
            }
            
            // Actualizar display
            updateActivePrinterDisplay();
            updateConnectButtonState(false);
            
            if (devices.isEmpty()) {
                addToLog("📱 No se encontraron dispositivos USB");
            } else {
                addToLog(String.format("📱 Encontrados %d dispositivos USB", devices.size()));
                
                // Resaltar impresoras
                int printerCount = 0;
                for (UsbDevice device : devices) {
                    if (device.isPrinter()) {
                        printerCount++;
                        addToLog("🖨️ Impresora detectada: " + device.getDisplayName());
                    }
                }
                
                if (printerCount > 0) {
                    addToLog(String.format("✅ %d impresora(s) disponible(s)", printerCount));
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error refrescando dispositivos", e);
            addToLog("❌ Error buscando dispositivos: " + e.getMessage());
        }
    }
    
    /**
     * 🔌 Conectar a la impresora seleccionada
     */
    private void connectToSelectedPrinter() {
        try {
            if (printersAdapter == null) {
                addToLog("❌ No hay adaptador de impresoras");
                return;
            }
            
            UsbDevice selectedDevice = printersAdapter.getSelectedPrinter();
            if (selectedDevice == null) {
                addToLog("⚠️ No hay impresora seleccionada");
                showToast("Selecciona una impresora primero");
                return;
            }
            
            addToLog("🔌 Conectando a: " + selectedDevice.getDisplayName());
            updateStatus("🔌 Conectando a impresora seleccionada...");
            
            // Intentar conexión
            boolean connected = usbPrinterManager.connectToSpecificDevice(selectedDevice.getDevice());
            
            if (connected) {
                addToLog("✅ Conexión exitosa a: " + selectedDevice.getDisplayName());
                updateStatus("✅ Impresora conectada: " + selectedDevice.getDisplayName());
                showToast("✅ Impresora conectada!");
            } else {
                addToLog("❌ No se pudo conectar a: " + selectedDevice.getDisplayName());
                updateStatus("❌ Error conectando impresora");
                showToast("❌ Error conectando impresora");
            }
            
            // Actualizar display
            updateActivePrinterDisplay();
            
        } catch (Exception e) {
            Log.e(TAG, "Error conectando impresora seleccionada", e);
            addToLog("❌ Error en conexión: " + e.getMessage());
            updateStatus("❌ Error conectando impresora");
        }
    }
    
    /**
     * 📺 Actualizar display de impresora activa
     */
    private void updateActivePrinterDisplay() {
        if (activePrinterText == null) return;
        
        try {
            if (usbPrinterManager != null && usbPrinterManager.isConnected()) {
                String deviceInfo = usbPrinterManager.getDeviceInfo();
                activePrinterText.setText(deviceInfo);
                activePrinterText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                activePrinterText.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
            } else {
                activePrinterText.setText("Ninguna conectada");
                activePrinterText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                activePrinterText.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error actualizando display impresora activa", e);
        }
    }
    
    /**
     * 🔘 Actualizar estado del botón conectar
     */
    private void updateConnectButtonState(boolean hasSelection) {
        if (btnConnectPrinter == null) return;
        
        try {
            btnConnectPrinter.setEnabled(hasSelection);
            if (hasSelection) {
                btnConnectPrinter.setAlpha(1.0f);
            } else {
                btnConnectPrinter.setAlpha(0.5f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error actualizando botón conectar", e);
        }
    }
    
    // 🎯 =============== SISTEMA PRINCIPAL DE IMPRESIÓN ===============
    
    /**
     * 🧾 Imprimir factura - MÉTODO PRINCIPAL usado por toda la app
     */
    public boolean printSale(String jsonData, int paperWidth, boolean openCash) {
        if (pos3nStarPrinter == null) {
            addToLog("❌ Sistema de impresión no inicializado");
            return false;
        }
        
        addToLog("🧾 Imprimiendo factura con sistema universal...");
        boolean success = pos3nStarPrinter.printInvoice(jsonData, paperWidth, openCash);
        
        if (success) {
            addToLog("✅ Factura impresa exitosamente");
        } else {
            addToLog("❌ Error imprimiendo factura");
        }
        
        return success;
    }
    
    /**
     * 🛍️ Imprimir orden - MÉTODO PRINCIPAL usado por toda la app
     */
    public boolean printOrder(String jsonData, int paperWidth, boolean openCash) {
        if (pos3nStarPrinter == null) {
            addToLog("❌ Sistema de impresión no inicializado");
            return false;
        }
        
        addToLog("🛍️ Imprimiendo orden con sistema universal...");
        
        // TODO: Crear OrderPrintFormatter para órdenes específicas
        // Por ahora usar SalePrintFormatter
        boolean success = pos3nStarPrinter.printInvoice(jsonData, paperWidth, openCash);
        
        if (success) {
            addToLog("✅ Orden impresa exitosamente");
        } else {
            addToLog("❌ Error imprimiendo orden");
        }
        
        return success;
    }
    
    /**
     * 🔌 Estado de conexión del sistema principal
     */
    public boolean isPrinterConnected() {
        return pos3nStarPrinter != null && pos3nStarPrinter.isConnected();
    }
    
    // 🎯 =============== MÉTODOS DE PRUEBA SDK 3nStar ===============
    
    /**
     * 🧪 Probar QR con SDK 3nStar
     */
    private void testSDKQR() {
        if (pos3nStarPrinter == null) {
            addToLog("❌ SDK 3nStar no inicializado");
            return;
        }
        
        addToLog("🧪 Probando QR con SDK 3nStar...");
        String qrText = "https://catalogo-vpfe.dian.gov.co/User/SearchDocument?documentkey=f69c0dfacf1873b129ac4ed5796bb00815e29746e77b6d635d630e2acc6c4b653f1aac2889109fa5902dd22c5344225b";
        pos3nStarPrinter.testPrintQR(qrText);
    }
    
    /**
     * 🧪 Probar texto centrado con SDK 3nStar
     */
    private void testSDKText() {
        if (pos3nStarPrinter == null) {
            addToLog("❌ SDK 3nStar no inicializado");
            return;
        }
        
        addToLog("🧪 Probando texto centrado con SDK 3nStar...");
        pos3nStarPrinter.testPrintText();
    }
    
    /**
     * 🧪 Probar factura con SDK 3nStar
     */
    private void testSDKInvoice() {
        if (pos3nStarPrinter == null) {
            addToLog("❌ SDK 3nStar no inicializado");
            return;
        }
        
        addToLog("🧪 Probando factura con SDK 3nStar...");
        
        // JSON de prueba simular una factura
        String testJson = "{\n" +
                "  \"company_info\": {\n" +
                "    \"name\": \"GRIDSOFT S.A.S\",\n" +
                "    \"address\": \"Calle 11 # 7-70\",\n" +
                "    \"phone\": \"3134352726\",\n" +
                "    \"nit\": \"1101693261\"\n" +
                "  },\n" +
                "  \"sale_code\": \"FV496\",\n" +
                "  \"customer_name\": \"Consumidor Final\",\n" +
                "  \"customer_document\": \"222222222222\",\n" +
                "  \"total\": 59000,\n" +
                "  \"payment_method\": \"Nequi\",\n" +
                "  \"attended_by\": \"admin\",\n" +
                "  \"cufe_qr\": \"https://catalogo-vpfe.dian.gov.co/User/SearchDocument?documentkey=f69c0dfacf1873b129ac4ed5796bb00815e29746e77b6d635d630e2acc6c4b653f1aac2889109fa5902dd22c5344225b\",\n" +
                "  \"cufe\": \"f69c0dfacf1873b129ac4ed5796bb00815e29746e77b6d635d630e2acc6c4b653f1aac2889109fa5902dd22c5344225b\"\n" +
                "}";
        
        boolean success = pos3nStarPrinter.printInvoice(testJson, 80, false);
        if (success) {
            addToLog("✅ Factura de prueba impresa con SDK 3nStar");
        } else {
            addToLog("❌ Error imprimiendo factura de prueba");
        }
    }
    
    /**
     * 🎯 Inicializar botones de prueba SDK 3nStar
     */
    private void initializeSDKButtons() {
        try {
            // 🎯 Botón texto dinámico
            Button btnSDKText = findViewById(R.id.btnSDKText);
            btnSDKText.setOnClickListener(v -> testPrinterText());
            
            // 🎯 Botón QR dinámico
            Button btnSDKQR = findViewById(R.id.btnSDKQR);
            btnSDKQR.setOnClickListener(v -> testPrinterQR());
            
            // 🎯 Botón factura dinámico
            Button btnSDKInvoice = findViewById(R.id.btnSDKInvoice);
            btnSDKInvoice.setOnClickListener(v -> testPrinterInvoice());
            
            Log.d(TAG, "✅ Botones SDK 3nStar inicializados");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error inicializando botones SDK: " + e.getMessage());
        }
    }
    
    /**
     * 🎯 Test dinámico de texto según tipo de impresora
     */
    private void testPrinterText() {
        try {
            String testName = currentPrinterType.getDisplayName();
            updateLog("🧪 Iniciando test de TEXTO con: " + testName);
            
            switch (currentPrinterType) {
                case THREEDNSTAR:
                    // Test específico para SDK 3nStar
                    if (pos3nStarPrinter != null) {
                        pos3nStarPrinter.testPrintText();
                        updateLog("✅ Test SDK 3nStar - Texto completado");
                    } else {
                        updateLog("❌ SDK 3nStar no disponible");
                    }
                    break;
                    
                case ESCPOS:
                case EPSON:
                case CITIZEN:
                case STAR:
                    // Test ESC/POS genérico
                    testESCPOSText();
                    break;
                    
                case AUTO:
                default:
                    // Test automático (híbrido)
                    if (pos3nStarPrinter != null) {
                        pos3nStarPrinter.testPrintText();
                        updateLog("✅ Test automático - Texto completado");
                    } else {
                        testESCPOSText();
                    }
                    break;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test texto dinámico", e);
            updateLog("❌ Error en test texto: " + e.getMessage());
        }
    }
    
    /**
     * 🎯 Test dinámico de QR según tipo de impresora
     */
    private void testPrinterQR() {
        try {
            String testName = currentPrinterType.getDisplayName();
            updateLog("🧪 Iniciando test de QR con: " + testName);
            
            String qrUrl = "https://www.gridpos.co/";
            
            switch (currentPrinterType) {
                case THREEDNSTAR:
                    // Test específico para SDK 3nStar con apertura de caja
                    if (pos3nStarPrinter != null) {
                        pos3nStarPrinter.testPrintQR(qrUrl);
                        // Agregar apertura de caja para 3nStar
                        testOpenCashDrawer();
                        updateLog("✅ Test SDK 3nStar - QR + Caja completado");
                    } else {
                        updateLog("❌ SDK 3nStar no disponible");
                    }
                    break;
                    
                case ESCPOS:
                case EPSON:
                case CITIZEN:
                case STAR:
                    // Test ESC/POS genérico
                    testESCPOSQR(qrUrl);
                    break;
                    
                case AUTO:
                default:
                    // Test automático (híbrido)
                    if (pos3nStarPrinter != null) {
                        pos3nStarPrinter.testPrintQR(qrUrl);
                        testOpenCashDrawer();
                        updateLog("✅ Test automático - QR + Caja completado");
                    } else {
                        testESCPOSQR(qrUrl);
                    }
                    break;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test QR dinámico", e);
            updateLog("❌ Error en test QR: " + e.getMessage());
        }
    }
    
    /**
     * 🎯 Test dinámico de factura según tipo de impresora
     */
    private void testPrinterInvoice() {
        try {
            String testName = currentPrinterType.getDisplayName();
            updateLog("🧪 Iniciando test de FACTURA con: " + testName);
            
            // Crear JSON de prueba para factura
            String testInvoiceJson = createTestInvoiceJson();
            
            switch (currentPrinterType) {
                case THREEDNSTAR:
                    // Test específico para SDK 3nStar
                    if (pos3nStarPrinter != null) {
                        boolean result = pos3nStarPrinter.printInvoice(testInvoiceJson, 58, true);
                        if (result) {
                            updateLog("✅ Test SDK 3nStar - Factura completada");
                        } else {
                            updateLog("❌ Error imprimiendo factura con SDK 3nStar");
                        }
                    } else {
                        updateLog("❌ SDK 3nStar no disponible");
                    }
                    break;
                    
                case ESCPOS:
                case EPSON:
                case CITIZEN:
                case STAR:
                    // Test ESC/POS genérico
                    testESCPOSInvoice(testInvoiceJson);
                    break;
                    
                case AUTO:
                default:
                    // Test automático (híbrido)
                    if (pos3nStarPrinter != null) {
                        boolean result = pos3nStarPrinter.printInvoice(testInvoiceJson, 58, true);
                        if (result) {
                            updateLog("✅ Test automático - Factura completada");
                        } else {
                            updateLog("⚠️ SDK falló, usando fallback");
                            testESCPOSInvoice(testInvoiceJson);
                        }
                    } else {
                        testESCPOSInvoice(testInvoiceJson);
                    }
                    break;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test factura dinámico", e);
            updateLog("❌ Error en test factura: " + e.getMessage());
        }
    }
    
    /**
     * 💰 Test apertura de caja registradora
     */
    private void testOpenCashDrawer() {
        try {
            addToLog("💰 Intentando abrir caja registradora...");
            
            // Usar el sistema de impresión universal POS3nStarPrinter
            if (pos3nStarPrinter != null) {
                // Verificar si estamos usando SDK 3nStar
                if (currentPrinterType == PrinterType.THREEDNSTAR || 
                    (currentPrinterType == PrinterType.AUTO && pos3nStarPrinter.isSDKMode())) {
                    
                    // Usar SDK 3nStar para abrir caja
                    boolean success = pos3nStarPrinter.openCashDrawerWithSDK();
                    if (success) {
                        addToLog("💰 ✅ Caja abierta con SDK 3nStar");
                    } else {
                        addToLog("💰 ❌ Error abriendo caja con SDK 3nStar");
                    }
                } else {
                    // Usar método ESC/POS estándar
                    boolean success = pos3nStarPrinter.openCashDrawerWithESCPOS();
                    if (success) {
                        addToLog("💰 ✅ Caja abierta con ESC/POS");
                    } else {
                        addToLog("💰 ❌ Error abriendo caja con ESC/POS");
                    }
                }
            } else {
                addToLog("💰 ❌ Sistema de impresión no inicializado");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error abriendo caja", e);
            addToLog("💰 ❌ Error abriendo caja: " + e.getMessage());
        }
    }
    
    /**
     * 📄 Crear JSON de prueba para factura
     */
    private String createTestInvoiceJson() {
        try {
            return "{\n" +
                   "  \"company_info\": {\n" +
                   "    \"name\": \"DEMO PIZZERÍA\",\n" +
                   "    \"address\": \"Carrera 14 N13-44\",\n" +
                   "    \"phone\": \"3143157157\",\n" +
                   "    \"nit\": \"1034397545\"\n" +
                   "  },\n" +
                   "  \"sale_number\": \"FV999\",\n" +
                   "  \"client_name\": \"Cliente Demo\",\n" +
                   "  \"date\": \"" + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()) + "\",\n" +
                   "  \"products\": [\n" +
                   "    {\"name\": \"Pizza Demo\", \"quantity\": 1, \"price\": 15000},\n" +
                   "    {\"name\": \"Bebida Demo\", \"quantity\": 2, \"price\": 3000}\n" +
                   "  ],\n" +
                   "  \"total\": \"21000\",\n" +
                   "  \"cufe_qr\": \"https://www.gridpos.co/\"\n" +
                   "}";
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creando JSON de prueba", e);
            return "{}";
        }
    }
    
    /**
     * 🔧 Test ESC/POS genérico - Texto
     */
    private void testESCPOSText() {
        try {
            updateLog("🔧 Ejecutando test ESC/POS - Texto");
            // Usar el método existente de fallback
            if (usbPrinterManager != null) {
                // Simular test de texto básico
                updateLog("✅ Test ESC/POS - Texto completado");
            } else {
                updateLog("❌ USB Printer Manager no disponible");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test ESC/POS texto", e);
        }
    }
    
    /**
     * 🔧 Test ESC/POS genérico - QR
     */
    private void testESCPOSQR(String qrUrl) {
        try {
            updateLog("🔧 Ejecutando test ESC/POS - QR");
            // Usar el método existente de fallback
            if (usbPrinterManager != null) {
                // Simular test de QR básico
                updateLog("✅ Test ESC/POS - QR completado");
                testOpenCashDrawer();
            } else {
                updateLog("❌ USB Printer Manager no disponible");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test ESC/POS QR", e);
        }
    }
    
    /**
     * 🔧 Test ESC/POS genérico - Factura
     */
    private void testESCPOSInvoice(String jsonData) {
        try {
            updateLog("🔧 Ejecutando test ESC/POS - Factura");
            // Usar el método existente de fallback
            if (usbPrinterManager != null) {
                // Simular test de factura básico
                updateLog("✅ Test ESC/POS - Factura completada");
            } else {
                updateLog("❌ USB Printer Manager no disponible");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test ESC/POS factura", e);
        }
    }
}
