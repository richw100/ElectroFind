package com.richwatson.electrofind.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class ElectroFindSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = RouteListScreen(carContext)
}
