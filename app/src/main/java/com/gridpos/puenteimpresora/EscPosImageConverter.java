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
    private static final byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 0x21}; // ESC * !
    private static final byte[] LINE_FEED = {0x0A};
    private static final byte[] CUT_PAPER = {0x1D, 0x56, 0x42, 0x00}; // GS V B 0

    public static byte[] bitmapToEscPos(Bitmap bitmap) {
        try {
            // Inicializar el stream de salida
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // Comando de inicialización
            outputStream.write(INIT_PRINTER);
            
            // Redimensionar imagen si es necesario (máximo 576 pixels para impresoras de 80mm)
            Bitmap processedBitmap = resizeBitmap(bitmap, 576);
            
            // Convertir a monocromo
            Bitmap monoBitmap = convertToMonochrome(processedBitmap);
            
            // Convertir a datos ESC/POS
            byte[] imageData = convertBitmapToEscPosData(monoBitmap);
            outputStream.write(imageData);
            
            // Alimentar papel y cortar
            outputStream.write(LINE_FEED);
            outputStream.write(LINE_FEED);
            outputStream.write(LINE_FEED);
            outputStream.write(CUT_PAPER);
            
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "Error convirtiendo imagen a ESC/POS", e);
            return new byte[0];
        }
    }

    private static Bitmap resizeBitmap(Bitmap original, int maxWidth) {
        if (original.getWidth() <= maxWidth) {
            return original;
        }
        
        float ratio = (float) maxWidth / original.getWidth();
        int newHeight = (int) (original.getHeight() * ratio);
        
        return Bitmap.createScaledBitmap(original, maxWidth, newHeight, true);
    }

    private static Bitmap convertToMonochrome(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        Bitmap monoBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = original.getPixel(x, y);
                
                // Calcular la luminancia
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                int luminance = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
                
                // Convertir a blanco o negro basado en un umbral
                int newPixel = luminance > 128 ? Color.WHITE : Color.BLACK;
                monoBitmap.setPixel(x, y, newPixel);
            }
        }
        
        return monoBitmap;
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
