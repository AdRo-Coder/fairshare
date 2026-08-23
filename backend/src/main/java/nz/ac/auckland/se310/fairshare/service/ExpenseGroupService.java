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
        return group.getMembers().stream()
                .map(member -> toMemberResponse(member, currentUserId))
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
        return toMemberResponse(group.getMember(user.getId()), currentUserId);
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
        return group.getMembers().stream()
                .sorted(Comparator.comparing(member -> member.getUser().getId()))
                .map(member -> new MemberBalance(member.getUser().getId(), member.getNetBalance()))
                .toList();
    }

    public List<SettlementLine> computeSettlement(Long groupId, Long currentUserId, SettlementRequest request) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);

        List<ExpenseResponse> groupExpenses = expenseService.getExpensesForGroup(groupId, currentUserId);
        Map<Long, BigDecimal> paidPerUser = new HashMap<>();
        group.getMembers().forEach(m -> paidPerUser.put(m.getUser().getId(), BigDecimal.ZERO));

        for (ExpenseResponse expense : groupExpenses) {
            paidPerUser.merge(expense.paidByUserId(), expense.amount(), BigDecimal::add);
        }

        int count = paidPerUser.size();
        BigDecimal totalCost = paidPerUser.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageShare = totalCost.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);

        Map<Long, BigDecimal> netPositions = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> e : paidPerUser.entrySet()) {
            netPositions.put(e.getKey(), e.getValue().subtract(averageShare));
        }

        List<Settlement> settlements = settlementRepository.findByGroupId(groupId);
        for (Settlement s : settlements) {
            if (s.getSettlementDate() != null) {
                Long fromId = s.getFromUser().getId();
                Long toId = s.getToUser().getId();
                BigDecimal amt = s.getAmount();
                netPositions.put(fromId, netPositions.getOrDefault(fromId, BigDecimal.ZERO).add(amt));
                netPositions.put(toId, netPositions.getOrDefault(toId, BigDecimal.ZERO).subtract(amt));
            }
        }

        Map<Long, BigDecimal> effectivePaid = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> e : netPositions.entrySet()) {
            effectivePaid.put(e.getKey(), e.getValue().add(averageShare));
        }

        List<SettlementLine> result = calculateSettlements(effectivePaid);

        for (SettlementLine line : result) {
            Long fromId = line.fromUserId();
            Long toId = line.toUserId();
            BigDecimal amt = line.amount();

            Settlement sameOpen = findOpenSettlement(groupId, fromId, toId);
            if (sameOpen != null) {
                sameOpen.setAmount(amt);
                settlementRepository.save(sameOpen);
                cleanupExtraOpenSettlements(groupId, fromId, toId, sameOpen);
                continue;
            }

            Settlement oppOpen = findOpenSettlement(groupId, toId, fromId);
            if (oppOpen != null) {
                BigDecimal oppAmt = oppOpen.getAmount();
                int cmp = oppAmt.compareTo(amt);
                if (cmp > 0) {
                    oppOpen.setAmount(oppAmt.subtract(amt));
                    settlementRepository.save(oppOpen);
                    cleanupExtraOpenSettlements(groupId, toId, fromId, oppOpen);
                } else if (cmp == 0) {
                    settlementRepository.delete(oppOpen);
                    cleanupExtraOpenSettlements(groupId, toId, fromId, oppOpen);
                } else {
                    settlementRepository.delete(oppOpen);
                    Settlement newS = new Settlement(group, userRepository.findById(fromId).orElseThrow(), userRepository.findById(toId).orElseThrow(), amt.subtract(oppAmt));
                    newS.setSettlementDate(null);
                    settlementRepository.save(newS);
                    cleanupExtraOpenSettlements(groupId, fromId, toId, newS);
                }
                continue;
            }

            Settlement newS = new Settlement(group, userRepository.findById(fromId).orElseThrow(), userRepository.findById(toId).orElseThrow(), amt);
            newS.setSettlementDate(null);
            settlementRepository.save(newS);
            cleanupExtraOpenSettlements(groupId, fromId, toId, newS);
        }

        return result;
    }

    @Transactional
    public void markSettlementPaid(Long groupId, Long fromUserId, Long toUserId, Long currentUserId) {
        requireMemberGroup(groupId, currentUserId);

        Settlement settlement = findOpenSettlement(groupId, fromUserId, toUserId);
        if (settlement == null) {
            settlement = findOpenSettlement(groupId, toUserId, fromUserId);
        }
        if (settlement == null) {
            throw new IllegalArgumentException("No open settlement found for this user pair");
        }

        settlement.setSettlementDate(LocalDate.now());
        settlementRepository.save(settlement);
    }

    private Settlement findOpenSettlement(Long groupId, Long fromUserId, Long toUserId) {
        return settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(groupId, fromUserId, toUserId)
                .stream()
                .filter(s -> s.getSettlementDate() == null)
                .findFirst()
                .orElse(null);
    }

    private void cleanupExtraOpenSettlements(Long groupId, Long fromUserId, Long toUserId, Settlement keptSettlement) {
        settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(groupId, fromUserId, toUserId)
                .stream()
                .filter(s -> s.getSettlementDate() == null)
                .filter(s -> !s.equals(keptSettlement))
                .forEach(settlementRepository::delete);
    }

    public static List<SettlementLine> calculateSettlements(Map<Long, BigDecimal> totalExpenses) {
        if (totalExpenses == null || totalExpenses.isEmpty()) {
            return Collections.emptyList();
        }

        int count = totalExpenses.size();

        // 1. Calculate total expenses and average share per person
        BigDecimal totalCost = totalExpenses.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Divide by total people, keeping precision to 4 decimal places before final rounding
        BigDecimal averageShare = totalCost.divide(
                BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP
        );

        // 2. Separate participants into debtors and creditors
        List<PersonBalance> debtors = new ArrayList<>();
        List<PersonBalance> creditors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : totalExpenses.entrySet()) {
            BigDecimal netBalance = entry.getValue().subtract(averageShare);

            // Compare to zero using compareTo
            if (netBalance.compareTo(BigDecimal.ZERO) < 0) {
                // Net negative -> Debtor (store positive debt value)
                debtors.add(new PersonBalance(entry.getKey(), netBalance.abs()));
            } else if (netBalance.compareTo(BigDecimal.ZERO) > 0) {
                // Net positive -> Creditor
                creditors.add(new PersonBalance(entry.getKey(), netBalance));
            }
        }

        // 3. Greedy settlement using two pointers
        List<SettlementLine> transactions = new ArrayList<>();
        int i = 0; // Debtor pointer
        int j = 0; // Creditor pointer

        while (i < debtors.size() && j < creditors.size()) {
            PersonBalance debtor = debtors.get(i);
            PersonBalance creditor = creditors.get(j);

            // Payment is the minimum between debt owed and debt due
            BigDecimal payment = debtor.amount.min(creditor.amount)
                    .setScale(2, RoundingMode.HALF_UP);

            if (payment.compareTo(BigDecimal.ZERO) > 0) {
                transactions.add(new SettlementLine(debtor.name, creditor.name, payment));
            }

            debtor.amount = debtor.amount.subtract(payment);
            creditor.amount = creditor.amount.subtract(payment);

            // Move pointer if balance is fully settled (threshold for remaining fractions)
            if (debtor.amount.compareTo(new BigDecimal("0.005")) < 0) i++;
            if (creditor.amount.compareTo(new BigDecimal("0.005")) < 0) j++;
        }

        return transactions;
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

    private GroupMemberResponse toMemberResponse(UserInGroup member, Long currentUserId) {
        User user = member.getUser();
        return new GroupMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                member.getNetBalance(),
                user.getId().equals(currentUserId));
    }

}
