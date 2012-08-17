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

import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

public class Configure extends PreferenceActivity {

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

    public static String getHost(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("host", "");
    }
    
    private static String defaultMachineName() {
        return Build.PRODUCT + "-" + Build.DEVICE;
    }

    public static String getMachineName(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("machine_name", defaultMachineName());
    }
}