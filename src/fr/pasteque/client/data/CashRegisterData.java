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
package fr.pasteque.client.data;

import fr.pasteque.client.models.CashRegister;

import android.content.Context;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class CashRegisterData {

    private static final String FILENAME = "cashreg.data";

    private static CashRegister current;

    public static CashRegister current(Context ctx) {
        if (current == null) {
           try {
               load(ctx);
           } catch (IOException e) {
               e.printStackTrace();
           }
        }
        return current;
    }

    public static void set(CashRegister c) {
        current = c;
    }

    public static boolean save(Context ctx)
        throws IOException {
        FileOutputStream fos = ctx.openFileOutput(FILENAME, Context.MODE_PRIVATE);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(current);
        oos.close();
        return true;
    }

    public static boolean load(Context ctx)
        throws IOException {
        FileInputStream fis = ctx.openFileInput(FILENAME);
        ObjectInputStream ois = new ObjectInputStream(fis);
        CashRegister c = null;
        try {
            c = (CashRegister) ois.readObject();
            current = c;
            ois.close();
            return true;
        } catch (ClassNotFoundException cnfe) {
            // Should never happen
        }
        ois.close();
        return false;
    }

    /** Delete current cash */
    public static void clear(Context ctx) {
        current = null;
        ctx.deleteFile(FILENAME);
    }

}
