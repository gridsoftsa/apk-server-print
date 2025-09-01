package com.gridpos.puenteimpresora;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        
        holder.printerName.setText(printer.getDisplayName());
        holder.printerDetails.setText(printer.getDeviceInfo());
        
        // Mostrar estado de selección y si es impresora
        if (position == selectedPosition) {
            holder.printerStatus.setText("●");
            holder.printerStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        } else if (printer.isPrinter()) {
            holder.printerStatus.setText("✓");
            holder.printerStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_blue_dark));
        } else {
            holder.printerStatus.setText("?");
            holder.printerStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.darker_gray));
        }
        
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
        TextView printerName;
        TextView printerDetails;
        TextView printerStatus;

        PrinterViewHolder(@NonNull View itemView) {
            super(itemView);
            printerName = itemView.findViewById(R.id.printerNameText);
            printerDetails = itemView.findViewById(R.id.printerDetailsText);
            printerStatus = itemView.findViewById(R.id.printerStatusText);
        }
    }
}
