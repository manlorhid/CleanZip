package com.cleanzip.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cleanzip.app.billing.BillingManager

/** Diálogo para quitar anuncios o hacer una donación. */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val products = BillingManager.products
    fun price(id: String) = products[id]?.oneTimePurchaseOfferDetails?.formattedPrice

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Apoya CleanZip") },
        text = {
            Column {
                Text("Quitar anuncios", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (BillingManager.adsRemoved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Ya has quitado los anuncios. ¡Gracias!")
                    }
                } else {
                    Button(
                        onClick = { activity?.let { BillingManager.buy(it, BillingManager.REMOVE_ADS) } },
                        enabled = price(BillingManager.REMOVE_ADS) != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quitar anuncios" + (price(BillingManager.REMOVE_ADS)?.let { " · $it" } ?: ""))
                    }
                    Text(
                        "Pago único. Se mantiene si reinstalas la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text("Invítame a un café", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Una donación ayuda a mantener la app. No desbloquea nada extra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BillingManager.DONATIONS.forEach { id ->
                        val p = price(id)
                        OutlinedButton(
                            onClick = { activity?.let { BillingManager.buy(it, id) } },
                            enabled = p != null,
                            modifier = Modifier.weight(1f)
                        ) { Text(p ?: "—", maxLines = 1) }
                    }
                }

                if (products.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Las compras solo están disponibles si instalas la app desde Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
