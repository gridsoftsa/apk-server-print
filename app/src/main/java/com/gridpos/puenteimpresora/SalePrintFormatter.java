package com.gridpos.puenteimpresora;

import android.util.Log;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 🧾 Formateador de facturas para impresoras térmicas ESC/POS
 * Maneja el formato completo de las facturas de venta (GridSoft S.A.S)
 */
public class SalePrintFormatter {
    private static final String TAG = "SalePrintFormatter";
    
    // 🎯 Comandos ESC/POS optimizados para facturas (compatibilidad mejorada)
    private static final byte[] INITIALIZE = {27, 64}; // ESC @
    private static final byte[] JUSTIFY_CENTER = {27, 97, 1}; // ESC a 1 (centrado)
    private static final byte[] JUSTIFY_LEFT = {27, 97, 0}; // ESC a 0 (izquierda)
    private static final byte[] JUSTIFY_RIGHT = {27, 97, 2}; // ESC a 2 (derecha)
    
    // 🔄 Comandos alternativos de centrado para mayor compatibilidad
    private static final byte[] CENTER_ALT = {29, 76, 1, 0}; // GS L (centrado alternativo)
    private static final byte[] LEFT_ALT = {29, 76, 0, 0}; // GS L (izquierda alternativo)
    private static final byte[] MODE_EMPHASIZED = {27, 69, 1}; // ESC E 1
    private static final byte[] MODE_NORMAL = {27, 69, 0}; // ESC E 0
    private static final byte[] MODE_DOUBLE_WIDTH = {29, 33, 32}; // GS ! 32
    private static final byte[] MODE_DOUBLE_WIDTH_EMPHASIZED = {29, 33, 48}; // GS ! 48
    private static final byte[] FEED_LINE = {10}; // LF
    private static final byte[] CUT_PAPER = {29, 86, 65, 3}; // GS V A 3
    private static final byte[] OPEN_CASH = {27, 112, 0, 25, (byte) 250}; // ESC p 0 25 250
    
    // 🌍 Comandos para caracteres especiales
    private static final byte[] SET_CHARSET_SPAIN = {27, 82, 10}; // ESC R 10
    private static final byte[] SET_CHARSET_LATIN1 = {27, 116, 2}; // ESC t 2
    private static final String CHARSET_ENCODING = "ISO-8859-1";
    
    /**
     * 📄 Formatear datos de factura completa a comandos ESC/POS
     */
    public static byte[] formatSale(String jsonData, int paperWidth, boolean openCash) {
        try {
            JSONObject data = new JSONObject(jsonData);
            boolean isSmallPaper = paperWidth == 58;
            
            Log.d(TAG, "🧾 Formateando factura - Papel: " + paperWidth + "mm");
            
            StringBuilder output = new StringBuilder();
            
            // === INICIALIZACIÓN ===
            addCommand(output, INITIALIZE);
            addCommand(output, SET_CHARSET_SPAIN);
            addCommand(output, SET_CHARSET_LATIN1);
            addCommand(output, JUSTIFY_CENTER);
            
            // === ENCABEZADO DE EMPRESA ===
            // 🖼️ Logo de la empresa (si existe)
            String logoBase64 = data.optString("logo_base64", null);
            if (logoBase64 != null && !logoBase64.trim().isEmpty()) {
                formatLogo(output, logoBase64);
                addCommand(output, FEED_LINE);
            }
            
            formatCompanyHeader(output, data, isSmallPaper);
            
            // === INFORMACIÓN DE VENTA ===
            formatSaleInfo(output, data, isSmallPaper);
            
            // === INFORMACIÓN DE CLIENTE ===
            formatClientInfo(output, data, isSmallPaper);
            
            // === PRODUCTOS ===
            formatProducts(output, data, isSmallPaper);
            
            // === TOTALES ===
            formatTotals(output, data, isSmallPaper);
            
            // === INFORMACIÓN ADICIONAL ===
            formatAdditionalInfo(output, data, isSmallPaper);
            
            // === PIE DE PÁGINA ===
            formatFooter(output, data, isSmallPaper);
            
            // === FINALIZACIÓN ===
            addCommand(output, FEED_LINE);
            addCommand(output, FEED_LINE);
            addCommand(output, CUT_PAPER);
            
            // 💰 Abrir caja si se solicita
            if (openCash) {
                addCommand(output, OPEN_CASH);
            }
            
            // Convertir a bytes con codificación apropiada
            return output.toString().getBytes(CHARSET_ENCODING);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error formateando factura: " + e.getMessage(), e);
            return createErrorMessage();
        }
    }
    
