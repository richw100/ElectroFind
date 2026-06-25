package com.richwatson.electrofind.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class ValuePickerScreen(
    carContext: CarContext,
    private val title: String,
    private val options: List<Pair<String, Int>>,
    private val currentValue: Int,
    private val onPicked: (Int) -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        options.forEach { (label, value) ->
            val row = Row.Builder()
                .setTitle(if (value == currentValue) "✓ $label" else label)
                .setOnClickListener {
                    onPicked(value)
                    screenManager.pop()
                }
                .build()
            listBuilder.addItem(row)
        }

        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
