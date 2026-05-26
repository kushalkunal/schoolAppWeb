package in.schoolapp.fee;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Cron wrapper for {@link LateFeeService#applyForAll()}. Disabled by default — operators
 * flip {@code app.fee.late-fee.enabled=true} to switch it on (and can crank the schedule
 * via {@code app.fee.late-fee.cron} if 06:30 IST doesn't suit them).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.fee.late-fee.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LateFeeScheduler {

    private final LateFeeService lateFeeService;

    /**
     * Default: 06:30 IST every day. Override via {@code app.fee.late-fee.cron}.
     */
    @Scheduled(cron = "${app.fee.late-fee.cron:0 30 6 * * *}", zone = "Asia/Kolkata")
    public void tick() {
        long t0 = System.currentTimeMillis();
        Map<UUID, Integer> perSchool = lateFeeService.applyForAll();
        long ms = System.currentTimeMillis() - t0;
        int total = perSchool.values().stream().mapToInt(Integer::intValue).sum();
        log.info("[LATE-FEE-CRON] applied={} across {} schools in {}ms", total, perSchool.size(), ms);
    }
}
