# Budget Tracker — Data Context for Claude Code

## Overview
This is a monthly household budget tracker for the year. Data covers **January–May** (actuals); June–December are zeros (not yet filled).

## Financial Summary
| Field | Value |
|---|---|
| Monthly Income | ₱50,000 |
| Total Budget Allocated | ₱60,580 |
| Income minus Budget | **-₱5,580** (over-budgeted) |

## Data Files
- `budget_data.csv` — flat table, one row per category, columns: Category, Budget, Jan–Dec
- `budget_data.json` — structured JSON with metadata + per-category monthly breakdown

## Budget Categories (19 total)

| Category        | Monthly Budget |
|-----------------|---|
| Savings         | ₱0 |
| Rent now        | ₱11,000 |
| Electricity     | ₱3,500 |
| Food            | ₱8,000 |
| Transportation  | ₱0 |
| Subscription    | ₱3,600 |
| Grab            | ₱2,000 |
| Home            | ₱2,500 |
| Balay Kab       | ₱2,000 |
| Clothing        | ₱3,000 |
| St Peter        | ₱1,580 |
| Shoppee         | ₱5,000 |
| Personal        | ₱3,000 |
| Grocery         | ₱4,000 |
| Travel          | ₱3,000 |
| Wet Market      | ₱5,000 |
| Church          | ₱400 |
| Benevolent Fund | ₱3,000 |
| Tithes          | ₱4,000 |

## Notable Observations (from Jan–May actuals)
- **Food** consistently exceeds budget every month (e.g., March: ₱26,446 vs ₱8,000 budget)
- **Personal** spiked heavily in March (₱34,575 vs ₱3,000 budget)
- **Shoppee** had ₱0 in January but ₱18,080 in February
- **Balay Kab** spiked in May (₱13,669 vs ₱2,000 budget)
- **Benevolent Fund** had a large ₱13,000 in April vs ₱3,000 budget
- **Home** has June data (₱3,833) — only category with a June entry
- **Highlighted cells (pink/red)** in the original sheet = over-budget months

## Suggested Use Cases for Claude Code
- Build a budget vs actual comparison report
- Flag categories consistently over budget
- Generate monthly summaries
- Project year-end spend based on Jan–May averages
- Build a simple budget tracker web app or CLI tool
