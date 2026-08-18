package nz.ac.auckland.se310.fairshare.service;


import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.GroupResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupNotFoundException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.math.BigDecimal;

@Service
public class ExpenseGroupService {

    private final ExpenseGroupRepository groupRepository;
    private final UserRepository userRepository;

    public ExpenseGroupService(ExpenseGroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + creatorId));

        ExpenseGroup group = new ExpenseGroup(
                request.name().trim(),
                request.description(),
                creator.getCurrency(),   // AC: base currency defaults from the creator
                creator);

        return toResponse(groupRepository.save(group));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsForUser(Long userId) {
        return groupRepository.findByMembersUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long groupId, Long userId) {
        return groupRepository.findByIdAndMembersUserId(groupId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new GroupNotFoundException(groupId));  // AC8
    }

    /**
     * Compute a settlement plan that clears the provided balances.
     * The request must be authorised (caller must be a group member) before calling.
     * Algorithm: greedy match largest creditor with largest debtor producing a minimal set of transfers.
     */
    @Transactional(readOnly = true)
    public List<nz.ac.auckland.se310.fairshare.dto.SettlementLine> computeSettlement(Long groupId, Long userId, List<nz.ac.auckland.se310.fairshare.dto.MemberBalance> balances) {
        // Verify membership / existence (throws GroupNotFoundException if not a member)
        getGroup(groupId, userId);

        // Defensive copy and normalize to 2 decimal places
        List<nz.ac.auckland.se310.fairshare.dto.MemberBalance> copy = balances.stream()
                .map(b -> new nz.ac.auckland.se310.fairshare.dto.MemberBalance(b.userId(), b.balance().setScale(2, BigDecimal.ROUND_HALF_EVEN)))
                .toList();

        // Separate creditors and debtors
        Deque<MemberAmount> creditors = new ArrayDeque<>();
        Deque<MemberAmount> debtors = new ArrayDeque<>();

        for (nz.ac.auckland.se310.fairshare.dto.MemberBalance mb : copy) {
            int cmp = mb.balance().compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new MemberAmount(mb.userId(), mb.balance()));
            } else if (cmp < 0) {
                debtors.add(new MemberAmount(mb.userId(), mb.balance()));
            }
        }

        // Sort creditors descending, debtors ascending (most negative first)
        List<MemberAmount> credList = new ArrayList<>(creditors);
        List<MemberAmount> debtList = new ArrayList<>(debtors);
        credList.sort(Comparator.comparing(a -> a.amount, Comparator.reverseOrder()));
        debtList.sort(Comparator.comparing(a -> a.amount));
        creditors = new ArrayDeque<>(credList);
        debtors = new ArrayDeque<>(debtList);

        List<nz.ac.auckland.se310.fairshare.dto.SettlementLine> result = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            MemberAmount cred = creditors.peekFirst();
            MemberAmount debt = debtors.peekFirst();

            BigDecimal creditAmt = cred.amount;
            BigDecimal debtAmtAbs = debt.amount.abs();
            BigDecimal transfer = creditAmt.min(debtAmtAbs).setScale(2, BigDecimal.ROUND_HALF_EVEN);

            // from debtor to creditor
            result.add(new nz.ac.auckland.se310.fairshare.dto.SettlementLine(debt.userId, cred.userId, transfer));

            // adjust
            cred.amount = cred.amount.subtract(transfer);
            debt.amount = debt.amount.add(transfer); // debt.amount is negative

            if (cred.amount.compareTo(new BigDecimal("0.00")) == 0) creditors.removeFirst();
            if (debt.amount.compareTo(new BigDecimal("0.00")) == 0) debtors.removeFirst();
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<nz.ac.auckland.se310.fairshare.dto.MemberBalance> getMemberBalances(Long groupId, Long userId) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, userId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Placeholder: no expenses implemented yet — return zero balances for every member
        return group.getMembers().stream()
                .map(m -> new nz.ac.auckland.se310.fairshare.dto.MemberBalance(m.getUser().getId(), BigDecimal.ZERO.setScale(2)))
                .toList();
    }

    private GroupResponse toResponse(ExpenseGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getGroupName(),
                group.getDescription(),
                group.getBaseCurrency().name(),
                group.getCreatedAt(),
                group.getMembers().size());
    }

    // simple mutable helper for algorithm
    private static class MemberAmount {
        final Long userId;
        BigDecimal amount;
        MemberAmount(Long userId, BigDecimal amount) { this.userId = userId; this.amount = amount; }
    }
}