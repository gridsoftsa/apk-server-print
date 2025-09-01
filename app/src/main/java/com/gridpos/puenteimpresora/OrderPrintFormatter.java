package com.gridpos.puenteimpresora;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 🖨️ Formateador de órdenes para impresión ESC/POS
 * Replica la lógica del PrinterController de Windows
 */
public class OrderPrintFormatter {
    private static final String TAG = "OrderPrintFormatter";
    
    // Comandos ESC/POS
    private static final byte[] INITIALIZE = {0x1B, 0x40};
    private static final byte[] JUSTIFY_CENTER = {0x1B, 0x61, 0x01};
    private static final byte[] JUSTIFY_LEFT = {0x1B, 0x61, 0x00};
    private static final byte[] MODE_EMPHASIZED = {0x1B, 0x45, 0x01};
    private static final byte[] MODE_NORMAL = {0x1B, 0x45, 0x00};
    private static final byte[] MODE_DOUBLE_WIDTH = {0x1B, 0x21, 0x20};
    private static final byte[] MODE_DOUBLE_WIDTH_EMPHASIZED = {0x1B, 0x21, 0x28};
    private static final byte[] FEED_LINE = {0x0A};
    private static final byte[] CUT_PAPER = {0x1D, 0x56, 0x00};
    private static final byte[] OPEN_CASH = {0x1B, 0x70, 0x00, 0x19, (byte)0xFA};
    
    // 🌍 Configuración para caracteres especiales (ñ, tildes, etc.)
    private static final byte[] SET_CHARSET_SPAIN = {0x1B, 0x52, 0x0A}; // Página de códigos España
    private static final byte[] SET_CHARSET_LATIN1 = {0x1C, 0x2E}; // ISO-8859-1 Latin-1
    
    // Codificación para caracteres especiales
    private static final String CHARSET_ENCODING = "ISO-8859-1"; // Latin-1 para caracteres especiales
    
