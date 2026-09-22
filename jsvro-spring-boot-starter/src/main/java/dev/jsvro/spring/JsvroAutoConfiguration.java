package dev.jsvro.spring;

import dev.jsvro.core.JsvroCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({JsonMapper.class, HttpMessageConverter.class})
@ConditionalOnProperty(prefix = "jsvro", name = "enabled", matchIfMissing = true)
public class JsvroAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JsvroCodec jsvroCodec(JsonMapper jsonMapper) {
        return new JsvroCodec(jsonMapper);
    }

    @Bean
    ServerHttpMessageConvertersCustomizer jsvroServerHttpMessageConvertersCustomizer(
            JsonMapper jsonMapper,
            JsvroCodec codec) {
        // Append rather than register as a converter bean. Spring Boot automatically adds
        // HttpMessageConverter beans to MVC; doing both would register JSVRO twice.
        // Appending also means Accept: */* keeps Spring's normal JSON representation.
        return builder -> builder.configureMessageConvertersList(
                converters -> converters.add(new JsvroHttpMessageConverter(jsonMapper, codec)));
    }
}