    /**
     * 🏢 Formatear encabezado de empresa
     */
    private static void formatCompanyHeader(StringBuilder output, JSONObject data, boolean isSmallPaper) {
        try {
            // 🖼️ Logo de la empresa (si existe)
            String logoBase64 = data.optString("logo_base64", null);
            if (logoBase64 != null && !logoBase64.isEmpty()) {
                formatLogo(output, logoBase64);
            }
            
            JSONObject companyInfo = data.optJSONObject("company_info");
            if (companyInfo == null) return;
            
            String companyName = companyInfo.optString("name", "");
            String address = companyInfo.optString("address", "");
            String phone = companyInfo.optString("phone", "");
            String nit = companyInfo.optString("nit", "");
            
            // 🏢 Toda la información de empresa centrada (comandos múltiples)
            setCenterAlignment(output);
            
            if (!companyName.isEmpty()) {
                addCommand(output, MODE_EMPHASIZED);
                addText(output, companyName);
                addCommand(output, FEED_LINE);
                addCommand(output, MODE_NORMAL);
            }
            
            if (!address.isEmpty()) {
                addText(output, "DIRECCIÓN: " + address);
                addCommand(output, FEED_LINE);
            }
            
            if (!phone.isEmpty()) {
                addText(output, "CELULAR: " + phone);
                addCommand(output, FEED_LINE);
            }
            
            if (!nit.isEmpty()) {
                addText(output, "NIT: " + nit);
                addCommand(output, FEED_LINE);
            }
            
            // Volver a alineación izquierda para el resto del contenido
            setLeftAlignment(output);
            
            addCommand(output, FEED_LINE);
            
        } catch (Exception e) {
            Log.w(TAG, "Error en encabezado de empresa: " + e.getMessage());
        }
    }
    
    /**
     * 📋 Formatear información de venta
     */
    private static void formatSaleInfo(StringBuilder output, JSONObject data, boolean isSmallPaper) {
        try {
            JSONObject saleData = data.optJSONObject("sale_data");
            if (saleData == null) return;
            
            addCommand(output, JUSTIFY_LEFT);
            addCommand(output, MODE_EMPHASIZED);
            
            String billing = saleData.optString("billing", "");
            if (!billing.isEmpty()) {
                addText(output, "VENTA: " + billing);
                addCommand(output, FEED_LINE);
            }
            
            addCommand(output, MODE_NORMAL);
            
        } catch (Exception e) {
            Log.w(TAG, "Error en información de venta: " + e.getMessage());
        }
    }
    
    /**
     * 👤 Formatear información de cliente
     */
    private static void formatClientInfo(StringBuilder output, JSONObject data, boolean isSmallPaper) {
        try {
            JSONObject clientInfo = data.optJSONObject("client_info");
            if (clientInfo == null) return;
            
            String clientName = clientInfo.optString("name", "");
            String document = clientInfo.optString("document", "");
            
            if (!clientName.isEmpty()) {
                addCommand(output, MODE_EMPHASIZED);
                addText(output, "CLIENTE: " + clientName);
                addCommand(output, FEED_LINE);
                addCommand(output, MODE_NORMAL);
            }
            
            if (!document.isEmpty()) {
                addCommand(output, MODE_EMPHASIZED);
                addText(output, "DOCUMENTO: " + document);
                addCommand(output, FEED_LINE);
                addCommand(output, MODE_NORMAL);
            }
            
            addCommand(output, FEED_LINE);
            
        } catch (Exception e) {
            Log.w(TAG, "Error en información de cliente: " + e.getMessage());
        }
    }
    