    /**
     * 🎯 Formatear orden completa para impresión - RÉPLICA EXACTA de PrinterController.php
     */
    public static byte[] formatOrder(JSONObject orderData, int paperWidth, boolean openCash) {
        try {
            List<Byte> commandList = new ArrayList<>();
            boolean isSmallPaper = paperWidth == 58;
            
            Log.d(TAG, "Formateando orden para papel " + paperWidth + "mm (réplica PHP)");
            
            // === INICIALIZAR ===
            addBytes(commandList, INITIALIZE);
            
            // 🌍 Configurar codificación para caracteres especiales (ñ, tildes, etc.)
            addBytes(commandList, SET_CHARSET_SPAIN);
            addBytes(commandList, SET_CHARSET_LATIN1);
            
            addBytes(commandList, JUSTIFY_CENTER);
            
            // === ENCABEZADO - Cliente si existe - Ajustado por tamaño de papel ===
            String clientName = getClientName(orderData);
            if (clientName != null && !clientName.isEmpty()) {
                if (isSmallPaper) {
                    // 📱 Para papel 58mm: usar solo EMPHASIZED (texto moderado)
                    addBytes(commandList, MODE_EMPHASIZED);
                    
                    // Limitar nombre del cliente a 32 caracteres para 58mm
                    String clientNameFormatted;
                    if (clientName.length() > 32) {
                        clientNameFormatted = clientName.substring(0, 32);
                    } else {
                        clientNameFormatted = clientName;
                    }
                    addText(commandList, clientNameFormatted + "\n");
                } else {
                    // 🖨️ Para papel 80mm: texto grande normal
                    addBytes(commandList, MODE_DOUBLE_WIDTH_EMPHASIZED);
                    addText(commandList, clientName + "\n");
                }
                addBytes(commandList, MODE_NORMAL); // Reset
            }
            
            // Fecha de la orden
            String orderDate = getOrderDate(orderData);
            if (orderDate != null) {
                addText(commandList, orderDate + "\n");
            }
            
            // Si existe el phone de la empresa, imprimirlo (RÉPLICA PHP)
            String phone = getOrderPhone(orderData);
            if (phone != null && !phone.isEmpty()) {
                addText(commandList, "CEL: " + phone + "\n");
            }
            
            // Agregar la direccion de shipping_address si existe (RÉPLICA PHP)
            String shippingAddress = getShippingAddress(orderData);
            if (shippingAddress != null && !shippingAddress.isEmpty()) {
                addText(commandList, "DIRECCION: " + shippingAddress + "\n");
            }
            
            // === SEPARADOR GRUESO ===
            addBytes(commandList, JUSTIFY_LEFT);
            String separator = isSmallPaper ? repeatChar('-', 32) : repeatChar('-', 48);
            addText(commandList, separator + "\n");
            
            // ENCABEZADOS DE COLUMNAS - Ajustado para tamaño de papel (RÉPLICA PHP)
            addBytes(commandList, MODE_EMPHASIZED);
            if (isSmallPaper) {
                addText(commandList, "CANT  ITEM\n"); // Más compacto para 58mm
            } else {
                addText(commandList, "CANT     ITEM\n"); // Formato normal para 80mm
            }
            addBytes(commandList, MODE_NORMAL); // Reset
            addText(commandList, separator + "\n");
            
            // === PRODUCTOS - FORMATO OPTIMIZADO PARA TAMAÑO DE PAPEL (RÉPLICA PHP) ===
            JSONArray products = getProducts(orderData);
            if (products != null) {
                int productCount = products.length();
                int currentIndex = 0;
                
                for (int i = 0; i < productCount; i++) {
                    currentIndex++;
                    JSONObject product = products.getJSONObject(i);
                    formatProduct(commandList, product, isSmallPaper);
                    
                    // Agregar espacio solo si no es el último producto (RÉPLICA PHP)
                    if (currentIndex < productCount) {
                        addText(commandList, "\n"); // Pequeño espacio entre productos
                    }
                }
            }
            
            // === SEPARADOR FINAL ===
            addText(commandList, separator + "\n");
            
            // NOTA GENERAL si existe (RÉPLICA PHP)
            String generalNote = getGeneralNote(orderData);
            if (generalNote != null && !generalNote.isEmpty()) {
                addBytes(commandList, MODE_EMPHASIZED);
                addText(commandList, "NOTA: " + generalNote.toUpperCase() + "\n");
                addBytes(commandList, MODE_NORMAL); // Reset
                addBytes(commandList, FEED_LINE);
            }
            
            // === PIE DE PÁGINA (RÉPLICA EXACTA PHP) ===
            // Usuario que atiende
            String userName = getUserName(orderData);
            if (userName != null) {
                addText(commandList, "Atendido por: " + userName + "\n");
            }
            
            // Timestamp de impresión
            String printDate = getPrintDate(orderData);
            if (printDate != null) {
                addText(commandList, "Impresión: " + printDate + "\n");
            }
            
            // ID de orden más visible (RÉPLICA PHP)
            String orderIdDisplay = getOrderIdDisplay(orderData);
            if (orderIdDisplay != null) {
                addBytes(commandList, MODE_EMPHASIZED);
                addText(commandList, "ORDEN: " + orderIdDisplay + "\n");
                addBytes(commandList, MODE_NORMAL); // Reset
            }
            
            addBytes(commandList, FEED_LINE);
            addBytes(commandList, CUT_PAPER);
            
            // Abrir caja si se requiere (RÉPLICA PHP)
            if (openCash) {
                addBytes(commandList, OPEN_CASH);
            }
            
            // Convertir lista a array de bytes
            byte[] result = new byte[commandList.size()];
            for (int i = 0; i < commandList.size(); i++) {
                result[i] = commandList.get(i);
            }
            
            Log.d(TAG, "Orden formateada correctamente, " + result.length + " bytes");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error formateando orden", e);
            return createErrorMessage("Error formateando orden: " + e.getMessage());
        }
    }
    
