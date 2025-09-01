package com.gridpos.puenteimpresora;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.lang.reflect.Method;

// 🎯 SDK 3nStar - Imports reales desde la documentación
import net.posprinter.POSConnect;
import net.posprinter.POSPrinter;
import net.posprinter.POSConst;
import net.posprinter.IDeviceConnection;
import net.posprinter.IPOSListener;

import org.json.JSONObject;

/**
 * 🎯 Impresora POS Universal - Usa SDK 3nStar cuando esté disponible
 * Fallback a implementación manual si es necesario
 * ESTA SERÁ LA CLASE PRINCIPAL PARA TODAS LAS IMPRESIONES
 */
public class POS3nStarPrinter {
    private static final String TAG = "POS3nStarPrinter";
    
    private Context context;
    private UsbPrinterManager fallbackPrinter; // Fallback al sistema anterior
    private boolean useSDK = true; // 🎯 ACTIVADO! Vamos a probar el SDK
    private boolean forceSDK = false; // 🔒 Forzar uso exclusivo del SDK
    private boolean isConnected = false;
    
    // 🎯 SDK 3nStar - Variables reales
    private POSPrinter posPrinter = null;
    private IDeviceConnection posConnection = null;
    private boolean sdkInitialized = false;
    
    public POS3nStarPrinter(Context context) {
        this.context = context;
        initializeConnection();
    }
    
