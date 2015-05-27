package fr.pasteque.client.utils;

import android.content.Context;

import com.mpowa.android.powapos.peripherals.PowaPOS;
import com.mpowa.android.powapos.peripherals.platform.base.PowaPeripheralCallback;
import com.mpowa.android.powapos.peripherals.platform.base.PowaPeripheralCallbackIntMgr;

import java.util.ArrayList;

/**
 *  Singleton to prevent multiple connection to Powa
 *  PowaPos sdk 1.14.0 has one built-in
 *  This one is thread safe thought
 *  WIP
 */
public class PowaPosSingleton extends PowaPOS {
    private static final String POWA_POS_SING_TAG = "PowaPosSingleton";
    private static boolean mCreated = false;
    private static PowaPosSingleton mInstance;
    private static Context mContext;
    private static PowaPeripheralCallback mCallback;

    // Protected to for use of getInstance;
    protected PowaPosSingleton(Context context, PowaPeripheralCallback callback) {
        super(context, callback);
    }

    public synchronized static PowaPosSingleton getInstance() {
        if (mInstance == null) {
            mInstance = new PowaPosSingleton(null, null);
        }
        return mInstance;
    }

    public synchronized void create(Context context, PowaPeripheralCallback callback) {
        if (context == null) {
            throw new RuntimeException(POWA_POS_SING_TAG
                    + ".create()'s context should not be null");
        }
        if (!mCreated) {
            mCreated = true;
            this.activity = context;
            this.peripheralExternalEvents = callback;
            PowaPeripheralCallbackIntMgr.getInstance().addListener(this.peripheralInternalEvents);
        }
    }

    public synchronized void dispose() {
        super.dispose();
        mCreated = false;
    }
}
