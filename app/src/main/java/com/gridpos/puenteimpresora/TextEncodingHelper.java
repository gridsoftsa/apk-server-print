package com.gridpos.puenteimpresora;

import android.util.Log;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 🌍 Helper para manejar codificación de texto en impresoras térmicas
 * Soluciona problemas con caracteres especiales (ñ, tildes, etc.)
 */
public class TextEncodingHelper {
    private static final String TAG = "TextEncodingHelper";
    
    // Comandos ESC/POS para configurar página de códigos
    public static final byte[] ESC_INIT = {0x1B, 0x40}; // Inicializar
    public static final byte[] SET_CODEPAGE_CP437 = {0x1B, 0x74, 0x00}; // CP437 (USA)
    public static final byte[] SET_CODEPAGE_CP850 = {0x1B, 0x74, 0x02}; // CP850 (Latin-1)
    public static final byte[] SET_CODEPAGE_CP858 = {0x1B, 0x74, 0x13}; // CP858 (Latin-1 con €)
    public static final byte[] SET_INTERNATIONAL_CHARSET = {0x1B, 0x52, 0x0A}; // España
    
    /**
     * 🎯 Convertir texto con caracteres especiales para impresoras térmicas
     */
    public static byte[] encodeTextForThermalPrinter(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        
        try {
            Log.d(TAG, "🌍 Codificando texto: " + text);
            
            // Preparar comandos de inicialización
            java.util.List<Byte> commandList = new java.util.ArrayList<>();
            
            // Inicializar impresora
            addBytes(commandList, ESC_INIT);
            
            // Configurar página de códigos CP850 (mejor para español)
            addBytes(commandList, SET_CODEPAGE_CP850);
            
            // Configurar charset internacional para España
            addBytes(commandList, SET_INTERNATIONAL_CHARSET);
            
            // Convertir texto usando diferentes métodos según contenido
            String processedText = preprocessSpanishText(text);
            
            // Intentar diferentes codificaciones
            byte[] textBytes = encodeWithBestMethod(processedText);
            
            // Agregar el texto
            for (byte b : textBytes) {
                commandList.add(b);
            }
            
            // Convertir lista a array
            byte[] result = new byte[commandList.size()];
            for (int i = 0; i < commandList.size(); i++) {
                result[i] = commandList.get(i);
            }
            
            Log.d(TAG, "✅ Texto codificado: " + result.length + " bytes");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error codificando texto", e);
            // Fallback: texto sin caracteres especiales
            return text.replaceAll("[ñáéíóúü¿¡Ñ]", "?").getBytes(StandardCharsets.US_ASCII);
        }
    }
    
    /**
     * 🔄 Preprocesar texto español para mejor compatibilidad
     */
    private static String preprocessSpanishText(String text) {
        // Normalizar algunos caracteres problemáticos
        String processed = text;
        
        // Log caracteres especiales detectados
        if (processed.matches(".*[ñÑáéíóúüÁÉÍÓÚÜ¿¡].*")) {
            Log.d(TAG, "🌍 Caracteres especiales detectados en: " + text);
        }
        
        return processed;
    }
    
    /**
     * 📝 Probar diferentes métodos de codificación
     */
    private static byte[] encodeWithBestMethod(String text) {
        // Método 1: CP850 (recomendado para español)
        try {
            if (Charset.isSupported("CP850")) {
                byte[] cp850Bytes = text.getBytes("CP850");
                Log.d(TAG, "✅ Usando codificación CP850");
                return cp850Bytes;
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ CP850 no disponible");
        }
        
        // Método 2: ISO-8859-1 (Latin-1)
        try {
            if (Charset.isSupported("ISO-8859-1")) {
                byte[] iso88591Bytes = text.getBytes("ISO-8859-1");
                Log.d(TAG, "✅ Usando codificación ISO-8859-1");
                return iso88591Bytes;
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ ISO-8859-1 no disponible");
        }
        
        // Método 3: Windows-1252
        try {
            if (Charset.isSupported("windows-1252")) {
                byte[] windows1252Bytes = text.getBytes("windows-1252");
                Log.d(TAG, "✅ Usando codificación Windows-1252");
                return windows1252Bytes;
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Windows-1252 no disponible");
        }
        
        // Fallback: Reemplazar caracteres especiales manualmente
        Log.w(TAG, "⚠️ Usando fallback manual para caracteres especiales");
        return replaceSpanishCharsManually(text).getBytes(StandardCharsets.US_ASCII);
    }
    
    /**
     * 🔄 Reemplazo manual de caracteres especiales
     */
    private static String replaceSpanishCharsManually(String text) {
        return text
            .replace("ñ", "\u00F1") // ñ en Latin-1
            .replace("Ñ", "\u00D1") // Ñ en Latin-1
            .replace("á", "\u00E1") // á en Latin-1
            .replace("é", "\u00E9") // é en Latin-1
            .replace("í", "\u00ED") // í en Latin-1
            .replace("ó", "\u00F3") // ó en Latin-1
            .replace("ú", "\u00FA") // ú en Latin-1
            .replace("ü", "\u00FC") // ü en Latin-1
            .replace("Á", "\u00C1") // Á en Latin-1
            .replace("É", "\u00C9") // É en Latin-1
            .replace("Í", "\u00CD") // Í en Latin-1
            .replace("Ó", "\u00D3") // Ó en Latin-1
            .replace("Ú", "\u00DA") // Ú en Latin-1
            .replace("Ü", "\u00DC") // Ü en Latin-1
            .replace("¿", "\u00BF") // ¿ en Latin-1
            .replace("¡", "\u00A1"); // ¡ en Latin-1
    }
    
    /**
     * 🛠️ Helper para agregar bytes a lista
     */
    private static void addBytes(java.util.List<Byte> list, byte[] bytes) {
        for (byte b : bytes) {
            list.add(b);
        }
    }
    
    /**
     * 🧪 Método de prueba para diferentes codificaciones
     */
    public static void testEncodings(String text) {
        Log.d(TAG, "🧪 Probando codificaciones para: " + text);
        
        try {
            // UTF-8
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            Log.d(TAG, "UTF-8: " + java.util.Arrays.toString(utf8));
            
            // ISO-8859-1
            if (Charset.isSupported("ISO-8859-1")) {
                byte[] iso = text.getBytes("ISO-8859-1");
                Log.d(TAG, "ISO-8859-1: " + java.util.Arrays.toString(iso));
            }
            
            // CP850
            if (Charset.isSupported("CP850")) {
                byte[] cp850 = text.getBytes("CP850");
                Log.d(TAG, "CP850: " + java.util.Arrays.toString(cp850));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error en test de codificaciones", e);
        }
    }
}
