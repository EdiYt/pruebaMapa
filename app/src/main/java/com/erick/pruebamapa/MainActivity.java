package com.erick.pruebamapa;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.here.sdk.core.Color;
import com.here.sdk.core.GeoCircle;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoCoordinatesUpdate;
import com.here.sdk.core.GeoOrientationUpdate;
import com.here.sdk.core.GeoPolygon;
import com.here.sdk.core.LanguageCode;
import com.here.sdk.core.engine.SDKNativeEngine;
import com.here.sdk.core.engine.SDKOptions;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapview.LocationIndicator;
import com.here.sdk.mapview.MapCamera;
import com.here.sdk.mapview.MapCameraAnimation;
import com.here.sdk.mapview.MapCameraAnimationFactory;
import com.here.sdk.mapview.MapMarker;
import com.here.sdk.mapview.MapPolygon;
import com.here.sdk.mapview.MapError;
import com.here.sdk.mapview.MapMeasure;
import com.here.sdk.mapview.MapScene;
import com.here.sdk.mapview.MapScheme;
import com.here.sdk.mapview.MapView;
import com.here.sdk.search.AddressQuery;
import com.here.sdk.search.Place;
import com.here.sdk.search.SearchCallback;
import com.here.sdk.search.SearchEngine;
import com.here.sdk.search.SearchError;
import com.here.sdk.search.SearchOptions;
import com.here.time.Duration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PlatformPositioningProvider.PlatformLocationListener {

    private static final int REQUEST_INTERNET_PERMISSION = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 101;

    private MapView mapView;
    private MapCamera mapCamera;
    private PlatformPositioningProvider positioningProvider;
    private LocationIndicator currentLocationIndicator;
    private ImageButton botonBuscar, regresarUbicacion, botonLimite, botonEliminar, botonRadio, buscarDireccion, botonRuta, boton_ruta, botonLimpiar;
    private EditText cajaBusqueda, textoRadio, input_coordenada1, input_coordenada2;
    private SearchExample searchExample;
    private LinearLayout layoutRadio, linearLayout, rutaLayout;
    private MapPolygon mapCircle;
    private MapScene mapScene;
    private RoutingExample routingExample;
    private GeoCoordinates coordenada1, coordenada2;
    private SearchEngine searchEngine;
    private List<MapMarker> mapMarkerList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Siempre se debe inicializar el sdk antes del contentView si no la app peta
        initializeHERESDK();
        setContentView(R.layout.activity_main);

        // Creamos la instancia del mapa
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        searchExample = new SearchExample(MainActivity.this, mapView);

        tiltMap();
        try {
            searchEngine = new SearchEngine();
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of SearchEngine failed: " + e.error.name());
        }
        loadMapScene();

        // Solicitar permisos de internet y de localización
        requestInternetPermission();
        requestLocationPermission();

        mapScene = mapView.getMapScene();

        if (!isGPSEnabled()) {
            // Si el GPS está apagado, muestra un mensaje o realiza las acciones necesarias para que prenda el GPS
            showGPSDisabledDialog();
        } else {
            // El GPS está encendido
        }

        // Initialize positioning provider
        positioningProvider = new PlatformPositioningProvider(this);

        cajaBusqueda = findViewById(R.id.cajaBusqueda);
        botonBuscar = findViewById(R.id.botonBuscar);

        botonBuscar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String direccion = cajaBusqueda.getText().toString();
                if (!direccion.isEmpty()) {
                    // Llamar al método de búsqueda en la clase SearchExample
                    searchExample.geocodeAddressAtLocation(direccion, mapView.getCamera().getState().targetCoordinates);
                } else {
                    Toast.makeText(MainActivity.this, "Por favor, ingrese una dirección", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mapCamera = mapView.getCamera();
        regresarUbicacion = findViewById(R.id.regresarUbicacion);

        regresarUbicacion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moverCamaraAUbicacionActual();
            }
        });

        layoutRadio = findViewById(R.id.layoutRadio);
        botonRadio = findViewById(R.id.botonRadio);
        textoRadio = findViewById(R.id.textoRadio);
        botonEliminar = findViewById(R.id.botonEliminar);
        botonLimite = findViewById(R.id.botonLimite);

        botonRadio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutRadio.getVisibility() == View.GONE) {
                    layoutRadio.setVisibility(View.VISIBLE);
                    ocultarMenuRuta();
                    ocultarBusquedaDirecciones();
                    ocultarOtrosMenus(botonRadio);
                    ocultarBusquedaDirecciones();
                    ocultarMenuRuta();
                } else {
                    layoutRadio.setVisibility(View.GONE);
                }
            }
        });

        botonLimite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String textoRad = textoRadio.getText().toString();
                if (!textoRad.isEmpty()) {
                    double radio = Double.parseDouble(textoRad);
                    if (radio <= 5000) {
                        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        if (locationManager != null && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (lastKnownLocation != null) {
                                GeoCoordinates userCoordinates = new GeoCoordinates(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                if (mapScene != null) {
                                    showMapCircle(userCoordinates, (float) radio);
                                } else {
                                    // Manejar el caso en que mapScene sea nulo
                                }
                            }
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "El radio máximo permitido es de 5000 metros.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Manejar el caso en que el EditText esté vacío
                    Toast.makeText(MainActivity.this, "Por favor ingrese un valor de radio.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        botonEliminar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mapCircle != null) {
                    mapScene.removeMapPolygon(mapCircle);
                    mapCircle = null;
                }
            }
        });

        buscarDireccion = findViewById(R.id.buscarDireccion);
        linearLayout = findViewById(R.id.linearLayout);

        buscarDireccion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (linearLayout.getVisibility() == View.GONE) {
                    linearLayout.setVisibility(View.VISIBLE);
                    ocultarOtrosMenus(buscarDireccion);
                    ocultarMenuRadio();
                    ocultarMenuRuta();
                } else {
                    linearLayout.setVisibility(View.GONE);
                }
            }
        });

        rutaLayout = findViewById(R.id.rutaLayout);
        botonRuta = findViewById(R.id.botonRuta);


        botonRuta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rutaLayout.getVisibility() == View.GONE && boton_ruta.getVisibility() == View.GONE) {
                    rutaLayout.setVisibility(View.VISIBLE);
                    boton_ruta.setVisibility(View.VISIBLE);
                    ocultarBusquedaDirecciones();
                    ocultarOtrosMenus(botonRuta);
                    ocultarBusquedaDirecciones();
                    ocultarMenuRadio();
                } else {
                    rutaLayout.setVisibility(View.GONE);
                    boton_ruta.setVisibility(View.GONE);
                }
            }
        });

        input_coordenada1 = findViewById(R.id.coordendas_iniciales_input);
        input_coordenada2 = findViewById(R.id.coordendas_finales_input);
        boton_ruta = findViewById(R.id.btn_input_coordenadas);
        boton_ruta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String direccion1 = input_coordenada1.getText().toString();
                String direccion2 = input_coordenada2.getText().toString();
                if (!direccion1.isEmpty() && !direccion2.isEmpty()) {
                    getCoordenada1(direccion1, mapView.getCamera().getState().targetCoordinates);
                    getCoordenada2(direccion2, mapView.getCamera().getState().targetCoordinates);
                } else {
                    Toast.makeText(MainActivity.this, "Por favor, ingrese ambas direcciones", Toast.LENGTH_SHORT).show();
                }
            }
        });

        botonLimpiar = findViewById(R.id.botonLimpiar);
        botonLimpiar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eliminarMarcadores();
                limpiarMapa();
            }
        });
    }

    private void eliminarMarcadores() {
        if (mapView != null && mapView.getMapScene() != null) {
            MapScene mapScene = mapView.getMapScene();
            if (mapMarkerList != null) {
                for (MapMarker mapMarker : mapMarkerList) {
                    mapScene.removeMapMarker(mapMarker);
                }
                mapMarkerList.clear();
            }
        }
    }


    private void limpiarMapa() {
        // Limpiar el círculo del mapa
        if (mapCircle != null) {
            mapScene.removeMapPolygon(mapCircle);
            mapCircle = null;
        }

        // Limpiar la ruta del mapa
        if (routingExample != null) {
            routingExample.clearRoute();
        }
    }

    private void ocultarBusquedaDirecciones() {
        linearLayout.setVisibility(View.GONE);
        cajaBusqueda.setText("");
    }

    private void ocultarOtrosMenus(View botonSeleccionado) {
        if (botonSeleccionado != botonRadio) {
            layoutRadio.setVisibility(View.GONE);
            textoRadio.setText("");
            if (mapCircle != null) {
                mapScene.removeMapPolygon(mapCircle);
                mapCircle = null;
            }
        }
        if (botonSeleccionado != botonRuta) {
            rutaLayout.setVisibility(View.GONE);
            boton_ruta.setVisibility(View.GONE);
            input_coordenada1.setText("");
            input_coordenada2.setText("");
            // Aquí puedes agregar código para limpiar cualquier otra cosa relacionada con la ruta
        }
    }
    private void ocultarMenuRadio() {
        layoutRadio.setVisibility(View.GONE);
        textoRadio.setText("");
        if (mapCircle != null) {
            mapScene.removeMapPolygon(mapCircle);
            mapCircle = null;
        }
    }

    private void ocultarMenuRuta() {
        rutaLayout.setVisibility(View.GONE);
        boton_ruta.setVisibility(View.GONE);
        input_coordenada1.setText("");
        input_coordenada2.setText("");
        // Aquí puedes agregar código adicional para limpiar cualquier cosa relacionada con la ruta
    }

    private final SearchCallback addressSearchCallback = new SearchCallback() {
        @Override
        public void onSearchCompleted(@Nullable SearchError searchError, @Nullable List<Place> list) {
            if (searchError != null) {
                showDialog("Reverse geocoding", "Error: " + searchError.toString());
                return;
            }

            // If error is null, list is guaranteed to be not empty.
            showDialog("Información de la ubicación:", list.get(0).getAddress().addressText);
        }
    };

    private final SearchCallback addresssSearchCallback = new SearchCallback() {
        @Override
        public void onSearchCompleted(@Nullable SearchError searchError, @Nullable List<Place> list) {
            if (searchError != null) {
                showDialog("Reverse geocoding", "Error: " + searchError.toString());
                return;
            }

            // If error is null, list is guaranteed to be not empty.
            input_coordenada1.setText(list.get(0).getAddress().addressText);
        }
    };

    public void getCoordenada1(String queryString, GeoCoordinates geoCoordinates) {
        AddressQuery query = new AddressQuery(queryString, geoCoordinates);
        SearchOptions options = new SearchOptions();
        options.languageCode = LanguageCode.DE_DE;
        options.maxItems = 1;

        searchEngine.search(query, options, Coordenada1SearchCallback);
    }

    private void showDialog(String title, String message) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.show();
    }

    private final SearchCallback Coordenada1SearchCallback = new SearchCallback() {
        @Override
        public void onSearchCompleted(SearchError searchError, List<Place> list) {
            if (searchError != null) {
                showDialog("Geocoding", "Error: " + searchError.toString());
                return;
            }

            if (list.isEmpty()) {
                showDialog("Geocoding result", "No se encontraron resultados");
                return;
            }

            Place geocodingResult = list.get(0); // Obtener el primer resultado
            GeoCoordinates geoCoordinates = geocodingResult.getGeoCoordinates();
            coordenada1 = geoCoordinates; // Guardar las coordenadas obtenidas en la variable global
            addRouteIfBothCoordinatesAreAvailable(); // Llamar a la función para agregar la ruta si ambas coordenadas están disponibles
        }
    };

    private void addRouteIfBothCoordinatesAreAvailable() {
        // Verificar si ambas coordenadas están disponibles
        if (coordenada1 != null && coordenada2 != null) {
            // Ambas coordenadas están disponibles, llamar al método addRoute
            routingExample.addRoute(coordenada1, coordenada2);
            flyTo(coordenada1);
        }
    }

    private void flyTo(GeoCoordinates geoCoordinates) {
        GeoCoordinatesUpdate geoCoordinatesUpdate = new GeoCoordinatesUpdate(geoCoordinates);
        double bowFactor = 1;
        MapCameraAnimation animation =
                MapCameraAnimationFactory.flyTo(geoCoordinatesUpdate, bowFactor, Duration.ofSeconds(3));
        mapCamera.startAnimation(animation);
    }

    public void getCoordenada2(String queryString, GeoCoordinates geoCoordinates) {

        AddressQuery query = new AddressQuery(queryString, geoCoordinates);
        SearchOptions options = new SearchOptions();
        options.languageCode = LanguageCode.DE_DE;
        options.maxItems = 1;

        searchEngine.search(query, options, Coordenada2SearchCallback);
    }

    private final SearchCallback Coordenada2SearchCallback = new SearchCallback() {
        @Override
        public void onSearchCompleted(SearchError searchError, List<Place> list) {
            if (searchError != null) {
                showDialog("Geocoding", "Error: " + searchError.toString());
                return;
            }

            if (list.isEmpty()) {
                showDialog("Geocoding result", "No se encontraron resultados");
                return;
            }

            Place geocodingResult = list.get(0); // Obtener el primer resultado
            GeoCoordinates geoCoordinates = geocodingResult.getGeoCoordinates();
            coordenada2 = geoCoordinates; // Guardar las coordenadas obtenidas en la variable global
            addRouteIfBothCoordinatesAreAvailable(); // Llamar a la función para agregar la ruta si ambas coordenadas están disponibles
        }
    };

    public void showMapCircle(GeoCoordinates centerCoordinates, float radius) {
        // Primero verifica si hay un MapPolygon existente y lo elimina
        if (mapCircle != null) {
            mapScene.removeMapPolygon(mapCircle);
        }

        // Crea el nuevo MapPolygon
        mapCircle = createMapCircle(centerCoordinates, radius);

        // Agrega el nuevo MapPolygon al mapScene
        mapScene.addMapPolygon(mapCircle);
    }

    private MapPolygon createMapCircle(GeoCoordinates centerCoordinates, float radiusInMeters) {
        GeoCircle geoCircle = new GeoCircle(centerCoordinates, radiusInMeters);

        GeoPolygon geoPolygon = new GeoPolygon(geoCircle);
        Color fillColor = Color.valueOf(255, 165, 0, 0.5f); // Naranja en RGBA
        MapPolygon mapPolygon = new MapPolygon(geoPolygon, fillColor);

        return mapPolygon;
    }


    private void showGPSDisabledDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GPS desactivado");
        builder.setMessage("El GPS está desactivado. ¿Desea activarlo?");
        builder.setPositiveButton("Activar GPS", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Abrimos la configuración de ubicación del dispositivo para que el usuario la active
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private boolean isGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    private void moverCamaraAUbicacionActual() {
        // Verifica si tienes permisos para acceder a la ubicación del usuario
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                GeoCoordinates userCoordinates = new GeoCoordinates(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                double distanceInMeters = 1000 * 0.5; // Ajusta la distancia según tus preferencias
                MapMeasure mapMeasureZoom = new MapMeasure(MapMeasure.Kind.DISTANCE, distanceInMeters);
                mapView.getCamera().lookAt(userCoordinates, mapMeasureZoom);
            }
        }
    }



    public void searchExampleButtonClicked(View view) {
        searchExample.onSearchButtonClicked();
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
        // Verifica si tienes permisos para acceder a la ubicación del usuario
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Obtén la última ubicación conocida del usuario
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                // Si se encuentra una ubicación conocida, mueve la cámara del mapa a esa ubicación
                GeoCoordinates userCoordinates = new GeoCoordinates(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                routingExample = new RoutingExample(MainActivity.this, mapView,userCoordinates);
                //mapView.getCamera().lookAt(userCoordinates);
            }
        }
        // Verifica si es después de las 8:00 p.m. y antes de las 6:00 a.m.
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour >= 20 || hour < 6) {
            // Carga la escena del mapa con el esquema MapScheme.NORMAL_NIGHT
            mapView.getMapScene().loadScene(MapScheme.NORMAL_NIGHT, new MapScene.LoadSceneCallback() {
                @Override
                public void onLoadScene(@Nullable MapError mapError) {
                    if (mapError == null) {
                        // No se produjo ningún error al cargar la escena del mapa
                    } else {
                        Log.d("loadMapScene()", "Loading map failed: mapError: " + mapError.name());
                    }
                }
            });
        } else {
            // Carga la escena del mapa con el esquema MapScheme.NORMAL_DAY
            mapView.getMapScene().loadScene(MapScheme.NORMAL_DAY, new MapScene.LoadSceneCallback() {
                @Override
                public void onLoadScene(@Nullable MapError mapError) {
                    if (mapError == null) {
                        // No se produjo ningún error al cargar la escena del mapa
                    } else {
                        Log.d("loadMapScene()", "Loading map failed: mapError: " + mapError.name());
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        // Stop location updates when the activity pauses
        stopLocationUpdates();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        // Start location updates when the activity resumes
        startLocationUpdates();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        disposeHERESDK();
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
            case REQUEST_LOCATION_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // El permiso de Internet fue concedido
                } else {
                    // El permiso de Internet fue denegado
                }
                break;
            // El permiso de localización fue concedido
            // El permiso de localización fue denegado
        }
    }

    private void startLocationUpdates() {
        // Start location updates
        positioningProvider.startLocating(this);
    }

    private void stopLocationUpdates() {
        // Stop location updates
        positioningProvider.stopLocating();
    }

    @Override
    public void onLocationUpdated(Location location) {
        GeoCoordinates userCoordinates = new GeoCoordinates(location.getLatitude(), location.getLongitude());
        addLocationIndicator(userCoordinates, LocationIndicator.IndicatorStyle.PEDESTRIAN);
    }

    private void addLocationIndicator(GeoCoordinates geoCoordinates,
                                      LocationIndicator.IndicatorStyle indicatorStyle) {
        // Elimina el indicador de ubicación actual, si existe
        removeLocationIndicator();

        // Crea un nuevo indicador de ubicación
        LocationIndicator locationIndicator = new LocationIndicator();
        locationIndicator.setLocationIndicatorStyle(indicatorStyle);
        com.here.sdk.core.Location location = new com.here.sdk.core.Location(geoCoordinates);
        location.time = new Date();
        location.bearingInDegrees = getRandom(0, 360);

        locationIndicator.updateLocation(location);

        // Show the indicator on the map view.
        locationIndicator.enable(mapView);

        // Asigna la referencia al nuevo indicador de ubicación
        currentLocationIndicator = locationIndicator;
    }

    private void removeLocationIndicator() {
        // Verifica si hay un indicador de ubicación actual mostrándose y lo elimina
        if (currentLocationIndicator != null) {
            currentLocationIndicator.disable();
            currentLocationIndicator = null;
        }
    }

    private double getRandom(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private void tiltMap() {
        double bearing = mapView.getCamera().getState().orientationAtTarget.bearing;
        double tilt =  60;
        mapView.getCamera().setOrientationAtTarget(new GeoOrientationUpdate(bearing, tilt));
    }

}