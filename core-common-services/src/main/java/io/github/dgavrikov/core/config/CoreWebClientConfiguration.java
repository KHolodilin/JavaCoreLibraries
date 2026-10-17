package io.github.dgavrikov.core.config;

import io.github.dgavrikov.core.http.PathExclusion;
import io.github.dgavrikov.core.http.WebResponseHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreWebClientConfiguration {

    @Bean("webResponseHandler")
    public WebResponseHandler webResponseHandler(PathExclusion pathExclusion){
        return new WebResponseHandler(pathExclusion);
    }
}
