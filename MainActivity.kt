package com.example.uberclone

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var database: DatabaseReference

    private var myMarker: Marker? = null
    private var driverMarker: Marker? = null
    private var routeLine: Polyline? = null

    private var userLocation: GeoPoint? = null

    private var currentDistance = 0.0
    private var currentFare = 0.0

    private val LOCATION_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        setContentView(R.layout.activity_map)

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        database = FirebaseDatabase.getInstance().reference
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        val requestBtn = findViewById<Button>(R.id.requestRide)
        val completeBtn = findViewById<Button>(R.id.completeRide)

        requestBtn.setOnClickListener {

            val uid = FirebaseAuth.getInstance().uid ?: return@setOnClickListener

            database.child("RideRequests")
                .child(uid)
                .get()
                .addOnSuccessListener { snapshot ->

                    if (snapshot.exists()) {
                        cancelRide()
                        requestBtn.text = "Request Ride 🚖"
                    } else {
                        requestRide()
                        requestBtn.text = "Cancel Ride ❌"
                    }
                }
        }

        completeBtn.setOnClickListener {
            completeRide()
        }

        // Permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
        } else {
            startLocationUpdates()
        }

        listenForDriver()
    }

    // 🔐 Permission
    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    // 🚖 REQUEST RIDE
    private fun requestRide() {

        val userId = FirebaseAuth.getInstance().uid ?: return

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locationClient.lastLocation.addOnSuccessListener { location ->

            if (location != null) {

                val data = HashMap<String, Any>()
                data["lat"] = location.latitude
                data["lng"] = location.longitude
                data["status"] = "waiting"
                data["driverId"] = ""

                database.child("RideRequests")
                    .child(userId)
                    .setValue(data)

                Toast.makeText(this, "Searching Driver 🚗...", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({

                    val driverId = "driver001"

                    database.child("RideRequests")
                        .child(userId)
                        .child("status")
                        .setValue("accepted")

                    database.child("RideRequests")
                        .child(userId)
                        .child("driverId")
                        .setValue(driverId)

                    simulateDriverMovement(driverId)

                    Toast.makeText(this, "Driver Found 🚗", Toast.LENGTH_SHORT).show()

                }, 3000)
            }
        }
    }

    // ❌ Cancel Ride
    private fun cancelRide() {

        val userId = FirebaseAuth.getInstance().uid ?: return

        database.child("RideRequests")
            .child(userId)
            .removeValue()

        driverMarker?.let { map.overlays.remove(it) }
        routeLine?.let { map.overlays.remove(it) }

        Toast.makeText(this, "Ride Cancelled ❌", Toast.LENGTH_SHORT).show()
    }

    // 📍 USER LOCATION
    private fun startLocationUpdates() {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                for (loc in result.locations) {

                    val geoPoint = GeoPoint(loc.latitude, loc.longitude)
                    userLocation = geoPoint

                    map.controller.setZoom(18.0)
                    map.controller.setCenter(geoPoint)

                    myMarker?.let { map.overlays.remove(it) }

                    myMarker = Marker(map)
                    myMarker!!.position = geoPoint
                    myMarker!!.title = "You"

                    map.overlays.add(myMarker)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locationClient.requestLocationUpdates(
            request,
            callback,
            mainLooper
        )
    }

    // 🚗 LISTEN DRIVER STATUS
    private fun listenForDriver() {

        val userId = FirebaseAuth.getInstance().uid ?: return

        database.child("RideRequests")
            .child(userId)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    if (snapshot.exists()) {

                        val status = snapshot.child("status").value.toString()

                        if (status == "accepted") {
                            val driverId = snapshot.child("driverId").value.toString()
                            listenDriverLocation(driverId)
                        }

                        if (status == "arrived") {
                            Toast.makeText(
                                this@MainActivity,
                                "Driver Arrived 🚗",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        if (status == "completed") {
                            Toast.makeText(
                                this@MainActivity,
                                "Ride Completed 🎉",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // 🚗 DRIVER LOCATION
    private fun listenDriverLocation(driverId: String) {

        database.child("Drivers")
            .child(driverId)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    if (snapshot.exists()) {

                        val lat = snapshot.child("lat").value as? Double ?: return
                        val lng = snapshot.child("lng").value as? Double ?: return

                        val driverPoint = GeoPoint(lat, lng)

                        driverMarker?.let { map.overlays.remove(it) }

                        driverMarker = Marker(map)
                        driverMarker!!.position = driverPoint
                        driverMarker!!.title = "Driver 🚗"

                        map.overlays.add(driverMarker)

                        routeLine?.let { map.overlays.remove(it) }

                        userLocation?.let {

                            routeLine = Polyline()
                            routeLine!!.setPoints(listOf(it, driverPoint))
                            map.overlays.add(routeLine)

                            val distance = calculateDistance(
                                it.latitude, it.longitude,
                                lat, lng
                            )

                            currentDistance = distance
                            currentFare = distance * 20

                            val eta = (distance / 40) * 60

                            Toast.makeText(
                                this@MainActivity,
                                "Distance: %.2f km\nETA: %.1f min\nFare: ₹%.2f"
                                    .format(distance, eta, currentFare),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // 🚗 DRIVER MOVEMENT
    private fun simulateDriverMovement(driverId: String) {

        var driverLat = 19.081
        var driverLng = 72.883

        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {

                userLocation?.let { user ->

                    driverLat += (user.latitude - driverLat) * 0.05
                    driverLng += (user.longitude - driverLng) * 0.05

                    val data = HashMap<String, Any>()
                    data["lat"] = driverLat
                    data["lng"] = driverLng

                    database.child("Drivers")
                        .child(driverId)
                        .setValue(data)

                    val distance = calculateDistance(
                        driverLat, driverLng,
                        user.latitude, user.longitude
                    )

                    if (distance < 0.1) {
                        database.child("RideRequests")
                            .child(FirebaseAuth.getInstance().uid!!)
                            .child("status")
                            .setValue("arrived")
                    }

                    handler.postDelayed(this, 2000)
                }
            }
        }

        handler.post(runnable)
    }

    // ✅ COMPLETE RIDE
    private fun completeRide() {

        val userId = FirebaseAuth.getInstance().uid ?: return

        database.child("RideRequests")
            .child(userId)
            .child("status")
            .setValue("completed")

        Toast.makeText(
            this,
            "Ride Completed 🎉\nFare: ₹%.2f".format(currentFare),
            Toast.LENGTH_LONG
        ).show()

        driverMarker?.let { map.overlays.remove(it) }
        routeLine?.let { map.overlays.remove(it) }
    }

    // 📏 DISTANCE
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {

        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] / 1000.0
    }
}