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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v7.widget.ListPopupWindow;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SlidingDrawer;
import android.widget.SlidingDrawer.OnDrawerCloseListener;
import android.widget.SlidingDrawer.OnDrawerOpenListener;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.pasteque.client.data.CatalogData;
import fr.pasteque.client.data.CashData;
import fr.pasteque.client.data.CustomerData;
import fr.pasteque.client.data.CompositionData;
import fr.pasteque.client.data.TariffAreaData;
import fr.pasteque.client.data.ReceiptData;
import fr.pasteque.client.data.SessionData;
import fr.pasteque.client.models.Catalog;
import fr.pasteque.client.models.Category;
import fr.pasteque.client.models.CompositionInstance;
import fr.pasteque.client.models.Customer;
import fr.pasteque.client.models.Product;
import fr.pasteque.client.models.TariffArea;
import fr.pasteque.client.models.Ticket;
import fr.pasteque.client.models.TicketLine;
import fr.pasteque.client.models.Session;
import fr.pasteque.client.models.User;
import fr.pasteque.client.sync.TicketUpdater;
import fr.pasteque.client.utils.BarcodeInput;
import fr.pasteque.client.utils.ScreenUtils;
import fr.pasteque.client.utils.TrackedActivity;
import fr.pasteque.client.widgets.CategoriesAdapter;
import fr.pasteque.client.widgets.ProductBtnItem;
import fr.pasteque.client.widgets.ProductsBtnAdapter;
import fr.pasteque.client.widgets.SessionTicketsAdapter;
import fr.pasteque.client.widgets.TicketLineItem;
import fr.pasteque.client.widgets.TicketLinesAdapter;

