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
        // Buscar impresora USB conectada
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        
        for (UsbDevice device : deviceList.values()) {
            // Buscar dispositivos de clase 7 (impresoras)
            if (device.getDeviceClass() == 7 || 
                (device.getInterfaceCount() > 0 && device.getInterface(0).getInterfaceClass() == 7)) {
                
                printerDevice = device;
                Log.d(TAG, "Impresora encontrada: " + device.getDeviceName());
                
                // Solicitar permisos si no los tenemos
                if (!usbManager.hasPermission(device)) {
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(
                        context, 0, new Intent(ACTION_USB_PERMISSION), 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    usbManager.requestPermission(device, permissionIntent);
                    
                    // Esperar permisos (en una implementación real, esto sería asíncrono)
                    int attempts = 0;
                    while (!hasPermission && attempts < 50) {
                        try {
                            Thread.sleep(100);
                            attempts++;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } else {
                    hasPermission = true;
                }
                
                if (hasPermission) {
                    return establishConnection();
                }
                break;
            }
        }
        
        Log.e(TAG, "No se encontró ninguna impresora USB o no se obtuvieron permisos");
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

    public void cleanup() {
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error al limpiar receiver", e);
        }
        disconnect();
    }
}