    /**
     * 🔌 Inicializar conexión (híbrida con SDK 3nStar real)
     */
    private void initializeConnection() {
        try {
            // Siempre inicializar fallback como backup
            fallbackPrinter = new UsbPrinterManager(context);
            Log.d(TAG, "✅ Sistema fallback inicializado");
            
            // 🎯 Inicializar SDK 3nStar real
            if (useSDK && initializeSDK()) {
                Log.d(TAG, "🎯 SDK 3nStar inicializado correctamente");
            } else {
                Log.w(TAG, "⚠️ SDK 3nStar no disponible, usando fallback");
                useSDK = false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error inicializando sistema: " + e.getMessage());
            useSDK = false; // Fallback si hay error
        }
    }
    
    /**
     * 🎯 Inicializar SDK 3nStar real
     */
    private boolean initializeSDK() {
        try {
            // El SDK ya fue inicializado en PrinterApplication
            Log.d(TAG, "✅ POSConnect ya inicializado en Application");
            
            // Crear listener para eventos de conexión
            IPOSListener connectListener = new IPOSListener() {
                @Override
                public void onStatus(int code, String msg) {
                    switch (code) {
                        case POSConnect.CONNECT_SUCCESS:
                            Log.d(TAG, "✅ Conexión exitosa");
                            isConnected = true;
                            break;
                        case POSConnect.CONNECT_FAIL:
                            Log.w(TAG, "❌ Conexión fallida: " + msg);
                            isConnected = false;
                            break;
                        case POSConnect.CONNECT_INTERRUPT:
                            Log.w(TAG, "⚠️ Conexión interrumpida");
                            isConnected = false;
                            break;
                        case POSConnect.SEND_FAIL:
                            Log.w(TAG, "⚠️ Error enviando datos");
                            break;
                        case POSConnect.USB_DETACHED:
                            Log.w(TAG, "📱 USB desconectado");
                            isConnected = false;
                            break;
                        case POSConnect.USB_ATTACHED:
                            Log.d(TAG, "📱 USB conectado");
                            break;
                    }
                }
            };
            
            // TODO: La conexión se hará cuando sea necesaria
            // Por ahora solo inicializamos el framework
            sdkInitialized = true;
            Log.d(TAG, "🎯 SDK 3nStar listo para usar");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error inicializando SDK 3nStar: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 🎯 Conectar a impresora USB usando SDK 3nStar (patrón oficial)
     */
    private boolean connectSDKUSB() {
        try {
            if (!sdkInitialized) {
                Log.w(TAG, "⚠️ SDK no inicializado");
                return false;
            }
            
            // Obtener dispositivos USB disponibles
            String[] usbDevices = POSConnect.getUsbDevices(context).toArray(new String[0]);
            if (usbDevices == null || usbDevices.length == 0) {
                Log.w(TAG, "⚠️ No se encontraron impresoras USB");
                return false;
            }
            
            Log.d(TAG, "🔍 Dispositivos USB encontrados: " + usbDevices.length);
            for (int i = 0; i < usbDevices.length; i++) {
                Log.d(TAG, "📱 Dispositivo " + i + ": " + usbDevices[i]);
            }
            
            // Cerrar conexión anterior si existe
            if (posConnection != null) {
                posConnection.close();
                posConnection = null;
                posPrinter = null;
            }
            
            // Crear conexión USB (patrón oficial)
            posConnection = POSConnect.createDevice(POSConnect.DEVICE_TYPE_USB);
            if (posConnection == null) {
                Log.e(TAG, "❌ No se pudo crear conexión USB");
                return false;
            }
            
            // Conectar al primer dispositivo disponible
            String devicePath = usbDevices[0];
            Log.d(TAG, "🔌 Conectando a dispositivo: " + devicePath);
            
            // Crear listener de conexión simplificado
            final boolean[] connectionResult = {false};
            IPOSListener connectListener = new IPOSListener() {
                @Override
                public void onStatus(int code, String msg) {
                    Log.d(TAG, "📡 Estado SDK: code=" + code + ", msg=" + msg);
                    switch (code) {
                        case POSConnect.CONNECT_SUCCESS:
                            Log.d(TAG, "✅ SDK: Conexión exitosa");
                            isConnected = true;
                            connectionResult[0] = true;
                            // Crear instancia del printer
                            posPrinter = new POSPrinter(posConnection);
                            Log.d(TAG, "🖨️ POSPrinter creado: " + (posPrinter != null));
                            break;
                        case POSConnect.CONNECT_FAIL:
                            Log.w(TAG, "❌ SDK: Error conectando - " + msg);
                            isConnected = false;
                            connectionResult[0] = false;
                            break;
                        case POSConnect.CONNECT_INTERRUPT:
                            Log.w(TAG, "⚠️ SDK: Conexión interrumpida");
                            isConnected = false;
                            break;
                        case POSConnect.USB_DETACHED:
                            Log.w(TAG, "📱 SDK: USB desconectado");
                            isConnected = false;
                            break;
                        case POSConnect.USB_ATTACHED:
                            Log.d(TAG, "📱 SDK: USB conectado");
                            break;
                    }
                }
            };
            
            // Ejecutar conexión
            Log.d(TAG, "🚀 Iniciando conexión SDK...");
            posConnection.connect(devicePath, connectListener);
            
            // Esperar resultado de la conexión
            Log.d(TAG, "⏱️ Esperando resultado de conexión...");
            for (int i = 0; i < 50; i++) { // 5 segundos máximo
                Thread.sleep(100);
                if (connectionResult[0] || isConnected) {
                    break;
                }
            }
            
            Log.d(TAG, "🔍 Resultado final: isConnected=" + isConnected + 
                      ", posPrinter=" + (posPrinter != null ? "✅" : "❌"));
            
            return isConnected && posPrinter != null;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error conectando USB SDK: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 🔗 Conectar a impresora USB (híbrido)
     */
    public void connectUSB() {
        try {
            if (forceSDK || useSDK) {
                Log.d(TAG, "🎯 Intentando conexión con SDK 3nStar (forceSDK=" + forceSDK + ")");
                if (connectSDKUSB()) {
                    Log.d(TAG, "✅ Conectado exitosamente con SDK");
                    return; // Salir exitosamente
                } else {
                    Log.w(TAG, "⚠️ SDK falló al conectar");
                    if (forceSDK) {
                        Log.e(TAG, "❌ SDK forzado pero falló - no usar fallback");
                        isConnected = false;
                        return; // No usar fallback si está forzado
                    } else {
                        Log.w(TAG, "🔄 Cambiando a fallback...");
                        useSDK = false;
                    }
                }
            }
            
            // Usar sistema fallback (solo si SDK no está forzado)
            if (!forceSDK) {
                Log.d(TAG, "🔄 Usando sistema fallback...");
                isConnected = fallbackPrinter.connectToPrinter();
                Log.d(TAG, isConnected ? "✅ Fallback: Conectado USB" : "❌ Fallback: Error USB");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en conexión USB: " + e.getMessage());
            isConnected = false;
        }
    }
    
    /**
     * 🔌 Asegurar conexión antes de imprimir
     */
    private boolean ensureConnection() {
        if (isConnected && ((forceSDK && posPrinter != null) || (!forceSDK && fallbackPrinter != null))) {
            Log.d(TAG, "✅ Conexión ya establecida");
            return true;
        }
        
        Log.d(TAG, "🔌 Estableciendo conexión automática...");
        connectUSB();
        
        // Verificar si la conexión fue exitosa
        boolean connected = isConnected && 
                           ((forceSDK && posPrinter != null) || 
                            (!forceSDK && (posPrinter != null || fallbackPrinter != null)));
        
        Log.d(TAG, connected ? "✅ Conexión establecida" : "❌ Fallo al conectar");
        return connected;
    }
    
    /**
     * 🔗 Conectar a impresora IP (híbrido)
     */
    public void connectIP(String ipAddress, int port) {
        try {
            if (useSDK) {
                // TODO: Usar SDK cuando esté listo
                /*
                if (posConnect != null) {
                    posConnect.disconnect();
                }
                posConnect = new POSConnect.Builder()
                        .setConnectType(POSConnect.DEVICE_TYPE_ETHERNET)
                        .setIp(ipAddress)
                        .setPort(port)
                        .build();
                posPrinter = new POSPrinter(posConnect);
                posConnect.connect(new IConnectListener() {
                    @Override
                    public void onStatus(boolean isConnect, String message) {
                        isConnected = isConnect;
                        Log.d(TAG, isConnect ? "✅ SDK: Conectado IP" : "❌ SDK: Error IP: " + message);
                    }
                });
                */
                Log.w(TAG, "⚠️ SDK IP connection no implementado aún");
            } else {
                // Sistema fallback no soporta IP directamente
                Log.w(TAG, "⚠️ Conexión IP no soportada en modo fallback");
                Log.i(TAG, "💡 Sugerencia: Use configuración de red del sistema o active SDK");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en conexión IP: " + e.getMessage());
        }
    }
    
    /**
     * 🎯 Estado de conexión (híbrido)
     */
    public boolean isConnected() {
        if (useSDK) {
            // TODO: return isConnected && posConnect != null && posConnect.isConnect();
            return isConnected; // Temporal
        } else {
            return fallbackPrinter != null && fallbackPrinter.isConnected();
        }
    }
    
    /**
     * 🧾 Imprimir factura (MÉTODO PRINCIPAL UNIVERSAL)
     * Usa SDK 3nStar cuando esté disponible, fallback al sistema anterior
     */
    public boolean printInvoice(String jsonData, int paperWidth, boolean openCash) {
        // 🔌 Asegurar conexión antes de imprimir
        if (!ensureConnection()) {
            Log.e(TAG, "❌ No se pudo establecer conexión para imprimir factura");
            return false;
        }
        
        try {
            // 🔒 Si está en modo forzado, SOLO usar SDK
            if (forceSDK) {
                if (!useSDK || !sdkInitialized || posPrinter == null) {
                    Log.e(TAG, "❌ MODO FORZADO: SDK 3nStar no disponible");
                    return false;
                }
                Log.d(TAG, "🔒 MODO FORZADO: Usando SOLO SDK 3nStar");
                return printInvoiceWithSDK(jsonData, paperWidth, openCash);
            }
            
            // 🔄 Modo híbrido normal
            if (useSDK && sdkInitialized && posPrinter != null) {
                Log.d(TAG, "🎯 Usando SDK 3nStar para impresión");
                return printInvoiceWithSDK(jsonData, paperWidth, openCash);
            } else {
                Log.d(TAG, "🔄 Usando sistema mejorado para impresión");
                return printInvoiceWithFallback(jsonData, paperWidth, openCash);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error imprimiendo factura: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 🎯 Imprimir con SDK 3nStar real
     */
    private boolean printInvoiceWithSDK(String jsonData, int paperWidth, boolean openCash) {
        try {
            if (posPrinter == null || !isConnected) {
                Log.w(TAG, "⚠️ SDK no conectado, usando fallback");
                return printInvoiceWithFallback(jsonData, paperWidth, openCash);
            }
            
            Log.d(TAG, "🎯 Imprimiendo con SDK 3nStar");
            
            // Parsear JSON
            JSONObject data = new JSONObject(jsonData);
            
            // ===== EJEMPLO DE IMPRESIÓN CON SDK =====
            
            // Inicializar impresora
            posPrinter.initializePrinter();
            
            // Imprimir encabezado de empresa
            if (data.has("company_info")) {
                JSONObject company = data.getJSONObject("company_info");
                
                // Nombre de empresa en negrita y centrado
                if (company.has("name")) {
                    String companyName = company.getString("name");
                    posPrinter.printText(companyName + "\n", 
                                       POSConst.ALIGNMENT_CENTER, 
                                       POSConst.FNT_BOLD, 
                                       POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
                
                // Dirección
                if (company.has("address")) {
                    posPrinter.printText(company.getString("address") + "\n", 
                                       POSConst.ALIGNMENT_CENTER, 
                                       POSConst.FNT_DEFAULT, 
                                       POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
                
                // Teléfono y NIT
                if (company.has("phone")) {
                    posPrinter.printText("Tel: " + company.getString("phone") + "\n", 
                                       POSConst.ALIGNMENT_CENTER, 
                                       POSConst.FNT_DEFAULT, 
                                       POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
                
                if (company.has("nit")) {
                    posPrinter.printText("NIT: " + company.getString("nit") + "\n", 
                                       POSConst.ALIGNMENT_CENTER, 
                                       POSConst.FNT_DEFAULT, 
                                       POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
            }
            
            // Separador
            posPrinter.printString("----------------------------------------\n");
            
            // Información de venta
            if (data.has("sale_number")) {
                posPrinter.printText("VENTA: " + data.getString("sale_number") + "\n", 
                                   POSConst.ALIGNMENT_LEFT, 
                                   POSConst.FNT_BOLD, 
                                   POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            }
            
            // Cliente
            if (data.has("client_name")) {
                posPrinter.printString("CLIENTE: " + data.getString("client_name") + "\n");
            }
            
            // Fecha
            if (data.has("date")) {
                posPrinter.printString("FECHA: " + data.getString("date") + "\n");
            }
            
            posPrinter.printString("----------------------------------------\n");
            
            // Productos
            if (data.has("products")) {
                // TODO: Implementar tabla de productos completa
                posPrinter.printString("PRODUCTOS:\n");
                // Por ahora una versión simple
                posPrinter.printString("Item    Cant  Valor\n");
            }
            
            // Total
            if (data.has("total")) {
                posPrinter.printString("----------------------------------------\n");
                posPrinter.printText("TOTAL: $" + data.getString("total") + "\n", 
                                   POSConst.ALIGNMENT_RIGHT, 
                                   POSConst.FNT_BOLD, 
                                   POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            }
            
            // QR Code si está disponible
            if (data.has("cufe_qr") && !data.getString("cufe_qr").equals("null")) {
                posPrinter.printString("\n");
                posPrinter.printQRCode(data.getString("cufe_qr"));
            }
            
            // Footer
            posPrinter.printString("\n¡Gracias por tu compra!\n");
            posPrinter.printText("GridPOS 2025 © GridSoft S.A.S\n", 
                               POSConst.ALIGNMENT_CENTER, 
                               POSConst.FNT_DEFAULT, 
                               POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            // Cortar papel
            posPrinter.cutHalfAndFeed(1);
            
            // Abrir caja registradora si se solicita
            if (openCash) {
                posPrinter.openCashBox(POSConst.PIN_TWO);
            }
            
            Log.d(TAG, "✅ Impresión con SDK completada");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error con SDK, usando fallback: " + e.getMessage());
            return printInvoiceWithFallback(jsonData, paperWidth, openCash);
        }
    }
    
    /**
     * 🔄 Imprimir con sistema mejorado actual
     */
    private boolean printInvoiceWithFallback(String jsonData, int paperWidth, boolean openCash) {
        try {
            // Usar SalePrintFormatter mejorado
            byte[] printData = SalePrintFormatter.formatSale(jsonData, paperWidth, openCash);
            
            if (printData != null && printData.length > 0) {
                fallbackPrinter.sendRawData(printData);
                Log.d(TAG, "✅ Factura impresa con sistema mejorado");
                return true;
            } else {
                Log.e(TAG, "❌ Error: Datos de impresión vacíos");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error imprimiendo con fallback: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 🏢 Imprimir encabezado de empresa (CENTRADO)
     */
    private void printCompanyHeader(JSONObject data) {
        try {
            JSONObject companyInfo = data.optJSONObject("company_info");
            if (companyInfo == null) return;
            
            String companyName = companyInfo.optString("name", "");
            String address = companyInfo.optString("address", "");
            String phone = companyInfo.optString("phone", "");
            String nit = companyInfo.optString("nit", "");
            
            if (!companyName.isEmpty()) {
                posPrinter.printText(companyName + "\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_BOLD, 
                    POSConst.TXT_1WIDTH);
            }
            
            if (!address.isEmpty()) {
                posPrinter.printText("DIRECCIÓN: " + address + "\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH);
            }
            
            if (!phone.isEmpty()) {
                posPrinter.printText("CELULAR: " + phone + "\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH);
            }
            
            if (!nit.isEmpty()) {
                posPrinter.printText("NIT: " + nit + "\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH);
            }
            
            posPrinter.feedLine(1);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en encabezado empresa: " + e.getMessage());
        }
    }
    
    /**
     * 📄 Imprimir información de factura
     */
    private void printInvoiceInfo(JSONObject data) {
        try {
            String saleCode = data.optString("sale_code", "");
            String customerName = data.optString("customer_name", "Consumidor Final");
            String customerDocument = data.optString("customer_document", "");
            
            if (!saleCode.isEmpty()) {
                posPrinter.printText("VENTA: " + saleCode + "\n", 
                    POSConst.ALIGNMENT_LEFT, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH);
            }
            
            posPrinter.printText("CLIENTE: " + customerName + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH);
            
            if (!customerDocument.isEmpty()) {
                posPrinter.printText("DOCUMENTO: " + customerDocument + "\n", 
                    POSConst.ALIGNMENT_LEFT, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH);
            }
            
            posPrinter.feedLine(1);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en información factura: " + e.getMessage());
        }
    }
    
    /**
     * 🛍️ Imprimir productos
     */
    private void printProducts(JSONObject data, boolean isSmallPaper) {
        try {
            // Encabezados de tabla
            String header = isSmallPaper ? 
                "ITEM                CANT   VALOR" : 
                "ITEM                          CANT      VALOR";
            
            posPrinter.printText(header + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH);
            
            posPrinter.printText(isSmallPaper ? 
                "--------------------------------" : 
                "------------------------------------------------" + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH);
            
            // Productos (aquí necesitamos procesar el JSON de productos)
            // Por ahora una implementación básica
            posPrinter.feedLine(1);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en productos: " + e.getMessage());
        }
    }
    
    /**
     * 💰 Imprimir totales
     */
    private void printTotals(JSONObject data) {
        try {
            double total = data.optDouble("total", 0.0);
            String formattedTotal = SalePrintFormatter.formatCurrency(total);
            
            posPrinter.printText("TOTAL: " + formattedTotal + "\n", 
                POSConst.ALIGNMENT_RIGHT, 
                POSConst.FNT_BOLD, 
                POSConst.TXT_1WIDTH);
            
            posPrinter.feedLine(1);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en totales: " + e.getMessage());
        }
    }
    
    /**
     * 💳 Imprimir información de pago
     */
    private void printPaymentInfo(JSONObject data) {
        try {
            String paymentMethod = data.optString("payment_method", "");
            String attendedBy = data.optString("attended_by", "");
            
            if (!paymentMethod.isEmpty()) {
                posPrinter.printText("Forma de pago: " + paymentMethod + "\n", 
                    POSConst.ALIGNMENT_LEFT, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH);
            }
            
            if (!attendedBy.isEmpty()) {
                posPrinter.printText("Atendido por: " + attendedBy + "\n", 
                    POSConst.ALIGNMENT_LEFT, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH);
            }
            
            posPrinter.feedLine(1);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en información pago: " + e.getMessage());
        }
    }
    
    /**
     * 🔗 Imprimir QR y CUFE usando SDK 3nStar
     */
    private void printQRAndCUFE(JSONObject data) {
        try {
            String cufeQR = data.optString("cufe_qr", null);
            String cufe = data.optString("cufe", null);
            
            // Validaciones estrictas
            boolean validCufeQR = cufeQR != null && 
                                  !cufeQR.trim().isEmpty() && 
                                  !cufeQR.equals("null") && 
                                  !cufeQR.toLowerCase().equals("null");
            
            boolean validCufe = cufe != null && 
                               !cufe.trim().isEmpty() && 
                               !cufe.equals("null") && 
                               !cufe.toLowerCase().equals("null");
            
            if (validCufeQR) {
                Log.d(TAG, "🔗 Generando QR con SDK 3nStar: " + cufeQR);
                
                // 🎯 USAR SDK 3nStar para QR - MUCHO MEJOR CALIDAD
                posPrinter.printQRCode(cufeQR);
                posPrinter.feedLine(1);
                
                // CUFE en texto
                if (validCufe) {
                    Log.d(TAG, "📄 Imprimiendo CUFE: " + cufe);
                    posPrinter.printText("CUFE:\n" + cufe + "\n", 
                        POSConst.ALIGNMENT_LEFT, 
                        POSConst.FNT_DEFAULT, 
                        POSConst.TXT_1WIDTH);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en QR/CUFE: " + e.getMessage());
        }
    }
    
    /**
     * 🙏 Imprimir pie de página
     */
    private void printFooter() {
        try {
            posPrinter.printText("¡Gracias por tu compra!\n", 
                POSConst.ALIGNMENT_CENTER, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH);
            
            posPrinter.printText("GridPOS 2025 © GridSoft S.A.S\n", 
                POSConst.ALIGNMENT_CENTER, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH);
            
            posPrinter.feedLine(2);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en pie de página: " + e.getMessage());
        }
    }
    
    /**
     * 🧪 Método de prueba para QR (híbrido)
     */
    public void testPrintQR(String qrText) {
        // 🔌 Asegurar conexión antes de imprimir
        if (!ensureConnection()) {
            Log.e(TAG, "❌ No se pudo establecer conexión para test QR");
            return;
        }
        
        try {
            // 🔒 Si está en modo forzado, SOLO usar SDK
            if (forceSDK) {
                if (!useSDK || !sdkInitialized || posPrinter == null) {
                    Log.e(TAG, "❌ MODO FORZADO: SDK 3nStar no disponible para test QR");
                    return;
                }
                Log.d(TAG, "🔒 MODO FORZADO: Test QR solo con SDK 3nStar");
            }
            
            if (useSDK && posPrinter != null && sdkInitialized) {
                // 🎯 Usar SDK 3nStar real
                posPrinter.initializePrinter();
                posPrinter.printText("🧪 PRUEBA QR CON SDK 3nStar\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_BOLD, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                posPrinter.feedLine(1);
                posPrinter.printQRCode(qrText);
                posPrinter.feedLine(1);
                posPrinter.printText("URL: " + qrText + "\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                posPrinter.feedLine(2);
                posPrinter.cutHalfAndFeed(1);
                Log.d(TAG, "✅ QR de prueba impreso con SDK 3nStar");
            } else if (!forceSDK) {
                // Usar sistema fallback para QR
                String testHeader = "🧪 PRUEBA QR SISTEMA MEJORADO\n";
                String testFooter = "URL: " + qrText + "\n";
                
                // Crear bitmap QR
                Bitmap qrBitmap = QRCodeGenerator.generateQRCode(qrText, 120, 120);
                if (qrBitmap != null) {
                    byte[] qrCommands = SimpleImageConverter.convertBitmapSimple(qrBitmap);
                    if (qrCommands != null) {
                        fallbackPrinter.sendRawData(testHeader.getBytes());
                        fallbackPrinter.sendRawData(qrCommands);
                        fallbackPrinter.sendRawData(testFooter.getBytes());
                        Log.d(TAG, "✅ QR de prueba impreso con sistema fallback");
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test QR: " + e.getMessage());
        }
    }
    
    /**
     * 🧪 Método de prueba para texto centrado (híbrido)
     */
    public void testPrintText() {
        // 🔌 Asegurar conexión antes de imprimir
        if (!ensureConnection()) {
            Log.e(TAG, "❌ No se pudo establecer conexión para test texto");
            return;
        }
        
        try {
            // 🔒 Si está en modo forzado, SOLO usar SDK
            if (forceSDK) {
                if (!useSDK || !sdkInitialized || posPrinter == null) {
                    Log.e(TAG, "❌ MODO FORZADO: SDK 3nStar no disponible para test texto");
                    return;
                }
                Log.d(TAG, "🔒 MODO FORZADO: Test texto solo con SDK 3nStar");
            }
            
            if (useSDK && posPrinter != null && sdkInitialized) {
                // 🎯 Usar SDK 3nStar real
                posPrinter.initializePrinter();
                
                posPrinter.printText("🧪 PRUEBA SDK 3nStar\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_BOLD, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_2HEIGHT);
                
                posPrinter.printText("TEXTO CENTRADO\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                
                posPrinter.printText("Texto izquierda\n", 
                    POSConst.ALIGNMENT_LEFT, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                
                posPrinter.printText("Texto derecha\n", 
                    POSConst.ALIGNMENT_RIGHT, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                
                posPrinter.printString("Caracteres: ñáéíóú ÑÁÉÍÓÚ\n");
                
                posPrinter.feedLine(2);
                posPrinter.cutHalfAndFeed(1);
                
                Log.d(TAG, "✅ Texto de prueba impreso con SDK 3nStar");
            } else if (!forceSDK) {
                // Usar sistema fallback con comandos ESC/POS mejorados
                StringBuilder testPrint = new StringBuilder();
                
                // Usar SalePrintFormatter para comandos ESC/POS
                testPrint.append("🧪 PRUEBA CENTRADO SISTEMA MEJORADO\n");
                testPrint.append("TEXTO CENTRADO\n");
                testPrint.append("Texto izquierda\n");
                testPrint.append("Texto derecha\n");
                testPrint.append("Caracteres: ñáéíóú ÑÁÉÍÓÚ\n");
                testPrint.append("\n\n");
                
                // Usar TextEncodingHelper para caracteres especiales
                byte[] encodedText = TextEncodingHelper.encodeTextForThermalPrinter(testPrint.toString());
                fallbackPrinter.sendRawData(encodedText);
                
                Log.d(TAG, "✅ Texto de prueba impreso con sistema mejorado");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en test texto: " + e.getMessage());
        }
    }
    
    /**
     * 🔒 Configurar modo de impresión
     */
    public void setForceSDK(boolean force) {
        this.forceSDK = force;
        if (force) {
            Log.d(TAG, "🔒 MODO FORZADO: Solo SDK 3nStar");
        } else {
            Log.d(TAG, "🔄 MODO HÍBRIDO: SDK + Fallback");
        }
    }
    
    /**
     * 🎯 Obtener estado del SDK
     */
    public boolean isSDKMode() {
        return useSDK && sdkInitialized;
    }
    
    /**
     * 🔒 Obtener si está en modo forzado
     */
    public boolean isForcedSDK() {
        return forceSDK;
    }
    
    /**
     * 🔌 Desconectar impresora (híbrido)
     */
    public void disconnect() {
        try {
            if (useSDK) {
                // TODO: Usar SDK cuando esté listo
                /*
                if (posConnect != null && posConnect.isConnect()) {
                    posConnect.disconnect();
                    isConnected = false;
                    Log.d(TAG, "✅ SDK: Impresora desconectada");
                }
                */
                Log.d(TAG, "✅ SDK: Desconectado (cuando esté disponible)");
            } else {
                // Usar sistema fallback
                if (fallbackPrinter != null) {
                    fallbackPrinter.disconnect();
                    isConnected = false;
                    Log.d(TAG, "✅ Fallback: Impresora desconectada");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error desconectando: " + e.getMessage());
        }
    }
    
    /**
     * 💰 Abrir caja registradora usando SDK 3nStar
     */
    public boolean openCashDrawerWithSDK() {
        try {
            // 🔌 Asegurar conexión antes de abrir caja
            if (!ensureConnection()) {
                Log.w(TAG, "⚠️ No se pudo conectar para abrir caja con SDK");
                return false;
            }
            
            if (!sdkInitialized || posPrinter == null) {
                Log.w(TAG, "⚠️ SDK no inicializado para abrir caja");
                return false;
            }
            
            Log.d(TAG, "💰 Abriendo caja con SDK 3nStar...");
            
            // Inicializar impresora y abrir caja
            posPrinter.initializePrinter();
            posPrinter.openCashBox(POSConst.PIN_TWO);
            
            Log.d(TAG, "💰 ✅ Comando de apertura de caja enviado con SDK");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "💰 ❌ Error abriendo caja con SDK: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 💰 Abrir caja registradora usando ESC/POS estándar
     */
    public boolean openCashDrawerWithESCPOS() {
        try {
            // 🔌 Asegurar conexión antes de abrir caja
            if (!ensureConnection()) {
                Log.w(TAG, "⚠️ No se pudo conectar para abrir caja con ESC/POS");
                return false;
            }
            
            Log.d(TAG, "💰 Abriendo caja con ESC/POS...");
            
            // Comando ESC/POS para abrir caja registradora
            // ESC p m t1 t2 - Comando estándar de apertura de caja
            byte[] openDrawerCommand = new byte[]{
                0x1B, 0x70, 0x00, 0x19, (byte) 0xFA  // ESC p 0 25 250
            };
            
            if (fallbackPrinter != null) {
                try {
                    fallbackPrinter.sendRawData(openDrawerCommand);
                    Log.d(TAG, "💰 ✅ Comando ESC/POS de caja enviado");
                    return true;
                } catch (Exception e) {
                    Log.w(TAG, "💰 ⚠️ Fallo al enviar comando ESC/POS de caja: " + e.getMessage());
                    return false;
                }
            } else {
                Log.w(TAG, "💰 ⚠️ Fallback printer no disponible");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "💰 ❌ Error abriendo caja con ESC/POS: " + e.getMessage());
            return false;
        }
    }
    


}
