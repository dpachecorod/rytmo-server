package com.rytmo.models.cardaccounts

data class CardTransactionResponse(
    val id: String,
    val cardAccountId: String?,
    val category: String?,
    val amount: String?,
    val billingAmount: String?,
    val currency: String?,
    val merchantName: String?,
    val merchantLocation: String?,
    val merchantCategoryCode: String?,
    val description: String?,
    val postedAt: String?,
    val authorizedAt: String?,
    val status: String?,
)

data class PaginatedCardTransactionsResponse(
    val page: Int?,
    val paginationToken: String?,
    val count: Int?,
    val totalPages: Int?,
    val totalCount: Int?,
    val data: List<CardTransactionResponse>,
)
