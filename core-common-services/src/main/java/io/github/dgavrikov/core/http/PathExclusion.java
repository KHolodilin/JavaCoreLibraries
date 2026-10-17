package io.github.dgavrikov.core.http;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

@Slf4j
public class PathExclusion {
    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final List<String> excludePaths;

    public PathExclusion(){
        this.excludePaths = Collections.emptyList();
    }

    public PathExclusion(List<String> paths) {
        this.excludePaths = paths;
    }

    public boolean isExcluded() {
        return isExcluded(excludePaths);
    }

    public boolean isExcludedPatterns(List<String> patterns) {
        return isExcluded(patterns);
    }

    public static boolean isExcluded(List<String> patterns) {
        try {
            if (CollectionUtils.isEmpty(patterns))
                return false;

            var restClientPath = RestClientContextHolder.getUri();
            if (!StringUtils.isBlank(restClientPath)) {
                return matchUrl(patterns, restClientPath);
            }

            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes == null)
                return false;

            if (attributes instanceof ServletRequestAttributes sra) {
                var request = sra.getRequest();
                return matchUrl(patterns, request.getRequestURI());
            }

            return false;
        } catch (Exception e) {
            log.warn(e.getLocalizedMessage(), e);
            return false;
        }
    }

    public static boolean isLogBody(List<String> paths, String path) {
        return !match(paths, path);
    }

    public static boolean matchUrl(List<String> paths, String path) {
        return match(paths, path);
    }

    public static boolean match(List<String> patterns, String path) {
        if(StringUtils.isBlank(path))
            return false;
        for (var pattern : patterns) {
            if(antPathMatcher.match(pattern, path))
                return true;
        }
        return false;
    }
}
