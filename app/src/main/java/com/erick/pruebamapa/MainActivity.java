package com.erick.pruebamapa;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.engine.SDKNativeEngine;
import com.here.sdk.core.engine.SDKOptions;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapview.MapError;
import com.here.sdk.mapview.MapMeasure;
import com.here.sdk.mapview.MapScene;
import com.here.sdk.mapview.MapScheme;
import com.here.sdk.mapview.MapView;


import java.util.Locale;

public class MainActivity extends AppCompatActivity implements PlatformPositioningProvider.PlatformLocationListener{

    private static final int REQUEST_INTERNET_PERMISSION = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 101;
    private MapView mapView;
    private LocationManager locationManager;
    private PlatformPositioningProvider platformPositioningProvider;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Siempre se debe inicializar el sdk antes del contentView si no la app peta
        initializeHERESDK();
        setContentView(R.layout.activity_main);
        context = this;

        // Creamos la instancia del mapa
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        loadMapScene();

        // Solicitar permisos de internet y de localización
        requestInternetPermission();
        requestLocationPermission();

        // Creamos una escena y ponemos el diseño que se ocupe
        MapScheme mapScheme = MapScheme.NORMAL_DAY;

        // Ocupamos esto para que se cargue la escena
        mapView.getMapScene().loadScene(mapScheme, new MapScene.LoadSceneCallback(){
            @Override
            public void onLoadScene(@Nullable MapError mapError) {
                if (mapError == null) {
                    // ...
                } else {

                }
            }
        });

        // Esto limita los fps en los que se ve el mapa
        mapView.setFrameRate(60);



        platformPositioningProvider = new PlatformPositioningProvider(context);
        platformPositioningProvider.startLocating(new PlatformPositioningProvider.PlatformLocationListener() {
            @Override
            public void onLocationUpdated(android.location.Location location) {
                // Aquí puedes acceder a la ubicación actualizada
                double latitud = location.getLatitude();
                double longitud = location.getLongitude();
                double altitud = location.getAltitude();

                // Puedes mostrar la ubicación en el mapa o realizar otras operaciones
                GeoCoordinates geoCoordinates = new GeoCoordinates(latitud, longitud);
                mapView.getCamera().lookAt(geoCoordinates);
            }
        });


    }



    // Método que se pasa al oncreate
    private void initializeHERESDK() {
        // Ingresamos las credenciales que nos da HERE
        String accessKeyID = "fGLXo2jToDFFBWr2LiqErg";
        String accessKeySecret = "4QQKkB8QAd08HePdpq-Fc6RPomrsK0IK0xSM2hiGy6cNfzVjIzIrMMaOZmQnvGCESk-VaElTQ3oYxWHu2SmbPQ";
        SDKOptions options = new SDKOptions(accessKeyID, accessKeySecret);
        try {
            Context context = this;
            SDKNativeEngine.makeSharedInstance(context, options);
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of HERE SDK failed: " + e.error.name());
        }
    }

    private void disposeHERESDK() {
        // Free HERE SDK resources before the application shuts down.
        // Usually, this should be called only on application termination.
        // Afterwards, the HERE SDK is no longer usable unless it is initialized again.
        SDKNativeEngine sdkNativeEngine = SDKNativeEngine.getSharedInstance();
        if (sdkNativeEngine != null) {
            sdkNativeEngine.dispose();
            // For safety reasons, we explicitly set the shared instance to null to avoid situations,
            // where a disposed instance is accidentally reused.
            SDKNativeEngine.setSharedInstance(null);
        }
    }

    private void loadMapScene() {
        // Load a scene from the HERE SDK to render the map with a map scheme.
        mapView.getMapScene().loadScene(MapScheme.NORMAL_DAY, new MapScene.LoadSceneCallback() {
            @Override
            public void onLoadScene(@Nullable MapError mapError) {
                if (mapError == null) {
                    double distanceInMeters = 1000 * 10;
                    MapMeasure mapMeasureZoom = new MapMeasure(MapMeasure.Kind.DISTANCE, distanceInMeters);
                    mapView.getCamera().lookAt(
                            new GeoCoordinates(52.530932, 13.384915), mapMeasureZoom);
                } else {
                    Log.d("loadMapScene()", "Loading map failed: mapError: " + mapError.name());
                }
            }
        });
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mapView.onResume();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        disposeHERESDK();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        mapView.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    // Método para solicitar el permiso de Internet
    private void requestInternetPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, REQUEST_INTERNET_PERMISSION);
        } else {
            // El permiso ya está concedido
        }
    }

    // Método para solicitar el permiso de localización
    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            // El permiso ya está concedido
        }
    }

    // Método para manejar las respuestas de las solicitudes de permisos
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_INTERNET_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // El permiso de Internet fue concedido
                } else {
                    // El permiso de Internet fue denegado
                }
                break;
            case REQUEST_LOCATION_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // El permiso de localización fue concedido
                } else {
                    // El permiso de localización fue denegado
                }
                break;
        }
    }

    @Override
    public void onLocationUpdated(android.location.Location location) {

    }
}