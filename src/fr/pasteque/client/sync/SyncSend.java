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
package fr.pasteque.client.sync;

import android.content.Context;
import android.os.Message;
import android.os.Handler;
import android.util.Log;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fr.pasteque.client.models.Cash;
import fr.pasteque.client.models.Receipt;
import fr.pasteque.client.utils.URLTextGetter;

public class SyncSend {

    private static final String LOG_TAG = "Pasteque/SyncSend";

    public static final int TICKETS_BUFFER = 10;
    // Note: SyncUpdate uses positive values, SyncSend negative ones
    public static final int SYNC_DONE = -1;
    public static final int CONNECTION_FAILED = -2;
    public static final int RECEIPTS_SYNC_DONE = -3;
    public static final int RECEIPTS_SYNC_FAILED = -4;
    public static final int CASH_SYNC_DONE = -5;
    public static final int CASH_SYNC_FAILED = -6;
    public static final int EPIC_FAIL = -7;
    public static final int SYNC_ERROR = -8;
    public static final int RECEIPTS_SYNC_PROGRESSED = -9;
    public static final int CLOSE_INV_SYNC_DONE = -10;
    public static final int CLOSE_INV_SYNC_FAILED = -11;

    private Context ctx;
    private Handler listener;

    /** The tickets to send */
    private List<Receipt> receipts;
    /** Index of first ticket to send in a call */
    private int ticketOffset;
    private int currentChunkSize;
    private Cash cash;
    private boolean receiptsDone;
    private boolean cashDone;
    private boolean closeInvDone;
    private boolean killed;

    public SyncSend(Context ctx, Handler listener,
                    List<Receipt> receipts, Cash cash) {
        this.listener = listener;
        this.ctx = ctx;
        this.receipts = receipts;
        this.cash = cash;
        if (this.cash.getCloseInventory() == null) {
            this.closeInvDone = true;
        }
    }

    public void synchronize() {
        runCashSync();
     }

    private void fail(Exception e) {
        SyncUtils.notifyListener(this.listener, CASH_SYNC_FAILED, e);
    }

    private void runReceiptsSync() {
        if (this.receipts.size() == 0) {
            // No receipts, skip and notify
            this.receiptsDone = true;
            SyncUtils.notifyListener(this.listener, RECEIPTS_SYNC_DONE, true);
            this.checkFinished();
            return;
        } else {
            if (!this.nextTicketRush()) {
                this.finish();
            }
        }
    }

    private void runCashSync() {
        Map<String, String> postBody = SyncUtils.initParams(this.ctx,
                "CashesAPI", "update");
        try {
            postBody.put("cash", this.cash.toJSON().toString());
        } catch (JSONException e) {
            Log.e(LOG_TAG, this.cash.toString(), e);
            this.fail(e);
            return;
        }
        URLTextGetter.getText(SyncUtils.apiUrl(this.ctx), null, postBody,
                new DataHandler(DataHandler.TYPE_CASH));
    }

    private void runCloseInventorySync() {
        Map<String, String> postBody = SyncUtils.initParams(this.ctx,
                "InventoriesAPI", "save");
        try {
            postBody.put("inventory",
                    this.cash.getCloseInventory().toJSON().toString());
        } catch (JSONException e) {
            Log.e(LOG_TAG, this.cash.toString(), e);
            this.fail(e);
            return;
        }
        URLTextGetter.getText(SyncUtils.apiUrl(this.ctx), null, postBody,
                new DataHandler(DataHandler.TYPE_CLOSEINVENTORY));
    }

    private boolean nextTicketRush() {
        if (this.ticketOffset >= this.receipts.size()) {
            return false;
        }
        JSONArray rcptsJSON = new JSONArray();
        for (int i = this.ticketOffset; i < this.receipts.size()
                && i < this.ticketOffset + TICKETS_BUFFER; i++) {
            Receipt r = this.receipts.get(i);
            try {
                JSONObject o = r.toJSON(this.ctx);
                rcptsJSON.put(o);
            } catch (JSONException e) {
                Log.e(LOG_TAG, r.toString(), e);
                this.fail(e);
                return false;
            }
        }
        this.currentChunkSize = rcptsJSON.length();
        Map<String, String> postBody = SyncUtils.initParams(this.ctx,
                "TicketsAPI", "save");
        postBody.put("tickets", rcptsJSON.toString());
        postBody.put("cashId", this.cash.getId());
        URLTextGetter.getText(SyncUtils.apiUrl(this.ctx), null,
                postBody, new DataHandler(DataHandler.TYPE_RECEIPTS));
        return true;
    }