public class TicketInput extends TrackedActivity
    implements TicketLineEditListener, AdapterView.OnItemSelectedListener,
    GestureDetector.OnGestureListener {

    private static final String LOG_TAG = "Pasteque/TicketInput";
    private static final int CODE_SCAN = 4;
    private static final int CODE_COMPO = 5;
    private static final int CODE_AREA = 6;
    private final Context context = this;

    private Catalog catalog;
    private Ticket ticket;
    private Category currentCategory;
    private BarcodeInput barcodeInput;
    private GestureDetector gestureDetector;

    private TextView ticketLabel;
    private TextView ticketCustomer;
    private TextView ticketArticles;
    private TextView ticketTotal;
    private TextView tariffArea;
    private Button ticketAccess;
    private Button productAccess;    
    private ListView ticketContent;
    private Gallery categories;
    private GridView products;

    private static Catalog catalogInit;
    private static Ticket ticketInit;
    private static Ticket ticketSwitch;
    public static void setup(Catalog catalog, Ticket ticket) {
        catalogInit = catalog;
        ticketInit = ticket;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            // From state
            this.catalog = (Catalog) state.getSerializable("catalog");
            this.ticket = (Ticket) state.getSerializable("ticket");
            this.currentCategory = this.catalog.getAllCategories().get(0);
        } else {
            // From scratch
            this.catalog = catalogInit;
            catalogInit = null;
            this.currentCategory = this.catalog.getAllCategories().get(0);
            if (ticketInit == null) {
                this.ticket = new Ticket();
            } else {
                this.ticket = ticketInit;
                ticketInit = null;
            }
        }
        this.barcodeInput = new BarcodeInput();
        this.gestureDetector = new GestureDetector(this, this);
        View.OnTouchListener touchListener = new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) {
                    return TicketInput.this.gestureDetector.onTouchEvent(e);
                }
            };
        // Set views
        setContentView(R.layout.products);
        this.ticketLabel = (TextView) this.findViewById(R.id.ticket_label);
        this.ticketCustomer = (TextView) this.findViewById(R.id.ticket_customer);
        this.ticketArticles = (TextView) this.findViewById(R.id.ticket_articles);
        this.ticketTotal = (TextView) this.findViewById(R.id.ticket_total);
        this.tariffArea = (TextView) this.findViewById(R.id.ticket_area);

        this.categories = (Gallery) this.findViewById(R.id.categoriesGrid);
        this.products = (GridView) this.findViewById(R.id.productsGrid);
        CategoriesAdapter catAdapt = new CategoriesAdapter(this.catalog.getAllCategories());
        this.categories.setAdapter(catAdapt);
        this.categories.setOnItemSelectedListener(this);
        this.categories.setSelection(0, false);
        ProductClickListener prdListnr = new ProductClickListener();
        this.products.setOnItemClickListener(prdListnr);
        this.products.setOnItemLongClickListener(prdListnr);
        this.products.setOnTouchListener(touchListener);
        // Hide new ticket/delete ticket on simple mode
        if (Configure.getTicketsMode(this) == Configure.SIMPLE_MODE) {
            View deleteView = this.findViewById(R.id.ticket_delete);
            if (deleteView != null) {
                deleteView.setVisibility(View.GONE);
            }
        }

        // Check presence of barcode scanner
        Intent i = new Intent("com.google.zxing.client.android.SCAN");
        List<ResolveInfo> list = this.getPackageManager().queryIntentActivities(i,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (list.size() == 0) {
            this.findViewById(R.id.scan_customer).setVisibility(View.GONE);
        }

        this.ticketContent = (ListView) this.findViewById(R.id.ticket_content);
        this.ticketContent.setAdapter(new TicketLinesAdapter(this.ticket,
                                                             this));
        this.ticketContent.setOnTouchListener(touchListener);
        // Check presence of tariff areas
        if (TariffAreaData.areas.size() == 0) {
            this.findViewById(R.id.change_area).setVisibility(View.GONE);
            this.tariffArea.setVisibility(View.GONE);
        }
        this.updateProducts();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ticketSwitch != null) {
            this.switchTicket(ticketSwitch);
            ticketSwitch = null;
        } else {
            this.updateTicketView();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("catalog", this.catalog);
        outState.putSerializable("ticket", this.ticket);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Configure.getTicketsMode(this) == Configure.RESTAURANT_MODE
                && Configure.getSyncMode(this) == Configure.AUTO_SYNC_MODE) {
            TicketUpdater.getInstance().execute(this, null,
                    TicketUpdater.TICKETSERVICE_SEND
                    | TicketUpdater.TICKETSERVICE_ONE, this.ticket);
        }
        this.overridePendingTransition(R.transition.fade_in,
                R.transition.fade_out);
    }

    private void switchTicket(Ticket t) {
        this.ticket = t;
        this.ticketContent.setAdapter(new TicketLinesAdapter(this.ticket,
                                                             this));
        this.updateTicketView();
    }

    /** Callback for delete ticket button */
    public void deleteTicket(View v) {
        // Show confirmation
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(this.getString(R.string.delete_ticket_title));
        String message = this.getResources().getQuantityString(
                R.plurals.delete_ticket_message,
                this.ticket.getArticlesCount(), this.ticket.getArticlesCount());
        b.setMessage(message);
        b.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Session currSession = SessionData.currentSession(TicketInput.this);
                    Ticket current = currSession.getCurrentTicket();
                    for (Ticket t : currSession.getTickets()) {
                        if (t.getLabel().equals(current.getLabel())) {
                            currSession.getTickets().remove(t);
                            break;
                        }
                    }
                    if (currSession.getTickets().size() == 0) {
                        currSession.newTicket();
                    } else {
                        currSession.setCurrentTicket(currSession.getTickets().get(currSession.getTickets().size() - 1));
                    }
                    switchTicket(currSession.getCurrentTicket());
                }
            });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    /** New ticket button callback */
    public void newTicket(View v) {
        Session currSession = SessionData.currentSession(this);
        currSession.newTicket();
        switchTicket(currSession.getCurrentTicket());
        try {
            SessionData.saveSession(this);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to save session", e);
        }
    }

    /** Callback for ticket switch button */
    public void switchTicketBtn(View v) {
        if (Configure.getTicketsMode(this) != Configure.SIMPLE_MODE) {
            this.openSwitchTicket();
        }
    }

    /** Request a switch to an other ticket. It will be effective
     * next time the activity is displayed
     */
    public static void requestTicketSwitch(Ticket t) {
        ticketSwitch = t;
    }

    private void updateTicketView() {
        // Update ticket info
        String count = this.getString(R.string.ticket_articles,
                                      this.ticket.getArticlesCount());
        String total = this.getString(R.string.ticket_total,
                                      this.ticket.getTotalPrice());
        String label = this.getString(R.string.ticket_label,
                                      this.ticket.getLabel());
        this.ticketLabel.setText(label);
        this.ticketArticles.setText(count);
        this.ticketTotal.setText(total);
        // Update customer info
        if (this.ticket.getCustomer() != null) {
            Customer cust = this.ticket.getCustomer();
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
        // Update tariff area info
        ((TicketLinesAdapter)TicketInput.this.ticketContent.getAdapter()).notifyDataSetChanged();
        if (this.ticket.getTariffArea() != null) {
            this.tariffArea.setText(this.ticket.getTariffArea().getLabel());
        } else {
            this.tariffArea.setText(R.string.default_tariff_area);
        }
    }

    private void updateProducts() {
        ProductsBtnAdapter prdAdapt = new ProductsBtnAdapter(this.catalog.getProducts(this.currentCategory));
        this.products.setAdapter(prdAdapt);
    }

    private class ProductClickListener
        implements OnItemClickListener, OnItemLongClickListener {
        public void onItemClick(AdapterView<?> parent, View v,
                                int position, long id) {
            ProductBtnItem item = (ProductBtnItem) v;
            final Product p = item.getProduct();
            TicketInput.this.productPicked(p);
        }

        public boolean onItemLongClick(AdapterView<?> parent, View v,
                                        int position, long id) {
            ProductBtnItem item = (ProductBtnItem) v;
            Product p = item.getProduct();
            AlertDialog.Builder b = new AlertDialog.Builder(TicketInput.this);
            b.setTitle(p.getLabel());
            String message = TicketInput.this.getString(R.string.prd_info_price,
                    p.getTaxedPrice(TicketInput.this.ticket.getTariffArea()));
            b.setMessage(message);
            b.setNeutralButton(android.R.string.ok, null);
            b.show();
            return true;
        }
    }

    /** Trigger actions required before adding the product then add it
     * to the ticket
     */
    private void productPicked(final Product p) {
        if (CompositionData.isComposition(p)) {
            Intent i = new Intent(this, CompositionInput.class);
            CompositionInput.setup(this.catalog,
                    CompositionData.getComposition(p.getId()));
            this.startActivityForResult(i, CODE_COMPO);
        } else {
            /* If the product is scaled, then a message pops up and let
             * the user choose the weight
             */
            if(p.isScaled()) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                alertDialogBuilder.setView(input);
                alertDialogBuilder.setTitle(p.getLabel());
                alertDialogBuilder
                        .setView(input)
                        .setIcon(R.drawable.scale)
                        .setMessage(R.string.scaled_products_info)
                        .setCancelable(false)
                        .setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    //On click, add the scaled product to the ticket
                                    String getString = input.getText().toString();
                                    if (!TextUtils.isEmpty(getString)) {
                                        addScaledProduct(p, getString);
                                    }
                                }
                            })
                        .setNegativeButton(R.string.scaled_products_cancel,new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    //On click, dismiss the dialog
                                    dialog.cancel();
                                }
                            });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            } else {
                this.ticket.addProduct(p);
                this.updateTicketView();
            }
        }
    }

    private void readBarcode(String code) {
        boolean found = false;
        if (code.startsWith("c")) {
            // Scanned a customer card
            for (Customer c : CustomerData.customers) {
                if (code.equals(c.getCard())) {
                    this.ticket.setCustomer(c);
                    this.updateTicketView();
                    found = true;
                    break;
                }
            }
            if (!found) {
                String text = this.getString(R.string.customer_not_found,
                        code);
                Toast t = Toast.makeText(this, text,
                        Toast.LENGTH_LONG);
                t.show();
            }
        } else {
            // Other scan, assumed to be product
            Catalog cat = CatalogData.catalog(this);
            Product p = cat.getProductByBarcode(code);
            if (p != null) {
                this.productPicked(p);
                String text = this.getString(R.string.barcode_found,
                        p.getLabel());
                Toast t = Toast.makeText(this, text,
                        Toast.LENGTH_SHORT);
                t.show();
            } else {
                String text = this.getString(R.string.barcode_not_found,
                        code);
                Toast t = Toast.makeText(this, text,
                        Toast.LENGTH_LONG);
                t.show();
            }
        }

    }

    /** Add scaled product to the ticket
     * @param p the product to add
     * @param input the weight in kg
     */
    private void addScaledProduct(Product p, String input) {
        double scale = Double.valueOf(input);
        TicketInput.this.ticket.addScaledProduct(p, scale);
        TicketInput.this.updateTicketView();
    }


    public void payTicket(View v) {
        ProceedPayment.setup(this.ticket);
        Intent i = new Intent(this, ProceedPayment.class);
        this.startActivity(i);
        this.overridePendingTransition(R.transition.slide_in,
                R.transition.fade_out);
    }

    public void scanBarcode(View v) {
        Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
        intentScan.addCategory(Intent.CATEGORY_DEFAULT);
        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivityForResult(intentScan, CODE_SCAN);
    }

    public void changeArea(View v) {
        Intent i = new Intent(this, TariffAreaSelect.class);
        this.startActivityForResult(i, CODE_AREA);
    }

    public void addQty(TicketLine l) {
        this.ticket.adjustQuantity(l, 1);
        this.updateTicketView();
    }

    public void remQty(TicketLine l) {
        this.ticket.adjustQuantity(l, -1);
        this.updateTicketView();
    }

    /** Modifies the weight of the product by asking the user a new one
     * @param the ticket's line
     */
    public void mdfyQty(final TicketLine l) {
        Product p = l.getProduct();
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER |
                            InputType.TYPE_NUMBER_FLAG_DECIMAL |
                            InputType.TYPE_NUMBER_FLAG_SIGNED);
        alertDialogBuilder.setView(input);
        alertDialogBuilder.setTitle(p.getLabel());
        alertDialogBuilder
            .setView(input)
            .setIcon(R.drawable.scale)
            .setMessage(R.string.scaled_products_info)
            .setCancelable(false)
            .setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    String recup = input.getText().toString();
                    double scale = Double.valueOf(recup);
                    TicketInput.this.ticket.adjustScale(l, scale);
                    TicketInput.this.updateTicketView();
                }
              })
            .setNegativeButton(R.string.scaled_products_cancel,new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void delete(TicketLine l) {
        this.ticket.removeLine(l);
        this.updateTicketView();
    }

    /** Category selected */
    public void onItemSelected(AdapterView<?> parent, View v,
                               int position, long id) {
        CategoriesAdapter adapt = (CategoriesAdapter)
            this.categories.getAdapter();
        Category cat = (Category) adapt.getItem(position);
        this.currentCategory = cat;
        this.updateProducts();
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    /** Update the UI to switch ticket */
    public void openSwitchTicket() {
        // Send current ticket data in connected mode
        if (Configure.getSyncMode(this) == Configure.AUTO_SYNC_MODE) {
            TicketUpdater.getInstance().execute(getApplicationContext(),
                    null,
                    TicketUpdater.TICKETSERVICE_SEND
                    | TicketUpdater.TICKETSERVICE_ONE, ticket);
        }
        // Open ticket picker
        switch (Configure.getTicketsMode(this)) {
        case Configure.STANDARD_MODE:
            // Open selector popup
            try {
                final ListPopupWindow popup = new ListPopupWindow(this);
                ListAdapter adapter = new SessionTicketsAdapter(this);
                popup.setAnchorView(this.ticketLabel);
                popup.setAdapter(adapter);
                popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        public void onItemClick(AdapterView<?> parent, View v,
                                int position, long id) {
                            // TODO: handle connected mode on switch
                            Ticket t = SessionData.currentSession(TicketInput.this).getTickets().get(position);
                            TicketInput.this.switchTicket(t);
                            popup.dismiss();
                        }
                        public void onNothingSelected(AdapterView v) {}
                    });
                popup.setWidth(ScreenUtils.inToPx(2, this));
                int ticketsCount = adapter.getCount();
                int height = ScreenUtils.dipToPx(SessionTicketsAdapter.HEIGHT_DIP * Math.min(5, ticketsCount), this);
                popup.setHeight(height);
                popup.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
            break;
        case Configure.RESTAURANT_MODE:
            // Open restaurant activity
            Intent i = new Intent(this, TicketSelect.class);
            this.startActivityForResult(i, TicketSelect.CODE_TICKET);
            break;
            }
    }

    protected void onActivityResult (int requestCode, int resultCode,
                                     Intent data) {
        switch (requestCode) {
        case TicketSelect.CODE_TICKET:
            switch (resultCode) {
            case Activity.RESULT_CANCELED:
                break;
            case Activity.RESULT_OK:
                this.switchTicket(SessionData.currentSession(this).getCurrentTicket());
                break;
            }
            break;
        case CustomerSelect.CODE_CUSTOMER:
            switch (resultCode) {
            case Activity.RESULT_CANCELED:
                break;
            case Activity.RESULT_OK:
                updateTicketView();
                try {
                    SessionData.saveSession(this);
                } catch (IOException ioe) {
                    Log.e(LOG_TAG, "Unable to save session", ioe);
                    Error.showError(R.string.err_save_session, this);
                }
                break;
            }
            break;
        case CODE_SCAN:
            if (resultCode == Activity.RESULT_OK) {
                String code = data.getStringExtra("SCAN_RESULT");
                this.readBarcode(code);
            }
            break;
        case CODE_COMPO:
            if (resultCode == Activity.RESULT_OK) {
                CompositionInstance compo = (CompositionInstance)
                        data.getSerializableExtra("composition");
                this.ticket.addProduct(compo);
                this.updateTicketView();
            }
            break;
        case CODE_AREA:
            if (resultCode == Activity.RESULT_OK) {
                TariffArea area = (TariffArea) data.getSerializableExtra("tariffArea");
                this.ticket.setTariffArea(area);
                this.updateTicketView();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        System.out.println("down " + keyCode);
        if (keyCode == 160) { // numpad enter
            // Fuck off sa mere
            this.onKeyUp(KeyEvent.KEYCODE_ENTER, event);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Handle keyboard input for barcode scanning */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (this.barcodeInput.append(keyCode, event)) {
            if (this.barcodeInput.isTerminated()) {
                String barcode = this.barcodeInput.getInput();
                this.readBarcode(barcode);
            }
            return true;
        } else {
            return super.onKeyUp(keyCode, event);
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
        if (e1.getX() > (e2.getX() + 50) && velocityX < -1500) {
            // Swipe left
            this.payTicket(null);
            return true;
        }
        return false;
    }
    public void onLongPress(MotionEvent e) {}
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) { return false; }
    public void onShowPress(MotionEvent e) {}
    public boolean onSingleTapUp(MotionEvent e) { return false;}

    private static final int MENU_CLOSE_CASH = 0;
    private static final int MENU_SWITCH_TICKET = 1;
    private static final int MENU_NEW_TICKET = 2;
    private static final int MENU_CUSTOMER = 3;
    private static final int MENU_EDIT = 4;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int i = 0;
        User cashier = SessionData.currentSession(this).getUser();
        if (cashier.hasPermission("fr.pasteque.pos.panels.JPanelCloseMoney")) {
            MenuItem close = menu.add(Menu.NONE, MENU_CLOSE_CASH, i++,
                                      this.getString(R.string.menu_main_close));
            close.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM
                    | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
        if (Configure.getTicketsMode(this) == Configure.STANDARD_MODE) {
            MenuItem newTicket = menu.add(Menu.NONE, MENU_NEW_TICKET, i++,
                    this.getString(R.string.menu_new_ticket));
            newTicket.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        if (CustomerData.customers.size() > 0) {
            MenuItem customer = menu.add(Menu.NONE, MENU_CUSTOMER, i++,
                    this.getString(R.string.menu_assign_customer));
            customer.setIcon(R.drawable.customer);
            customer.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return (i > 0)
                // menu entries added on open
                || (Configure.getTicketsMode(this) == Configure.STANDARD_MODE)
                || (ReceiptData.hasReceipts());
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (Configure.getTicketsMode(this) == Configure.STANDARD_MODE) {
            MenuItem switchTkt = menu.findItem(MENU_SWITCH_TICKET);
            if (SessionData.currentSession(this).hasWaitingTickets()) {
                if (switchTkt == null) {
                    switchTkt = menu.add(Menu.NONE, MENU_SWITCH_TICKET, 10,
                            this.getString(R.string.menu_switch_ticket));
                    switchTkt.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
            } else {
                if (switchTkt != null) {
                    menu.removeItem(MENU_SWITCH_TICKET);
                }
            }
        }
        if (ReceiptData.hasReceipts()
                && SessionData.currentSession(this).getUser().hasPermission("sales.EditTicket")) {
            MenuItem edit = menu.findItem(MENU_EDIT);
            if (edit == null) {
                edit = menu.add(Menu.NONE, MENU_EDIT, 20,
                        this.getString(R.string.menu_edit_tickets));
            }
        } else {
            menu.removeItem(MENU_EDIT);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_CLOSE_CASH:
            CloseCash.close(this);
            break;
        case MENU_NEW_TICKET:
        	if (Configure.getSyncMode(this) == Configure.AUTO_SYNC_MODE) {
    			TicketUpdater.getInstance().execute(getApplicationContext(),
                        null,
                        TicketUpdater.TICKETSERVICE_SEND
                        | TicketUpdater.TICKETSERVICE_ONE, ticket);
    		}
            SessionData.currentSession(this).newTicket();
            try {
                SessionData.saveSession(this);
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "Unable to save session", ioe);
                Error.showError(R.string.err_save_session, this);
            }
            this.switchTicket(SessionData.currentSession(this).getCurrentTicket());
            break;
        case MENU_SWITCH_TICKET:
            this.openSwitchTicket();
            break;
        case MENU_CUSTOMER:
            Intent i = new Intent(this, CustomerSelect.class);
            CustomerSelect.setup(this.ticket.getCustomer() != null);
            this.startActivityForResult(i, CustomerSelect.CODE_CUSTOMER);
            break;
        case MENU_EDIT:
            i = new Intent(this, ReceiptSelect.class);
            this.startActivity(i);
            break;
        }
        return true;
    }

}