    /**
     * 🍕 Formatear producto individual - RÉPLICA EXACTA del PrinterController.php
     */
    private static void formatProduct(List<Byte> commandList, JSONObject product, boolean isSmallPaper) {
        try {
            int qty = product.optInt("quantity", 1);
            String name = product.optString("name", "Producto");
            String notes = product.optString("notes", "");
            
            if (isSmallPaper) {
                // 📱 FORMATO PARA PAPEL 58MM - Texto moderado sin cortes (RÉPLICA PHP)
                String qtyPadded = String.format("%-2s", String.valueOf(qty));
                
                // Usar solo EMPHASIZED para 58mm (sin DOUBLE_WIDTH que corta el texto)
                addBytes(commandList, MODE_EMPHASIZED);
                
                // Calcular espacio disponible: 32 chars - 2 qty - 2 espacios = 28 chars para nombre
                int maxNameChars = 28;
                String nameFormatted = name.length() > maxNameChars ? 
                    name.substring(0, maxNameChars) : name;
                
                addText(commandList, qtyPadded + "  " + nameFormatted.toUpperCase() + "\n");
                addBytes(commandList, MODE_NORMAL); // Reset
                
                // Si el nombre fue cortado, imprimir el resto en la siguiente línea (RÉPLICA PHP)
                if (name.length() > maxNameChars) {
                    String remainingName = name.substring(maxNameChars);
                    addBytes(commandList, MODE_EMPHASIZED);
                    addText(commandList, "    " + remainingName.toUpperCase() + "\n");
                    addBytes(commandList, MODE_NORMAL); // Reset
                }
            } else {
                // 🖨️ FORMATO PARA PAPEL 80MM - Texto grande normal (RÉPLICA PHP)
                addBytes(commandList, MODE_DOUBLE_WIDTH_EMPHASIZED);
                String qtyPadded = String.format("%-2s", String.valueOf(qty));
                addText(commandList, qtyPadded + "  " + name.toUpperCase() + "\n");
                addBytes(commandList, MODE_NORMAL); // Reset
            }
            
            // Notas del producto si existen (ajustadas por tamaño de papel) (RÉPLICA PHP)
            if (notes != null && !notes.isEmpty()) {
                addBytes(commandList, MODE_EMPHASIZED);
                
                if (isSmallPaper) {
                    // Para 58mm: limitar notas a 28 caracteres por línea (RÉPLICA PHP)
                    int maxNoteChars = 28;
                    List<String> noteLines = wordWrapEscPos(notes, maxNoteChars);
                    for (String noteLine : noteLines) {
                        addText(commandList, "  * " + noteLine.toUpperCase() + "\n");
                    }
                } else {
                    // Para 80mm: formato normal (RÉPLICA PHP)
                    addText(commandList, "    * " + notes.toUpperCase() + "\n");
                }
                
                addBytes(commandList, MODE_NORMAL); // Reset
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error formateando producto", e);
            addText(commandList, "Error en producto\n");
        }
    }
    
    /**
     * 📝 Word wrap mejorado para ESC/POS - RÉPLICA EXACTA del PrinterController.php
     * Optimizado para papel 58mm
     */
    private static List<String> wordWrapEscPos(String text, int maxChars) {
        if (text.length() <= maxChars) {
            List<String> result = new ArrayList<>();
            result.add(text);
            return result; // Si el texto ya cabe, devolver como está
        }

        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            // Si la palabra sola es más larga que el ancho máximo, dividirla
            if (word.length() > maxChars) {
                // Finalizar línea actual si tiene contenido
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString().trim());
                    currentLine = new StringBuilder();
                }

                // Dividir palabra larga en chunks
                for (int i = 0; i < word.length(); i += maxChars) {
                    int end = Math.min(i + maxChars, word.length());
                    lines.add(word.substring(i, end));
                }
                continue;
            }

            // Verificar si la palabra cabe en la línea actual
            String testLine = currentLine.length() > 0 ? 
                currentLine + " " + word : word;