    /**
     * 📦 Formatear productos
     */
    private static void formatProducts(StringBuilder output, JSONObject data, boolean isSmallPaper) {
        try {
            JSONArray products = data.optJSONArray("products");
            if (products == null || products.length() == 0) return;
            
            // Separador de encabezado
            String separator = isSmallPaper ? 
                "--------------------------------" : 
                "------------------------------------------------";
            
            addText(output, separator);
            addCommand(output, FEED_LINE);
            
            // Encabezados de columnas
            addCommand(output, MODE_EMPHASIZED);
            if (isSmallPaper) {
                addText(output, "ITEM                  CANT VALOR");
            } else {
                addText(output, "ITEM                        CANT      VALOR");
            }
            addCommand(output, FEED_LINE);
            addCommand(output, MODE_NORMAL);
            addText(output, separator);
            addCommand(output, FEED_LINE);
            
            // Productos
            for (int i = 0; i < products.length(); i++) {
                JSONObject product = products.getJSONObject(i);
                
                String name = product.optString("name", "Producto");
                int quantity = product.optInt("quantity", 1);
                double totalValue = product.optDouble("total_value", 0.0);
                String notes = product.optString("notes", "");
                double discount = product.optDouble("discount", 0.0);
                
                if (isSmallPaper) {
                    // Formato para papel 58mm
                    formatProduct58mm(output, name, quantity, totalValue, notes, discount);
                } else {
                    // Formato para papel 80mm
                    formatProduct80mm(output, name, quantity, totalValue, notes, discount);
                }
            }
            
            addText(output, separator);
            addCommand(output, FEED_LINE);
            
        } catch (Exception e) {
            Log.w(TAG, "Error formateando productos: " + e.getMessage());
        }
    }
    
