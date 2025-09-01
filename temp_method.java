    private boolean printInvoiceWithSDK(String jsonData, int paperWidth, boolean openCash) {
        try {
            if (posPrinter == null || !isConnected) {
                Log.w(TAG, "⚠️ SDK no conectado, usando fallback");
                return printInvoiceWithFallback(jsonData, paperWidth, openCash);
            }
            
            Log.d(TAG, "🎯 Procesando factura completa con SDK 3nStar...");
            
            // Inicializar impresora
            posPrinter.initializePrinter();
            
            // Procesar datos JSON
            org.json.JSONObject invoice = new org.json.JSONObject(jsonData);
            
            // 🏢 ENCABEZADO DE EMPRESA
            if (invoice.has("company") || invoice.has("company_info")) {
                org.json.JSONObject company = invoice.has("company") ? 
                    invoice.getJSONObject("company") : 
                    invoice.getJSONObject("company_info");
                
                // Nombre de la empresa
                String companyName = company.optString("name", "");
                if (!companyName.isEmpty()) {
                    posPrinter.printText(companyName + "\n", 
                        POSConst.ALIGNMENT_CENTER, 
                        POSConst.FNT_BOLD, 
                        POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
                
                // Dirección
                String address = company.optString("address", "");
                if (!address.isEmpty()) {
                    posPrinter.printText("DIRECCIÓN: " + address + "\n", 
                        POSConst.ALIGNMENT_CENTER, 
                        POSConst.FNT_DEFAULT, 
                        POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
                
                // Teléfono
                String phone = company.optString("phone", "");
                if (!phone.isEmpty()) {
                    posPrinter.printText("CELULAR: " + phone + "\n", 
                        POSConst.ALIGNMENT_CENTER, 
                        POSConst.FNT_DEFAULT, 
                        POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
                
                // NIT
                String nit = company.optString("nit", "");
                if (!nit.isEmpty()) {
                    posPrinter.printText("NIT: " + nit + "\n", 
                        POSConst.ALIGNMENT_CENTER, 
                        POSConst.FNT_DEFAULT, 
                        POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
            }
            
            // Línea separadora
            posPrinter.feedLine(1);
            posPrinter.printText("================================\n", 
                POSConst.ALIGNMENT_CENTER, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            // 📄 DATOS DE LA VENTA
            String saleNumber = invoice.optString("sale_number", "");
            if (!saleNumber.isEmpty()) {
                posPrinter.printText("VENTA: " + saleNumber + "\n", 
                    POSConst.ALIGNMENT_LEFT, 
                    POSConst.FNT_BOLD, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            }
            
            String customerName = invoice.optString("customer_name", "Consumidor Final");
            posPrinter.printText("CLIENTE: " + customerName + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            String customerDoc = invoice.optString("customer_document", "222222222222");
            posPrinter.printText("DOCUMENTO: " + customerDoc + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            // 🛒 PRODUCTOS
            posPrinter.feedLine(1);
            posPrinter.printText("ITEM CANT VALOR\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_BOLD, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            if (invoice.has("items") && invoice.getJSONArray("items").length() > 0) {
                org.json.JSONArray items = invoice.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject item = items.getJSONObject(i);
                    String name = item.optString("name", "");
                    int quantity = item.optInt("quantity", 1);
                    double price = item.optDouble("price", 0.0);
                    
                    String itemLine = name + " " + quantity + " $ " + String.format("%.3f", price) + "\n";
                    posPrinter.printText(itemLine, 
                        POSConst.ALIGNMENT_LEFT, 
                        POSConst.FNT_DEFAULT, 
                        POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                }
            }
            
            // 💰 TOTAL
            double total = invoice.optDouble("total", 0.0);
            posPrinter.printText("TOTAL $ " + String.format("%.3f", total) + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_BOLD, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            // 💳 FORMA DE PAGO
            String paymentMethod = invoice.optString("payment_method", "Efectivo");
            posPrinter.printText("Forma de pago: " + paymentMethod + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            // 👤 ATENDIDO POR
            String attendedBy = invoice.optString("attended_by", "admin");
            posPrinter.printText("Atendido por: " + attendedBy + "\n", 
                POSConst.ALIGNMENT_LEFT, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            // 📅 FECHA
            String date = invoice.optString("date", "");
            if (!date.isEmpty()) {
                posPrinter.printText("Generación: " + date + "\n", 
                    POSConst.ALIGNMENT_LEFT, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            }
            
            // 📱 QR CUFE (si existe)
            String cufe = invoice.optString("cufe", "");
            if (!cufe.isEmpty() && !cufe.equals("null")) {
                posPrinter.feedLine(1);
                posPrinter.printText("CUFE:\n", 
                    POSConst.ALIGNMENT_CENTER, 
                    POSConst.FNT_DEFAULT, 
                    POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
                posPrinter.printQRCode(cufe);
                posPrinter.feedLine(1);
            }
            
            // 🙏 PIE DE PÁGINA
            posPrinter.printText("¡Gracias por tu compra!\n", 
                POSConst.ALIGNMENT_CENTER, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            posPrinter.printText("GridPOS 2025 © GridSoft S.A.S\n", 
                POSConst.ALIGNMENT_CENTER, 
                POSConst.FNT_DEFAULT, 
                POSConst.TXT_1WIDTH | POSConst.TXT_1HEIGHT);
            
            // Cortar papel
            posPrinter.feedLine(2);
            posPrinter.cutHalfAndFeed(1);
            
            // 💰 Abrir caja si se solicita
            if (openCash) {
                posPrinter.openCashBox(POSConst.PIN_TWO);
                Log.d(TAG, "💰 Caja registradora abierta");
            }
            
            Log.d(TAG, "✅ Factura impresa exitosamente con SDK 3nStar");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error imprimiendo factura con SDK: " + e.getMessage());
            return printInvoiceWithFallback(jsonData, paperWidth, openCash);
        }
    }