            if (testLine.length() <= maxChars) {
                currentLine = new StringBuilder(testLine);
            } else {
                // No cabe, finalizar línea actual y empezar nueva
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString().trim());
                }
                currentLine = new StringBuilder(word);
            }
        }

        // Agregar última línea si tiene contenido
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString().trim());
        }

        return lines;
    }

    /**
     * 📝 Word wrap simple para texto largo (método anterior)
     */
    private static List<String> wordWrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        
        if (text.length() <= maxChars) {
            lines.add(text);
            return lines;
        }
        
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (word.length() > maxChars) {
                // Palabra muy larga, dividirla
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString().trim());
                    currentLine = new StringBuilder();
                }
                
                // Dividir palabra en chunks
                for (int i = 0; i < word.length(); i += maxChars) {
                    int end = Math.min(i + maxChars, word.length());
                    lines.add(word.substring(i, end));
                }
                continue;
            }
            
            String testLine = currentLine.length() > 0 ? 
                currentLine + " " + word : word;
            
            if (testLine.length() <= maxChars) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString().trim());
                }
                currentLine = new StringBuilder(word);
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString().trim());
        }
        
        return lines;
    }
    
    // === MÉTODOS DE UTILIDAD ===
    
    private static void addBytes(List<Byte> list, byte[] bytes) {
        for (byte b : bytes) {
            list.add(b);
        }
    }
    
    private static void addText(List<Byte> list, String text) {
        try {
            // 🌍 Normalizar caracteres especiales antes de enviar a impresora
            String normalizedText = normalizeSpanishCharacters(text);
            
            // Usar codificación Latin-1 para caracteres especiales (ñ, tildes, etc.)
            byte[] bytes = normalizedText.getBytes(CHARSET_ENCODING);
            for (byte b : bytes) {
                list.add(b);
            }
            Log.v(TAG, "Texto añadido con codificación " + CHARSET_ENCODING + ": " + normalizedText);
        } catch (Exception e) {
            Log.e(TAG, "Error añadiendo texto con caracteres especiales", e);
            // Fallback a UTF-8 si falla
            try {
                String fallbackText = normalizeSpanishCharacters(text);
                byte[] fallbackBytes = fallbackText.getBytes("UTF-8");
                for (byte b : fallbackBytes) {
                    list.add(b);
                }
            } catch (Exception fallbackError) {
                Log.e(TAG, "Error crítico en codificación", fallbackError);
            }
        }
    }
    
    /**
     * 🌍 Normalizar caracteres especiales del español para impresoras térmicas
     */
    private static String normalizeSpanishCharacters(String text) {
        if (text == null) return "";
        
        // Crear una copia para procesar
        String normalized = text;
        
        // Log para debug
        if (text.matches(".*[ñÑáéíóúüÁÉÍÓÚÜ¿¡].*")) {
            Log.d(TAG, "Texto con caracteres especiales detectado: " + text);
        }
        
        // Mantener los caracteres especiales tal como están para Latin-1
        // La codificación Latin-1 debería manejar correctamente:
        // ñ, Ñ, á, é, í, ó, ú, ü, Á, É, Í, Ó, Ú, Ü, ¿, ¡
        
        return normalized;
    }
    
    private static String repeatChar(char c, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
    
    private static byte[] createErrorMessage(String message) {
        try {
            String errorText = "ERROR DE IMPRESIÓN\n" + message + "\n\n";
            // 🌍 Usar codificación Latin-1 para caracteres especiales
            return errorText.getBytes(CHARSET_ENCODING);
        } catch (Exception e) {
            try {
                return "ERROR CRÍTICO\n\n".getBytes(CHARSET_ENCODING);
            } catch (Exception fallbackError) {
                return "ERROR CRITICO\n\n".getBytes();
            }
        }
    }
    
    // === EXTRACTORES DE DATOS JSON ===
    
    private static String getClientName(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                String clientName = orderInfo.optString("client_name", null);
                if (clientName != null && !clientName.isEmpty()) {
                    return clientName;
                }
            }
            
            JSONObject clientInfo = orderData.optJSONObject("client_info");
            if (clientInfo != null) {
                return clientInfo.optString("name", null);
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo client name", e);
            return null;
        }
    }
    
    private static String getOrderDate(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                return orderInfo.optString("date", null);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo order date", e);
            return null;
        }
    }
    
    private static String getOrderPhone(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                return orderInfo.optString("phone", null);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo phone", e);
            return null;
        }
    }
    
    private static String getShippingAddress(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                return orderInfo.optString("shipping_address", null);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo shipping address", e);
            return null;
        }
    }
    
    private static JSONArray getProducts(JSONObject orderData) {
        try {
            return orderData.optJSONArray("products");
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo products", e);
            return null;
        }
    }
    
    private static String getGeneralNote(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                String note = orderInfo.optString("note", null);
                if (note != null && !note.isEmpty()) {
                    return note;
                }
            }
            
            return orderData.optString("general_note", null);
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo general note", e);
            return null;
        }
    }
    
    private static String getUserName(JSONObject orderData) {
        try {
            JSONObject userInfo = orderData.optJSONObject("user");
            if (userInfo != null) {
                String name = userInfo.optString("name", null);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
                
                String nickname = userInfo.optString("nickname", null);
                if (nickname != null && !nickname.isEmpty()) {
                    return nickname;
                }
            }
            
            return "Sistema";
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo user name", e);
            return "Sistema";
        }
    }
    
    private static String getPrintDate(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                String datePrint = orderInfo.optString("date_print", null);
                if (datePrint != null && !datePrint.isEmpty()) {
                    return datePrint;
                }
            }
            
            // Si no hay date_print, usar fecha actual
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date());
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo print date", e);
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        }
    }
    
    private static String getOrderId(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                // Priorizar order_number si hay shipping_address
                String shippingAddress = orderInfo.optString("shipping_address", null);
                if (shippingAddress != null && !shippingAddress.isEmpty()) {
                    String orderNumber = orderInfo.optString("order_number", null);
                    if (orderNumber != null && !orderNumber.isEmpty()) {
                        return orderNumber;
                    }
                }
                
                // Usar ID normal si no hay shipping
                String id = orderInfo.optString("id", null);
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            }
            
            return "1";
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo order ID", e);
            return "1";
        }
    }
    
    /**
     * 🆔 Obtener ID de orden para mostrar - RÉPLICA EXACTA del PrinterController.php
     */
    private static String getOrderIdDisplay(JSONObject orderData) {
        try {
            JSONObject orderInfo = orderData.optJSONObject("order_data");
            if (orderInfo != null) {
                // Priorizar order_number si hay shipping_address (RÉPLICA PHP)
                String shippingAddress = orderInfo.optString("shipping_address", null);
                if (shippingAddress != null && !shippingAddress.isEmpty()) {
                    String orderNumber = orderInfo.optString("order_number", null);
                    if (orderNumber != null && !orderNumber.isEmpty()) {
                        return orderNumber;
                    }
                }
                
                // Usar ID normal si no hay shipping (RÉPLICA PHP)
                String id = orderInfo.optString("id", null);
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            }
            
            return "1";
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo order ID display", e);
            return "1";
        }
    }
}
