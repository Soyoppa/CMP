package org.example.project.config

/**
 * Configuration manager that provides secure access to API keys and URLs
 * Uses environment-based configuration for production deployments
 */
object ConfigManager {
    
    data class ApiConfiguration(
        val spreadsheetId: String,
        val apiKey: String,
        val scriptUrl: String,
        val sheetRange: String = "'Data Dump'!A:H"
    )
    
    private var _config: ApiConfiguration? = null
    
    /**
     * Get the current API configuration
     * This will use environment variables in production or fallback to development config
     */
    fun getConfig(): ApiConfiguration {
        if (_config == null) {
            _config = ApiConfiguration(
                spreadsheetId = Environment.getSpreadsheetId(),
                apiKey = Environment.getApiKey(),
                scriptUrl = Environment.getScriptUrl()
            )
        }
        return _config!!
    }
    
    /**
     * Override configuration (useful for testing)
     */
    fun setConfig(config: ApiConfiguration) {
        _config = config
    }
    
    /**
     * Check if we're running with production configuration
     */
    fun isProductionConfig(): Boolean {
        val config = getConfig()
        return config.spreadsheetId != ApiConfig.SPREADSHEET_ID ||
               config.apiKey != ApiConfig.API_KEY ||
               config.scriptUrl != ApiConfig.SCRIPT_URL
    }
}