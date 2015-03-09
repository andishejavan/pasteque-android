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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.widget.ListPopupWindow;
import android.util.Log;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SlidingDrawer;
import android.widget.SlidingDrawer.OnDrawerCloseListener;
import android.widget.SlidingDrawer.OnDrawerOpenListener;
import android.widget.TextView;
import android.widget.Toast;
import com.payleven.payment.api.OpenTransactionDetailsCompletedStatus;
import com.payleven.payment.api.PaylevenApi;
import com.payleven.payment.api.PaylevenResponseListener;
import com.payleven.payment.api.PaymentCompletedStatus;
import com.payleven.payment.api.TransactionRequest;
import com.payleven.payment.api.TransactionRequestBuilder;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import net.atos.sdk.tpe.PaymentAsyncTaskActions;
import net.atos.sdk.tpe.PaymentManager;
import net.atos.sdk.tpe.datas.PaymentResponse;
import net.atos.sdk.tpe.enums.AuthorizationCall;
import net.atos.sdk.tpe.enums.Delay;
import net.atos.sdk.tpe.enums.PaymentMethod;
import net.atos.sdk.tpe.enums.PaymentResponseCode;
import net.atos.sdk.tpe.enums.ResponseIndicatorField;
import net.atos.sdk.tpe.enums.TransactionStatus;
import net.atos.sdk.tpe.enums.TransactionType;
import net.atos.sdk.tpe.exceptions.AmountMaxLengthException;
import net.atos.sdk.tpe.exceptions.IncompatibleTerminalMethodException;
import net.atos.sdk.tpe.terminalmethods.XengoTerminalMethod;
import net.atos.sdk.tpe.terminalmethods.YomaniNetworkTerminalMethod;

import fr.pasteque.client.TicketInput;
import fr.pasteque.client.data.CashData;
import fr.pasteque.client.data.CatalogData;
import fr.pasteque.client.data.CustomerData;
import fr.pasteque.client.data.ReceiptData;
import fr.pasteque.client.data.SessionData;
import fr.pasteque.client.printing.PrinterConnection;
import fr.pasteque.client.models.Catalog;
import fr.pasteque.client.models.Category;
import fr.pasteque.client.models.Customer;
import fr.pasteque.client.models.Ticket;
import fr.pasteque.client.models.TicketLine;
import fr.pasteque.client.models.Payment;
import fr.pasteque.client.models.PaymentMode;
import fr.pasteque.client.models.Product;
import fr.pasteque.client.models.Receipt;
import fr.pasteque.client.models.Session;
import fr.pasteque.client.models.User;
import fr.pasteque.client.sync.TicketUpdater;
import fr.pasteque.client.utils.ScreenUtils;
import fr.pasteque.client.utils.TrackedActivity;
import fr.pasteque.client.widgets.CustomersAdapter;
import fr.pasteque.client.widgets.NumKeyboard;
import fr.pasteque.client.widgets.PaymentsAdapter;
import fr.pasteque.client.widgets.PaymentModesAdapter;
import fr.pasteque.client.widgets.SessionTicketsAdapter;
import fr.pasteque.client.widgets.TicketLinesAdapter;
import fr.pasteque.client.widgets.TicketLineItem;

