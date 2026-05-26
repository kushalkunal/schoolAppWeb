package in.schoolapp.cafeteria;

import in.schoolapp.cafeteria.dto.PlaceOrderRequest;
import in.schoolapp.cafeteria.entity.CafeteriaOrder;
import in.schoolapp.cafeteria.entity.CafeteriaOrder.Status;
import in.schoolapp.cafeteria.entity.MenuItem;
import in.schoolapp.cafeteria.entity.OrderItem;
import in.schoolapp.cafeteria.entity.Wallet;
import in.schoolapp.cafeteria.entity.WalletTransaction;
import in.schoolapp.cafeteria.entity.WalletTransaction.Type;
import in.schoolapp.cafeteria.repository.CafeteriaOrderRepository;
import in.schoolapp.cafeteria.repository.MenuItemRepository;
import in.schoolapp.cafeteria.repository.OrderItemRepository;
import in.schoolapp.cafeteria.repository.WalletRepository;
import in.schoolapp.cafeteria.repository.WalletTransactionRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single service for the cafeteria module. Three flows:
 *
 * <ul>
 *   <li><strong>Menu management</strong> — add/list/update menu items.</li>
 *   <li><strong>Wallet top-up / refund</strong> — append-only ledger; service derives
 *       the new balance from previous + delta to detect drift.</li>
 *   <li><strong>Order placement</strong> — snapshots prices at order time so a later
 *       menu price change doesn't retroactively affect historical orders.</li>
 * </ul>
 *
 * <p>Wallet invariant: a DEBIT that would push balance negative throws
 * {@code VALIDATION_ERROR} — callers should top up first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CafeteriaService {

    private final MenuItemRepository menuRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txnRepository;
    private final CafeteriaOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // ---------------- menu ----------------

    @Transactional
    public MenuItem createMenuItem(UUID tenantId, MenuItem template) {
        template.setSchoolId(tenantId);
        return menuRepository.save(template);
    }

    public List<MenuItem> listMenu(UUID tenantId, boolean onlyAvailable) {
        return onlyAvailable
            ? menuRepository.findBySchoolIdAndAvailableOrderByCategoryAscNameAsc(tenantId, true)
            : menuRepository.findBySchoolIdOrderByCategoryAscNameAsc(tenantId);
    }

    // ---------------- wallet ----------------

    /** Returns the existing wallet or creates one with zero balance. Idempotent per student. */
    @Transactional
    public Wallet getOrCreateWallet(UUID tenantId, UUID studentId) {
        return walletRepository.findBySchoolIdAndStudentId(tenantId, studentId)
            .orElseGet(() -> {
                Wallet w = new Wallet();
                w.setSchoolId(tenantId);
                w.setStudentId(studentId);
                return walletRepository.save(w);
            });
    }

    @Transactional
    public WalletTransaction topUp(UUID tenantId, UUID studentId, long amountPaise, String notes) {
        if (amountPaise <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Top-up amount must be positive");
        }
        Wallet w = getOrCreateWallet(tenantId, studentId);
        w.setBalancePaise(w.getBalancePaise() + amountPaise);
        w.setTotalToppedUpPaise(w.getTotalToppedUpPaise() + amountPaise);
        w.setLastToppedUpAt(OffsetDateTime.now());
        walletRepository.save(w);

        return recordTxn(tenantId, w, Type.TOPUP, amountPaise, null, notes);
    }

    @Transactional
    public WalletTransaction refund(UUID tenantId, UUID walletId, long amountPaise, UUID orderId, String notes) {
        if (amountPaise <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Refund amount must be positive");
        }
        Wallet w = walletRepository.findByIdAndSchoolId(walletId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));
        w.setBalancePaise(w.getBalancePaise() + amountPaise);
        w.setTotalSpentPaise(Math.max(0, w.getTotalSpentPaise() - amountPaise));
        walletRepository.save(w);
        return recordTxn(tenantId, w, Type.REFUND, amountPaise, orderId, notes);
    }

    public List<WalletTransaction> listTransactions(UUID tenantId, UUID walletId) {
        // Tenant-scope first.
        walletRepository.findByIdAndSchoolId(walletId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));
        return txnRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }

    // ---------------- orders ----------------

    @Transactional
    public CafeteriaOrder placeOrder(UUID tenantId, PlaceOrderRequest req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Order must contain at least one item");
        }

        // Snapshot prices at order time.
        Map<UUID, MenuItem> items = menuRepository.findAllById(
            req.items().stream().map(PlaceOrderRequest.Item::menuItemId).toList()
        ).stream().collect(Collectors.toMap(MenuItem::getId, i -> i));

        long total = 0;
        List<OrderItem> lines = new ArrayList<>(req.items().size());
        for (PlaceOrderRequest.Item it : req.items()) {
            MenuItem mi = items.get(it.menuItemId());
            if (mi == null || !mi.getSchoolId().equals(tenantId)) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Menu item " + it.menuItemId() + " not found");
            }
            if (!mi.isAvailable()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Item \"" + mi.getName() + "\" is currently unavailable");
            }
            long line = mi.getPricePaise() * it.quantity();
            total += line;

            OrderItem line_ = new OrderItem();
            line_.setMenuItemId(mi.getId());
            line_.setQuantity(it.quantity());
            line_.setUnitPricePaise(mi.getPricePaise());
            line_.setLineTotalPaise(line);
            lines.add(line_);
        }

        // Debit the wallet. Walls of paise can't go negative.
        Wallet w = getOrCreateWallet(tenantId, req.studentId());
        if (w.getBalancePaise() < total) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Insufficient wallet balance: have " + w.getBalancePaise() + " need " + total);
        }
        w.setBalancePaise(w.getBalancePaise() - total);
        w.setTotalSpentPaise(w.getTotalSpentPaise() + total);
        walletRepository.save(w);

        // Persist order + items.
        CafeteriaOrder order = new CafeteriaOrder();
        order.setSchoolId(tenantId);
        order.setStudentId(req.studentId());
        order.setWalletId(w.getId());
        order.setTotalPaise(total);
        order.setStatus(Status.PLACED);
        order.setNotes(req.notes());
        order = orderRepository.save(order);

        for (OrderItem line : lines) {
            line.setOrderId(order.getId());
            orderItemRepository.save(line);
        }
        recordTxn(tenantId, w, Type.DEBIT, total, order.getId(), "Order " + order.getId());

        log.info("Cafeteria order tenant={} student={} total={}", tenantId, req.studentId(), total);
        return order;
    }

    @Transactional
    public CafeteriaOrder cancelOrder(UUID tenantId, UUID orderId, String reason) {
        CafeteriaOrder order = orderRepository.findByIdAndSchoolId(orderId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        if (order.getStatus() != Status.PLACED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Cannot cancel order in status " + order.getStatus());
        }
        order.setStatus(Status.CANCELLED);
        order.setCancelledAt(OffsetDateTime.now());
        order.setCancelReason(reason);
        orderRepository.save(order);
        // Refund the wallet.
        if (order.getWalletId() != null) {
            refund(tenantId, order.getWalletId(), order.getTotalPaise(), orderId, "Order cancelled: " + reason);
        }
        return order;
    }

    @Transactional
    public CafeteriaOrder markFulfilled(UUID tenantId, UUID orderId) {
        CafeteriaOrder order = orderRepository.findByIdAndSchoolId(orderId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        if (order.getStatus() != Status.PLACED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Only PLACED orders can be fulfilled (this is " + order.getStatus() + ")");
        }
        order.setStatus(Status.FULFILLED);
        order.setFulfilledAt(OffsetDateTime.now());
        return orderRepository.save(order);
    }

    public List<CafeteriaOrder> listOrders(UUID tenantId) {
        return orderRepository.findBySchoolIdOrderByPlacedAtDesc(tenantId);
    }

    public List<CafeteriaOrder> listStudentOrders(UUID tenantId, UUID studentId) {
        return orderRepository.findBySchoolIdAndStudentIdOrderByPlacedAtDesc(tenantId, studentId);
    }

    public List<OrderItem> listOrderItems(UUID tenantId, UUID orderId) {
        orderRepository.findByIdAndSchoolId(orderId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        return orderItemRepository.findByOrderId(orderId);
    }

    // ---------------- internal ----------------

    private WalletTransaction recordTxn(UUID tenantId, Wallet w, Type type,
                                        long amount, UUID orderId, String notes) {
        WalletTransaction txn = new WalletTransaction();
        txn.setSchoolId(tenantId);
        txn.setWalletId(w.getId());
        txn.setTxnType(type);
        txn.setAmountPaise(amount);
        txn.setRefOrderId(orderId);
        txn.setBalanceAfterPaise(w.getBalancePaise());
        txn.setNotes(notes);
        return txnRepository.save(txn);
    }
}
