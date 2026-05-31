package in.schoolapp.documents;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Dedicated Thymeleaf engine for PDF documents, separate from any web-view engine.
 *
 * <p>It is a {@link SpringTemplateEngine} (SpEL dialect) on purpose: the document templates use
 * SpEL idioms — the Elvis operator ({@code a ?: b}) and {@code ${map.key}} access — that the
 * plain OGNL {@code StandardDialect} cannot evaluate. {@link DocumentService} injects this bean
 * via {@code @Qualifier("documentTemplateEngine")}; if it is ever absent the service falls back
 * to an equivalent engine built on demand.
 */
@Configuration
public class DocumentTemplateConfig {

    @Bean(name = "documentTemplateEngine")
    public SpringTemplateEngine documentTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
