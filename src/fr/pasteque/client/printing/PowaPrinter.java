/*
    Pasteque Android client
    Copyright (C) Pasteque contributors, see the COPYRIGHT file

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package fr.pasteque.client.printing;

import fr.pasteque.client.models.Cash;
import fr.pasteque.client.models.Catalog;
import fr.pasteque.client.models.Customer;
import fr.pasteque.client.models.Payment;
import fr.pasteque.client.models.PaymentMode;
import fr.pasteque.client.models.Product;
import fr.pasteque.client.models.Receipt;
import fr.pasteque.client.models.TicketLine;
import fr.pasteque.client.models.ZTicket;
import fr.pasteque.client.data.CatalogData;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import com.mpowa.android.sdk.powapos.*;
import com.mpowa.android.sdk.powapos.core.*;
import com.mpowa.android.sdk.powapos.core.abstracts.*;
import com.mpowa.android.sdk.powapos.core.callbacks.*;
import com.mpowa.android.sdk.powapos.core.dataobjects.*;
import com.mpowa.android.sdk.powapos.drivers.s10.*;
import com.mpowa.android.sdk.powapos.drivers.tseries.*;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class PowaPrinter extends PrinterHelper {

    private PowaPOS powa;
    private String buffer;
    private PowaCallback powaCallback;

    public PowaPrinter(Context ctx, Handler callback) {
        super(ctx, null, callback);
    }

    public void connect() throws IOException {
        // Start Powa printer
        if(this.powa == null) {
            this.powaCallback = new PowaCallback();
            this.powa = new PowaPOS(this.ctx, this.powaCallback);
            PowaMCU mcu = new PowaTSeries(this.ctx);
            this.powa.addPeripheral(mcu);
        }
    }

    public void disconnect() throws IOException {
    }

    public void printReceipt(Receipt r) {
        super.printReceipt(r);
    }

    protected void printLine(String data) {
        String ascii = data.replace("é", "e");
        ascii = ascii.replace("è", "e");
        ascii = ascii.replace("ê", "e");
        ascii = ascii.replace("ë", "e");
        ascii = ascii.replace("à", "a");
        ascii = ascii.replace("ï", "i");
        ascii = ascii.replace("ô", "o");
        ascii = ascii.replace("ç", "c");
        ascii = ascii.replace("ù", "u");
        ascii = ascii.replace("É", "E");
        ascii = ascii.replace("È", "E");
        ascii = ascii.replace("Ê", "E");
        ascii = ascii.replace("Ë", "E");
        ascii = ascii.replace("À", "A");
        ascii = ascii.replace("Ï", "I");
        ascii = ascii.replace("Ô", "O");
        ascii = ascii.replace("Ç", "c");
        ascii = ascii.replace("Ù", "u");
        ascii = ascii.replace("€", "E");
        while (ascii.length() > 32) {
            String sub = ascii.substring(0, 32);
            this.powa.printText("        " + sub + "        \n");
            ascii = ascii.substring(32);
        }
        this.powa.printText("        " + ascii + "        \n");
    }

    protected void printLine() {
        this.powa.printText("\n");
    }

    protected void cut() {
    }

    private class PowaCallback extends PowaPOSCallback {
        public void onCashDrawerStatus(PowaPOSEnums.CashDrawerStatus status) {}
        public void onScannerInitialized(final PowaPOSEnums.InitializedResult result) {}
        public void onScannerRead(final String data) {}
        public void onUSBDeviceAttached(final PowaPOSEnums.PowaUSBCOMPort port) {}
        public void onUSBDeviceDetached(final PowaPOSEnums.PowaUSBCOMPort port) {}
        public void onUSBReceivedData(PowaPOSEnums.PowaUSBCOMPort port,
                final byte[] data) {}
        public void onPrinterOutOfPaper() {}
        public void onPrintJobResult(PowaPOSEnums.PrintJobResult result) {}
        public void onPrintJobCompleted(PowaPOSEnums.PrintJobResult result) { 
            PowaPrinter.this.powa.openCashDrawer();
            if (PowaPrinter.this.callback != null) {
                Message m = new Message();
                m.what = PRINT_DONE;
                PowaPrinter.this.callback.sendMessageDelayed(m, 3000);
            }
        }
        @Override
        public void onRotationSensorStatus(PowaPOSEnums.RotationSensorStatus status) {}
        public void onMCUConnectionStateChanged(PowaPOSEnums.ConnectionState newState) {}
        public void onMCUSystemConfiguration(Map<String, String> config) {}
        @Override
        public void onMCUBootloaderUpdateFailed(final PowaPOSEnums.BootloaderUpdateError error) {}
        @Override
        public void onMCUBootloaderUpdateStarted() {}
        @Override
        public void onMCUBootloaderUpdateProgress(final int progress) {}
        @Override
        public void onMCUBootloaderUpdateFinished() {}
        @Override
        public void onMCUInitialized(final PowaPOSEnums.InitializedResult result) {
            PowaPrinter.this.connected = true;
            if (queued != null) {
                printReceipt(queued);
            }
            if (zQueued != null) {
                printZTicket(zQueued, crQueued);
            }
        }
        @Override
        public void onMCUFirmwareUpdateStarted() {}
        @Override
        public void onMCUFirmwareUpdateProgress(final int progress) {}
        @Override
        public void onMCUFirmwareUpdateFinished() {}
    }

}
