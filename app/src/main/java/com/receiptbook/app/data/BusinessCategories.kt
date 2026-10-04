package com.receiptbook.app.data

import com.receiptbook.app.R

/** A predefined category with a stable id (stored/synced) and its translated label resource. */
data class Category(val id: String, val labelRes: Int)

/**
 * What does the business make or sell? Stored as a stable English id (so it syncs and filters
 * consistently across languages/devices) and only translated for display - see Loc-style usage
 * via BusinessFields.label(id, loc). "other" pairs with Business.fieldOfBusinessOther, which holds
 * the person's own typed-in description when none of these fit.
 */
object BusinessFields {
    const val OTHER = "other"

    val all = listOf(
        Category("food", R.string.field_food),
        Category("engineering", R.string.field_engineering),
        Category("manufacturing", R.string.field_manufacturing),
        Category("it", R.string.field_it),
        Category("retail", R.string.field_retail),
        Category("construction", R.string.field_construction),
        Category("textile", R.string.field_textile),
        Category("agriculture", R.string.field_agriculture),
        Category("healthcare", R.string.field_healthcare),
        Category("education", R.string.field_education),
        Category("automotive", R.string.field_automotive),
        Category("electronics", R.string.field_electronics),
        Category("furniture", R.string.field_furniture),
        Category("hardware", R.string.field_hardware),
        Category("pharmaceuticals", R.string.field_pharmaceuticals),
        Category("transport", R.string.field_transport),
        Category("real_estate", R.string.field_real_estate),
        Category(OTHER, R.string.field_other),
    )

    fun labelRes(id: String): Int = all.firstOrNull { it.id == id }?.labelRes ?: R.string.field_other
}

/** How does the business operate? A shorter, fixed list - no free-text "other" here since these
 * four categories are meant to be exhaustive for how a small business actually trades. */
object BusinessNatures {
    val all = listOf(
        Category("manufacturing", R.string.nature_manufacturing),
        Category("retail", R.string.nature_retail),
        Category("marketing_booking", R.string.nature_marketing_booking),
        Category("distribution", R.string.nature_distribution),
    )

    fun labelRes(id: String): Int = all.firstOrNull { it.id == id }?.labelRes ?: R.string.nature_retail
}