public class ProceedPayment extends TrackedActivity
    implements Handler.Callback, AdapterView.OnItemSelectedListener,
    TicketLineEditListener,
    PaymentEditListener, GestureDetector.OnGestureListener {
    
    private static final String LOG_TAG = "Pasteque/ProceedPayment";
    private static final String PAYLEVEN_API_KEY = "edaffb929bd34aa78122b2d15a36a5c7";
    private static final int SCROLL_WHAT = 90; // Be sure not to conflict with keyboard whats
    
    private static Ticket ticketInit;
    public static void setup(Ticket ticket) {
        ticketInit = ticket;
    }

    private Ticket ticket;
    private List<Payment> payments;
    private PaymentMode currentMode;
    private boolean paymentClosed;
    private PrinterConnection printer;
    private GestureDetector gestureDetector;
    private static Ticket ticketSwitch;
    private static Catalog catalogInit;
    private TextView tariffArea;


    private NumKeyboard keyboard;
    private EditText input;
    private Gallery paymentModes;
    private TextView ticketTotal;
    private TextView ticketRemaining;
    private TextView giveBack;
    private TextView ticketArticles;
    private ListView paymentsList;
    private SlidingDrawer slidingDrawer;
    private ImageView slidingHandle;
    private Button backAccess;
    private Button detailsAccess;    
    private ScrollView scroll;
    private Handler scrollHandler;
    private boolean printEnabled;
    private ListView ticketContent;
    private TextView ticketLabel;
    private TextView ticketCustomer;
    private ProgressDialog paymentDialog;
    private TextView mountMax;
    private TextView currentDebt;
    private View customersList;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean open = false;
        if (state != null) {
            this.ticket = (Ticket) state.getSerializable("ticket");
            this.payments = new ArrayList<Payment>();
            int count = state.getInt("payCount");
            for (int i = 0; i < count; i++) {
                Payment p = (Payment) state.getSerializable("payment" + i);
                this.payments.add(p);
            }
            open = state.getBoolean("drawerOpen");
            this.printEnabled = state.getBoolean("printEnabled");
            if (state.getBoolean("paymentDialog")) {
                this.paymentDialog = new ProgressDialog(ProceedPayment.this);
                this.paymentDialog.setMessage("Transaction via TPE en cours");
                this.paymentDialog.show();
            }
        } else {
            this.ticket = ticketInit;
            ticketInit = null;
            this.payments = new ArrayList<Payment>();
            this.printEnabled = true;
        }
        setContentView(R.layout.payments);
        this.gestureDetector = new GestureDetector(this, this);
        View.OnTouchListener touchListener = new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) {
                    return ProceedPayment.this.gestureDetector.onTouchEvent(e);
                }
            };
        this.scroll = (ScrollView) this.findViewById(R.id.scroll);
        this.scrollHandler = new Handler(this);
        this.keyboard = (NumKeyboard) this.findViewById(R.id.numkeyboard);
        this.keyboard.setOnTouchListener(touchListener);
        keyboard.setKeyHandler(new Handler(this));
        this.input = (EditText) this.findViewById(R.id.input);
        this.giveBack = (TextView) this.findViewById(R.id.give_back);
        this.ticketTotal = (TextView) this.findViewById(R.id.ticket_total);
        this.ticketRemaining = (TextView) this.findViewById(R.id.ticket_remaining);
        this.paymentModes = (Gallery) this.findViewById(R.id.payment_modes);
        PaymentModesAdapter adapt = new PaymentModesAdapter(PaymentMode.defaultModes(this));
        this.paymentModes.setAdapter(adapt);
        this.paymentModes.setOnItemSelectedListener(this);
        this.paymentModes.setSelection(0, false);
        this.currentMode = PaymentMode.defaultModes(this).get(0);
        String total = this.getString(R.string.ticket_total,
                                      this.ticket.getTotalPrice());

        this.slidingHandle = (ImageView) this.findViewById(R.id.handle);
        this.slidingDrawer = (SlidingDrawer) this.findViewById(R.id.drawer);
        this.detailsAccess = (Button) this.findViewById(R.id.payment_access);
        this.detailsAccess.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
	            ProceedPayment.this.slidingHandle.performClick();
			}
		});
        
        this.backAccess = (Button) this.findViewById(R.id.payment_back);
        this.backAccess.setOnClickListener(new OnClickListener() {	
			@Override
			public void onClick(View v) {
	            ProceedPayment.this.slidingHandle.performClick();
			}
		});

        if (open) {
            this.slidingDrawer.open();
        }

        this.paymentsList = (ListView) this.findViewById(R.id.payments_list);
        PaymentsAdapter padapt = new PaymentsAdapter(this.payments, this);
        this.paymentsList.setAdapter(padapt);
        this.customersList = this.findViewById(R.id.customers_list);

        this.ticketLabel = (TextView) this.findViewById(R.id.ticket_label);
        this.ticketContent = (ListView) this.findViewById(R.id.ticket_content);
        if (this.ticketContent != null) {
            this.ticketContent.setAdapter(new TicketLinesAdapter(this.ticket,
                            this, false));
            this.ticketContent.setOnTouchListener(touchListener);
        }

        if (this.ticketLabel != null) {
            String label = this.getString(R.string.ticket_label,
                    this.ticket.getLabel());
            this.ticketLabel.setText(label);
        }
        this.mountMax = (TextView) this.findViewById(R.id.mountMax);
        this.currentDebt = (TextView) this.findViewById(R.id.currentDebt);
        this.ticketCustomer = (TextView) this.findViewById(R.id.ticket_customer);
        if (this.ticketCustomer != null
                && this.ticket.getCustomer() != null) {
            Customer cust = this.ticket.getCustomer();
            this.mountMax.setText(String.valueOf(cust.getMaxDebt()));
            this.currentDebt.setText(String.valueOf(cust.getCurrDebt()));
            String name = null;
            if (cust.getPrepaid() > 0.005) {
                name = this.getString(R.string.customer_prepaid_label,
                        cust.getName(), cust.getPrepaid());
            } else {
                name = cust.getName();
            }
            this.ticketCustomer.setText(name);
            this.ticketCustomer.setVisibility(View.VISIBLE);
        } else {
            this.ticketCustomer.setVisibility(View.INVISIBLE);
        }

        this.ticketTotal.setText(total);
        this.updateDisplayToMode();
        this.refreshRemaining();
        this.refreshGiveBack();
        this.refreshInput();
        // Init printer connection
        this.printer = new PrinterConnection(new Handler(this));
        try {
            if (!this.printer.connect(this)) {
                this.printer = null;
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "Unable to connect to printer", e);
            Error.showError(R.string.print_no_connexion, this);
            // Set null to cancel printing
            this.printer = null;
        }
        // Init Payleven API
        PaylevenApi.configure("edaffb929bd34aa78122b2d15a36a5c7");
        // Init Wordline TPE
        PaymentManager pm = PaymentManager.getInstance();
        YomaniNetworkTerminalMethod yomani = new YomaniNetworkTerminalMethod(1, "192.168.1.150", 3333, ResponseIndicatorField.NO_FIELD, PaymentMethod.INDIFFERENT, null, Delay.END_OF_TRANSACTION_RESPONSE, AuthorizationCall.TPE_DECISION);
        XengoTerminalMethod xengo = new XengoTerminalMethod(2, this,
                "https://macceptance.sygea.com/tpm/tpm-shop-service/",
                "demo_a554314", "20017884", "motdepasse", "", "", "");
        try {
            pm.addTerminalMethod(yomani);
            pm.addTerminalMethod(xengo);
        } catch (IncompatibleTerminalMethodException e) {
            e.printStackTrace();
        }
        // Update UI based upon settings
        View paylevenBtn = this.findViewById(R.id.btnPayleven);
        if (Configure.getPayleven(this)) {
            paylevenBtn.setVisibility(View.VISIBLE);
        } else {
            paylevenBtn.setVisibility(View.INVISIBLE);
        }
    }

    public void openSwitchCustomer(View v) {
        // Open selector popup
        try {
            final ListPopupWindow popup = new ListPopupWindow(this);
            final List<Customer> data = CustomerData.customers;
            popup.setAdapter(new CustomersAdapter(data, this));
            popup.setAnchorView(this.customersList);
            popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v,
                                        int position, long id) {
                    // TODO: handle connected mode on switch
                    Customer c = data.get(position);
                    ProceedPayment.this.switchCustomer(c);
                    popup.dismiss();
                }
                public void onNothingSelected(AdapterView v) {}
            });
            popup.setWidth(ScreenUtils.inToPx(2, this));
            int customerCount = data.size();
            int height = ScreenUtils.dipToPx(SessionTicketsAdapter.HEIGHT_DIP * Math.min(5, customerCount), this);
            popup.setHeight(height);
            popup.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void switchCustomer(Customer c) {
        this.ticket.setCustomer(c);
        this.updateCustomerView(c);
    }

    private void updateCustomerView(Customer c){
        this.ticketCustomer.setText(c.getName().toString());
        this.ticketCustomer.setVisibility(View.VISIBLE);
        this.mountMax.setText(String.valueOf(c.getMaxDebt()));
        this.currentDebt.setText(String.valueOf(c.getCurrDebt()));
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.printer != null) {
           try {
               this.printer.disconnect();
           } catch (IOException e) {
               e.printStackTrace();
           }
        }
        this.printer = null;
    }

    public void finish() {
        super.finish();
        this.overridePendingTransition(R.transition.fade_in,
                R.transition.slide_out);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("ticket", this.ticket);
        outState.putInt("payCount", this.payments.size());
        for (int i = 0; i < this.payments.size(); i++) {
            outState.putSerializable("payment" + i, this.payments.get(i));
        }
        outState.putBoolean("drawerOpen", this.slidingDrawer.isOpened());
        outState.putBoolean("printEnabled", this.printEnabled);
        outState.putBoolean("paymentDialog", this.paymentDialog != null);
    }

    /** Update display to current payment mode */
    private void updateDisplayToMode() {
        if (this.currentMode.isGiveBack() || this.currentMode.isDebt()
                || this.currentMode.isPrepaid()) {
            this.giveBack.setVisibility(View.VISIBLE);
        } else {
            this.giveBack.setVisibility(View.INVISIBLE);
        }
    }

    private double getRemainingPrepaid() {
        if (this.ticket.getCustomer() != null) {
            double prepaid = this.ticket.getCustomer().getPrepaid();
            // Substract prepaid payments
            for (Payment p : this.payments) {
                if (p.getMode().isPrepaid()) {
                    prepaid -= p.getAmount();
                }
            }
            // Add ordered refills
            for (TicketLine l : this.ticket.getLines()) {
                Product p = l.getProduct();
                Catalog cat = CatalogData.catalog(this);
                Category prepaidCat = cat.getPrepaidCategory();
                if (prepaidCat != null
                        && cat.getProducts(prepaidCat).contains(p)) {
                    prepaid += p.getTaxedPrice() * l.getQuantity();
                }
            }
            return prepaid;
        } else {
            return 0.0;
        }
    }

    private double getRemaining() {
        double paid = 0.0;
        for (Payment p : this.payments) {
            paid += p.getAmount();
        }
        return this.ticket.getTotalPrice() - paid;
    }

    /** Get entered amount. If money is given back, amount is the final sum
     * (not the given one).
     */
    private double getAmount() {
        double remaining = this.getRemaining();
        double amount = remaining;
        if (!this.input.getText().toString().equals("")) {
            amount = Double.parseDouble(this.input.getText().toString());
        }
        // Use remaining when money is given back
        if (this.currentMode.isGiveBack() && amount > remaining) {
            amount = remaining;
        }
        return amount;
    }
    private double getGiven() {
        double remaining = this.getRemaining();
        double given = remaining;
        if (!this.input.getText().toString().equals("")) {
            given = Double.parseDouble(this.input.getText().toString());
        }
        return given;
    }

    private void refreshRemaining() {
        double remaining = this.getRemaining();
        String strRemaining = this.getString(R.string.ticket_remaining,
                                             remaining);
        this.ticketRemaining.setText(strRemaining);
    }

    private void refreshGiveBack() {
        if (this.currentMode.isGiveBack()) {
            double overflow = this.keyboard.getValue() - this.getRemaining();
            if (overflow > 0.0) {
                String back = this.getString(R.string.payment_give_back,
                                             overflow);
                this.giveBack.setText(back);
            } else {
                String back = this.getString(R.string.payment_give_back,
                                             0.0);
                this.giveBack.setText(back);
            }
        }
        if (this.currentMode.isCustAssigned()
                && this.ticket.getCustomer() == null) {
            this.giveBack.setText(R.string.payment_no_customer);
        } else {
            if (this.currentMode.isDebt()
                    && this.ticket.getCustomer() != null) {
                double debt = this.ticket.getCustomer().getCurrDebt();
                for (Payment p : this.payments) {
                    if (p.getMode().isDebt()) {
                        debt += p.getAmount();
                    }
                }
                double maxDebt = this.ticket.getCustomer().getMaxDebt();
                String debtStr = this.getString(R.string.payment_debt,
                        debt, maxDebt);
                this.giveBack.setText(debtStr);
            } else if (this.currentMode.isPrepaid()
                    && this.ticket.getCustomer() != null) {
                double prepaid = this.getRemainingPrepaid();
                String strPrepaid = this.getString(R.string.payment_prepaid,
                        prepaid);
                this.giveBack.setText(strPrepaid);
            }
        }
    }

    private void refreshInput() {
        this.input.setHint(String.format("%.2f", this.getRemaining()));
        this.input.setText(this.keyboard.getRawValue());
    }

    public void resetInput() {
        this.keyboard.clear();
        this.refreshInput();
        this.refreshGiveBack();
    }

    public void back(View v) {
        this.finish();
    }

    public void correct(View v) {
        this.keyboard.correct();
        this.refreshInput();
        this.refreshGiveBack();
        this.input.setSelection(this.input.getText().toString().length());
    }

    public void clear(View v) {
        this.resetInput();
    }

    public void sendToPayleven(View v) {
        int amount = (int) (Math.round(this.getAmount() * 100)); // in cents
        TransactionRequestBuilder builder = new TransactionRequestBuilder(amount, Currency.getInstance("EUR"));
        TransactionRequest request = builder.createTransactionRequest();
        String orderId = "42";
        PaylevenApi.initiatePayment(this, orderId, request);
    }

    private void scrollToKeyboard() {
        if (this.scroll != null) {
            this.scroll.fullScroll(ScrollView.FOCUS_DOWN);
        }
    }

    public void onItemSelected(AdapterView<?> parent, View v,
                               int position, long id) {
        if (this.currentMode != null) {
            // Not first auto-selection, trigger scroll
            // Cancel previous scroll call and send a new delayed one
            this.scrollHandler.removeMessages(SCROLL_WHAT);
            this.scrollHandler.sendEmptyMessageDelayed(SCROLL_WHAT, 800);
        }
        PaymentModesAdapter adapt = (PaymentModesAdapter)
            this.paymentModes.getAdapter();
        PaymentMode mode = (PaymentMode) adapt.getItem(position);
        this.currentMode = mode;
        this.resetInput();
        this.updateDisplayToMode();
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    public boolean handleMessage(Message m) {
        switch (m.what) {
        case NumKeyboard.KEY_ENTER:
            this.validatePayment();
            break;
        case SCROLL_WHAT:
            this.scrollToKeyboard();
            break;
        case PrinterConnection.PRINT_DONE:
            this.end();
            break;
        case PrinterConnection.PRINT_CTX_ERROR:
            Exception e = (Exception) m.obj;
            Log.w(LOG_TAG, "Unable to connect to printer", e);
            if (this.paymentClosed) {
                Toast t = Toast.makeText(this,
                        R.string.print_no_connexion, Toast.LENGTH_LONG);
                t.show();
                this.end();
            } else {
                Error.showError(R.string.print_no_connexion, this);
            }
            break;
        case PrinterConnection.PRINT_CTX_FAILED:
            // Give up
            if (this.paymentClosed) {
                Toast t = Toast.makeText(this, R.string.print_no_connexion,
                        Toast.LENGTH_LONG);
                t.show();
                this.end();
            } else {
                Error.showError(R.string.print_no_connexion, this);
                // Set null to disable printing
                this.printer = null;
            }
            break;
        default:
            this.refreshInput();
            this.input.setSelection(this.input.getText().toString().length());
            this.refreshGiveBack();
            break;
        }
        return true;
    }

    public void deletePayment(Payment p) {
        this.payments.remove(p);
        ((PaymentsAdapter)this.paymentsList.getAdapter()).notifyDataSetChanged();
        this.refreshRemaining();
    }


    /** Pre-payment actions */
    public void validatePayment() {
        if (this.currentMode != null) {
            double remaining = this.getRemaining();
            // Get amount from entered value (default is remaining)
            double amount = this.getAmount();
            // Check for debt and cust assignment
            if (this.currentMode.isCustAssigned()
                    && this.ticket.getCustomer() == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.payment_no_customer);
                builder.setNeutralButton(android.R.string.ok, null);
                builder.show();
                return;
            }
            if (this.currentMode.isDebt()) {
                double debt = this.ticket.getCustomer().getCurrDebt();
                for (Payment p : this.payments) {
                    if (p.getMode().isDebt()) {
                        debt += p.getAmount();
                    }
                }
                if (debt + amount > this.ticket.getCustomer().getMaxDebt()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.payment_debt_exceeded);
                    builder.setNeutralButton(android.R.string.ok, null);
                    builder.show();
                    return;
                }
            }
            if (this.currentMode.isPrepaid()) {
                double prepaid = this.getRemainingPrepaid();
                if (prepaid < amount) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.payment_no_enough_prepaid);
                    builder.setNeutralButton(android.R.string.ok, null);
                    builder.show();
                    return;
                }
            }
            boolean proceed = true;
            if (remaining - amount < 0.005) {
                // Confirm payment end
                proceed = false;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.confirm_payment_end)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes,                                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                proceedPayment();
                            }
                        })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id)  {
                                dialog.cancel();
                            }
                        })
                    .show();
            }
            if (proceed) {
                this.proceedPayment();
            }
        }
    }

    /** Register the payment.
     * @return True if payment is registered, false if an operation is pending.
     */
    private boolean proceedPayment() {
        double amount = this.getAmount();
        Payment p = new Payment(this.currentMode, amount, this.getGiven());
        if (p.getMode().getCode().equals("magcard")) {
            // Send request to the TPE, register on response
            PaymentManager pm = PaymentManager.getInstance();
            pm.proceedPayment(1, TransactionType.DEBIT, amount,
                    "978", // currecy code for euro
                    new WorldlineTPEResultHandler(p));
            return false;
        } else {
            // Register immediately
            this.registerPayment(p);
            return true;
        }
    }
    /** Add a payment to the registered ones and update ui
     * (update remaining or close payment)
     */
    private void registerPayment(Payment p) {
        this.payments.add(p);
        ((PaymentsAdapter)this.paymentsList.getAdapter()).notifyDataSetChanged();
        double remaining = this.getRemaining();
        if (remaining < 0.005) {
            this.closePayment();
        } else {
            this.refreshRemaining();
            this.resetInput();
            Toast t = Toast.makeText(this, R.string.payment_done,
                    Toast.LENGTH_SHORT);
            t.show();
        }
    }
    
    /** Save ticket and return to a new one */
    private void closePayment() {
        // Create and save the receipt and remove from session
        Session currSession = SessionData.currentSession(this);
        User u = currSession.getUser();
        final Receipt r = new Receipt(this.ticket, this.payments, u);
        ReceiptData.addReceipt(r);
        try {
            ReceiptData.save(this);
        } catch(IOException e) {
            Log.e(LOG_TAG, "Unable to save receipts", e);
            Error.showError(R.string.err_save_receipts, this);
        }
        currSession.closeTicket(this.ticket);
        try {
            SessionData.saveSession(ProceedPayment.this);
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Unable to save session", ioe);
            Error.showError(R.string.err_save_session,
                           ProceedPayment.this);
        }
        // Update customer debt
        boolean custDirty = false;
        for (Payment p : this.payments) {
            if (p.getMode().isDebt()) {
                this.ticket.getCustomer().addDebt(p.getAmount());
                custDirty = true;
            }
        }
        if (this.ticket.getCustomer() != null) {
            Customer cust = this.ticket.getCustomer();
            if (this.getRemainingPrepaid() != cust.getPrepaid()) {
                cust.setPrepaid(this.getRemainingPrepaid());
                custDirty = true;
            }
        }
        if (custDirty) {
            int index = CustomerData.customers.indexOf(this.ticket.getCustomer());
            CustomerData.customers.remove(index);
            CustomerData.customers.add(index, this.ticket.getCustomer());
            try {
                CustomerData.save(this);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to save customers", e);
                Error.showError(R.string.err_save_customers, this);
            }
        }
        this.paymentClosed = true;
        // Check printer
        if (this.printer != null && this.printEnabled == true) {
            printer.printReceipt(r);
            ProgressDialog progress = new ProgressDialog(this);
            progress.setIndeterminate(true);
            progress.setMessage(this.getString(R.string.print_printing));
            progress.show();
        } else {
            this.end();
        }
    }

    private void end() {
        Session currSession = SessionData.currentSession(this);
        // Return to a new ticket edit
        switch (Configure.getTicketsMode(this)) {
        case Configure.SIMPLE_MODE:
            TicketInput.requestTicketSwitch(currSession.newTicket());
            this.finish();
            break;
        case Configure.STANDARD_MODE:
            if (!currSession.hasTicket()) {
                TicketInput.requestTicketSwitch(currSession.newTicket());
                this.finish();
            } else {
                // Pick last ticket
                currSession.setCurrentTicket(currSession.getTickets().get(currSession.getTickets().size() - 1));
                TicketInput.requestTicketSwitch(currSession.getCurrentTicket());
                this.finish();
            }
            break;
        case Configure.RESTAURANT_MODE:
            Intent i = new Intent(this, TicketSelect.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            this.startActivityForResult(i, TicketSelect.CODE_TICKET);
            break;
        }
    }

    protected void onActivityResult (int requestCode, int resultCode,
                                     Intent data) {
        PaylevenApi.handleIntent(requestCode, data,
                new PaylevenResultHandler());
        switch (requestCode) {
        case TicketSelect.CODE_TICKET:
            switch (resultCode) {
            case Activity.RESULT_CANCELED:
                // Back to start
                Intent i = new Intent(this, Start.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(i);
                break;
            case Activity.RESULT_OK:
                TicketInput.requestTicketSwitch(SessionData.currentSession(this).getCurrentTicket());
                this.finish();
            break;
            }
        }
    }

    private class PaylevenResultHandler implements PaylevenResponseListener {
        public void onPaymentFinished(String orderId,
                TransactionRequest originalRequest, Map<String, String> result,
                PaymentCompletedStatus status) {
            switch (status) {
            case AMOUNT_TOO_LOW:
                Error.showError(R.string.payment_card_rejected,
                        ProceedPayment.this);
                break;
            case API_KEY_DISABLED:
            case API_KEY_NOT_FOUND:
            case API_KEY_VERIFICATION_ERROR:
                Error.showError(R.string.err_payleven_key, ProceedPayment.this);
                break;
            case ANOTHER_API_CALL_IN_PROGRESS:
                Error.showError(R.string.err_payleven_concurrent_call,
                        ProceedPayment.this);
                break;
            case API_SERVICE_ERROR:
            case API_SERVICE_FAILED:
            case ERROR:
            case PAYMENT_ALREADY_EXISTS:
                Error.showError(R.string.err_payleven_general,
                        ProceedPayment.this);
                break;
            case CARD_AUTHORIZATION_ERROR:
                Error.showError(R.string.payment_card_rejected,
                        ProceedPayment.this);
                break;
            case INVALID_CURRENCY:
            case WRONG_COUNTRY_CODE:
                Error.showError(R.string.err_payleven_forbidden,
                        ProceedPayment.this);
                break;
            case SUCCESS:
                ProceedPayment.this.proceedPayment();
                break;
            }
         }

         public void onNoPaylevenResponse(Intent data) {
         }

         public void onOpenTransactionDetailsFinished(String orderId,
                 Map<String, String> transactionData,
                 OpenTransactionDetailsCompletedStatus status) {
         }

         public void onOpenSalesHistoryFinished() {
         }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }
    public boolean onDown(MotionEvent e) { return false; }
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        if (e1 == null || e2 == null) {
            return false;
        }
        if (e1.getX() < (e2.getX() - 50) && velocityX > 1500) {
            // Swipe Right
            this.finish();
            return true;
        }
        return false;
    }
    public void onLongPress(MotionEvent e) {}
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) { return false; }
    public void onShowPress(MotionEvent e) {}
    public boolean onSingleTapUp(MotionEvent e) { return false;}


    public void addQty(TicketLine l) {}
    public void remQty(TicketLine l) {}
    public void mdfyQty(TicketLine t) {}
    public void delete(TicketLine t) {}

    /** Worldline TPE response callback */
    private class WorldlineTPEResultHandler implements PaymentAsyncTaskActions {

        private Payment payment;

        public WorldlineTPEResultHandler(Payment p) {
            this.payment = p;
        }

        @Override
        public void onTransactionStatusChange(TransactionStatus status) {
        }

        @Override
        public void onPrePayment() {
            if (ProceedPayment.this.paymentDialog == null) {
                ProceedPayment.this.paymentDialog = new ProgressDialog(ProceedPayment.this);
            ProceedPayment.this.paymentDialog.setMessage("Transaction via TPE en cours");
            ProceedPayment.this.paymentDialog.show();
            }
        }
        @Override
        public void onPostPayment(PaymentResponse response) {
            if (ProceedPayment.this.paymentDialog != null) {
                ProceedPayment.this.paymentDialog.dismiss();
                ProceedPayment.this.paymentDialog = null;
            }
            if (response.getPaymentResponseCode() != PaymentResponseCode.OK) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ProceedPayment.this);
                builder.setMessage("Échec de la transaction");
                builder.setNeutralButton(android.R.string.ok, null);
                builder.show();
                return;
            }
            TransactionStatus status = response.getTransactionStatus();
            switch (status) {
            case SUCCESS: // Validated
                ProceedPayment.this.registerPayment(this.payment);
                break;
            case REFUSED: // Canceled
                AlertDialog.Builder builder = new AlertDialog.Builder(ProceedPayment.this);
                builder.setMessage("Paiement annulé");
                builder.setNeutralButton(android.R.string.ok, null);
                builder.show();
                break;
            }
        }
    } 

}
