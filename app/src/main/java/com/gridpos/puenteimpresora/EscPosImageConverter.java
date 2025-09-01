package com.gridpos.puenteimpresora;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;

public class EscPosImageConverter {
    private static final String TAG = "EscPosImageConverter";
    
    // Comandos ESC/POS
    private static final byte[] ESC = {0x1B};
    private static final byte[] GS = {0x1D};
    private static final byte[] INIT_PRINTER = {0x1B, 0x40}; // ESC @
    private static final byte[] CENTER_ALIGN = {0x1B, 0x61, 0x01}; // ESC a 1
    private static final byte[] LEFT_ALIGN = {0x1B, 0x61, 0x00}; // ESC a 0
    private static final byte[] LINE_FEED = {0x0A};
    private static final byte[] CUT_PAPER = {0x1D, 0x56, 0x00}; // GS V 0

    public static byte[] bitmapToEscPos(Bitmap bitmap) {
        try {
            Log.d(TAG, "🖼️ Iniciando conversión de imagen: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // Inicializar impresora
            outputStream.write(INIT_PRINTER);
            outputStream.write(CENTER_ALIGN); // Centrar imagen
            
            // Redimensionar para impresora de 80mm (384 pixels de ancho óptimo)
            Bitmap processedBitmap = resizeBitmapOptimal(bitmap, 384);
            Log.d(TAG, "🔧 Imagen redimensionada a: " + processedBitmap.getWidth() + "x" + processedBitmap.getHeight());
            
            // Convertir a monocromo con dithering mejorado
            Bitmap monoBitmap = convertToMonochromeWithDithering(processedBitmap);
            
            // Usar método raster más efectivo
            byte[] imageData = convertBitmapToRasterData(monoBitmap);
            outputStream.write(imageData);
            
            // Restaurar alineación y alimentar papel
            outputStream.write(LEFT_ALIGN);
            outputStream.write(LINE_FEED);
            outputStream.write(LINE_FEED);
            outputStream.write(CUT_PAPER);
            
            Log.d(TAG, "✅ Conversión completada, " + outputStream.size() + " bytes totales");
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error convirtiendo imagen a ESC/POS", e);
            return createErrorFallback();
        }
    }

    /**
     * 🔧 Redimensionar imagen de manera óptima para impresoras térmicas
     */
    private static Bitmap resizeBitmapOptimal(Bitmap original, int targetWidth) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        Log.d(TAG, "📐 Redimensionando desde " + originalWidth + "x" + originalHeight + " a ancho " + targetWidth);
        
        // Si ya es del tamaño correcto o menor, mantener proporción
        if (originalWidth <= targetWidth) {
            Log.d(TAG, "✅ Imagen ya es del tamaño correcto");
            return original;
        }
        
        // Calcular nueva altura manteniendo proporción
        float ratio = (float) targetWidth / originalWidth;
        int newHeight = Math.round(originalHeight * ratio);
        
        // Usar FILTER = true para mejor calidad en el redimensionamiento
        Bitmap resized = Bitmap.createScaledBitmap(original, targetWidth, newHeight, true);
        Log.d(TAG, "🎯 Imagen redimensionada a: " + resized.getWidth() + "x" + resized.getHeight());
        
        return resized;
    }

