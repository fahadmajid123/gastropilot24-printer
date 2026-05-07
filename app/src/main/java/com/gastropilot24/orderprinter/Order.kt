package com.gastropilot24.orderprinter

import org.json.JSONObject

data class OrderItem(
    val name: String,
    val quantity: Int,
    val price: Double
)

data class Order(
    val id: Int,
    val customerName: String,
    val phone: String,
    val items: List<OrderItem>,
    val total: Double,
    val deliveryType: String,
    val notes: String,
    val createdAt: String
)

fun parseOrders(json: String): List<Order> {
    val orders = mutableListOf<Order>()
    val array = org.json.JSONArray(json)
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val itemsArray = obj.getJSONArray("items")
        val items = mutableListOf<OrderItem>()
        for (j in 0 until itemsArray.length()) {
            val item = itemsArray.getJSONObject(j)
            items.add(OrderItem(
                name = item.getString("name"),
                quantity = item.getInt("quantity"),
                price = item.getDouble("price")
            ))
        }
        orders.add(Order(
            id = obj.getInt("id"),
            customerName = obj.optString("customer_name", "Unbekannt"),
            phone = obj.optString("phone", ""),
            items = items,
            total = obj.getDouble("total"),
            deliveryType = obj.optString("delivery_type", "Abholung"),
            notes = obj.optString("notes", ""),
            createdAt = obj.optString("created_at", "").take(16).replace("T", " ")
        ))
    }
    return orders
}
