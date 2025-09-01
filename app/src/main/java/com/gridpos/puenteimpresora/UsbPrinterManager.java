package com.gridpos.puenteimpresora;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbConstants;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class UsbPrinterManager {
    private static final String TAG = "UsbPrinterManager";
    private static final String ACTION_USB_PERMISSION = "com.gridpos.puenteimpresora.USB_PERMISSION";
    
    private Context context;
    private UsbManager usbManager;
    private UsbDevice printerDevice;
    private UsbDeviceConnection connection;
    private UsbEndpoint endpointOut;
    private boolean hasPermission = false;

    public UsbPrinterManager(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        
        // Registrar receiver para permisos USB
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(usbReceiver, filter);
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            hasPermission = true;
                            printerDevice = device;
                            Log.d(TAG, "Permiso USB otorgado para dispositivo: " + device.getDeviceName());
                        }
                    } else {
                        Log.d(TAG, "Permiso USB denegado para dispositivo " + device);
                    }
                }
            }
        }
    };

    public boolean connectToPrinter() {
        Log.d(TAG, "🔍 Iniciando búsqueda de impresoras USB...");
        
        // Buscar impresora USB conectada
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Log.d(TAG, "📱 Dispositivos USB encontrados: " + deviceList.size());
        
        // Lista todos los dispositivos para debug
        for (String deviceName : deviceList.keySet()) {
            UsbDevice device = deviceList.get(deviceName);
            Log.d(TAG, "🔌 Dispositivo: " + deviceName + 
                  " | Clase: " + device.getDeviceClass() + 
                  " | VendorId: " + device.getVendorId() + 
                  " | ProductId: " + device.getProductId() +
                  " | Interfaces: " + device.getInterfaceCount());
            
            // Mostrar información de interfaces
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface usbInterface = device.getInterface(i);
                Log.d(TAG, "  └─ Interface " + i + ": Clase=" + usbInterface.getInterfaceClass() + 
                      " | Subclase=" + usbInterface.getInterfaceSubclass() +
                      " | Protocolo=" + usbInterface.getInterfaceProtocol());
            }
        }
        
        // Buscar impresoras con criterios más amplios
        for (UsbDevice device : deviceList.values()) {
            boolean isPrinter = false;
            String detectionReason = "";
            
            // Criterio 1: Clase de dispositivo 7 (impresoras)
            if (device.getDeviceClass() == 7) {
                isPrinter = true;
                detectionReason = "Device Class 7 (Printer)";
            }
            
            // Criterio 2: Interface clase 7 (impresoras)
            if (!isPrinter && device.getInterfaceCount() > 0) {
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = device.getInterface(i);
                    if (usbInterface.getInterfaceClass() == 7) {
                        isPrinter = true;
                        detectionReason = "Interface Class 7 (Printer)";
                        break;
                    }
                }
            }
            
            // Criterio 3: Vendors conocidos de impresoras térmicas (backup)
            if (!isPrinter) {
                int vendorId = device.getVendorId();
                // VendorIDs comunes de impresoras térmicas
                int[] printerVendors = {
                    0x04b8, // EPSON
                    0x0519, // Star Micronics  
                    0x20d1, // RONGTA
                    0x0fe6, // ICS Advent
                    0x1504, // BIXOLON
                    0x1fc9, // NXP (algunas impresoras)
                    0x1a86, // QinHeng Electronics
                    0x067b, // Prolific
                    0x0483  // STMicroelectronics
                };
                
                for (int knownVendor : printerVendors) {
                    if (vendorId == knownVendor) {
                        isPrinter = true;
                        detectionReason = "Known Printer Vendor (0x" + Integer.toHexString(vendorId).toUpperCase() + ")";
                        break;
                    }
                }
            }
            
            if (isPrinter) {
                printerDevice = device;
                Log.d(TAG, "🖨️ IMPRESORA DETECTADA: " + device.getDeviceName() + " (" + detectionReason + ")");
                
                // Verificar si ya tenemos permisos
                if (!usbManager.hasPermission(device)) {
                    Log.d(TAG, "🔐 Solicitando permisos USB...");
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(
                        context, 0, new Intent(ACTION_USB_PERMISSION), 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    usbManager.requestPermission(device, permissionIntent);
                    
                    // Esperar permisos con timeout mejorado
                    int attempts = 0;
                    int maxAttempts = 100; // 10 segundos
                    while (!hasPermission && attempts < maxAttempts) {
                        try {
                            Thread.sleep(100);
                            attempts++;
                            if (attempts % 10 == 0) {
                                Log.d(TAG, "⏳ Esperando permisos... (" + attempts/10 + "s)");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    if (!hasPermission) {
                        Log.w(TAG, "⚠️ Timeout esperando permisos USB");
                    }
                } else {
                    Log.d(TAG, "✅ Ya tenemos permisos USB");
                    hasPermission = true;
                }
                
                if (hasPermission) {
                    Log.d(TAG, "🔗 Estableciendo conexión...");
                    return establishConnection();
                } else {
                    Log.w(TAG, "❌ No se obtuvieron permisos para la impresora");
                }
                break;
            }
        }
        
        Log.e(TAG, "❌ No se encontró ninguna impresora USB compatible");
        return false;
    }

    private boolean establishConnection() {
        if (printerDevice == null) return false;
        
        try {
            connection = usbManager.openDevice(printerDevice);
            if (connection == null) {
                Log.e(TAG, "No se pudo abrir conexión con el dispositivo");
                return false;
            }

            // Obtener la primera interfaz
            UsbInterface usbInterface = printerDevice.getInterface(0);
            if (!connection.claimInterface(usbInterface, true)) {
                Log.e(TAG, "No se pudo reclamar la interfaz USB");
                connection.close();
                return false;
            }

            // Buscar endpoint de salida (OUT)
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    endpointOut = endpoint;
                    break;
                }
            }

            if (endpointOut == null) {
                Log.e(TAG, "No se encontró endpoint de salida");
                connection.releaseInterface(usbInterface);
                connection.close();
                return false;
            }

            Log.d(TAG, "Conexión USB establecida exitosamente");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error al establecer conexión USB", e);
            disconnect();
            return false;
        }
    }

    public void printBytes(byte[] data) throws IOException {
        if (connection == null || endpointOut == null) {
            throw new IOException("Impresora no conectada");
        }

        int transferred = connection.bulkTransfer(endpointOut, data, data.length, 5000);
        if (transferred < 0) {
            throw new IOException("Error al enviar datos a la impresora");
        }

        Log.d(TAG, "Datos enviados a la impresora: " + transferred + " bytes");
    }

    public void disconnect() {
        try {
            if (connection != null) {
                if (printerDevice != null && printerDevice.getInterfaceCount() > 0) {
                    connection.releaseInterface(printerDevice.getInterface(0));
                }
                connection.close();
                connection = null;
            }
            endpointOut = null;
            Log.d(TAG, "Conexión USB cerrada");
        } catch (Exception e) {
            Log.e(TAG, "Error al cerrar conexión USB", e);
        }
    }

    /**
     * 🔍 Verificar si hay una conexión activa con la impresora
     */
    public boolean isConnected() {
        return connection != null && printerDevice != null && endpointOut != null && hasPermission;
    }
    
    /**
     * 📤 Enviar datos raw directamente a la impresora
     */
    public void sendRawData(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Impresora no conectada");
        }
        
        try {
            printBytes(data);
            Log.d(TAG, "Datos enviados correctamente, " + data.length + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "Error enviando datos raw", e);
            throw new IOException("Error enviando datos: " + e.getMessage());
        }
    }
    
    /**
     * 📊 Obtener información del estado de la conexión
     */
    public String getConnectionStatus() {
        if (isConnected()) {
            return "✅ Conectado - " + printerDevice.getProductName();
        } else if (printerDevice != null && !hasPermission) {
            return "🔑 Esperando permisos - " + printerDevice.getProductName();
        } else if (printerDevice != null) {
            return "⚠️ Dispositivo encontrado pero no conectado";
        } else {
            return "❌ No hay impresora conectada";
        }
    }
    
    /**
     * 🔧 Obtener información detallada del dispositivo USB
     */
    public String getDeviceInfo() {
        if (printerDevice != null) {
            return String.format(
                "Dispositivo: %s\nVendor ID: %d\nProduct ID: %d\nPermiso: %s\nConexión: %s",
                printerDevice.getProductName(),
                printerDevice.getVendorId(),
                printerDevice.getProductId(),
                hasPermission ? "✅" : "❌",
                (connection != null) ? "✅" : "❌"
            );
        }
        return "No hay dispositivo USB detectado";
    }
    
    /**
     * 📋 Obtener lista de todos los dispositivos USB conectados
     */
    public List<com.gridpos.puenteimpresora.UsbDevice> getAvailableUsbDevices() {
        List<com.gridpos.puenteimpresora.UsbDevice> devices = new ArrayList<>();
        
        try {
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Log.d(TAG, "Dispositivos USB encontrados: " + deviceList.size());
            
            for (UsbDevice device : deviceList.values()) {
                // Crear información del dispositivo
                String displayName = getDeviceDisplayName(device);
                String deviceInfo = getDeviceDetailedInfo(device);
                boolean isPrinter = isPrinterDevice(device);
                
                com.gridpos.puenteimpresora.UsbDevice usbDevice = 
                    new com.gridpos.puenteimpresora.UsbDevice(device, displayName, deviceInfo, isPrinter);
                
                devices.add(usbDevice);
                
                Log.d(TAG, String.format("Dispositivo: %s | Info: %s | Es Impresora: %s", 
                    displayName, deviceInfo, isPrinter));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo dispositivos USB", e);
        }
        
        return devices;
    }
    
    /**
     * 🔌 Conectar a un dispositivo USB específico
     */
    public boolean connectToSpecificDevice(UsbDevice targetDevice) {
        try {
            Log.d(TAG, "Intentando conectar a dispositivo específico: " + getDeviceDisplayName(targetDevice));
            
            // Verificar permisos
            if (!usbManager.hasPermission(targetDevice)) {
                Log.d(TAG, "Sin permisos, solicitando...");
                requestPermission(targetDevice);
                return false; // Esperará al callback del receiver
            }
            
            // Cerrar conexión anterior si existe
            if (connection != null) {
                connection.close();
                connection = null;
            }
            
            // Establecer nueva conexión
            connection = usbManager.openDevice(targetDevice);
            if (connection == null) {
                Log.e(TAG, "No se pudo abrir el dispositivo");
                return false;
            }
            
            // Buscar interfaz y endpoint apropiados
            UsbInterface usbInterface = null;
            UsbEndpoint endpoint = null;
            
            for (int i = 0; i < targetDevice.getInterfaceCount(); i++) {
                UsbInterface iface = targetDevice.getInterface(i);
                
                // Buscar endpoints OUT
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        usbInterface = iface;
                        endpoint = ep;
                        break;
                    }
                }
                
                if (endpoint != null) break;
            }
            
            if (usbInterface == null || endpoint == null) {
                Log.e(TAG, "No se encontró interfaz/endpoint válido");
                connection.close();
                connection = null;
                return false;
            }
            
            // Reclamar interfaz
            if (!connection.claimInterface(usbInterface, true)) {
                Log.e(TAG, "No se pudo reclamar la interfaz");
                connection.close();
                connection = null;
                return false;
            }
            
            // Guardar referencias
            this.printerDevice = targetDevice;
            this.endpointOut = endpoint;
            this.hasPermission = true;
            
            Log.i(TAG, "✅ Conectado exitosamente a: " + getDeviceDisplayName(targetDevice));
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error conectando a dispositivo específico", e);
            return false;
        }
    }
    
    /**
     * 📝 Obtener nombre de visualización amigable para un dispositivo
     */
    private String getDeviceDisplayName(UsbDevice device) {
        StringBuilder name = new StringBuilder();
        
        // Buscar marca conocida por Vendor ID
        String vendorName = getVendorName(device.getVendorId());
        if (vendorName != null) {
            name.append(vendorName);
        } else {
            name.append("Dispositivo USB");
        }
        
        // Agregar información del producto si está disponible
        if (device.getProductName() != null && !device.getProductName().trim().isEmpty()) {
            name.append(" - ").append(device.getProductName().trim());
        }
        
        return name.toString();
    }
    
    /**
     * 📋 Obtener información detallada del dispositivo
     */
    private String getDeviceDetailedInfo(UsbDevice device) {
        return String.format("VID:%04X PID:%04X | %s | %d interfaces", 
            device.getVendorId(), 
            device.getProductId(),
            getDeviceClassName(device),
            device.getInterfaceCount());
    }
    
    /**
     * 🏷️ Obtener nombre del fabricante por Vendor ID
     */
    private String getVendorName(int vendorId) {
        switch (vendorId) {
            case 0x04b8: return "Epson";
            case 0x04e8: return "Samsung";
            case 0x0419: return "Samsung";
            case 0x0483: return "STMicroelectronics";
            case 0x1a86: return "QinHeng Electronics";
            case 0x067b: return "Prolific";
            case 0x1fc9: return "NXP";
            case 0x0fe6: return "ICS Advent";
            case 0x0416: return "Winbond";
            case 0x1659: return "Prolific";
            case 0x10c4: return "Silicon Labs";
            case 0x0403: return "FTDI";
            case 0x2341: return "Arduino";
            case 0x1a40: return "Terminus";
            default: return null;
        }
    }
    
    /**
     * 📁 Obtener nombre de la clase del dispositivo
     */
    private String getDeviceClassName(UsbDevice device) {
        int deviceClass = device.getDeviceClass();
        switch (deviceClass) {
            case 7: return "Printer";
            case 9: return "Hub";
            case 3: return "HID";
            case 8: return "Mass Storage";
            case 2: return "Communications";
            default: return "Class " + deviceClass;
        }
    }
    
    /**
     * 🔑 Solicitar permisos USB para un dispositivo específico
     */
    private void requestPermission(UsbDevice device) {
        try {
            PendingIntent permissionIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                permissionIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    new Intent(ACTION_USB_PERMISSION), 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            } else {
                permissionIntent = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    new Intent(ACTION_USB_PERMISSION), 
                    PendingIntent.FLAG_UPDATE_CURRENT
                );
            }
            
            usbManager.requestPermission(device, permissionIntent);
            Log.d(TAG, "Permisos USB solicitados para: " + device.getDeviceName());
            
        } catch (Exception e) {
            Log.e(TAG, "Error solicitando permisos USB", e);
        }
    }
    
    /**
     * 🖨️ Verificar si un dispositivo USB es una impresora
     */
    private boolean isPrinterDevice(UsbDevice device) {
        try {
            // Método 1: Verificar clase de dispositivo = 7 (Printer)
            if (device.getDeviceClass() == 7) {
                Log.d(TAG, "Dispositivo es impresora por Device Class 7: " + device.getDeviceName());
                return true;
            }
            
            // Método 2: Verificar interfaces con clase 7 (Printer)
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface usbInterface = device.getInterface(i);
                if (usbInterface.getInterfaceClass() == 7) {
                    Log.d(TAG, "Dispositivo es impresora por Interface Class 7: " + device.getDeviceName());
                    return true;
                }
            }
            
            // Método 3: Verificar Vendor IDs conocidos de impresoras térmicas
            int vendorId = device.getVendorId();
            switch (vendorId) {
                case 0x04b8: // Epson
                case 0x04e8: // Samsung  
                case 0x0419: // Samsung
                case 0x0483: // STMicroelectronics (algunos modelos térmicos)
                case 0x1a86: // QinHeng Electronics (CH340 - usado en impresoras térmicas)
                case 0x0fe6: // ICS Advent (impresoras POS)
                    Log.d(TAG, "Dispositivo es impresora por Vendor ID conocido: " + String.format("0x%04X", vendorId));
                    return true;
                default:
                    break;
            }
            
            // Método 4: Verificar por nombre del producto (si está disponible)
            String productName = device.getProductName();
            if (productName != null) {
                String productLower = productName.toLowerCase();
                if (productLower.contains("printer") || 
                    productLower.contains("pos") || 
                    productLower.contains("thermal") ||
                    productLower.contains("receipt")) {
                    Log.d(TAG, "Dispositivo es impresora por nombre: " + productName);
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            Log.w(TAG, "Error verificando si es impresora: " + e.getMessage());
            return false;
        }
    }

    public void cleanup() {
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error al limpiar receiver", e);
        }
        disconnect();
    }

    public void openCashDrawer() {
    }
}