    /**
     * 🎨 Convertir a monocromo con dithering mejorado
     */
    private static Bitmap convertToMonochromeWithDithering(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        Log.d(TAG, "🎨 Convirtiendo a monocromo con dithering: " + width + "x" + height);
        
        Bitmap monoBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        
        // Array para dithering Floyd-Steinberg
        float[][] luminanceArray = new float[width][height];
        
        // Calcular luminancia inicial
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = original.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                luminanceArray[x][y] = 0.299f * red + 0.587f * green + 0.114f * blue;
            }
        }
        
        // Aplicar dithering Floyd-Steinberg
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float oldPixel = luminanceArray[x][y];
                int newPixel = oldPixel > 128 ? 255 : 0;
                float error = oldPixel - newPixel;
                
                monoBitmap.setPixel(x, y, newPixel == 255 ? Color.WHITE : Color.BLACK);
                
                // Distribuir error a píxeles vecinos
                if (x + 1 < width) {
                    luminanceArray[x + 1][y] += error * 7.0f / 16.0f;
                }
                if (y + 1 < height) {
                    if (x - 1 >= 0) {
                        luminanceArray[x - 1][y + 1] += error * 3.0f / 16.0f;
                    }
                    luminanceArray[x][y + 1] += error * 5.0f / 16.0f;
                    if (x + 1 < width) {
                        luminanceArray[x + 1][y + 1] += error * 1.0f / 16.0f;
                    }
                }
            }
        }
        
        Log.d(TAG, "✅ Conversión a monocromo completada");
        return monoBitmap;
    }

    /**
     * 🖨️ Convertir bitmap a datos raster ESC/POS (más efectivo)
     */
    private static byte[] convertBitmapToRasterData(Bitmap bitmap) {
        try {
            Log.d(TAG, "🖨️ Convirtiendo a datos raster ESC/POS");
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int widthBytes = (width + 7) / 8; // Redondear hacia arriba a múltiplo de 8
            
            Log.d(TAG, "📏 Dimensiones raster: " + width + "x" + height + ", bytes por línea: " + widthBytes);
            
            // Comando raster bit image: GS v 0
            outputStream.write(GS);
            outputStream.write(0x76); // v
            outputStream.write(0x30); // 0 (modo normal)
            
            // Ancho en bytes (low byte, high byte)
            outputStream.write(widthBytes & 0xFF);
            outputStream.write((widthBytes >> 8) & 0xFF);
            
            // Altura en píxeles (low byte, high byte)
            outputStream.write(height & 0xFF);
            outputStream.write((height >> 8) & 0xFF);
            
            // Convertir imagen línea por línea
            for (int y = 0; y < height; y++) {
                for (int byteIndex = 0; byteIndex < widthBytes; byteIndex++) {
                    int byteValue = 0;
                    
                    for (int bit = 0; bit < 8; bit++) {
                        int x = byteIndex * 8 + bit;
                        
                        if (x < width) {
                            int pixel = bitmap.getPixel(x, y);
                            if (pixel == Color.BLACK) {
                                byteValue |= (0x80 >> bit); // Bit más significativo primero
                            }
                        }
                    }
                    
                    outputStream.write(byteValue);
                }
            }
            
            Log.d(TAG, "✅ Datos raster generados: " + outputStream.size() + " bytes");
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en conversión raster", e);
            return new byte[0];
        }
    }

    /**
     * 🚨 Crear mensaje de error como fallback
     */
    private static byte[] createErrorFallback() {
        try {
            String errorMsg = "ERROR DE IMPRESION\nImagen no procesable\n\n";
            return errorMsg.getBytes("UTF-8");
        } catch (Exception e) {
            return "ERROR\n\n".getBytes();
        }
    }

    private static byte[] convertBitmapToEscPosData(Bitmap bitmap) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            // Procesar la imagen línea por línea
            for (int y = 0; y < height; y += 24) { // ESC/POS procesa en grupos de 24 pixels de altura
                // Comando para modo de imagen de bits
                outputStream.write(ESC);
                outputStream.write(0x2A); // *
                outputStream.write(0x21); // Modo 8-dot single-density
                
                // Ancho en bytes (low byte, high byte)
                int widthBytes = (width + 7) / 8; // Redondear hacia arriba
                outputStream.write(widthBytes & 0xFF);
                outputStream.write((widthBytes >> 8) & 0xFF);
                
                // Convertir 24 líneas de pixels a bytes
                for (int x = 0; x < width; x += 8) {
                    for (int slice = 0; slice < 3; slice++) { // 3 slices de 8 bits = 24 bits de altura
                        int byteValue = 0;
                        
                        for (int bit = 0; bit < 8; bit++) {
                            int pixelX = x + bit;
                            int pixelY = y + (slice * 8) + bit;
                            
                            if (pixelX < width && pixelY < height) {
                                int pixel = bitmap.getPixel(pixelX, pixelY);
                                if (pixel == Color.BLACK) {
                                    byteValue |= (1 << (7 - bit));
                                }
                            }
                        }
                        
                        outputStream.write(byteValue);
                    }
                }
                
                // Nueva línea después de cada franja
                outputStream.write(LINE_FEED);
            }
            
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "Error en conversión de bitmap", e);
            return new byte[0];
        }
    }
}
