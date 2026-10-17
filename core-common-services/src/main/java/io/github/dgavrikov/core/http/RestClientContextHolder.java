package io.github.dgavrikov.core.http;

import org.apache.commons.lang3.StringUtils;


public class RestClientContextHolder {

    private static final ThreadLocal<String> CURRENT_OUTGOING_URI = new ThreadLocal<>();

    public static void setUri(String uri) {
        if (!StringUtils.isBlank(uri))
            CURRENT_OUTGOING_URI.set(uri);
    }

    public static String getUri() {
        return CURRENT_OUTGOING_URI.get();
    }

    public static void clear() {
        CURRENT_OUTGOING_URI.remove();
    }
}
