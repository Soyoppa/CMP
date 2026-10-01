package org.example.project.domain.transaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionFormReducerTest {

    private val empty = TransactionFormState()

    private fun reduce(vararg events: TransactionFormEvent) =
        events.fold(empty) { state, event -> TransactionFormReducer.reduce(state, event) }

    @Test
    fun amountAcceptsOnlyMoneyShapedInput() {
        assertEquals("12.5", reduce(TransactionFormEvent.AmountChanged("12.5")).amount)
        assertEquals("12.5", reduce(TransactionFormEvent.AmountChanged("12.5"), TransactionFormEvent.AmountChanged("12.555")).amount)
        assertEquals("", reduce(TransactionFormEvent.AmountChanged("abc")).amount)
        assertEquals(20, reduce(TransactionFormEvent.AmountChanged("1".repeat(40))).amount.length)
    }

    @Test
    fun descriptionIsCapped() {
        assertEquals(200, reduce(TransactionFormEvent.DescriptionChanged("x".repeat(500))).description.length)
    }

    @Test
    fun switchingTypeResetsCategory() {
        val state = reduce(
            TransactionFormEvent.CategorySelected("Rent"),
            TransactionFormEvent.TransactionTypeChanged(isIncome = true),
        )
        assertTrue(state.isIncome)
        assertEquals("", state.selectedCategory) // expense and income lists differ
    }

    @Test
    fun aSelectionRemovedElsewhereIsCleared() {
        val state = reduce(
            TransactionFormEvent.OptionsLoaded(listOf("Food", "Rent"), emptyList(), listOf("Cash")),
            TransactionFormEvent.CategorySelected("Rent"),
            TransactionFormEvent.PaymentModeSelected("Cash"),
            // "Rent" renamed in the editor; "Cash" untouched.
            TransactionFormEvent.OptionsLoaded(listOf("Food", "Housing"), emptyList(), listOf("Cash")),
        )
        assertEquals("", state.selectedCategory)
        assertEquals("Cash", state.selectedPaymentMode)
        assertEquals(listOf("Food", "Housing"), state.categoryOptions)
    }

    @Test
    fun dropdownsAreMutuallyExclusive() {
        val state = reduce(TransactionFormEvent.CategoryDropdownToggled, TransactionFormEvent.PaymentDropdownToggled)
        assertTrue(state.showPaymentDropdown)
        assertFalse(state.showCategoryDropdown)
    }

    @Test
    fun voiceResultOnlyOverwritesConfidentFields() {
        val state = reduce(
            TransactionFormEvent.DescriptionChanged("Lunch"),
            TransactionFormEvent.VoiceResultApplied(amount = "250", description = "", category = null, isIncome = null),
        )
        assertEquals("250", state.amount)
        assertEquals("Lunch", state.description)
    }

    @Test
    fun validityRequiresPositiveAmountDescriptionAndDate() {
        assertFalse(empty.isValid)
        val valid = empty.copy(amount = "10", description = "Coffee", selectedDate = "3/1/2026")
        assertTrue(valid.isValid)
        assertFalse(valid.copy(amount = "0").isValid)
    }
}
