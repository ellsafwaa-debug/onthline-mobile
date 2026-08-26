package com.onthline.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * بلجن Capacitor مخصص: بيجيب إحداثيات GPS حقيقية *وفي نفس الوقت* بيتأكد
 * إذا كانت جايه من تطبيق "موقع وهمي" (Fake GPS) ولا لأ، عن طريق
 * Location.isFromMockProvider() بتاعة أندرويد نفسه.
 *
 * ليه مش استخدمنا @capacitor/geolocation بس؟ لأنها بترجع lat/lng بس، من
 * غير علم إذا كانت حقيقية أو مزيفة. الفحصين (الإحداثيات + هل هي وهمية)
 * لازم يجوا من نفس الـ Location fix بالظبط، مش من طلبين منفصلين، عشان
 * منقعش في حالة سباق (race condition) بين الاتنين.
 */
@CapacitorPlugin(
    name = "OnthlineLocation",
    permissions = {
        @Permission(
            strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
            alias = "location"
        )
    }
)
public class OnthlineLocationPlugin extends Plugin {

    @PluginMethod
    public void getFix(PluginCall call) {
        if (getPermissionState("location") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "locationPermsCallback");
            return;
        }
        fetchFix(call);
    }

    @PermissionCallback
    private void locationPermsCallback(PluginCall call) {
        if (getPermissionState("location") == com.getcapacitor.PermissionState.GRANTED) {
            fetchFix(call);
        } else {
            call.reject("Location permission denied");
        }
    }

    private void fetchFix(PluginCall call) {
        LocationManager lm = (LocationManager) getContext().getSystemService(android.content.Context.LOCATION_SERVICE);

        if (lm == null) {
            call.reject("Location service unavailable");
            return;
        }

        boolean hasFine = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasFine) {
            call.reject("Location permission not granted");
            return;
        }

        // لو عندنا آخر موقع معروف وحديث (أقل من 30 ثانية)، رجّعه فورًا —
        // أسرع بكتير من ما نستنى GPS يلتقط إشارة جديدة من الصفر.
        Location best = null;
        try {
            for (String provider : new String[] { LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER }) {
                if (!lm.isProviderEnabled(provider)) continue;
                Location loc = lm.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                    best = loc;
                }
            }
        } catch (SecurityException e) {
            call.reject("Location permission not granted");
            return;
        }

        long thirtySecondsAgo = System.currentTimeMillis() - 30000;
        if (best != null && best.getTime() > thirtySecondsAgo) {
            resolveWithLocation(call, best);
            return;
        }

        // مفيش موقع حديث كفاية — نطلب تحديث جديد من GPS مباشرة (بحد أقصى مهلة
        // 12 ثانية، بعدها لو مفيش رد بنرجع بآخر حاجة عندنا أو بفشل واضح).
        final Location[] holder = new Location[] { best };
        final boolean[] done = new boolean[] { false };

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (done[0]) return;
                done[0] = true;
                lm.removeUpdates(this);
                resolveWithLocation(call, location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, listener, Looper.getMainLooper());
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, listener, Looper.getMainLooper());
            } else {
                call.reject("No location provider enabled");
                return;
            }
        } catch (SecurityException e) {
            call.reject("Location permission not granted");
            return;
        }

        getActivity().getWindow().getDecorView().postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            lm.removeUpdates(listener);
            if (holder[0] != null) {
                resolveWithLocation(call, holder[0]);
            } else {
                call.reject("Timed out waiting for a GPS fix");
            }
        }, 12000);
    }

    private void resolveWithLocation(PluginCall call, Location location) {
        JSObject ret = new JSObject();
        ret.put("lat", location.getLatitude());
        ret.put("lng", location.getLongitude());
        ret.put("accuracy", location.getAccuracy());
        ret.put("provider", location.getProvider());
        //noinspection deprecation — isFromMockProvider() فضلت شغالة في كل نسخ
        // أندرويد لحد دلوقتي، وهي الطريقة العملية الوحيدة اللي بتغطي كل
        // الإصدارات (بديلها isMock() متاح بس من Android 12+).
        ret.put("isMock", location.isFromMockProvider());
        call.resolve(ret);
    }
}
