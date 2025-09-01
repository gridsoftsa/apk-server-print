package com.gridpos.puenteimpresora;

/**
 * 🖨️ Tipos de impresoras soportadas
 */
public enum PrinterType {
    AUTO("Auto-detectar", "Detecta automáticamente el mejor driver"),
    THREEDNSTAR("3nStar SDK", "Impresoras compatibles con SDK 3nStar (Recomendado)"),
    ESCPOS("ESC/POS Generic", "Impresoras térmicas genéricas con comandos ESC/POS"),
    EPSON("Epson Compatible", "Impresoras Epson y compatibles"),
    CITIZEN("Citizen Compatible", "Impresoras Citizen y compatibles"),
    STAR("Star Micronics", "Impresoras Star TSP y compatibles"),
    CUSTOM("Personalizada", "Configuración manual avanzada");

    private final String displayName;
    private final String description;

    PrinterType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * 🎯 Obtener tipo por nombre
     */
    public static PrinterType fromString(String name) {
        for (PrinterType type : PrinterType.values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return AUTO; // Default
    }
}
