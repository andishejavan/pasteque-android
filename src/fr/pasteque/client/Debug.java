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
package fr.pasteque.client;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Date;

import fr.pasteque.client.data.CashArchive;
import fr.pasteque.client.data.CashData;
import fr.pasteque.client.data.CrashData;
import fr.pasteque.client.data.ReceiptData;
import fr.pasteque.client.data.SessionData;
import fr.pasteque.client.models.Cash;
import fr.pasteque.client.models.Receipt;
import fr.pasteque.client.models.Ticket;

public class Debug extends Activity {

    private static final String LOG_TAG = "Pasteque/Debug";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debug);
        this.refresh();
    }

    public void refresh() {
        TextView archives = (TextView) this.findViewById(R.id.dbg_archives);
        try {
            int count = CashArchive.getArchiveCount(this);
            archives.setText(count + " archives");
        } catch (IOException e) {
            archives.setText("Error: " + e.getMessage());
        }

        TextView cash = (TextView) this.findViewById(R.id.dbg_current_cash);
        if (CashData.currentCash(this) == null) {
            cash.setText("Null");
        } else {
            Cash c = CashData.currentCash(this);
            String strCash = "Id: " + c.getId() + "\n";
            strCash += "CashRegId: " + c.getCashRegisterId() + "\n";
            strCash += "Open date: ";
            if (c.wasOpened()) {
                Date d = new Date(c.getOpenDate() * 1000);
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                strCash += cal.get(Calendar.YEAR) + "-"
                    + (cal.get(Calendar.MONTH) + 1) + "-"
                    + cal.get(Calendar.DAY_OF_MONTH) + " "
                    + cal.get(Calendar.HOUR_OF_DAY) + ":"
                    + cal.get(Calendar.MINUTE) + "\n";
            } else {
                strCash += "not opened\n";
            }
            strCash += "Close date: ";
            if (c.isClosed()) {
                Date d = new Date(c.getCloseDate() * 1000);
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                strCash += cal.get(Calendar.YEAR) + "-"
                    + (cal.get(Calendar.MONTH) + 1) + "-"
                    + cal.get(Calendar.DAY_OF_MONTH) + " "
                    + cal.get(Calendar.HOUR_OF_DAY) + ":"
                    + cal.get(Calendar.MINUTE) + "\n";
            } else {
                strCash += "not closed\n";
            }
            strCash += "Dirty: " + CashData.dirty;
            cash.setText(strCash);
        }

        TextView rcpts = (TextView) this.findViewById(R.id.dbg_receipts);
        String strrcpts = ReceiptData.getReceipts(this).size() + " tickets\n";
        for (Receipt r : ReceiptData.getReceipts(this)) {
            try {
                strrcpts += r.toJSON(this).toString(2) + "\n";
            } catch (Exception e) {
                e.printStackTrace();
                StringWriter w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                strrcpts += w.toString();
            }
        }
        rcpts.setText(strrcpts);

        TextView session = (TextView) this.findViewById(R.id.dbg_current_session);
        String strSession = SessionData.currentSession(this).getTickets().size()
            + " tickets\n";
        for (Ticket t : SessionData.currentSession(this).getTickets()) {
            try {
                strSession += t.toJSON(true).toString(2) + "\n";
            } catch (Exception e) {
                e.printStackTrace();
                StringWriter w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                strrcpts += w.toString();
            }
        }
        session.setText(strSession);

        TextView error = (TextView) this.findViewById(R.id.dbg_last_error);
        try {
        String lastError = CrashData.load(this);
        error.setText(lastError);
        } catch (IOException e) {
            error.setText(e.getMessage());
        }
    }

    public void deleteCash(View v) {
        CashData.clear(this);
        this.refresh();
    }

    public void deleteReceipts(View v) {
        ReceiptData.clear(this);
        this.refresh();
    }

    public void deleteSession(View v) {
        SessionData.clear(this);
        this.refresh();
    }

    public void deleteArchives(View v) {
        try {
            CashArchive.clear(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.refresh();
    }
}
