package com.example.uberclone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var locationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        locationClient = LocationServices.getFusedLocationProviderClient(this)

        findViewById<Button>(R.id.requestRide).setOnClickListener {
            requestRide()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        getLocation()
    }

    private fun getLocation() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locationClient.lastLocation.addOnSuccessListener {

            val latLng = LatLng(it.latitude, it.longitude)

            map.addMarker(MarkerOptions().position(latLng).title("You"))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    private fun requestRide() {

        locationClient.lastLocation.addOnSuccessListener {

            val ride = HashMap<String, Any>()
            ride["lat"] = it.latitude
            ride["lng"] = it.longitude

            FirebaseDatabase.getInstance().reference
                .child("RideRequests")
                .child(FirebaseAuth.getInstance().uid!!)
                .setValue(ride)

            Toast.makeText(this, "Ride Requested 🚖", Toast.LENGTH_SHORT).show()
        }
    }
}