package com.gridpos.puenteimpresora;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * 📱 Generador de códigos QR para impresión térmica
 * Optimizado para GridPOS Puente Impresora
 * Versión mejorada con mejor compatibilidad
 */
public class QRCodeGenerator {
    private static final String TAG = "QRCodeGenerator";
    
    /**
     * 🎯 Generar código QR desde URL para impresión térmica (MÉTODO MEJORADO)
     */
    public static Bitmap generateQRCode(String url, int width, int height) {
        try {
            Log.d(TAG, "🎯 Generando QR para URL: " + url);
            Log.d(TAG, "📐 Tamaño solicitado: " + width + "x" + height);
            
            if (url == null || url.trim().isEmpty()) {
                Log.e(TAG, "❌ URL vacía o nula");
                return null;
            }
            
            // Configurar opciones de QR optimizadas para impresoras térmicas
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M); // Corrección media
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); // Soporte para caracteres especiales
            hints.put(EncodeHintType.MARGIN, 2); // Margen suficiente para impresoras térmicas
            
            // Generar matriz de bits
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(url.trim(), BarcodeFormat.QR_CODE, width, height, hints);
            
            Log.d(TAG, "✅ BitMatrix generada: " + bitMatrix.getWidth() + "x" + bitMatrix.getHeight());
            
            // Crear bitmap usando método mejorado
            Bitmap bitmap = createBitmapFromBitMatrix(bitMatrix, width, height);
            
            if (bitmap != null) {
                Log.d(TAG, "✅ QR generado exitosamente: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                Log.d(TAG, "🎨 Configuración: " + bitmap.getConfig());
            } else {
                Log.e(TAG, "❌ Error: Bitmap resultante es null");
            }
            
            return bitmap;
            
        } catch (WriterException e) {
            Log.e(TAG, "❌ Error generando QR: " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error inesperado generando QR: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 🎨 Crear bitmap optimizado desde BitMatrix
     */
    private static Bitmap createBitmapFromBitMatrix(BitMatrix bitMatrix, int width, int height) {
        try {
            // Usar ARGB_8888 para mejor calidad
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            // Crear canvas para dibujar
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setAntiAlias(false); // Sin antialiasing para mejor definición
            
            // Fondo blanco
            canvas.drawColor(Color.WHITE);
            
            // Calcular escala para centrar el QR
            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();
            
            float scaleX = (float) width / matrixWidth;
            float scaleY = (float) height / matrixHeight;
            float scale = Math.min(scaleX, scaleY);
            
            int scaledWidth = (int) (matrixWidth * scale);
            int scaledHeight = (int) (matrixHeight * scale);
            
            int offsetX = (width - scaledWidth) / 2;
            int offsetY = (height - scaledHeight) / 2;
            
            Log.d(TAG, "🎯 Escala: " + scale + " | Offset: " + offsetX + "," + offsetY);
            
            // Dibujar píxeles del QR
            paint.setColor(Color.BLACK);
            for (int x = 0; x < matrixWidth; x++) {
                for (int y = 0; y < matrixHeight; y++) {
                    if (bitMatrix.get(x, y)) {
                        int pixelX = offsetX + (int) (x * scale);
                        int pixelY = offsetY + (int) (y * scale);
                        int pixelSize = Math.max(1, (int) scale);
                        
                        canvas.drawRect(pixelX, pixelY, 
                                      pixelX + pixelSize, pixelY + pixelSize, paint);
                    }
                }
            }
            
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creando bitmap: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 🎯 Generar QR optimizado para impresoras térmicas (130x130px)
     * Equivalente a style="width: 130px; height: 130px; display: block;"
     */
    public static Bitmap generateQRForThermalPrinter(String url) {
        return generateQRCode(url, 130, 130); // Tamaño optimizado 130x130px como en CSS
    }
    
    /**
     * 📱 Generar QR para papel 58mm (más pequeño)
     */
    public static Bitmap generateQRForSmallPrinter(String url) {
        return generateQRCode(url, 100, 100); // Tamaño óptimo para impresoras 58mm
    }
    
    /**
     * 🖨️ Generar QR para papel 80mm (más grande)
     */
    public static Bitmap generateQRForLargePrinter(String url) {
        return generateQRCode(url, 160, 160); // Tamaño óptimo para impresoras 80mm
    }
    
    /**
     * 🌍 Validar URL antes de generar QR
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        // Agregar protocolo si no existe
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        
        // Validaciones básicas
        return url.contains(".") && url.length() > 10;
    }
    
    /**
     * 🔧 Normalizar URL para QR
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "https://www.gridpos.co/"; // URL por defecto
        }
        
        String normalized = url.trim();
        
        // Agregar protocolo si no existe
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        
        return normalized;
    }
    
    /**
     * 🧪 Método de prueba para generar QR simple (SOLO PARA DEBUG)
     */
    public static Bitmap generateTestQR() {
        Log.d(TAG, "🧪 Generando QR de prueba simple...");
        
        try {
            String testUrl = "https://www.gridpos.co/";
            
            // Configuración básica sin hints complejos
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(testUrl, BarcodeFormat.QR_CODE, 200, 200);
            
            // Método simple de conversión
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            
            Log.d(TAG, "✅ QR de prueba generado: " + width + "x" + height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en QR de prueba: " + e.getMessage(), e);
            return null;
        }
    }
}
