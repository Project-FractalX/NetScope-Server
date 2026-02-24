package org.fractalx.netscope.security;

import org.fractalx.netscope.config.NetScopeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Validates API keys against the configured list of valid keys.
 */
public class ApiKeyValidator {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyValidator.class);

    private final List<String> validKeys;

    public ApiKeyValidator(NetScopeConfig config) {
        this.validKeys = config.getSecurity().getApiKey().getKeys();
        logger.info("NetScope: API key validator activated ({} key(s) configured)", validKeys.size());
    }

    public boolean isValid(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return validKeys.contains(apiKey);
    }
}