    /**
     * 📱 Formatear producto para papel 58mm
     */
    private static void formatProduct58mm(StringBuilder output, String name, int quantity, 
                                        double totalValue, String notes, double discount) {
        try {
            // Truncar nombre a 20 caracteres para 58mm
            String truncatedName = name.length() > 20 ? name.substring(0, 20) : name;
            
            // Formatear línea: NOMBRE CANTIDAD VALOR
            addText(output, String.format("%-20s %2d %8s", 
                truncatedName.toUpperCase(), quantity, formatCurrencySimple(totalValue)));
            addCommand(output, FEED_LINE);
            
            // Mostrar descuento si existe
            if (discount > 0) {
                addText(output, String.format("  Descuento: -%s", formatCurrencySimple(discount)));
                addCommand(output, FEED_LINE);
            }
            
            // Mostrar notas si existen
            if (!notes.isEmpty()) {
                addText(output, "  * " + notes.toUpperCase());
                addCommand(output, FEED_LINE);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error formateando producto 58mm: " + e.getMessage());
        }
    }
    
    /**
     * 🖨️ Formatear producto para papel 80mm
     */
    private static void formatProduct80mm(StringBuilder output, String name, int quantity, 
                                        double totalValue, String notes, double discount) {
        try {
            // Formatear línea: NOMBRE CANTIDAD VALOR
            addText(output, String.format("%-28s %4d %12s", 
                name.toUpperCase(), quantity, formatCurrencySimple(totalValue)));
            addCommand(output, FEED_LINE);
            
            // Mostrar descuento si existe
            if (discount > 0) {
                addText(output, String.format("    Descuento: -%s", formatCurrencySimple(discount)));
                addCommand(output, FEED_LINE);
            }
            
            // Mostrar notas si existen
            if (!notes.isEmpty()) {
                addText(output, "    * " + notes.toUpperCase());
                addCommand(output, FEED_LINE);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error formateando producto 80mm: " + e.getMessage());
        }
    }
    
    /**
     * 💰 Formatear totales
     */
    private static void formatTotals(StringBuilder output, JSONObject data, boolean isSmallPaper) {
        try {
            JSONObject totals = data.optJSONObject("totals");
            if (totals == null) return;
            
            double subTotal = totals.optDouble("sub_total", 0.0);
            double totalTax = totals.optDouble("total_tax_value", 0.0);
            double totalValue = totals.optDouble("total_value", 0.0);
            double totalTip = totals.optDouble("total_tip", 0.0);
            double discount = totals.optDouble("discount", 0.0);
            
            addCommand(output, JUSTIFY_RIGHT);
            
            // Mostrar subtotal si es diferente del total
            if (subTotal != totalValue && subTotal > 0) {
                addText(output, String.format("SUBTOTAL: %12s", formatCurrency(subTotal)));
                addCommand(output, FEED_LINE);
            }
            
            // Mostrar descuento si existe
            if (discount > 0) {
                addText(output, String.format("DESCUENTO: -%11s", formatCurrency(discount)));
                addCommand(output, FEED_LINE);
            }
            
            // Mostrar impuestos si existen
            if (totalTax > 0) {
                addText(output, String.format("IMPUESTO: %12s", formatCurrency(totalTax)));
                addCommand(output, FEED_LINE);
            }
            
            // Mostrar propina si existe
            if (totalTip > 0) {
                addText(output, String.format("PROPINA: %13s", formatCurrency(totalTip)));
                addCommand(output, FEED_LINE);
            }
            
            // Total final
            addCommand(output, MODE_EMPHASIZED);
            addText(output, String.format("TOTAL: %15s", formatCurrency(totalValue + totalTip)));
            addCommand(output, FEED_LINE);
            addCommand(output, MODE_NORMAL);
            
            addCommand(output, JUSTIFY_LEFT);
            addCommand(output, FEED_LINE);
            
        } catch (Exception e) {
            Log.w(TAG, "Error formateando totales: " + e.getMessage());
        }
    }
    
    /**
     * ℹ️ Formatear información adicional
     */
    private static void formatAdditionalInfo(StringBuilder output, JSONObject data, boolean isSmallPaper) {
        try {
            // Observaciones de la venta
            JSONObject saleData = data.optJSONObject("sale_data");
            if (saleData != null) {
                String observation = saleData.optString("observation", "");
                if (!observation.isEmpty()) {
                    addCommand(output, MODE_EMPHASIZED);
                    addText(output, "Nota: " + observation);
                    addCommand(output, FEED_LINE);
                    addCommand(output, MODE_NORMAL);
                }
            }
            
            // Información de delivery si existe
            JSONObject deliveryOrder = data.optJSONObject("delivery_order");
            if (deliveryOrder != null) {
                String shippingAddress = deliveryOrder.optString("shipping_address", "");
                String phone = deliveryOrder.optString("phone", "");
                String clientName = deliveryOrder.optString("client_name", "");
                
                if (!shippingAddress.isEmpty()) {
                    addCommand(output, MODE_EMPHASIZED);
                    addText(output, "Dirección: " + shippingAddress);
                    addCommand(output, FEED_LINE);
                    addCommand(output, MODE_NORMAL);
                }
                
                if (!phone.isEmpty()) {
                    addCommand(output, MODE_EMPHASIZED);
                    addText(output, "Celular: " + phone);
                    addCommand(output, FEED_LINE);
                    addCommand(output, MODE_NORMAL);
                }
                
                if (!clientName.isEmpty()) {
                    addCommand(output, MODE_EMPHASIZED);
                    addText(output, "Referencia: " + clientName);
                    addCommand(output, FEED_LINE);
                    addCommand(output, MODE_NORMAL);
                }
            }
            
            // Información de mesa si existe
            JSONObject tableOrder = data.optJSONObject("table_order");
            if (tableOrder != null) {
                JSONObject table = tableOrder.optJSONObject("table");
                if (table != null) {
                    String tableName = table.optString("name", "");
                    String tableNumber = table.optString("table_number", "");
                    
                    if (!tableName.isEmpty() && !tableNumber.isEmpty()) {
                        addCommand(output, MODE_EMPHASIZED);
                        addText(output, tableName + ": " + tableNumber);
                        addCommand(output, FEED_LINE);
                        addCommand(output, MODE_NORMAL);
                    }
                }
            }
            
            // Métodos de pago
            JSONArray paymentMethods = data.optJSONArray("payment_methods");
            if (paymentMethods != null && paymentMethods.length() > 0) {
                if (paymentMethods.length() == 1) {
                    JSONObject method = paymentMethods.getJSONObject(0);
                    String methodName = method.optString("name", "");
                    addCommand(output, MODE_EMPHASIZED);
                    addText(output, "Forma de pago: " + methodName);
                    addCommand(output, FEED_LINE);
                    addCommand(output, MODE_NORMAL);
                } else {
                    addCommand(output, MODE_EMPHASIZED);
                    addText(output, "Formas de pago:");
                    addCommand(output, FEED_LINE);
                    addCommand(output, MODE_NORMAL);
                    
                    for (int i = 0; i < paymentMethods.length(); i++) {
                        JSONObject method = paymentMethods.getJSONObject(i);
                        String methodName = method.optString("name", "");
                        double amount = method.optDouble("amount", 0.0);
                        addText(output, methodName + ": " + formatCurrency(amount));
                        addCommand(output, FEED_LINE);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error en información adicional: " + e.getMessage());
        }
    }
    
    /**
     * 📋 Formatear pie de página
     */
    private static void formatFooter(StringBuilder output, JSONObject data, boolean isSmallPaper) {
        try {
            addCommand(output, FEED_LINE);
            
            // Usuario que imprime
            JSONObject user = data.optJSONObject("user");
            if (user != null) {
                String userName = user.optString("name", "Sistema");
                addText(output, "Atendido por: " + userName);
                addCommand(output, FEED_LINE);
            }
            
            // Fecha de impresión
            String currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date());
            addText(output, "Impresión: " + currentDate);
            addCommand(output, FEED_LINE);
            
            // ID de venta
            JSONObject saleData = data.optJSONObject("sale_data");
            if (saleData != null) {
                String saleId = saleData.optString("id", "");
                if (!saleId.isEmpty()) {
                    addCommand(output, MODE_EMPHASIZED);
                    addText(output, "VENTA: " + saleId);
                    addCommand(output, FEED_LINE);
                    addCommand(output, MODE_NORMAL);
                }
            }
            
            // 🔗 QR del CUFE (si existe factura electrónica)
            String cufeQR = data.optString("cufe_qr", null);
            String cufe = data.optString("cufe", null);
            
            // Validaciones estrictas - no mostrar si es null, "null", vacío o inválido
            boolean validCufeQR = cufeQR != null && 
                                  !cufeQR.trim().isEmpty() && 
                                  !cufeQR.equals("null") && 
                                  !cufeQR.toLowerCase().equals("null");
                                  
            boolean validCufe = cufe != null && 
                               !cufe.trim().isEmpty() && 
                               !cufe.equals("null") && 
                               !cufe.toLowerCase().equals("null");
            
            if (validCufeQR) {
                Log.d(TAG, "🔗 Generando QR para CUFE: " + cufeQR);
                addCommand(output, FEED_LINE);
                
                // Imprimir QR centrado
                formatQRCode(output, cufeQR);
                
                // Mostrar CUFE en texto de forma más compacta y legible
                if (validCufe) {
                    Log.d(TAG, "📄 Mostrando CUFE en texto: " + cufe);
                    setLeftAlignment(output); // Alineado a la izquierda para mejor legibilidad
                    addText(output, "CUFE: " + formatCufeText(cufe));
                    addCommand(output, FEED_LINE);
                } else {
                    Log.d(TAG, "⚠️ CUFE de texto inválido, no se muestra: " + cufe);
                }
            } else {
                Log.d(TAG, "⚠️ CUFE QR inválido, no se genera QR: " + cufeQR);
            }
            
            // Mensaje de agradecimiento (centrado con comandos múltiples)
            setCenterAlignment(output);
            addText(output, "¡Gracias por tu compra!");
            addCommand(output, FEED_LINE);
            addText(output, "GridPOS 2025 © GridSoft S.A.S");
            addCommand(output, FEED_LINE);
            
        } catch (Exception e) {
            Log.w(TAG, "Error en pie de página: " + e.getMessage());
        }
    }
    
    /**
     * 🔧 Agregar comando ESC/POS al output
     */
    private static void addCommand(StringBuilder output, byte[] command) {
        for (byte b : command) {
            output.append((char) (b & 0xFF));
        }
    }
    
    /**
     * 🎯 Centrar texto con múltiples comandos para mejor compatibilidad
     */
    private static void setCenterAlignment(StringBuilder output) {
        addCommand(output, JUSTIFY_CENTER); // Comando principal
        addCommand(output, CENTER_ALT);     // Comando alternativo
    }
    
    /**
     * 📏 Centrar texto manualmente con espacios (fallback)
     */
    private static void addCenteredText(StringBuilder output, String text, int paperWidth) {
        if (text == null || text.isEmpty()) return;
        
        int maxChars = paperWidth == 58 ? 32 : 48; // Caracteres por línea según papel
        int textLength = text.length();
        
        if (textLength >= maxChars) {
            addText(output, text); // Si es muy largo, imprimir normal
            return;
        }
        
        // Calcular espacios para centrar
        int totalSpaces = maxChars - textLength;
        int leftSpaces = totalSpaces / 2;
        
        String centeredText = " ".repeat(Math.max(0, leftSpaces)) + text;
        addText(output, centeredText);
    }
    
    /**
     * 🎯 Alinear a la izquierda con múltiples comandos
     */
    private static void setLeftAlignment(StringBuilder output) {
        addCommand(output, JUSTIFY_LEFT); // Comando principal
        addCommand(output, LEFT_ALT);     // Comando alternativo
    }
    
    /**
     * 📝 Agregar texto normalizado
     */
    private static void addText(StringBuilder output, String text) {
        if (text == null || text.isEmpty()) return;
        
        // Usar TextEncodingHelper para manejar caracteres especiales
        try {
            byte[] encodedText = TextEncodingHelper.encodeTextForThermalPrinter(text);
            for (byte b : encodedText) {
                output.append((char) (b & 0xFF));
            }
        } catch (Exception e) {
            // Fallback: usar texto simple
            output.append(normalizeSpanishCharacters(text));
        }
    }
    
    /**
     * 🔄 Normalizar caracteres españoles (fallback)
     */
    private static String normalizeSpanishCharacters(String text) {
        if (text == null) return "";
        
        return text
            .replace("ñ", "n")
            .replace("Ñ", "N")
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ü", "U")
            .replace("¿", "?")
            .replace("¡", "!");
    }
    
    /**
     * ❌ Crear mensaje de error en caso de fallo
     */
    private static byte[] createErrorMessage() {
        try {
            StringBuilder error = new StringBuilder();
            
            addCommand(error, INITIALIZE);
            addCommand(error, JUSTIFY_CENTER);
            addCommand(error, MODE_EMPHASIZED);
            addText(error, "ERROR EN FACTURA");
            addCommand(error, FEED_LINE);
            addCommand(error, MODE_NORMAL);
            addText(error, "No se pudo procesar");
            addCommand(error, FEED_LINE);
            addText(error, "la información de venta");
            addCommand(error, FEED_LINE);
            addCommand(error, FEED_LINE);
            addCommand(error, CUT_PAPER);
            
            return error.toString().getBytes(CHARSET_ENCODING);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creando mensaje de error: " + e.getMessage());
            return new byte[0];
        }
    }
    
    // 💰 Método auxiliar para formatear moneda
    static String formatCurrency(double amount) {
        if (amount % 1.0 == 0) {
            // Sin decimales si es número entero
            return String.format("$%,.0f", amount);
        } else {
            // Con decimales si es necesario
            return String.format("$%,.2f", amount);
        }
    }
    
    // 💰 Método auxiliar para formatear moneda simple (solo enteros)
    private static String formatCurrencySimple(double amount) {
        return String.format("$%,.0f", amount);
    }
    
    /**
     * 🖼️ Formatear logo de la empresa (Base64 a imagen ESC/POS)
     */
    private static void formatLogo(StringBuilder output, String logoBase64) {
        try {
            Log.d(TAG, "🖼️ Procesando logo de empresa...");
            
            // Decodificar Base64
            byte[] logoBytes = Base64.decode(logoBase64, Base64.DEFAULT);
            Bitmap logoBitmap = BitmapFactory.decodeByteArray(logoBytes, 0, logoBytes.length);
            
            if (logoBitmap != null) {
                // Redimensionar logo para impresora térmica (máximo 200px de ancho)
                int maxWidth = 200;
                int width = logoBitmap.getWidth();
                int height = logoBitmap.getHeight();
                
                if (width > maxWidth) {
                    float scale = (float) maxWidth / width;
                    int newHeight = (int) (height * scale);
                    logoBitmap = Bitmap.createScaledBitmap(logoBitmap, maxWidth, newHeight, true);
                }
                
                // Convertir a ESC/POS usando SimpleImageConverter
                byte[] logoCommands = SimpleImageConverter.convertBitmapSimple(logoBitmap);
                if (logoCommands != null && logoCommands.length > 0) {
                    output.append(new String(logoCommands, CHARSET_ENCODING));
                    addCommand(output, FEED_LINE);
                }
                
                Log.d(TAG, "✅ Logo procesado correctamente");
            } else {
                Log.w(TAG, "⚠️ No se pudo decodificar el logo");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error procesando logo: " + e.getMessage());
        }
    }
    
    /**
     * 🔗 Formatear código QR (URL a QR ESC/POS)
     */
    private static void formatQRCode(StringBuilder output, String qrUrl) {
        try {
            Log.d(TAG, "🔗 Generando QR para URL: " + qrUrl);
            
            // Generar QR tamaño óptimo para no cortarse (120x120px)
            Bitmap qrBitmap = QRCodeGenerator.generateQRCode(qrUrl, 120, 120); // Tamaño conservador
            
            if (qrBitmap != null) {
                // Convertir QR a ESC/POS con centrado mejorado
                byte[] qrCommands = SimpleImageConverter.convertBitmapSimple(qrBitmap);
                if (qrCommands != null && qrCommands.length > 0) {
                    setCenterAlignment(output); // Centrado mejorado
                    output.append(new String(qrCommands, CHARSET_ENCODING));
                    addCommand(output, FEED_LINE);
                    setLeftAlignment(output); // Volver a izquierda
                }
                
                Log.d(TAG, "✅ QR generado correctamente");
            } else {
                Log.w(TAG, "⚠️ No se pudo generar el QR");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error generando QR: " + e.getMessage());
        }
    }
    
    /**
     * 📄 Formatear CUFE para mejor legibilidad en impresora térmica
     */
    private static String formatCufeText(String cufe) {
        if (cufe == null || cufe.length() < 20) {
            return cufe;
        }
        
        // Dividir CUFE en líneas de máximo 32 caracteres para papel 58mm
        // y 48 caracteres para papel 80mm
        StringBuilder formatted = new StringBuilder();
        int lineLength = 32; // Conservador para ambos tamaños
        
        for (int i = 0; i < cufe.length(); i += lineLength) {
            int endIndex = Math.min(i + lineLength, cufe.length());
            String line = cufe.substring(i, endIndex);
            formatted.append(line);
            
            // Agregar salto de línea si no es la última línea
            if (endIndex < cufe.length()) {
                formatted.append("\n");
            }
        }
        
        return formatted.toString();
    }
}
