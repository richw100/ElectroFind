package com.richwatson.electrofind.car

import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row

// Splits `rows` across pages sized to the host's current driving/parked row
// limit (ConstraintManager.CONTENT_LIMIT_TYPE_LIST) — while driving this is
// much smaller than while parked, and exceeding it makes the host refuse to
// render the list interactively.
internal fun CarContext.pagedListTemplate(
    title: String,
    rows: List<Row>,
    page: Int,
    headerAction: Action = Action.BACK,
    onPageChange: (Int) -> Unit
): ListTemplate {
    val maxItems = getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

    if (rows.size <= maxItems) {
        val listBuilder = ItemList.Builder()
        rows.forEach { listBuilder.addItem(it) }
        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(headerAction)
            .setSingleList(listBuilder.build())
            .build()
    }

    val perPage = maxOf(1, maxItems - 2) // reserve Previous/Next rows
    val totalPages = (rows.size + perPage - 1) / perPage
    val p = page.coerceIn(0, totalPages - 1)
    val listBuilder = ItemList.Builder()
    if (p > 0) {
        listBuilder.addItem(Row.Builder().setTitle("← Previous")
            .setOnClickListener { onPageChange(p - 1) }.build())
    }
    rows.drop(p * perPage).take(perPage).forEach { listBuilder.addItem(it) }
    if (p < totalPages - 1) {
        listBuilder.addItem(Row.Builder().setTitle("Next →")
            .setOnClickListener { onPageChange(p + 1) }.build())
    }
    return ListTemplate.Builder()
        .setTitle("$title · ${p + 1}/$totalPages")
        .setHeaderAction(headerAction)
        .setSingleList(listBuilder.build())
        .build()
}
