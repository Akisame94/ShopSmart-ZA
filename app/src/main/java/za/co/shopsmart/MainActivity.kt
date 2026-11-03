package za.co.shopsmart

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

data class Product(
    val id: Long,
    val name: String,
    val priceCents: Int
)

data class ShoppingEntry(
    val id: Long,
    val productId: Long,
    val name: String,
    val priceCents: Int,
    val quantity: Int,
    val bought: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ShopSmartApp()
            }
        }
    }
}

@Composable
fun ShopSmartApp() {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(
            "shopsmart_data",
            Context.MODE_PRIVATE
        )
    }

    val products = remember {
        mutableStateListOf<Product>().apply {
            addAll(loadProducts(context))
        }
    }

    val shopping = remember {
        mutableStateListOf<ShoppingEntry>().apply {
            addAll(loadShopping(context))
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun saveAll() {
        saveProducts(context, products)
        saveShopping(context, shopping)
    }

    fun addToShopping(product: Product) {
        val existing = shopping.indexOfFirst {
            it.productId == product.id && !it.bought
        }

        if (existing >= 0) {
            shopping[existing] =
                shopping[existing].copy(
                    quantity = shopping[existing].quantity + 1
                )
        } else {
            shopping.add(
                ShoppingEntry(
                    id = System.nanoTime(),
                    productId = product.id,
                    name = product.name,
                    priceCents = product.priceCents,
                    quantity = 1,
                    bought = false
                )
            )
        }

        saveAll()
    }

    val estimatedTotal = shopping
        .filter { !it.bought }
        .sumOf { it.priceCents * it.quantity }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "ShopSmart ZA",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Text(
                        "Your smarter shopping companion",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            TabRow(selectedTabIndex = selectedTab) {

                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Shopping List") }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Catalogue") }
                )
            }

            if (selectedTab == 0) {

                Column(Modifier.fillMaxSize()) {

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {

                            Text("Estimated till total")

                            Text(
                                formatRand(estimatedTotal),
                                style = MaterialTheme.typography.headlineLarge
                            )

                            Text(
                                "Only items not yet marked as bought are included."
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Your items",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text("${shopping.size} items")
                    }

                    if (shopping.isEmpty()) {

                        Text(
                            "Your shopping list is empty. Add products from your catalogue.",
                            modifier = Modifier.padding(20.dp)
                        )

                    } else {

                        LazyColumn(Modifier.weight(1f)) {

                            items(shopping, key = { it.id }) { entry ->

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 5.dp
                                        )
                                ) {

                                    Column(Modifier.padding(12.dp)) {

                                        Row(
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Checkbox(
                                                checked = entry.bought,
                                                onCheckedChange = { checked ->

                                                    val index =
                                                        shopping.indexOfFirst {
                                                            it.id == entry.id
                                                        }

                                                    if (index >= 0) {
                                                        shopping[index] =
                                                            entry.copy(
                                                                bought = checked
                                                            )
                                                        saveAll()
                                                    }
                                                }
                                            )

                                            Column(
                                                Modifier.weight(1f)
                                            ) {

                                                Text(
                                                    entry.name,
                                                    style = MaterialTheme.typography.titleMedium
                                                )

                                                Text(
                                                    "${formatRand(entry.priceCents)} each"
                                                )
                                            }

                                            Text(
                                                formatRand(
                                                    entry.priceCents *
                                                        entry.quantity
                                                ),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }

                                        Row(
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Text("Quantity: ${entry.quantity}")

                                            Spacer(Modifier.weight(1f))

                                            TextButton(onClick = {

                                                val index =
                                                    shopping.indexOfFirst {
                                                        it.id == entry.id
                                                    }

                                                if (index >= 0) {

                                                    if (entry.quantity > 1) {
                                                        shopping[index] =
                                                            entry.copy(
                                                                quantity =
                                                                    entry.quantity - 1
                                                            )
                                                    } else {
                                                        shopping.removeAt(index)
                                                    }

                                                    saveAll()
                                                }
                                            }) {
                                                Text("−")
                                            }

                                            TextButton(onClick = {

                                                val index =
                                                    shopping.indexOfFirst {
                                                        it.id == entry.id
                                                    }

                                                if (index >= 0) {
                                                    shopping[index] =
                                                        entry.copy(
                                                            quantity =
                                                                entry.quantity + 1
                                                        )
                                                    saveAll()
                                                }
                                            }) {
                                                Text("+")
                                            }

                                            TextButton(onClick = {
                                                shopping.removeAll {
                                                    it.id == entry.id
                                                }
                                                saveAll()
                                            }) {
                                                Text("Remove")
                                            }
                                        }

                                        if (entry.bought) {
                                            Text(
                                                "Bought",
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } else {

                Column(Modifier.fillMaxSize()) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            "Product Catalogue",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )

                        Button(onClick = {
                            showAddDialog = true
                        }) {
                            Text("Add Product")
                        }
                    }

                    if (products.isEmpty()) {

                        Text(
                            "No products saved yet. Tap Add Product to get started.",
                            modifier = Modifier.padding(20.dp)
                        )

                    } else {

                        LazyColumn {

                            items(products, key = { it.id }) { product ->

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 5.dp
                                        )
                                ) {

                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Column(
                                            Modifier.weight(1f)
                                        ) {

                                            Text(
                                                product.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )

                                            Text(formatRand(product.priceCents))
                                        }

                                        TextButton(onClick = {
                                            addToShopping(product)
                                        }) {
                                            Text("Add to list")
                                        }

                                        TextButton(onClick = {
                                            products.removeAll {
                                                it.id == product.id
                                            }
                                            saveAll()
                                        }) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {

        AddProductDialog(
            onDismiss = {
                showAddDialog = false
            },
            onAdd = { name, price ->

                products.add(
                    Product(
                        id = System.nanoTime(),
                        name = name,
                        priceCents = price
                    )
                )

                saveAll()
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {

    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text("Add a product")
        },

        text = {

            Column {

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = {
                        Text("Price in Rand (e.g. 25.99)")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )

                if (error.isNotEmpty()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },

        confirmButton = {

            Button(onClick = {

                val parsed = price.trim()
                    .replace(",", ".")
                    .toBigDecimalOrNull()

                if (name.isBlank()) {

                    error = "Please enter a product name."

                } else if (
                    parsed == null ||
                    parsed <= BigDecimal.ZERO
                ) {

                    error = "Please enter a valid price greater than zero."

                } else {

                    val cents = parsed
                        .movePointRight(2)
                        .setScale(0, RoundingMode.HALF_UP)
                        .toInt()

                    onAdd(name.trim(), cents)
                }

            }) {
                Text("Save Product")
            }
        },

        dismissButton = {

            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatRand(cents: Int): String {
    return String.format(
        Locale("en", "ZA"),
        "R %.2f",
        cents / 100.0
    )
}

fun loadProducts(context: Context): List<Product> {

    return try {

        val json = context.getSharedPreferences(
            "shopsmart_data",
            Context.MODE_PRIVATE
        ).getString("products", "[]") ?: "[]"

        val array = JSONArray(json)

        (0 until array.length()).map { i ->

            val item = array.getJSONObject(i)

            Product(
                item.getLong("id"),
                item.getString("name"),
                item.getInt("price")
            )
        }

    } catch (_: Exception) {
        emptyList()
    }
}

fun loadShopping(context: Context): List<ShoppingEntry> {

    return try {

        val json = context.getSharedPreferences(
            "shopsmart_data",
            Context.MODE_PRIVATE
        ).getString("shopping", "[]") ?: "[]"

        val array = JSONArray(json)

        (0 until array.length()).map { i ->

            val item = array.getJSONObject(i)

            ShoppingEntry(
                item.getLong("id"),
                item.getLong("productId"),
                item.getString("name"),
                item.getInt("price"),
                item.getInt("quantity"),
                item.getBoolean("bought")
            )
        }

    } catch (_: Exception) {
        emptyList()
    }
}

fun saveProducts(
    context: Context,
    products: List<Product>
) {

    val array = JSONArray()

    products.forEach { product ->

        array.put(
            JSONObject().apply {
                put("id", product.id)
                put("name", product.name)
                put("price", product.priceCents)
            }
        )
    }

    context.getSharedPreferences(
        "shopsmart_data",
        Context.MODE_PRIVATE
    ).edit()
        .putString("products", array.toString())
        .apply()
}

fun saveShopping(
    context: Context,
    shopping: List<ShoppingEntry>
) {

    val array = JSONArray()

    shopping.forEach { entry ->

        array.put(
            JSONObject().apply {
                put("id", entry.id)
                put("productId", entry.productId)
                put("name", entry.name)
                put("price", entry.priceCents)
                put("quantity", entry.quantity)
                put("bought", entry.bought)
            }
        )
    }

    context.getSharedPreferences(
        "shopsmart_data",
        Context.MODE_PRIVATE
    ).edit()
        .putString("shopping", array.toString())
        .apply()
}