# Product Overview

A personal finance tracker app for Filipino users. It allows users to log financial transactions (income and expenses), categorize them, and query their financial data via an AI chat assistant powered by Ollama.

## Core Features
- Transaction input form (amount, description, date, category, payment mode, income/expense toggle)
- Data persistence via Google Sheets (read) and Google Apps Script (write)
- AI chat assistant (Ollama) with awareness of loaded transaction history
- Connection test screen for debugging API/Ollama connectivity

## Domain Concepts
- Transactions have: date, description, inflow (income), outflow (expense), category, modeOfPayment, isPaid
- `TransactionCategory` and `PaymentMode` are fixed enums with Filipino-context values (e.g., Maya, GCash, Balay Kab)
- Currency is Philippine Peso (₱)
- Target platforms: Android, iOS, Desktop (JVM), Web (JS + WasmJS)
