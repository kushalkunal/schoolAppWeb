package in.schoolapp.documents;

import in.schoolapp.branding.BrandingService;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.documents.entity.DocumentTemplate;
import in.schoolapp.documents.repository.DocumentTemplateRepository;
import in.schoolapp.pdf.PdfTemplateRenderer;
import in.schoolapp.storage.FileStorageService;
import in.schoolapp.storage.dto.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Generic document-generation entry point. The flow is always:
 *
 * <pre>
 *   resolve template (per-school override OR classpath default)
 *   → render Thymeleaf HTML with the supplied model
 *   → PDF bytes via {@link PdfTemplateRenderer}
 *   → upload to {@link FileStorageService} under documents/{schoolId}/{type}/{uuid}.pdf
 *   → return signed URL + raw bytes
 * </pre>
 *
 * Per-tenant template override is by inserting a row in {@code document_templates}.
 * Per-tenant PDF engine override flows through {@code TenantProviderConfig} on the
 * future {@code DOCUMENT} concern (not wired yet — wait until a school actually needs
 * a different renderer).
 */
@Slf4j
@Service
public class DocumentService {

    private final DocumentTemplateRepository templateRepository;
    private final PdfTemplateRenderer pdfRenderer;
    private final FileStorageService fileStorage;
    private final ApplicationContext applicationContext;
    /**
     * Slice 22: injects branding (logo URL, colors, GSTIN, contact strip) into every
     * Thymeleaf model so receipts, TCs, hall tickets and report cards all carry the
     * school's white-label identity without each template having to fetch it.
     */
    private final BrandingService brandingService;

    public DocumentService(DocumentTemplateRepository templateRepository,
                           PdfTemplateRenderer pdfRenderer,
                           FileStorageService fileStorage,
                           ApplicationContext applicationContext,
                           BrandingService brandingService) {
        this.templateRepository = templateRepository;
        this.pdfRenderer = pdfRenderer;
        this.fileStorage = fileStorage;
        this.applicationContext = applicationContext;
        this.brandingService = brandingService;
    }

    /** Lazy-init Thymeleaf engines so tests can override without booting auto-config. */
    @Autowired(required = false)
    @Qualifier("documentTemplateEngine")
    private TemplateEngine sharedClasspathEngine;

    /**
     * Render + store. Returns a presigned URL valid for 24 hours and the raw bytes (handy
     * for inlining or attaching to WhatsApp/email).
     *
     * @param schoolId   the tenant whose template override we consult
     * @param type       which document kind
     * @param model      variables exposed to the Thymeleaf template
     * @return URL + bytes
     */
    public GeneratedDocument generate(UUID schoolId, DocumentType type, Map<String, Object> model) {
        String html = renderHtml(schoolId, type, model);
        byte[] pdf = pdfRenderer.render(html, "classpath:/templates/");
        String key = "documents/" + schoolId + "/" + type.name().toLowerCase() + "/" + UUID.randomUUID() + ".pdf";
        StoredFile stored = fileStorage.store(key, pdf, "application/pdf");
        log.info("Generated {} for school={} size={} bytes key={}", type, schoolId, pdf.length, key);
        return new GeneratedDocument(stored.url(), key, pdf);
    }

    /**
     * Render only — returns the HTML string. Useful for HTML previews in the admin UI
     * before committing to PDF generation, or for sending an HTML email body.
     */
    public String renderHtml(UUID schoolId, DocumentType type, Map<String, Object> model) {
        Context ctx = new Context();
        model.forEach(ctx::setVariable);
        // Slice 22: every template gets a `branding` model variable. Templates that don't
        // reference it are unaffected. Failure to build branding never blocks the doc —
        // an empty map is returned so the default classpath template still renders.
        try {
            ctx.setVariable("branding", brandingService.brandingModel(schoolId));
        } catch (Exception e) {
            log.warn("Branding model unavailable for school={} ({}). Rendering without it.",
                schoolId, e.getMessage());
            ctx.setVariable("branding", java.util.Map.of());
        }

        // 1. Per-school override?
        return templateRepository.findBySchoolIdAndDocumentType(schoolId, type)
            .map(t -> renderInline(t, ctx))
            .orElseGet(() -> renderClasspath(type, ctx));
    }

    private String renderClasspath(DocumentType type, Context ctx) {
        TemplateEngine engine = sharedClasspathEngine != null ? sharedClasspathEngine : buildClasspathEngine();
        try {
            return engine.process(type.defaultTemplatePath(), ctx);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Template render failed for " + type + ": " + e.getMessage(), e);
        }
    }

    private String renderInline(DocumentTemplate t, Context ctx) {
        TemplateEngine engine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode("HTML");
        engine.setTemplateResolver(resolver);
        try {
            String body = t.getHtmlTemplate();
            if (t.getCssOverride() != null && !t.getCssOverride().isBlank()) {
                body = "<style>" + t.getCssOverride() + "</style>\n" + body;
            }
            return engine.process(body, ctx);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                "Custom template render failed: " + e.getMessage(), e);
        }
    }

    private TemplateEngine buildClasspathEngine() {
        TemplateEngine engine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /** Re-issues a fresh presigned URL for a previously-stored doc key. */
    public String presign(String key, Duration ttl) {
        return fileStorage.presignedUrl(key, ttl);
    }

    public record GeneratedDocument(String url, String storageKey, byte[] bytes) {}
}
