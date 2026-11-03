package za.co.shopsmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShopSmartApp() }
    }
}

private data class ShoppingItem(val name: String, val priceCents: Int)

private fun formatRand(cents: Int): String {
    val amount = NumberFormat.getNumberInstance(Locale("en", "ZA")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(cents / 100.0)
    return "R $amount"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopSmartApp() {
    val shoppingItems = remember {
        mutableStateListOf(
            ShoppingItem("Milk 2L", 3299),
            ShoppingItem("Brown bread", 1899),
            ShoppingItem("Eggs 18 pack", 5499)
        )
    }
    val totalCents = shoppingItems.sumOf { it.priceCents }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("ShopSmart ZA", fontWeight = FontWeight.Bold) }) }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Your shopping, smarter.", style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Estimated till total")
                        Text(formatRand(totalCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("${shoppingItems.size} items in your list")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { shoppingItems.add(ShoppingItem("New item", 0)) }) {
                        Text("Add sample item")
                    }
                    OutlinedButton(onClick = { shoppingItems.clear() }) {
                        Text("Clear list")
                    }
                }
                Text("Shopping list", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(shoppingItems) { index, item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Medium)
                                    Text(formatRand(item.priceCents), style = MaterialTheme.typography.bodyMedium)
                                }
                                OutlinedButton(onClick = { shoppingItems.removeAt(index) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
                Text(
                    "Starter build: core shopping screen. Product editing, offline database, pamphlet scanning and deal comparisons are planned next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
