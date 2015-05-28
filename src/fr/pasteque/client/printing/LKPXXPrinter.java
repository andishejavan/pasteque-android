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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import com.sewoo.jpos.command.ESCPOS;
import com.sewoo.jpos.printer.ESCPOSPrinter;
import com.sewoo.port.android.BluetoothPort;
import com.sewoo.request.android.RequestHandler;
import java.io.IOException;

public class LKPXXPrinter extends PrinterHelper {

    private static final char ESC = ESCPOS.ESC;
    private static final char LF = ESCPOS.LF;
    private static final String CUT = ESC + "|#fP";

    private ESCPOSPrinter printer;
    private BluetoothSocket sock;
    private BluetoothPort port;
    private Thread hThread;

    public LKPXXPrinter(Context ctx, String address, Handler callback) {
        super(ctx, address, callback);
        this.port = BluetoothPort.getInstance();
        this.printer = new ESCPOSPrinter();
    }

    @Override
	public void connect() throws IOException {
        BluetoothAdapter btadapt = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice dev = btadapt.getRemoteDevice(this.address);
        new ConnTask().execute(dev);
    }

    @Override
	public void disconnect() throws IOException {
        try {
            port.disconnect();
            if ((hThread != null) && (hThread.isAlive())) {
                hThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
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
        try {
            this.printer.printNormal(ascii + LF);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
	protected void printLine() {
        this.printer.lineFeed(1);
    }

    @Override
	protected void cut() {
    }

	// Bluetooth Connection Task.
	class ConnTask extends AsyncTask<BluetoothDevice, Void, Integer> {
		
		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
		}
		
		@Override
		protected Integer doInBackground(BluetoothDevice... params)
		{
			Integer retVal = null;
			try
			{
				port.connect(params[0]);
				retVal = new Integer(0);
			}
			catch (IOException e) {
			e.printStackTrace();
				retVal = new Integer(-1);
			}
			return retVal;
		}
		
		@Override
		protected void onPostExecute(Integer result)
		{
			if(result.intValue() == 0)	// Connection success.
			{
				RequestHandler rh = new RequestHandler();				
				hThread = new Thread(rh);
				hThread.start();
				connected = true;
				if (queued != null) {
					printReceipt(queued);
				}
                if (zQueued != null) {
                    printZTicket(zQueued, crQueued);
                }
			}
			else	// Connection failed.
			{
				if (callback != null) {
					Message m = callback.obtainMessage();
					m.what = PRINT_CTX_ERROR;
					m.sendToTarget();
				}
			}
			super.onPostExecute(result);
		}
	}
}
