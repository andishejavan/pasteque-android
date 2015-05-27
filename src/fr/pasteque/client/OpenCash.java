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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import java.io.IOException;

import fr.pasteque.client.data.CashData;
import fr.pasteque.client.data.CatalogData;
import fr.pasteque.client.data.SessionData;
import fr.pasteque.client.models.Cash;
import fr.pasteque.client.models.User;
import fr.pasteque.client.models.Session;
import fr.pasteque.client.utils.TrackedActivity;

public class OpenCash extends TrackedActivity {

    private static final String LOG_TAG = "Pasteque/Cash";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.open_cash);
        User cashier = SessionData.currentSession(this).getUser();
        if (!cashier.hasPermission("button.openmoney")
            || CashData.currentCash(this).isClosed()) {
            this.findViewById(R.id.open_cash_btn).setVisibility(View.GONE);
        }
        if (CashData.currentCash(this).isClosed()) {
            TextView status = (TextView) this.findViewById(R.id.open_cash_status);
            status.setText(R.string.cash_closed);
        }
    }

    public void open(View v) {
        // Open cash
        CashData.currentCash(this).openNow();
        CashData.dirty = true;
        try {
            CashData.save(this);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to save cash", e);
            Error.showError(R.string.err_save_cash, this);
        }
        // Go to ticket screen
        Intent i = new Intent(this, Transaction.class);
        this.setResult(Activity.RESULT_OK);
        // Kill
        this.finish();
    }

}
