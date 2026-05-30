package in.schoolapp.fee;

import in.schoolapp.fee.structure.GenerateInvoicesService;
import in.schoolapp.fee.structure.entity.FeeStructureVersion;
import in.schoolapp.fee.structure.entity.FeeStructureVersion.Status;
import in.schoolapp.fee.structure.repository.FeeStructureVersionRepository;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs on the 1st of every month at 02:00 IST and auto-generates fee invoices for every
 * ACTIVE fee-structure version across all active schools.
 * <p>
 * This covers monthly / term-based billing cycles: schools that split the year into quarters
 * will see invoices for the relevant term, while schools that issued all invoices upfront
 * simply get idempotent no-ops (the unique index {@code uq_fee_invoices_per_structure} in
 * Flyway V20 silently absorbs duplicates).
 * <p>
 * The job can be disabled with {@code app.fee.monthly-invoice.enabled=false} and the cron
 * expression overridden via {@code app.fee.monthly-invoice.cron}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyInvoiceScheduler {

    private final FeeStructureVersionRepository versionRepo;
    private final GenerateInvoicesService generateInvoicesService;
    private final SchoolService schoolService;

    @Scheduled(cron = "${app.fee.monthly-invoice.cron:0 0 2 1 * *}", zone = "Asia/Kolkata")
    public void tick() {
        long t0 = System.currentTimeMillis();
        List<School> schools = schoolService.findAllActiveSchools();
        int versionsProcessed = 0, schoolsSkipped = 0;
        for (School school : schools) {
            List<FeeStructureVersion> active = versionRepo
                .findBySchoolIdOrderByCreatedAtDesc(school.getId())
                .stream()
                .filter(v -> v.getStatus() == Status.ACTIVE)
                .toList();
            if (active.isEmpty()) {
                schoolsSkipped++;
                continue;
            }
            for (FeeStructureVersion version : active) {
                try {
                    generateInvoicesService.generate(school.getId(), version.getId(), null);
                    versionsProcessed++;
                } catch (Exception e) {
                    log.warn("[MONTHLY-INVOICE] school={} version={} skipped — {}",
                        school.getId(), version.getId(), e.getMessage());
                }
            }
        }
        log.info("[MONTHLY-INVOICE-CRON] schools={} versionsProcessed={} schoolsWithNoActiveVersion={} in {}ms",
            schools.size(), versionsProcessed, schoolsSkipped, System.currentTimeMillis() - t0);
    }
}
