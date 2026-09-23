package com.yash.tracker.ui.foods

import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.repository.displayName
import com.yash.tracker.domain.nutrition.NutritionPer100g

/**
 * One row of search results, from either the bundled catalogue or the user's own products.
 *
 * They log identically — both are per-100 g numbers scaled by a portion — so the screen and the
 * log sheet work on this rather than on two parallel shapes.
 */
sealed interface SearchHit {

    val key: String
    val title: String
    val subtitle: String
    val per100g: NutritionPer100g

    /** A product is transcribed off a packet; the catalogue is a reference table. */
    val isExact: Boolean

    data class Catalogue(val food: FoodEntity) : SearchHit {
        override val key = "food-${food.id}"
        override val title = food.name
        override val subtitle = food.category.orEmpty().lowercase().replace('_', ' ')
        override val per100g = food
        override val isExact = food.isVerified
    }

    data class Product(val product: ProductEntity) : SearchHit {
        override val key = "product-${product.id}"
        override val title = product.displayName()
        override val subtitle = product.servingLabel?.takeIf { it.isNotBlank() }
            ?.let { "your product · $it" }
            ?: "your product"
        override val per100g = product
        override val isExact = true
    }
}
