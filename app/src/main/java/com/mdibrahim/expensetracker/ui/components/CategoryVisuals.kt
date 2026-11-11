package com.mdibrahim.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdibrahim.expensetracker.core.model.Category

/**
 * Category.iconKey -> icon. Keys come from the seeder (lowercased name). Unknown
 * keys fall back to a generic icon, so a renamed or user-made category still renders.
 */
fun categoryIcon(iconKey: String?): ImageVector = when (iconKey) {
    "food" -> Icons.Filled.Restaurant
    "transport" -> Icons.Filled.DirectionsBus
    "groceries" -> Icons.Filled.LocalGroceryStore
    "bills" -> Icons.AutoMirrored.Filled.ReceiptLong
    "shopping" -> Icons.Filled.ShoppingBag
    "health" -> Icons.Filled.MedicalServices
    "self_transfer" -> Icons.Filled.SwapHoriz
    "lending" -> Icons.Filled.Handshake
    else -> Icons.Filled.Category
}

fun Category.color(): Color = Color(colorArgb)

/**
 * Round leading badge for a transaction: the category's icon on a tint of its
 * colour, "?" when it still needs a category, or a check when selected. Each state
 * differs in icon, not only colour.
 */
@Composable
fun CategoryAvatar(
    category: Category?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 42.dp,
) {
    val scheme = MaterialTheme.colorScheme
    when {
        selected -> Box(
            modifier.size(size).background(scheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = scheme.onPrimary)
        }
        category == null -> Box(
            modifier.size(size).border(1.5.dp, scheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.QuestionMark,
                contentDescription = "Needs category",
                tint = scheme.primary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
        else -> Box(
            modifier.size(size).background(category.color().copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                categoryIcon(category.iconKey),
                contentDescription = category.name,
                tint = category.color(),
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}
