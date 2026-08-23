package nz.ac.auckland.se310.fairshare.service;


import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.*;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberConflictException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.GroupNotFoundException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.Settlement;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class ExpenseGroupService {

    private static class PersonBalance {
        Long name;
        BigDecimal amount;

        PersonBalance(Long name, BigDecimal amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    private final ExpenseGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final SettlementRepository settlementRepository;

    public ExpenseGroupService(ExpenseGroupRepository groupRepository, UserRepository userRepository, ExpenseService expenseService, SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.settlementRepository = settlementRepository;
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

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        Map<Long, BigDecimal> memberBalances = computeEffectiveBalances(groupId, group, expenseService.getExpensesForGroup(groupId, currentUserId));
        return group.getMembers().stream()
                .map(member -> toMemberResponse(member, currentUserId, memberBalances))
                .sorted((first, second) -> first.username().compareToIgnoreCase(second.username()))
                .toList();
    }


    @Transactional
    public GroupMemberResponse addMember(Long groupId, String identifier, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        User user = findUser(identifier.trim());

        if (group.hasMember(user.getId())) {
            throw new GroupMemberConflictException("User is already a member of this group");
        }

        group.addMember(user);
        groupRepository.save(group);
        Map<Long, BigDecimal> memberBalances = computeEffectiveBalances(group.getId(), group, expenseService.getExpensesForGroup(group.getId(), currentUserId));
        return toMemberResponse(group.getMember(user.getId()), currentUserId, memberBalances);
    }

    @Transactional
    public void removeMember(Long groupId, Long memberUserId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        UserInGroup member = group.getMember(memberUserId);

        if (member == null) {
            throw new GroupMemberNotFoundException("Group member not found");
        }
        if (group.getMembers().size() == 1) {
            throw new GroupMemberConflictException("A group must have at least one member");
        }
        if (member.hasOutstandingBalance()) {
            throw new GroupMemberConflictException(
                    "The member's balance must be settled before removal");
        }

        group.removeMember(member);
        groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<MemberBalance> getBalances(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        Map<Long, BigDecimal> memberBalances = computeEffectiveBalances(groupId, group, null);
        return group.getMembers().stream()
                .sorted(Comparator.comparing(member -> member.getUser().getId()))
                .map(member -> new MemberBalance(member.getUser().getId(), memberBalances.getOrDefault(member.getUser().getId(), BigDecimal.ZERO)))
                .toList();
    }

    public List<SettlementLine> computeSettlement(Long groupId, Long currentUserId, SettlementRequest request) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);

        Map<Long, BigDecimal> effectiveBalances = computeEffectiveBalances(groupId, group, expenseService.getExpensesForGroup(groupId, currentUserId));
        List<SettlementLine> settlementPlan = calculateSettlements(effectiveBalances);
        persistSettlementPlan(group, groupId, settlementPlan);
        return settlementPlan;
    }

    @Transactional
    public void markSettlementPaid(Long groupId, Long fromUserId, Long toUserId, Long currentUserId) {
        // Only the payer or the recipient may mark the settlement as paid (AC5, AC8)
        if (!List.of(fromUserId, toUserId).contains(currentUserId)) {
            throw new GroupMemberConflictException("User not allowed to marked settlement as paid");
        }

        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);

        Settlement settlement = findOpenSettlement(groupId, fromUserId, toUserId);
        if (settlement == null) {
            settlement = findOpenSettlement(groupId, toUserId, fromUserId);
        }
        if (settlement == null) {
            throw new IllegalArgumentException("No open settlement found for this user pair");
        }

        // Apply the payment to persisted member net balances so stored balances reflect the transfer
        Long payerId = settlement.getFromUser().getId();
        Long recipientId = settlement.getToUser().getId();

        UserInGroup payer = group.getMember(payerId);
        UserInGroup recipient = group.getMember(recipientId);

        BigDecimal amount = settlement.getAmount() == null ? BigDecimal.ZERO : settlement.getAmount();

        if (payer != null) {
            BigDecimal old = payer.getNetBalance() == null ? BigDecimal.ZERO : payer.getNetBalance();
            payer.adjustNetBalance(old.subtract(amount));
        }
        if (recipient != null) {
            BigDecimal old = recipient.getNetBalance() == null ? BigDecimal.ZERO : recipient.getNetBalance();
            recipient.adjustNetBalance(old.add(amount));
        }

        // Mark the settlement as paid and persist changes
        settlement.setSettlementDate(LocalDate.now());
        settlementRepository.save(settlement);
        groupRepository.save(group);
    }

    private Map<Long, BigDecimal> computeEffectiveBalances(Long groupId, ExpenseGroup group, List<ExpenseResponse> groupExpenses) {
        // Start from the persisted per-member net balance (expenses are already applied)
        Map<Long, BigDecimal> balances = new HashMap<>();

        // Reconstruct from supplied expenses (used when computing a settlement plan)
        for (UserInGroup member : group.getMembers()) {
            balances.put(member.getUser().getId(), BigDecimal.ZERO);
        }

        List<ExpenseResponse> expenses = groupExpenses == null ? Collections.emptyList() : groupExpenses;
        for (ExpenseResponse expense : expenses) {
            if (expense == null) {
                continue;
            }

            List<Long> participantIds = expense.participantUserIds() == null || expense.participantUserIds().isEmpty()
                    ? group.getMembers().stream().map(member -> member.getUser().getId()).sorted().toList()
                    : expense.participantUserIds().stream().distinct().sorted().toList();
            if (participantIds.isEmpty()) {
                continue;
            }

            BigDecimal expenseAmount = expense.amount() == null ? BigDecimal.ZERO : expense.amount().setScale(2, RoundingMode.HALF_UP);

            // Reconstruct cent-accurate split
            long totalCents = expenseAmount.movePointRight(2).longValueExact();
            long baseShare = totalCents / participantIds.size();
            long extraCents = totalCents % participantIds.size();

            if (expense.paidByUserId() != null) {
                balances.merge(expense.paidByUserId(), expenseAmount.negate(), BigDecimal::add);
            }

            for (int i = 0; i < participantIds.size(); i++) {
                long shareCents = baseShare + (i < extraCents ? 1 : 0);
                BigDecimal share = BigDecimal.valueOf(shareCents, 2);
                balances.merge(participantIds.get(i), share, BigDecimal::add);
            }
        }

        // Apply settlements: open settlements increase debtor and decrease creditor;
        // historical (paid) settlements cancel previous debts (subtract from payer, add to payee).
        for (Settlement settlement : settlementRepository.findByGroupId(groupId)) {
            if (settlement.getSettlementDate() == null) {
                continue;
            }
            Long fromId = settlement.getFromUser().getId();
            Long toId = settlement.getToUser().getId();
            if (settlement.getSettlementDate() == null) {
                balances.merge(fromId, settlement.getAmount(), BigDecimal::add);
                balances.merge(toId, settlement.getAmount().negate(), BigDecimal::add);
            } else {
                balances.merge(fromId, settlement.getAmount().negate(), BigDecimal::add);
                balances.merge(toId, settlement.getAmount(), BigDecimal::add);
            }
        }

        balances.replaceAll((key, value) -> value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP));

        return balances;
    }

    private void persistSettlementPlan(ExpenseGroup group, Long groupId, List<SettlementLine> settlementPlan) {
        for (SettlementLine line : settlementPlan) {
            Long fromId = line.fromUserId();
            Long toId = line.toUserId();
            BigDecimal amount = line.amount();

            Settlement sameOpen = findOpenSettlement(groupId, fromId, toId);
            if (sameOpen != null) {
                sameOpen.setAmount(amount);
                settlementRepository.save(sameOpen);
                cleanupExtraOpenSettlements(groupId, fromId, toId, sameOpen);
                continue;
            }

            Settlement oppositeOpen = findOpenSettlement(groupId, toId, fromId);
            if (oppositeOpen != null) {
                BigDecimal oppositeAmount = oppositeOpen.getAmount();
                int comparison = oppositeAmount.compareTo(amount);

                if (comparison > 0) {
                    oppositeOpen.setAmount(oppositeAmount.subtract(amount));
                    settlementRepository.save(oppositeOpen);
                    cleanupExtraOpenSettlements(groupId, toId, fromId, oppositeOpen);
                } else if (comparison == 0) {
                    settlementRepository.delete(oppositeOpen);
                    cleanupExtraOpenSettlements(groupId, toId, fromId, oppositeOpen);
                } else {
                    settlementRepository.delete(oppositeOpen);
                    Settlement replacement = new Settlement(group, userRepository.findById(fromId).orElseThrow(), userRepository.findById(toId).orElseThrow(), amount.subtract(oppositeAmount));
                    replacement.setSettlementDate(null);
                    settlementRepository.save(replacement);
                    cleanupExtraOpenSettlements(groupId, fromId, toId, replacement);
                }
                continue;
            }

            Settlement newSettlement = new Settlement(group, userRepository.findById(fromId).orElseThrow(), userRepository.findById(toId).orElseThrow(), amount);
            newSettlement.setSettlementDate(null);
            settlementRepository.save(newSettlement);
            cleanupExtraOpenSettlements(groupId, fromId, toId, newSettlement);
        }
    }

    private Settlement findOpenSettlement(Long groupId, Long fromUserId, Long toUserId) {
        return settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(groupId, fromUserId, toUserId)
                .stream()
                .filter(settlement -> settlement.getSettlementDate() == null)
                .findFirst()
                .orElse(null);
    }

    private void cleanupExtraOpenSettlements(Long groupId, Long fromUserId, Long toUserId, Settlement keptSettlement) {
        settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(groupId, fromUserId, toUserId)
                .stream()
                .filter(settlement -> settlement.getSettlementDate() == null)
                .filter(settlement -> !settlement.equals(keptSettlement))
                .forEach(settlementRepository::delete);
    }

    public static List<SettlementLine> calculateSettlements(Map<Long, BigDecimal> totalExpenses) {
        if (totalExpenses == null || totalExpenses.isEmpty()) {
            return Collections.emptyList();
        }

        List<PersonBalance> debtors = new ArrayList<>();
        List<PersonBalance> creditors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : totalExpenses.entrySet()) {
            BigDecimal amount = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                debtors.add(new PersonBalance(entry.getKey(), amount));
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                creditors.add(new PersonBalance(entry.getKey(), amount.abs()));
            }
        }

        // Sort by amount descending to pair largest debtors with largest creditors first —
        // this tends to minimise the number of transactions compared to arbitrary ID ordering.
        debtors.sort((a, b) -> b.amount.compareTo(a.amount));
        creditors.sort((a, b) -> b.amount.compareTo(a.amount));

        List<SettlementLine> transactions = new ArrayList<>();
        int i = 0;
        int j = 0;

        while (i < debtors.size() && j < creditors.size()) {
            PersonBalance debtor = debtors.get(i);
            PersonBalance creditor = creditors.get(j);

            BigDecimal payment = debtor.amount.min(creditor.amount).setScale(2, RoundingMode.HALF_UP);
            if (payment.compareTo(BigDecimal.ZERO) > 0) {
                transactions.add(new SettlementLine(debtor.name, creditor.name, payment));
            }

            debtor.amount = debtor.amount.subtract(payment);
            creditor.amount = creditor.amount.subtract(payment);

            if (debtor.amount.compareTo(new BigDecimal("0.005")) < 0) i++;
            if (creditor.amount.compareTo(new BigDecimal("0.005")) < 0) j++;
        }

        return transactions;
    }

    private Map<Long, BigDecimal> computeBalancesIncludingSettlements(ExpenseGroup group) {
        Map<Long, BigDecimal> balances = new HashMap<>();

        for (Settlement settlement : settlementRepository.findByGroupId(group.getId())) {
            if (settlement.getSettlementDate() != null) {
                continue;
            }
            Long fromUserId = settlement.getFromUser().getId();
            Long toUserId = settlement.getToUser().getId();
            balances.merge(fromUserId, settlement.getAmount(), BigDecimal::add);
            balances.merge(toUserId, settlement.getAmount().negate(), BigDecimal::add);
        }

        return balances;
    }

    private ExpenseGroup requireMemberGroup(Long groupId, Long currentUserId) {
        return groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);
    }

    private User findUser(String identifier) {
        return userRepository.findByEmailIgnoreCase(identifier)
                .orElseGet(() -> findUserByUsername(identifier));
    }

    private User findUserByUsername(String username) {
        List<User> matches = userRepository.findAllByUsernameIgnoreCase(username);
        if (matches.isEmpty()) {
            throw new GroupMemberNotFoundException("No matching user was found");
        }
        if (matches.size() > 1) {
            throw new GroupMemberConflictException(
                    "Multiple users match that username; use an email address");
        }
        return matches.getFirst();
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

    private GroupMemberResponse toMemberResponse(UserInGroup member, Long currentUserId, Map<Long, BigDecimal> memberBalances) {
        User user = member.getUser();
        return new GroupMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                memberBalances.getOrDefault(user.getId(), BigDecimal.ZERO),
                user.getId().equals(currentUserId));
    }

}
