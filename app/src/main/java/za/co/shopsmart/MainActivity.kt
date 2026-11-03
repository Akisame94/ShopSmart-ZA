
package za.co.shopsmart

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private const val PROMO_NONE = "NONE"
private const val PROMO_UNIT = "UNIT"
private const val PROMO_COMBO = "COMBO"

data class Product(
    val id: Long,
    val name: String,
    val priceCents: Int,
    val brand: String = "",
    val unit: String = "",
    val promoType: String = PROMO_NONE,
    val promoPriceCents: Int = 0,
    val comboQuantity: Int = 2,
    val comboPriceCents: Int = 0
)

data class ShoppingEntry(
    val id: Long,
    val productId: Long,
    val name: String,
    val priceCents: Int,
    val quantity: Int,
    val bought: Boolean,
    val brand: String = "",
    val unit: String = "",
    val promoType: String = PROMO_NONE,
    val promoPriceCents: Int = 0,
    val comboQuantity: Int = 2,
    val comboPriceCents: Int = 0,
    val promotionApplied: Boolean = false,
    val actualPaidCents: Int? = null
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

fun promotionDescription(product: Product): String {
    return when (product.promoType) {
        PROMO_UNIT -> "Special: ${formatRand(product.promoPriceCents)} each"
        PROMO_COMBO -> "${product.comboQuantity} for ${formatRand(product.comboPriceCents)}"
        else -> "No promotion"
    }
}

fun promotionDescription(entry: ShoppingEntry): String {
    return when (entry.promoType) {
        PROMO_UNIT -> "Special: ${formatRand(entry.promoPriceCents)} each"
        PROMO_COMBO -> "${entry.comboQuantity} for ${formatRand(entry.comboPriceCents)}"
        else -> "No promotion"
    }
}

fun calculateLineTotal(entry: ShoppingEntry): Int {
    return when (entry.promoType) {
        PROMO_UNIT -> {
            if (entry.promoPriceCents > 0) {
                entry.promoPriceCents * entry.quantity
            } else {
                entry.priceCents * entry.quantity
            }
        }

        PROMO_COMBO -> {
            if (entry.comboQuantity >= 2 && entry.comboPriceCents > 0) {
                val bundles = entry.quantity / entry.comboQuantity
                val remaining = entry.quantity % entry.comboQuantity

                (bundles * entry.comboPriceCents) +
                    (remaining * entry.priceCents)
            } else {
                entry.priceCents * entry.quantity
            }
        }

        else -> entry.priceCents * entry.quantity
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
    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var selectedPurchase by remember { mutableStateOf<Long?>(null) }

    fun saveAll() {
        saveProducts(context, products)
        saveShopping(context, shopping)
        saveHistory(context, history)
    }

    fun productFromEntry(entry: ShoppingEntry): Product {
        return Product(
            id = entry.productId,
            name = entry.name,
            priceCents = entry.priceCents,
            brand = entry.brand,
            unit = entry.unit,
            promoType = entry.promoType,
            promoPriceCents = entry.promoPriceCents,
            comboQuantity = entry.comboQuantity,
            comboPriceCents = entry.comboPriceCents
        )
    }

    fun updateProduct(updated: Product) {
        val index = products.indexOfFirst { it.id == updated.id }

        if (index >= 0) {
            products[index] = updated
        } else {
            products.add(updated)
        }

        // Keep all active shopping entries in sync with the catalogue.
        shopping.indices.forEach { i ->
            val entry = shopping[i]

            if (entry.productId == updated.id) {
                shopping[i] = entry.copy(
                    name = updated.name,
                    priceCents = updated.priceCents,
                    brand = updated.brand,
                    unit = updated.unit,
                    promoType = updated.promoType,
                    promoPriceCents = updated.promoPriceCents,
                    comboQuantity = updated.comboQuantity,
                    comboPriceCents = updated.comboPriceCents
                )
            }
        }

        saveAll()
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
                    bought = false,
                    brand = product.brand,
                    unit = product.unit,
                    promoType = product.promoType,
                    promoPriceCents = product.promoPriceCents,
                    comboQuantity = product.comboQuantity,
                    comboPriceCents = product.comboPriceCents
                )
            )
        }

        saveAll()
    }

    fun completePurchase() {
        val boughtItems = shopping.filter { it.bought }

        if (boughtItems.isNotEmpty()) {
            val savedItems = boughtItems.map { entry ->
                entry.copy(
                    promotionApplied = entry.promoType != PROMO_NONE,
                    actualPaidCents = calculateLineTotal(entry)
                )
            }

            val total = savedItems.sumOf {
                it.actualPaidCents ?: calculateLineTotal(it)
            }

            history.add(
                0,
                PurchaseRecord(
                    id = System.nanoTime(),
                    timestamp = System.currentTimeMillis(),
                    items = savedItems,
                    totalCents = total
                )
            )

            shopping.removeAll { it.bought }
            saveAll()
        }
    }

    val estimatedTotal = shopping
        .filter { it.bought }
        .sumOf { calculateLineTotal(it) }

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

                // SHOPPING
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
                                                                entry.copy(bought = checked)
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

                                                    if (entry.brand.isNotBlank()) {
                                                        Text("Brand: ${entry.brand}")
                                                    }

                                                    if (entry.unit.isNotBlank()) {
                                                        Text("Unit: ${entry.unit}")
                                                    }

                                                    Text(
                                                        "Normal price: ${formatRand(entry.priceCents)}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )

                                                    if (entry.promoType != PROMO_NONE) {
                                                        Text(
                                                            promotionDescription(entry),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }

                                                Text(
                                                    formatRand(calculateLineTotal(entry)),
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
                                                                    quantity = entry.quantity - 1
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
                                                                quantity = entry.quantity + 1
                                                            )
                                                        saveAll()
                                                    }
                                                }) {
                                                    Text("+")
                                                }

                                                TextButton(onClick = {
                                                    editingProduct = productFromEntry(entry)
                                                    showProductDialog = true
                                                }) {
                                                    Text("Edit")
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
                                                    "Marked as bought",
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

                // CATALOGUE
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
                                editingProduct = null
                                showProductDialog = true
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
                                        Column(Modifier.padding(12.dp)) {

                                            Text(
                                                product.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )

                                            if (product.brand.isNotBlank()) {
                                                Text("Brand: ${product.brand}")
                                            }

                                            if (product.unit.isNotBlank()) {
                                                Text("Unit: ${product.unit}")
                                            }

                                            Text("Normal price: ${formatRand(product.priceCents)}")

                                            if (product.promoType != PROMO_NONE) {
                                                Text(
                                                    promotionDescription(product),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Row(
                                                horizontalArrangement =
                                                    Arrangement.End,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                TextButton(onClick = {
                                                    addToShopping(product)
                                                }) {
                                                    Text("Add to list")
                                                }

                                                TextButton(onClick = {
                                                    editingProduct = product
                                                    showProductDialog = true
                                                }) {
                                                    Text("Edit")
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
                                                    if (expanded) "Hide items"
                                                    else "View items"
                                                )
                                            }

                                            if (expanded) {
                                                HorizontalDivider()

                                                record.items.forEach { item ->
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 5.dp)
                                                    ) {
                                                        Row(
                                                            horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                "${item.name} x${item.quantity}"
                                                            )
                                                            Text(
                                                                formatRand(
                                                                    item.actualPaidCents
                                                                        ?: calculateLineTotal(item)
                                                                )
                                                            )
                                                        }

                                                        if (item.promotionApplied) {
                                                            Text(
                                                                "Promotion applied: ${promotionDescription(item)}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.primary
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

    if (showProductDialog) {
        ProductEditorDialog(
            initialProduct = editingProduct,
            onDismiss = {
                showProductDialog = false
                editingProduct = null
            },
            onSave = { updated ->
                updateProduct(updated)
                showProductDialog = false
                editingProduct = null
            }
        )
    }
}

@Composable
fun ProductEditorDialog(
    initialProduct: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember(initialProduct?.id) {
        mutableStateOf(initialProduct?.name ?: "")
    }

    var brand by remember(initialProduct?.id) {
        mutableStateOf(initialProduct?.brand ?: "")
    }

    var unit by remember(initialProduct?.id) {
        mutableStateOf(initialProduct?.unit ?: "")
    }

    var price by remember(initialProduct?.id) {
        mutableStateOf(
            initialProduct?.let {
                String.format(Locale.US, "%.2f", it.priceCents / 100.0)
            } ?: ""
        )
    }

    var promoType by remember(initialProduct?.id) {
        mutableStateOf(initialProduct?.promoType ?: PROMO_NONE)
    }

    var promoPrice by remember(initialProduct?.id) {
        mutableStateOf(
            initialProduct?.let {
                if (it.promoPriceCents > 0) {
                    String.format(Locale.US, "%.2f", it.promoPriceCents / 100.0)
                } else ""
            } ?: ""
        )
    }

    var comboQuantity by remember(initialProduct?.id) {
        mutableStateOf(
            initialProduct?.comboQuantity?.toString() ?: "2"
        )
    }

    var comboPrice by remember(initialProduct?.id) {
        mutableStateOf(
            initialProduct?.let {
                if (it.comboPriceCents > 0) {
                    String.format(Locale.US, "%.2f", it.comboPriceCents / 100.0)
                } else ""
            } ?: ""
        )
    }

    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProduct == null) "Add a product" else "Edit product")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand name") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit of measure (e.g. 2 L, 500 g, each)") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Normal price in Rand") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Promotion type",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = promoType == PROMO_NONE,
                        onClick = { promoType = PROMO_NONE }
                    )
                    Text("None")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = promoType == PROMO_UNIT,
                        onClick = { promoType = PROMO_UNIT }
                    )
                    Text("Special price per item")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = promoType == PROMO_COMBO,
                        onClick = { promoType = PROMO_COMBO }
                    )
                    Text("Combo deal (e.g. 2 for R100)")
                }

                if (promoType == PROMO_UNIT) {
                    OutlinedTextField(
                        value = promoPrice,
                        onValueChange = { promoPrice = it },
                        label = { Text("Promotional price per item (R)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                }

                if (promoType == PROMO_COMBO) {
                    OutlinedTextField(
                        value = comboQuantity,
                        onValueChange = { comboQuantity = it },
                        label = { Text("Number of items in combo (e.g. 2)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = comboPrice,
                        onValueChange = { comboPrice = it },
                        label = { Text("Total combo price in Rand (e.g. 100)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true
                    )

                    Text(
                        "Example: 2 for R100. Any extra items are charged at the normal price.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val normalCents = parsePriceCents(price)
                val specialCents = parsePriceCents(promoPrice)
                val comboCents = parsePriceCents(comboPrice)
                val comboQty = comboQuantity.toIntOrNull()

                when {
                    name.isBlank() -> {
                        error = "Please enter a product name."
                    }

                    normalCents == null || normalCents <= 0 -> {
                        error = "Enter a valid normal price greater than zero."
                    }

                    promoType == PROMO_UNIT &&
                        (specialCents == null || specialCents <= 0) -> {
                        error = "Enter a valid promotional price."
                    }

                    promoType == PROMO_COMBO &&
                        (comboQty == null || comboQty < 2) -> {
                        error = "Combo quantity must be at least 2."
                    }

                    promoType == PROMO_COMBO &&
                        (comboCents == null || comboCents <= 0) -> {
                        error = "Enter a valid combo price."
                    }

                    else -> {
                        onSave(
                            Product(
                                id = initialProduct?.id ?: System.nanoTime(),
                                name = name.trim(),
                                priceCents = normalCents,
                                brand = brand.trim(),
                                unit = unit.trim(),
                                promoType = promoType,
                                promoPriceCents =
                                    if (promoType == PROMO_UNIT) specialCents ?: 0 else 0,
                                comboQuantity =
                                    if (promoType == PROMO_COMBO) comboQty ?: 2 else 2,
                                comboPriceCents =
                                    if (promoType == PROMO_COMBO) comboCents ?: 0 else 0
                            )
                        )
                    }
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
            "Only normal-price purchases are included. Promotional purchases are excluded.",
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
                                .filter {
                                    it.productId == product.id &&
                                        !it.promotionApplied
                                }
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

                            if (product.brand.isNotBlank()) {
                                Text("Brand: ${product.brand}")
                            }

                            if (product.unit.isNotBlank()) {
                                Text("Unit: ${product.unit}")
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current normal price")
                                Text(
                                    formatRand(product.priceCents),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            if (purchases.isEmpty()) {

                                Text("No normal-price purchase history yet.")

                                Text(
                                    "Promotional purchases do not count towards normal price tracking.",
                                    style = MaterialTheme.typography.bodySmall
                                )

                            } else {

                                val latest = purchases[0]

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Last normal price paid")
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
                                        Text("Previous normal price")
                                        Text(formatRand(previous.priceCents))
                                    }

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
                                        "A second normal-price purchase is needed to compare prices.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    "Normal-price purchases recorded: ${purchases.size}",
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

fun parsePriceCents(value: String): Int? {
    return try {
        val parsed = value.trim()
            .replace(",", ".")
            .toBigDecimalOrNull()
            ?: return null

        parsed.movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .intValueExact()
    } catch (_: Exception) {
        null
    }
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
                id = item.getLong("id"),
                name = item.getString("name"),
                priceCents = item.getInt("price"),
                brand = item.optString("brand", ""),
                unit = item.optString("unit", ""),
                promoType = item.optString("promoType", PROMO_NONE),
                promoPriceCents = item.optInt("promoPrice", 0),
                comboQuantity = item.optInt("comboQuantity", 2),
                comboPriceCents = item.optInt("comboPrice", 0)
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
                id = item.getLong("id"),
                productId = item.getLong("productId"),
                name = item.getString("name"),
                priceCents = item.getInt("price"),
                quantity = item.getInt("quantity"),
                bought = item.getBoolean("bought"),
                brand = item.optString("brand", ""),
                unit = item.optString("unit", ""),
                promoType = item.optString("promoType", PROMO_NONE),
                promoPriceCents = item.optInt("promoPrice", 0),
                comboQuantity = item.optInt("comboQuantity", 2),
                comboPriceCents = item.optInt("comboPrice", 0),
                promotionApplied = item.optBoolean("promotionApplied", false),
                actualPaidCents =
                    if (item.has("actualPaid") && !item.isNull("actualPaid")) {
                        item.optInt("actualPaid")
                    } else null
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
                    id = item.getLong("id"),
                    productId = item.getLong("productId"),
                    name = item.getString("name"),
                    priceCents = item.getInt("price"),
                    quantity = item.getInt("quantity"),
                    bought = item.getBoolean("bought"),
                    brand = item.optString("brand", ""),
                    unit = item.optString("unit", ""),
                    promoType = item.optString("promoType", PROMO_NONE),
                    promoPriceCents = item.optInt("promoPrice", 0),
                    comboQuantity = item.optInt("comboQuantity", 2),
                    comboPriceCents = item.optInt("comboPrice", 0),
                    promotionApplied = item.optBoolean("promotionApplied", false),
                    actualPaidCents =
                        if (item.has("actualPaid") && !item.isNull("actualPaid")) {
                            item.optInt("actualPaid")
                        } else null
                )
            }

            PurchaseRecord(
                id = record.getLong("id"),
                timestamp = record.getLong("timestamp"),
                items = savedItems,
                totalCents = record.getInt("total")
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
                put("brand", product.brand)
                put("unit", product.unit)
                put("promoType", product.promoType)
                put("promoPrice", product.promoPriceCents)
                put("comboQuantity", product.comboQuantity)
                put("comboPrice", product.comboPriceCents)
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
                put("brand", entry.brand)
                put("unit", entry.unit)
                put("promoType", entry.promoType)
                put("promoPrice", entry.promoPriceCents)
                put("comboQuantity", entry.comboQuantity)
                put("comboPrice", entry.comboPriceCents)
                put("promotionApplied", entry.promotionApplied)

                if (entry.actualPaidCents != null) {
                    put("actualPaid", entry.actualPaidCents)
                } else {
                    put("actualPaid", JSONObject.NULL)
                }
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
                    put("brand", item.brand)
                    put("unit", item.unit)
                    put("promoType", item.promoType)
                    put("promoPrice", item.promoPriceCents)
                    put("comboQuantity", item.comboQuantity)
                    put("comboPrice", item.comboPriceCents)
                    put("promotionApplied", item.promotionApplied)

                    if (item.actualPaidCents != null) {
                        put("actualPaid", item.actualPaidCents)
                    } else {
                        put("actualPaid", JSONObject.NULL)
                    }
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
