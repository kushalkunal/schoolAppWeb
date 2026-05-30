package in.schoolapp.academics;

import in.schoolapp.fee.event.FeePaymentCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for fee payment events and automatically unblocks / regenerates admit cards
 * for students whose outstanding dues have been cleared.
 *
 * <p>Runs asynchronously so it does not slow down the payment path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmitCardFeeListener {

    private final AdmitCardService admitCardService;

    @Async
    @EventListener
    public void onFeePayment(FeePaymentCreatedEvent event) {
        try {
            admitCardService.recheckAfterFeePayment(event.studentId());
        } catch (Exception ex) {
            log.warn("[AdmitCardFeeListener] recheckAfterFeePayment failed for student={}: {}",
                event.studentId(), ex.getMessage());
        }
    }
}
