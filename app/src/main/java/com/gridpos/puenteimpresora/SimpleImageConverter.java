package com.gridpos.puenteimpresora;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import java.io.ByteArrayOutputStream;

/**
 * 🖼️ Convertidor simple de imágenes para impresoras térmicas
 * Alternativa simplificada cuando EscPosImageConverter falla
 */
public class SimpleImageConverter {
    private static final String TAG = "SimpleImageConverter";
    
    // Comandos ESC/POS básicos
    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    
    /**
     * 🎯 Conversión simple de bitmap a ESC/POS (método básico)
     */
    public static byte[] convertBitmapSimple(Bitmap bitmap) {
        try {
            Log.d(TAG, "🖼️ Conversión simple: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // Redimensionar a ancho fijo para impresoras de 80mm
            Bitmap resized = resizeForThermalPrinter(bitmap, 384);
            
            // Convertir a monocromo simple
            Bitmap monoBitmap = convertToSimpleMonochrome(resized);
            
            // Generar datos ESC/POS usando método bit-image
            return generateBitImageData(monoBitmap);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en conversión simple", e);
            return createTextFallback();
        }
    }
    
    /**
     * 📏 Redimensionar para impresora térmica
     */
    private static Bitmap resizeForThermalPrinter(Bitmap original, int targetWidth) {
        try {
            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();
            
            // 🎯 Manejo especial para QRs optimizados (130x130px)
            if (originalWidth == 130 && originalHeight == 130) {
                Log.d(TAG, "🎯 QR 130x130px detectado - manteniendo tamaño optimizado");
                return original; // Mantener el tamaño perfecto para QR
            }
            
            // Calcular nueva altura manteniendo proporción
            int targetHeight = (originalHeight * targetWidth) / originalWidth;
            
            // Limitar altura máxima
            if (targetHeight > 500) {
                targetHeight = 500;
                targetWidth = (originalWidth * targetHeight) / originalHeight;
            }
            
            Log.d(TAG, "📏 Redimensionando de " + originalWidth + "x" + originalHeight + 
                      " a " + targetWidth + "x" + targetHeight);
            
            return Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);
            
        } catch (Exception e) {
            Log.e(TAG, "Error redimensionando", e);
            return original;
        }
    }
    
    /**
     * ⚫⚪ Conversión simple a monocromo
     */
    private static Bitmap convertToSimpleMonochrome(Bitmap original) {
        try {
            int width = original.getWidth();
            int height = original.getHeight();
            
            Bitmap monoBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(monoBitmap);
            Paint paint = new Paint();
            
            // Fondo blanco
            canvas.drawColor(Color.WHITE);
            
            // Convertir pixel por pixel con umbral simple
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = original.getPixel(x, y);
                    
                    // Calcular brillo (luminancia)
                    int red = Color.red(pixel);
                    int green = Color.green(pixel);
                    int blue = Color.blue(pixel);
                    int brightness = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
                    
                    // Umbral: < 128 = negro, >= 128 = blanco
                    int newColor = brightness < 128 ? Color.BLACK : Color.WHITE;
                    monoBitmap.setPixel(x, y, newColor);
                }
            }
            
            Log.d(TAG, "⚫⚪ Conversión a monocromo completada");
            return monoBitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error convirtiendo a monocromo", e);
            return original;
        }
    }
    
    /**
     * 📊 Generar datos bit-image ESC/POS
     */
    private static byte[] generateBitImageData(Bitmap bitmap) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            Log.d(TAG, "📊 Generando bit-image: " + width + "x" + height);
            
            // Inicializar impresora
            stream.write(ESC);
            stream.write('@'); // Inicializar
            
            // Centrar
            stream.write(ESC);
            stream.write('a');
            stream.write(1); // Centro
            
            // Procesar imagen línea por línea (método simple)
            for (int y = 0; y < height; y += 24) { // 24 puntos por línea (3 bytes)
                int linesThisPass = Math.min(24, height - y);
                
                // Comando de imagen ESC * m nL nH
                stream.write(ESC);
                stream.write('*');
                stream.write(33); // Modo 33: 24 puntos, densidad normal
                stream.write((byte) (width & 0xFF)); // nL
                stream.write((byte) ((width >> 8) & 0xFF)); // nH
                
                // Datos de la imagen
                for (int x = 0; x < width; x++) {
                    // 3 bytes por columna (24 bits)
                    int byte1 = 0, byte2 = 0, byte3 = 0;
                    
                    for (int bit = 0; bit < 8 && (y + bit) < height; bit++) {
                        if (bitmap.getPixel(x, y + bit) == Color.BLACK) {
                            byte1 |= (1 << (7 - bit));
                        }
                    }
                    
                    for (int bit = 0; bit < 8 && (y + 8 + bit) < height; bit++) {
                        if (bitmap.getPixel(x, y + 8 + bit) == Color.BLACK) {
                            byte2 |= (1 << (7 - bit));
                        }
                    }
                    
                    for (int bit = 0; bit < 8 && (y + 16 + bit) < height; bit++) {
                        if (bitmap.getPixel(x, y + 16 + bit) == Color.BLACK) {
                            byte3 |= (1 << (7 - bit));
                        }
                    }
                    
                    stream.write(byte1);
                    stream.write(byte2);
                    stream.write(byte3);
                }
                
                // Nueva línea
                stream.write('\n');
            }
            
            // Restaurar alineación
            stream.write(ESC);
            stream.write('a');
            stream.write(0); // Izquierda
            
            // Líneas de separación
            stream.write('\n');
            stream.write('\n');
            
            Log.d(TAG, "✅ Bit-image generado: " + stream.size() + " bytes");
            return stream.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generando bit-image", e);
            return createTextFallback();
        }
    }
    
    /**
     * 📝 Crear fallback de texto cuando falla la imagen
     */
    private static byte[] createTextFallback() {
        try {
            String fallback = "\n=== IMAGEN NO DISPONIBLE ===\n";
            fallback += "Error procesando imagen\n";
            fallback += "Prueba con imagen mas pequena\n";
            fallback += "o diferente formato\n\n";
            
            return TextEncodingHelper.encodeTextForThermalPrinter(fallback);
            
        } catch (Exception e) {
            return "ERROR IMAGEN\n\n".getBytes();
        }
    }
}
