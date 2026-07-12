package com.example.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object VentasTable : IntIdTable("ventas") {
    val barberoId = reference("barbero_id", UsuariosTable, onDelete = ReferenceOption.CASCADE)
    val paymentMethod = varchar("payment_method", 20) // 'EFECTIVO', 'TARJETA', etc.
    val amountReceived = double("amount_received")
    val totalAmount = double("total_amount")
    val ghostName = varchar("ghost_name", 100).nullable()
    val date = varchar("date", 50)
    val isGhostSale = bool("is_ghost_sale").default(false)
}

object DetalleVentasTable : IntIdTable("detalle_ventas") {
    val ventaId = reference("venta_id", VentasTable, onDelete = ReferenceOption.CASCADE)
    val itemType = varchar("item_type", 20) // 'service', 'product', 'promotion'
    val itemId = integer("item_id")
    val itemName = varchar("name", 100)
    val price = double("price")
    val quantity = integer("quantity").default(1)
}
