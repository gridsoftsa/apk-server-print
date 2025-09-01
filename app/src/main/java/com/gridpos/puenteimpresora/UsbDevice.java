package com.gridpos.puenteimpresora;

/**
 * 🖨️ Modelo para representar un dispositivo USB conectado
 */
public class UsbDevice {
    private android.hardware.usb.UsbDevice device;
    private String displayName;
    private String deviceInfo;
    private boolean isPrinter;
    private boolean isSelected;

    public UsbDevice(android.hardware.usb.UsbDevice device, String displayName, String deviceInfo, boolean isPrinter) {
        this.device = device;
        this.displayName = displayName;
        this.deviceInfo = deviceInfo;
        this.isPrinter = isPrinter;
        this.isSelected = false;
    }

    public android.hardware.usb.UsbDevice getDevice() {
        return device;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public boolean isPrinter() {
        return isPrinter;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    @Override
    public String toString() {
        return displayName + " - " + deviceInfo;
    }
}
