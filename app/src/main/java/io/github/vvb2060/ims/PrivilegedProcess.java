package io.github.vvb2060.ims;

import static rikka.shizuku.ShizukuProvider.METHOD_GET_BINDER;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.IActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.permission.PermissionManager;
import android.system.Os;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import rikka.shizuku.ShizukuBinderWrapper;

public class PrivilegedProcess extends Instrumentation {
    static final String TAG = "vvb";

    @Override
    public void onCreate(Bundle arguments) {
        var context = getContext();
        if (Process.isSdkSandbox()) {
            var extras = makeExtras(context);
            var cr = getContext().getContentResolver();
            cr.call(BuildConfig.APPLICATION_ID + ".shizuku", METHOD_GET_BINDER, null, extras);
        } else if (arguments != null && arguments.getInt("pid", 0) == Process.myPid()) {
            var binder = ServiceManager.getService(Context.ACTIVITY_SERVICE);
            var am = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(binder));
            try {
                am.startDelegateShellPermissionIdentity(Os.getuid(), null);
                try {
                    grantPermission(context);
                    overrideConfig(context, false);
                } finally {
                    try {
                        am.stopDelegateShellPermissionIdentity();
                    } catch (Throwable e) {
                        Log.w(TAG, "stopDelegateShellPermissionIdentity ignored", e);
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            finish(0, new Bundle());
        } else {
            finish(0, new Bundle());
        }
    }

    private Bundle makeExtras(Context context) {
        var binder = new Binder() {
            @Override
            protected boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == 1) {
                    try {
                        grantPermission(context);
                        overrideConfig(context, true);
                    } catch (Throwable e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                    var handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> finish(0, new Bundle()), 1000);
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        var extras = new Bundle();
        extras.putBinder("binder", binder);
        return extras;
    }

    @SuppressLint("MissingPermission")
    private static void grantPermission(Context context) {
        var pm = context.getSystemService(PermissionManager.class);
        pm.grantRuntimePermission(BuildConfig.APPLICATION_ID,
                android.Manifest.permission.READ_PHONE_STATE, Process.myUserHandle());
    }

    @SuppressLint("MissingPermission")
    private static void overrideConfig(Context context, boolean persistent) {
        var cm = context.getSystemService(CarrierConfigManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        var values = getConfig();
        int customVersion = BuildConfig.VERSION_CODE + 100;
        for (var subId : sm.getActiveSubscriptionIdList()) {
            values.putInt("vvb2060_config_version", customVersion);
            try {
                cm.overrideConfig(subId, values, persistent);
            } catch (SecurityException e) {
                Log.w(TAG, "overrideConfig failed for subId " + subId, e);
                if (persistent) {
                    persistent = false;
                    cm.overrideConfig(subId, values, persistent);
                }
            }
            enable5gNetworkType(context, subId);
            var bundle = cm.getConfigForSubId(subId, "vvb2060_config_version");
            if (bundle.getInt("vvb2060_config_version", 0) == customVersion) {
                Log.i(TAG, "overrideConfig succeeded for subId " + subId + ", persistent=" + persistent);
            } else {
                Log.e(TAG, "overrideConfig failed for subId " + subId + ", persistent=" + persistent);
            }
        }
    }

    private static void enable5gNetworkType(Context context, int subId) {
        try {
            var tm = context.getSystemService(TelephonyManager.class);
            if (tm == null) return;
            var subTm = tm.createForSubscriptionId(subId);
            var getMethod = TelephonyManager.class.getMethod("getAllowedNetworkTypesForReason", int.class);
            var setMethod = TelephonyManager.class.getMethod("setAllowedNetworkTypesForReason", int.class, long.class);
            long nrBit = 1L << 19;  // NETWORK_TYPE_BITMASK_NR (5G)
            long lteBit = 1L << 13; // NETWORK_TYPE_BITMASK_LTE (4G)
            // REASON_USER = 0, REASON_CARRIER = 2
            long userMask = (long) getMethod.invoke(subTm, 0);
            setMethod.invoke(subTm, 0, userMask | nrBit | lteBit);
            long carrierMask = (long) getMethod.invoke(subTm, 2);
            setMethod.invoke(subTm, 2, carrierMask | nrBit | lteBit);
        } catch (Throwable e) {
            Log.w(TAG, "enable5gNetworkType skipped for subId " + subId, e);
        }
    }

    private static PersistableBundle getConfig() {
        var bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL, true);

        // VoLTE / VT / UT / Cross-SIM
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, true);

        // VoWiFi
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL, true);
        bundle.putInt(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT, 6);

        // 4G+ / Enhanced LTE UI
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false);

        // 5G NR (NSA + SA) & VoNR & Unmetered 5G
        bundle.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true);
        bundle.putIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                new int[]{
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA
                });
        bundle.putBoolean("unmetered_nr_nsa_bool", true);
        bundle.putBoolean("unmetered_nr_sa_bool", true);
        bundle.putBoolean("unmetered_nr_nsa_mmwave_bool", true);
        bundle.putBoolean("unmetered_nr_nsa_sub6_bool", true);
        bundle.putBoolean("ascii_7_bit_support_for_long_message_bool", true);

        // 5G Signal Thresholds (optimized for domestic signal display)
        bundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                new int[]{
                        -125, /* SIGNAL_STRENGTH_POOR */
                        -115, /* SIGNAL_STRENGTH_MODERATE */
                        -105, /* SIGNAL_STRENGTH_GOOD */
                        -95,  /* SIGNAL_STRENGTH_GREAT */
                });
        return bundle;
    }
}
