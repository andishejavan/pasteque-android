/*
    POS-Tech Android
    Copyright (C) 2012 SARL SCOP Scil (contact@scil.coop)

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
package fr.postech.client;

import android.app.AlertDialog;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class Configure extends PreferenceActivity {

    public static final int SIMPLE_MODE = 0;
    public static final int STANDARD_MODE = 1;
    public static final int RESTAURANT_MODE = 2;

    private static final String DEMO_HOST = "pt.scil.coop/pasteque/api";
    private static final String DEMO_USER = "demo";
    private static final String DEMO_PASSWORD = "demo";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Set default values
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains("machine_name")) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString("machine_name", defaultMachineName());
            edit.commit();
        }
        // Load preferences
        this.addPreferencesFromResource(R.layout.configure);
    }

    public static boolean isConfigured(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return !prefs.getString("host", "").equals("");
    }

    public static boolean isDemo(Context ctx) {
        return DEMO_HOST.equals(Configure.getHost(ctx))
               && DEMO_USER.equals(Configure.getUser(ctx))
               && DEMO_PASSWORD.equals(Configure.getPassword(ctx));
    }

    public static String getHost(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("host", DEMO_HOST);
    }

    public static String getUser(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("user", DEMO_USER);
    }

    public static String getPassword(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("password", DEMO_PASSWORD);
    }

    private static String defaultMachineName() {
        return Build.PRODUCT + "-" + Build.DEVICE;
    }

    public static String getMachineName(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("machine_name", defaultMachineName());
    }

    public static int getTicketsMode(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return Integer.parseInt(prefs.getString("tickets_mode",
                                                String.valueOf(SIMPLE_MODE)));
    }

    private static final int MENU_IMPORT_ID = 0;
    private static final int MENU_DEBUG_ID = 1;
    @Override
    public boolean onCreateOptionsMenu ( Menu menu ) {
        int i = 0;
        MenuItem imp = menu.add(Menu.NONE, MENU_IMPORT_ID, i++,
                                this.getString(R.string.menu_cfg_import));
        imp.setIcon(android.R.drawable.ic_menu_revert);
        MenuItem dbg = menu.add(Menu.NONE, MENU_DEBUG_ID, i++,
                                this.getString(R.string.menu_cfg_debug));
        dbg.setIcon(android.R.drawable.ic_menu_report_image);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected ( MenuItem item ) {
        switch (item.getItemId()) {
        case MENU_IMPORT_ID:
            // Get properties file
            // TODO: check external storage state and access
            File path = Environment.getExternalStorageDirectory();
            path = new File(path, "postech");
            File file = new File(path, "postech.properties");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast t = Toast.makeText(this,
                                         R.string.cfg_import_file_not_found,
                                         Toast.LENGTH_SHORT);
                t.show();
                return true;
            }
            Properties props = new Properties();
            try {
            props.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
                Toast t = Toast.makeText(this,
                                         R.string.cfg_import_read_error,
                                         Toast.LENGTH_SHORT);
                t.show();
                return true;
            }
            // Load props
            String host = props.getProperty("host", "");
            String machineName = props.getProperty("machine_name", "");
            String ticketsMode = props.getProperty("tickets_mode", "simple");
            // Save
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString("machine_name", defaultMachineName());
            edit.commit();
            if (!host.equals("")) {
                edit.putString("host", host);
            }
            if (!machineName.equals("")) {
                edit.putString("machine_name", machineName);
            }
            // Set tickets mode, simple by default
            if (ticketsMode.equals("restaurant")) {
                edit.putInt("tickets_mode", RESTAURANT_MODE);
            } else if (ticketsMode.equals("standard")) {
                edit.putInt("tickets_mode", STANDARD_MODE);
            } else {
                edit.putInt("tickets_mode", SIMPLE_MODE);
            }
            edit.commit();
            Toast t = Toast.makeText(this, R.string.cfg_import_done,
                                     Toast.LENGTH_SHORT);
            t.show();
            // Reset activity to reload values
            this.finish();
            Intent i = new Intent(this, Configure.class);
            this.startActivity(i);
            break;
        case MENU_DEBUG_ID:
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.cfg_debug_alert_title);
            b.setMessage(R.string.cfg_debug_alert_message);
            b.setIcon(android.R.drawable.ic_dialog_alert);
            b.setNegativeButton(android.R.string.cancel, null);
            b.setPositiveButton(R.string.cfg_debug_alert_continue,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Intent i = new Intent(Configure.this, Debug.class);
                                        Configure.this.startActivity(i);
                                    }
                                });
            b.show();
            break;
        }
        return true;
    }
    
}
