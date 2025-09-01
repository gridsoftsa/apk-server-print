package com.gridpos.puenteimpresora;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * 🖨️ Adaptador para mostrar lista de dispositivos USB en RecyclerView
 */
public class PrintersAdapter extends RecyclerView.Adapter<PrintersAdapter.PrinterViewHolder> {
    
    private List<UsbDevice> printers;
    private int selectedPosition = -1;
    private OnPrinterSelectedListener listener;

    public interface OnPrinterSelectedListener {
        void onPrinterSelected(UsbDevice printer, int position);
    }

    public PrintersAdapter(List<UsbDevice> printers, OnPrinterSelectedListener listener) {
        this.printers = printers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PrinterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_printer, parent, false);
        return new PrinterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PrinterViewHolder holder, int position) {
        UsbDevice printer = printers.get(position);
        
        holder.deviceName.setText(printer.getDisplayName());
        holder.deviceInfo.setText(printer.getDeviceInfo());
        
        // Mostrar indicador si es impresora
        if (printer.isPrinter()) {
            holder.printerIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.printerIndicator.setVisibility(View.GONE);
        }
        
        // Configurar RadioButton
        holder.radioButton.setChecked(position == selectedPosition);
        
        // Listener para selección
        holder.itemView.setOnClickListener(v -> {
            int oldPosition = selectedPosition;
            selectedPosition = position;
            
            // Actualizar UI
            if (oldPosition != -1) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(selectedPosition);
            
            // Notificar al listener
            if (listener != null) {
                listener.onPrinterSelected(printer, position);
            }
        });
        
        holder.radioButton.setOnClickListener(v -> {
            holder.itemView.performClick();
        });
    }

    @Override
    public int getItemCount() {
        return printers.size();
    }

    public UsbDevice getSelectedPrinter() {
        if (selectedPosition >= 0 && selectedPosition < printers.size()) {
            return printers.get(selectedPosition);
        }
        return null;
    }

    public void updatePrinters(List<UsbDevice> newPrinters) {
        this.printers = newPrinters;
        this.selectedPosition = -1; // Reset selection
        notifyDataSetChanged();
    }

    static class PrinterViewHolder extends RecyclerView.ViewHolder {
        RadioButton radioButton;
        TextView deviceName;
        TextView deviceInfo;
        TextView printerIndicator;

        PrinterViewHolder(@NonNull View itemView) {
            super(itemView);
            radioButton = itemView.findViewById(R.id.radioButton);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceInfo = itemView.findViewById(R.id.deviceInfo);
            printerIndicator = itemView.findViewById(R.id.printerIndicator);
        }
    }
}
