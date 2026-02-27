# Google Apps Script Update

## Problem
The current script is appending data thousands of rows apart instead of to the next available row.

## Solution
Replace your Google Apps Script with this updated version:

```javascript
function doPost(e) {
  try {
    // Parse the JSON request
    var data = JSON.parse(e.postData.contents);
    
    // Open the spreadsheet
    var spreadsheet = SpreadsheetApp.openById('1P7FnOo2Cv-HwfyY3RbWlrr6W1kLURNoEDjxmMj-3NCY');
    var sheet = spreadsheet.getSheetByName('Data Dump');
    
    if (!sheet) {
      return ContentService.createTextOutput(JSON.stringify({
        success: false,
        error: 'Sheet "Data Dump" not found'
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // Find the next empty row (right after the last row with data)
    var lastRow = sheet.getLastRow();
    var nextRow = lastRow + 1;
    
    // Prepare the row data
    var rowData = [
      data.date || '',
      data.description || '',
      data.inflow || '',
      data.outflow || '',
      data.category || '',
      data.modeOfPayment || '',
      data.isPaid || 'FALSE',
      data.remarks || ''
    ];
    
    // Append to the next available row
    sheet.getRange(nextRow, 1, 1, rowData.length).setValues([rowData]);
    
    Logger.log('Successfully added transaction to row ' + nextRow);
    
    return ContentService.createTextOutput(JSON.stringify({
      success: true,
      message: 'Transaction added successfully to row ' + nextRow
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (error) {
    Logger.log('Error: ' + error.toString());
    return ContentService.createTextOutput(JSON.stringify({
      success: false,
      error: error.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  return ContentService.createTextOutput(JSON.stringify({
    success: true,
    message: 'Finance Tracker API is running'
  })).setMimeType(ContentService.MimeType.JSON);
}
```

## Steps to Update:

1. Go to your Google Apps Script: https://script.google.com
2. Find your existing script (the one with URL ending in `...exec`)
3. Replace the entire code with the script above
4. Click "Deploy" → "Manage deployments"
5. Click the edit icon (pencil) on your existing deployment
6. Change "Version" to "New version"
7. Click "Deploy"
8. Copy the new Web App URL if it changed
9. Update your app's `SCRIPT_URL` if needed

## What Changed:
- Uses `getLastRow()` to find the actual last row with data
- Appends to `lastRow + 1` instead of using a range like `A:H`
- This ensures data is added immediately after existing data
- No more gaps of thousands of rows!
