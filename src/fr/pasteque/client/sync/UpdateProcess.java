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

import fr.pasteque.client.R;
import fr.pasteque.client.Configure;
import fr.pasteque.client.Error;
import fr.pasteque.client.data.CashData;
import fr.pasteque.client.data.CashRegisterData;
import fr.pasteque.client.data.CatalogData;
import fr.pasteque.client.data.CompositionData;
import fr.pasteque.client.data.CustomerData;
import fr.pasteque.client.data.PlaceData;
import fr.pasteque.client.data.StockData;
import fr.pasteque.client.data.TariffAreaData;
import fr.pasteque.client.data.UserData;
import fr.pasteque.client.models.Cash;
import fr.pasteque.client.models.CashRegister;
import fr.pasteque.client.models.Catalog;
import fr.pasteque.client.models.Composition;
import fr.pasteque.client.models.Customer;
import fr.pasteque.client.models.Floor;
import fr.pasteque.client.models.Stock;
import fr.pasteque.client.models.TariffArea;
import fr.pasteque.client.models.User;
import fr.pasteque.client.utils.TrackedActivity;
import fr.pasteque.client.widgets.ProgressPopup;

import android.content.Context;
import android.os.Message;
import android.os.Handler;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Manager for update processus and UI feedback */
public class UpdateProcess implements Handler.Callback {

    private static final String LOG_TAG = "Pasteque/UpdateProcess";

    private static UpdateProcess instance;

    private Context ctx;
    private boolean errorOccured;
    private ProgressPopup feedback;
    private TrackedActivity caller;
    private Handler listener;
    private int progress;

    private UpdateProcess(Context ctx) {
        this.ctx = ctx;
    }

    /** Start update process with the given context (should be application
     * context). If already started nothing happens.
     * @return True if started, false if already started.
     */
    public static boolean start(Context ctx) {
        if (instance == null) {
            // Create new process and run
            instance = new UpdateProcess(ctx);
            instance.errorOccured = false;
            SyncUpdate syncUpdate = new SyncUpdate(instance.ctx,
                    new Handler(instance));
            syncUpdate.startSyncUpdate();
            return true;
        } else {
            // Already started
            return false;
        }
    }
    public static boolean isStarted() {
        return instance != null;
    }
    private void finish() {
        unbind();
        instance = null;
        Log.i(LOG_TAG, "Update sync finished.");
        SyncUtils.notifyListener(this.listener, SyncUpdate.SYNC_DONE);
    }
    /** Bind a feedback popup to the process. Must be started before binding
     * otherwise nothing happens.
     * This will show the popup with the current state.
     */
    public static boolean bind(ProgressPopup feedback, TrackedActivity caller,
            Handler listener) {
        if (instance == null) {
            return false;
        }
        instance.caller = caller;
        instance.feedback = feedback;
        instance.listener = listener;
        // Update from current state
        feedback.setMax(SyncUpdate.STEPS);
        feedback.setTitle(instance.ctx.getString(R.string.sync_title));
        feedback.setMessage(instance.ctx.getString(R.string.sync_message));
        feedback.setProgress(instance.progress);
        feedback.show();
        return true;
    }
    /** Unbind feedback for when the popup is destroyed during the process. */
    public static void unbind() {
        if (instance == null) {
            return;
        }
        instance.feedback.dismiss();
        instance.feedback = null;
        instance.listener = null;
        instance.caller = null;
    }

    /** Increment progress by steps and update feedback. */
    private void progress(int steps) {
        this.progress += steps;
        if (this.feedback != null) {
            for (int i = 0; i < steps; i++) {
                this.feedback.increment();
            }
        }
    }
    /** Increment progress by 1 and update feedback. */
    private void progress() {
        this.progress(1);
    }

