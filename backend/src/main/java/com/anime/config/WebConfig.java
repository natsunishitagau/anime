package com.anime.config;

import com.anime.interceptor.VisitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final VisitInterceptor visitInterceptor;

    public WebConfig(VisitInterceptor visitInterceptor) {
        this.visitInterceptor = visitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(visitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/auth/**", "/static/**", "/error");
    }
}
