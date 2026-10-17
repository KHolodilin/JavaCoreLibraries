package io.github.dgavrikov.core.config;

import io.github.dgavrikov.core.http.PathExclusion;
import io.github.dgavrikov.core.service.logging.converter.LoggingHttpMessageConverter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final PathExclusion pathExclusion;

    @Override
    public void extendMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        logMessageReplaceConverter(converters, pathExclusion);
    }

    public static void logMessageReplaceConverter(List<HttpMessageConverter<?>> converters, PathExclusion exclusionService) {
        var iterator = converters.listIterator();
        while (iterator.hasNext()) {
            var converter = iterator.next();
            if(converter instanceof MappingJackson2HttpMessageConverter) {
                iterator.remove();
                iterator.add(new LoggingHttpMessageConverter((MappingJackson2HttpMessageConverter) converter, exclusionService));
                break;
            }
        }
    }
}
