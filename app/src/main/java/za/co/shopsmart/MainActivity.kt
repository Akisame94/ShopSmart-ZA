
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
import java.text.SimpleDateFormat
import java.util.Date

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

data class PurchaseRecord(
    val id: Long,
    val timestamp: Long,
    val items: List<ShoppingEntry>,
    val totalCents: Int
)

data class ProductPricePurchase(
    val timestamp: Long,
    val priceCents: Int
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

    val history = remember {
        mutableStateListOf<PurchaseRecord>().apply {
            addAll(loadHistory(context))
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPurchase by remember { mutableStateOf<Long?>(null) }

    fun saveAll() {
        saveProducts(context, products)
        saveShopping(context, shopping)
        saveHistory(context, history)
    }

    fun addToShopping(product: Product) {
        val existing = shopping.indexOfFirst {
            it.productId == product.id && !it.bought
        }

        if (existing >= 0) {
            shopping[existing] = shopping[existing].copy(
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

    fun completePurchase() {
        val boughtItems = shopping.filter { it.bought }

        if (boughtItems.isNotEmpty()) {
            val total = boughtItems.sumOf {
                it.priceCents * it.quantity
            }

            history.add(
                0,
                PurchaseRecord(
                    id = System.nanoTime(),
                    timestamp = System.currentTimeMillis(),
                    items = boughtItems.toList(),
                    totalCents = total
                )
            )

            shopping.removeAll { it.bought }

            saveAll()
        }
    }

    val estimatedTotal = shopping
        .filter { it.bought }
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

            ScrollableTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Shopping") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Catalogue") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Price Tracking") }
                )
            }

            when (selectedTab) {

                // SHOPPING LIST
                0 -> {
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
                                Text("Till total")
                                Text(
                                    formatRand(estimatedTotal),
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Text("Total of items marked as bought.")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Your shopping list",
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

                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
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
                                                    )
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

                        if (shopping.any { it.bought }) {
                            Button(
                                onClick = { completePurchase() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text("Save Purchase - ${formatRand(estimatedTotal)}")
                            }
                        }
                    }
                }

                // PRODUCT CATALOGUE
                1 -> {
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

                // PURCHASE HISTORY
                2 -> {
                    Column(Modifier.fillMaxSize()) {

                        Text(
                            "Purchase History",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )

                        if (history.isEmpty()) {
                            Text(
                                "No completed purchases yet. Your saved shopping trips will appear here.",
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {

                            LazyColumn {
                                items(history, key = { it.id }) { record ->

                                    val expanded =
                                        selectedPurchase == record.id

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = 12.dp,
                                                vertical = 5.dp
                                            )
                                    ) {
                                        Column(Modifier.padding(14.dp)) {

                                            Text(
                                                formatDate(record.timestamp),
                                                style = MaterialTheme.typography.titleMedium
                                            )

                                            Text(
                                                formatRand(record.totalCents),
                                                style = MaterialTheme.typography.headlineSmall
                                            )

                                            Text("${record.items.size} product lines")

                                            TextButton(onClick = {
                                                selectedPurchase =
                                                    if (expanded) null else record.id
                                            }) {
                                                Text(
                                                    if (expanded) {
                                                        "Hide items"
                                                    } else {
                                                        "View items"
                                                    }
                                                )
                                            }

                                            if (expanded) {
                                                HorizontalDivider()

                                                record.items.forEach { item ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 5.dp),
                                                        horizontalArrangement =
                                                            Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            "${item.name} x${item.quantity}"
                                                        )
                                                        Text(
                                                            formatRand(
                                                                item.priceCents *
                                                                    item.quantity
                                                            )
                                                        )
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

                // PRICE TRACKING
                3 -> {
                    PriceTrackingScreen(
                        products = products,
                        history = history
                    )
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
fun PriceTrackingScreen(
    products: List<Product>,
    history: List<PurchaseRecord>
) {
    Column(Modifier.fillMaxSize()) {

        Text(
            "Product Price Tracking",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 4.dp
            )
        )

        Text(
            "See how the prices you pay change over time.",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (products.isEmpty()) {
            Text(
                "Add products to your catalogue to start tracking their prices.",
                modifier = Modifier.padding(20.dp)
            )
        } else {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(products, key = { it.id }) { product ->

                    val purchases = history
                        .flatMap { record ->
                            record.items
                                .filter { it.productId == product.id }
                                .map { item ->
                                    ProductPricePurchase(
                                        timestamp = record.timestamp,
                                        priceCents = item.priceCents
                                    )
                                }
                        }
                        .sortedByDescending { it.timestamp }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                product.name,
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Catalogue price",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatRand(product.priceCents),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            if (purchases.isEmpty()) {

                                Text(
                                    "No purchase history yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    "Save a purchase containing this product to start tracking its price.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                            } else {

                                val latest = purchases[0]

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Last price paid")
                                    Text(
                                        formatRand(latest.priceCents),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Text(
                                    "Purchased: ${formatDate(latest.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(10.dp))

                                if (purchases.size >= 2) {

                                    val previous = purchases[1]
                                    val difference =
                                        latest.priceCents - previous.priceCents

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Previous price")
                                        Text(formatRand(previous.priceCents))
                                    }

                                    Text(
                                        "Previous purchase: ${formatDate(previous.timestamp)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    when {
                                        difference > 0 -> {
                                            Text(
                                                "Price increased by ${formatRand(difference)}",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }

                                        difference < 0 -> {
                                            Text(
                                                "Price decreased by ${formatRand(-difference)}",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }

                                        else -> {
                                            Text(
                                                "Price unchanged",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }

                                } else {
                                    Text(
                                        "A second purchase is needed to compare prices.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    "Recorded purchases: ${purchases.size}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
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
        title = { Text("Add a product") },
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
                    label = { Text("Price in Rand (e.g. 25.99)") },
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

fun formatDate(timestamp: Long): String {
    return SimpleDateFormat(
        "dd MMM yyyy, HH:mm",
        Locale("en", "ZA")
    ).format(Date(timestamp))
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

fun loadHistory(context: Context): List<PurchaseRecord> {
    return try {
        val json = context.getSharedPreferences(
            "shopsmart_data",
            Context.MODE_PRIVATE
        ).getString("history", "[]") ?: "[]"

        val array = JSONArray(json)

        (0 until array.length()).map { i ->
            val record = array.getJSONObject(i)
            val itemArray = record.getJSONArray("items")

            val savedItems = (0 until itemArray.length()).map { j ->
                val item = itemArray.getJSONObject(j)
                ShoppingEntry(
                    item.getLong("id"),
                    item.getLong("productId"),
                    item.getString("name"),
                    item.getInt("price"),
                    item.getInt("quantity"),
                    item.getBoolean("bought")
                )
            }

            PurchaseRecord(
                record.getLong("id"),
                record.getLong("timestamp"),
                savedItems,
                record.getInt("total")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveProducts(context: Context, products: List<Product>) {
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

fun saveHistory(
    context: Context,
    history: List<PurchaseRecord>
) {
    val array = JSONArray()

    history.forEach { record ->

        val itemArray = JSONArray()

        record.items.forEach { item ->
            itemArray.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("productId", item.productId)
                    put("name", item.name)
                    put("price", item.priceCents)
                    put("quantity", item.quantity)
                    put("bought", item.bought)
                }
            )
        }

        array.put(
            JSONObject().apply {
                put("id", record.id)
                put("timestamp", record.timestamp)
                put("total", record.totalCents)
                put("items", itemArray)
            }
        )
    }

    context.getSharedPreferences(
        "shopsmart_data",
        Context.MODE_PRIVATE
    ).edit()
        .putString("history", array.toString())
        .apply()
}