    public boolean handleMessage(Message m) {
        switch (m.what) {
        case SyncUpdate.SYNC_ERROR:
            if (m.obj instanceof Exception) {
                // Response error (unexpected content)
                Log.i(LOG_TAG, "Server error " + m.obj);
                Error.showError(R.string.err_server_error, this.caller);
            } else {
                // String user error
                String error = (String) m.obj;
                if ("Not logged".equals(error)) {
                    Log.i(LOG_TAG, "Not logged");
                    Error.showError(R.string.err_not_logged, this.caller);
                } else {
                    Log.e(LOG_TAG, "Unknown server errror: " + error);
                    Error.showError(R.string.err_server_error, this.caller);
                }
            }
            this.finish();
            break;
        case SyncUpdate.CONNECTION_FAILED:
            if (m.obj instanceof Exception) {
                Log.i(LOG_TAG, "Connection error", ((Exception)m.obj));
                Error.showError(R.string.err_connection_error, this.caller);
            } else {
                Log.i(LOG_TAG, "Server error " + m.obj);
                Error.showError(R.string.err_server_error, this.caller);
            }
            this.finish();
            break;

        case SyncUpdate.INCOMPATIBLE_VERSION:
            Error.showError(R.string.err_version_error, instance.caller);
            this.finish();
            break;
        case SyncUpdate.VERSION_DONE:
            this.progress();
            break;

        case SyncUpdate.CASHREG_SYNC_DONE:
            this.progress();
            // Get received cash register
            CashRegister cashReg = (CashRegister) m.obj;
            CashRegisterData.set(cashReg);
            try {
                CashRegisterData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save cash register", e);
                Error.showError(R.string.err_save_cash_register,
                        this.caller);
            }
            break;
        case SyncUpdate.CASH_SYNC_DONE:
            this.progress();
            // Get received cash
            Cash cash = (Cash) m.obj;
            boolean save = false;
            if (CashData.currentCash(this.ctx) == null) {
                // No current cash, set it
                CashData.setCash(cash);
                save = true;
            } else if (CashData.mergeCurrent(cash)) {
                save = true;
            } else {
                // TODO: Cash conflict!
            }
            if (save) {
                try {
                    CashData.save(this.ctx);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Unable to save cash", e);
                    Error.showError(R.string.err_save_cash, this.caller);
                }
            }
            break;

        case SyncUpdate.TAXES_SYNC_DONE:
        case SyncUpdate.CATEGORIES_SYNC_DONE:
            this.progress();
            break;
        case SyncUpdate.CATALOG_SYNC_DONE:
            this.progress();
            Catalog catalog = (Catalog) m.obj;
            CatalogData.setCatalog(catalog);
            try {
                CatalogData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save catalog", e);
                Error.showError(R.string.err_save_catalog, this.caller);
            }
            break;
        case SyncUpdate.COMPOSITIONS_SYNC_DONE:
            this.progress();
            Map<String, Composition> compos = (Map<String, Composition>) m.obj;
            CompositionData.compositions = compos;
            try {
                CompositionData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save compositions", e);
                Error.showError(R.string.err_save_compositions, this.caller);
            }
            break;

        case SyncUpdate.ROLES_SYNC_DONE:
            this.progress();
            break;
        case SyncUpdate.USERS_SYNC_DONE:
            this.progress();
            List<User> users = (List) m.obj;
            UserData.setUsers(users);
            try {
                UserData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save users", e);
                Error.showError(R.string.err_save_users, this.caller);
            }
            break;

        case SyncUpdate.CUSTOMERS_SYNC_DONE:
            this.progress();
            List<Customer> customers = (List) m.obj;
            CustomerData.customers = customers;
            try {
                CustomerData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save customers", e);
                Error.showError(R.string.err_save_customers, this.caller);
            }
            break;

        case SyncUpdate.TARIFF_AREAS_SYNC_DONE:
            this.progress();
            List<TariffArea> areas = (List<TariffArea>) m.obj;
            TariffAreaData.areas = areas;
            try {
                TariffAreaData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save tariff areas", e);
                Error.showError(R.string.err_save_tariff_areas, this.caller);
            }
            break;

        case SyncUpdate.PLACES_SKIPPED:
            this.progress();
            break;
        case SyncUpdate.PLACES_SYNC_DONE:
            this.progress();
            List<Floor> floors = (List<Floor>) m.obj;
            PlaceData.floors = floors;
            try {
                PlaceData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save places", e);
                Error.showError(R.string.err_save_places, this.caller);
            }
            break;

        case SyncUpdate.STOCKS_SKIPPED:
            this.progress(2);
            break;
        case SyncUpdate.LOCATIONS_SYNC_ERROR:
            if (m.obj instanceof Exception) {
                Log.e(LOG_TAG, "Location sync error", (Exception) m.obj);
                Error.showError(((Exception)m.obj).getMessage(), this.caller);
            } else {
                Log.w(LOG_TAG, "Location sync error: unknown location "
                        + Configure.getStockLocation(this.ctx));
                Error.showError(R.string.err_unknown_location, this.caller);
            }
            break;
        case SyncUpdate.LOCATIONS_SYNC_DONE:
            this.progress();
            break;
        case SyncUpdate.STOCK_SYNC_DONE:
            this.progress();
            Map<String, Stock> stocks = (Map<String, Stock>) m.obj;
            StockData.stocks = stocks;
            try {
                StockData.save(this.ctx);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save stocks", e);
                Error.showError(R.string.err_save_stocks, this.caller);
            }
            break;
        case SyncUpdate.STOCK_SYNC_ERROR:
            Log.e(LOG_TAG, "Stock sync error", (Exception) m.obj);
            Error.showError(((Exception)m.obj).getMessage(), this.caller);
            break;
        case SyncUpdate.CASHREG_SYNC_NOTFOUND:
            Error.showError(R.string.err_cashreg_not_found, this.caller);
            this.finish();
            break;

        case SyncUpdate.CATEGORIES_SYNC_ERROR:
        case SyncUpdate.TAXES_SYNC_ERROR:
        case SyncUpdate.CATALOG_SYNC_ERROR:
        case SyncUpdate.USERS_SYNC_ERROR:
        case SyncUpdate.CUSTOMERS_SYNC_ERROR:
        case SyncUpdate.CASH_SYNC_ERROR:
        case SyncUpdate.CASHREG_SYNC_ERROR:
        case SyncUpdate.PLACES_SYNC_ERROR:
        case SyncUpdate.COMPOSITIONS_SYNC_ERROR:
        case SyncUpdate.TARIFF_AREA_SYNC_ERROR:
            Error.showError(((Exception)m.obj).getMessage(), this.caller);
            break;

        case SyncUpdate.SYNC_DONE:
            this.finish();
            break;
        }
        return true;
    }
}