    private void parseReceiptsResult(JSONObject resp) {
        try {
            JSONObject o = resp.getJSONObject("content");
            int saved = o.getInt("saved");
            if (saved == this.currentChunkSize) {
                this.ticketOffset += this.currentChunkSize;
                if (!this.nextTicketRush()) {
                    SyncUtils.notifyListener(this.listener, RECEIPTS_SYNC_DONE);
                    if (this.cash.getCloseInventory() != null) {
                        // Continue with close inventory
                        this.runCloseInventorySync();
                    } else {
                        // Done
                        this.finish();
                    }
                } else {
                    SyncUtils.notifyListener(this.listener,
                            RECEIPTS_SYNC_PROGRESSED);
                }
            } else {
                SyncUtils.notifyListener(this.listener,
                        RECEIPTS_SYNC_FAILED,
                        saved + " tickets saved, expecting "
                        + this.currentChunkSize);
                return;
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error while parsing receipts result", e);
            SyncUtils.notifyListener(this.listener, RECEIPTS_SYNC_FAILED, resp);
            return;
        }
    }

    private void parseCashResult(JSONObject resp) {
        try {
            JSONObject o = resp.getJSONObject("content");
            Cash cash = Cash.fromJSON(o);
            // Update cash id for tickets
            cash.setCloseInventory(this.cash.getCloseInventory());
            this.cash = cash;
            SyncUtils.notifyListener(this.listener, CASH_SYNC_DONE, cash);
            // Continue with receipts
            this.runReceiptsSync();
        } catch(JSONException e) {
            Log.e(LOG_TAG, "Error while parsing cash result", e);
            SyncUtils.notifyListener(this.listener, CASH_SYNC_FAILED, resp);
            return;
        }
    }

    private void parseCloseInvResult(JSONObject resp) {
        try {
            int id = resp.getInt("content");
            SyncUtils.notifyListener(this.listener, CLOSE_INV_SYNC_DONE,
                    this.cash);
            // Done
            this.finish();
        } catch(JSONException e) {
            Log.e(LOG_TAG, "Error while parsing close inventory result", e);
            SyncUtils.notifyListener(this.listener, CLOSE_INV_SYNC_FAILED,
                    resp);
            return;
        }
    }

    private void finish() {
        SyncUtils.notifyListener(this.listener, SYNC_DONE);
    }

    private void checkFinished() {
        if (this.receiptsDone && this.cashDone && this.closeInvDone) {
            this.finish();
        }
    }

    
    private class DataHandler extends Handler {
        
        private static final int TYPE_RECEIPTS = 1;
        private static final int TYPE_CASH = 2;
        private static final int TYPE_CLOSEINVENTORY = 3;

        private int type;
        
        public DataHandler(int type) {
            this.type = type;
        }

        private String getError(String response) {
            try {
                JSONObject o = new JSONObject(response);
                if (o.has("error")) {
                    return o.getString("error");
                }
            } catch (JSONException e) {
            }
            return null;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (this.type) {
            case TYPE_RECEIPTS:
                SyncSend.this.receiptsDone = true;
                break;
            case TYPE_CASH:
                SyncSend.this.cashDone = true;
                break;
            case TYPE_CLOSEINVENTORY:
                SyncSend.this.closeInvDone = true;
                break;
            }
            switch (msg.what) {
            case URLTextGetter.SUCCESS:
                // Parse content
                String content = (String) msg.obj;
                try {
                    JSONObject result = new JSONObject(content);
                    String status = result.getString("status");
                    if (!status.equals("ok")) {
                        JSONObject err = result.getJSONObject("content");
                        String error = err.getString("code");
                        SyncUtils.notifyListener(listener, SYNC_ERROR, error);
                        finish();
                    } else {
                        switch (type) {
                        case TYPE_RECEIPTS:
                            parseReceiptsResult(result);
                            break;
                        case TYPE_CASH:
                            parseCashResult(result);
                            break;
                        case TYPE_CLOSEINVENTORY:
                            parseCloseInvResult(result);
                            break;
                        }
                    }
                } catch (JSONException e) {
                    SyncUtils.notifyListener(listener, SYNC_ERROR, content);
                    finish();
                }
                break;
            case URLTextGetter.ERROR:
                Log.e(LOG_TAG, "URLTextGetter error", (Exception)msg.obj);
            case URLTextGetter.STATUS_NOK:
                SyncUtils.notifyListener(listener, CONNECTION_FAILED, msg.obj);
                finish();
                return;
            }
            checkFinished();
        }
    }

}
