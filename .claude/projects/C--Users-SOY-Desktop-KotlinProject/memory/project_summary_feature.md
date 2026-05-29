---
name: project-summary-feature
description: Summary/trend chart feature — sheet key, parser filters, budget line, chat trigger
metadata:
  type: project
---

Spending Summary sheet reads from `'Sumarry Trend'!A:M` (intentional misspellings in both key and tab name).

**Config key:** `tracker_1.SUMARRY_TREND` in local.properties → `BuildConfig.SUMARRY_TREND` → `ApiConfig.SUMARRY_TREND` → `ConfigManager.summaryRange`

**Layout:** `Category | January | February | … | Month-N` (no Budget column — different from Budget tab)

**Parser filters** (getSummary in Tracker1Repository): skips rows where col-0 is "income", "expenses", "savings", "total", "net", "category" (section headers that bleed through), and rows with all-zero amounts.

**ViewModel:** `SummaryViewModel` fetches `getSummary()` + `getBudget()` in parallel; computes `totalMonthlyBudget = budget.sumOf { it.monthlyBudget }` as chart reference.

**UI entry point:** "📊 Show spending by category" highlighted suggestion chip in ChatScreen EmptyState → opens `SummarySheet` (ModalBottomSheet).

**Chart:** Pure Compose (no library) — vertical bar chart per month, dashed Canvas budget reference line at `totalMonthlyBudget` level, earthy category colors cycling, horizontal breakdown bars below, over-budget error pill.

**Why:** [[project-schema-convention]] — SUMARRY_TREND key kept misspelled to match live sheet exactly, same pattern as BUDGET_RANGE keeping 'Bugdet vs Expense'.
