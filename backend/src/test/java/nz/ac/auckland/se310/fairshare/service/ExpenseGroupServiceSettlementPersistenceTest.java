package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.dto.SettlementLine;
import nz.ac.auckland.se310.fairshare.dto.SettlementRequest;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.Settlement;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseGroupServiceSettlementPersistenceTest {

    @Mock
    ExpenseGroupRepository groupRepository;
    @Mock
    SettlementRepository settlementRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ExpenseService expenseService;

    private ExpenseGroupService service;

    private static final Long GROUP_ID = 42L;
    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    private User aliceUser;
    private User bobUser;
    private ExpenseGroup group;

    @Captor
    ArgumentCaptor<Settlement> settlementCaptor;

    private void stubPendingSettlement(Long fromUserId, Long toUserId, Settlement settlement) {
        when(settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(GROUP_ID, fromUserId, toUserId))
                .thenReturn(settlement == null ? List.of() : List.of(settlement));
    }

    @BeforeEach
    void setUp() {
        aliceUser = new User("alice", "pw", "alice@test.com", User.Country.NEW_ZEALAND, User.Currency.NZD);
        bobUser = new User("bob", "pw", "bob@test.com", User.Country.NEW_ZEALAND, User.Currency.NZD);
        ReflectionTestUtils.setField(aliceUser, "id", ALICE);
        ReflectionTestUtils.setField(bobUser, "id", BOB);

        group = new ExpenseGroup("Trip", null, User.Currency.NZD, aliceUser);
        group.addMember(bobUser);
        ReflectionTestUtils.setField(group, "id", GROUP_ID);

        service = new ExpenseGroupService(groupRepository, userRepository, expenseService, settlementRepository);

        lenient().when(groupRepository.findByIdAndMembersUserId(GROUP_ID, ALICE)).thenReturn(Optional.of(group));
        lenient().when(groupRepository.findByIdAndMembersUserId(GROUP_ID, BOB)).thenReturn(Optional.of(group));
        lenient().when(userRepository.findById(ALICE)).thenReturn(Optional.of(aliceUser));
        lenient().when(userRepository.findById(BOB)).thenReturn(Optional.of(bobUser));
        lenient().when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());
        lenient().when(settlementRepository.findByGroupIdAndFromUserIdAndToUserId(anyLong(), anyLong(), anyLong())).thenReturn(Optional.empty());
        lenient().when(settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(anyLong(), anyLong(), anyLong())).thenReturn(List.of());
    }

    @Test
    void oppositePendingLargerIsReduced() {
        when(expenseService.getExpensesForGroup(GROUP_ID, ALICE))
                .thenReturn(List.of(new ExpenseResponse(100L, GROUP_ID, ALICE, "alice", new BigDecimal("100.00"), null, null, null)));

        Settlement opp = new Settlement(group, aliceUser, bobUser, new BigDecimal("70.00"));
        opp.setSettlementDate(null);
        stubPendingSettlement(BOB, ALICE, null);
        stubPendingSettlement(ALICE, BOB, opp);

        List<SettlementLine> result = service.computeSettlement(GROUP_ID, ALICE, new SettlementRequest(List.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fromUserId()).isEqualTo(BOB);
        assertThat(result.get(0).toUserId()).isEqualTo(ALICE);
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(opp.getAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        verify(settlementRepository).save(opp);
    }

    @Test
    void oppositePendingEqualIsDeleted() {
        when(expenseService.getExpensesForGroup(GROUP_ID, ALICE))
                .thenReturn(List.of(new ExpenseResponse(100L, GROUP_ID, ALICE, "alice", new BigDecimal("100.00"), null, null, null)));

        Settlement opp = new Settlement(group, aliceUser, bobUser, new BigDecimal("50.00"));
        opp.setSettlementDate(null);
        stubPendingSettlement(BOB, ALICE, null);
        stubPendingSettlement(ALICE, BOB, opp);

        List<SettlementLine> result = service.computeSettlement(GROUP_ID, ALICE, new SettlementRequest(List.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        verify(settlementRepository).delete(opp);
    }

    @Test
    void oppositePendingSmallerCreatesNewPendingWithRemainder() {
        when(expenseService.getExpensesForGroup(GROUP_ID, ALICE))
                .thenReturn(List.of(new ExpenseResponse(100L, GROUP_ID, ALICE, "alice", new BigDecimal("100.00"), null, null, null)));

        Settlement opp = new Settlement(group, aliceUser, bobUser, new BigDecimal("20.00"));
        opp.setSettlementDate(null);
        stubPendingSettlement(BOB, ALICE, null);
        stubPendingSettlement(ALICE, BOB, opp);

        List<SettlementLine> result = service.computeSettlement(GROUP_ID, ALICE, new SettlementRequest(List.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        verify(settlementRepository).delete(opp);
        verify(settlementRepository).save(settlementCaptor.capture());
        Settlement saved = settlementCaptor.getValue();
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(saved.getSettlementDate()).isNull();
    }

    @Test
    void sameDirectionPendingIsUpdated() {
        when(expenseService.getExpensesForGroup(GROUP_ID, ALICE))
                .thenReturn(List.of(new ExpenseResponse(100L, GROUP_ID, ALICE, "alice", new BigDecimal("100.00"), null, null, null)));

        Settlement same = new Settlement(group, bobUser, aliceUser, new BigDecimal("10.00"));
        same.setSettlementDate(null);
        stubPendingSettlement(BOB, ALICE, same);

        List<SettlementLine> result = service.computeSettlement(GROUP_ID, ALICE, new SettlementRequest(List.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(same.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        verify(settlementRepository).save(same);
    }

    @Test
    void historicalOnlyCreatesPending() {
        when(expenseService.getExpensesForGroup(GROUP_ID, ALICE))
                .thenReturn(List.of(new ExpenseResponse(100L, GROUP_ID, ALICE, "alice", new BigDecimal("100.00"), null, null, null)));

        Settlement hist = new Settlement(group, bobUser, aliceUser, new BigDecimal("40.00"));
        // Mark historical settlement as already-paid
        hist.setSettlementDate(java.time.LocalDate.now());
        stubPendingSettlement(BOB, ALICE, null);
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(hist));

        service.computeSettlement(GROUP_ID, ALICE, new SettlementRequest(List.of()));

        verify(settlementRepository).save(settlementCaptor.capture());
        Settlement saved = settlementCaptor.getValue();
        assertThat(saved.getSettlementDate()).isNull();
        // After applying the historical paid settlement of 40.00, only 10.00 remains unsettled
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void markSettlementPaidSetsSettlementDateAndPersistsPayment() {
        Settlement open = new Settlement(group, bobUser, aliceUser, new BigDecimal("25.00"));
        open.setSettlementDate(null);
        when(settlementRepository.findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(GROUP_ID, BOB, ALICE))
                .thenReturn(List.of(open));

        service.markSettlementPaid(GROUP_ID, BOB, ALICE, BOB);

        assertThat(open.getSettlementDate()).isNotNull();
        verify(settlementRepository).save(open);
    }

    @Test
    void staleOpenSettlementsAreDeletedWhenNoSettlementPlanRemains() {
        when(expenseService.getExpensesForGroup(GROUP_ID, ALICE)).thenReturn(List.of());

        Settlement stale = new Settlement(group, bobUser, aliceUser, new BigDecimal("25.00"));
        stale.setSettlementDate(null);
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(stale));

        service.computeSettlement(GROUP_ID, ALICE, new SettlementRequest(List.of()));

        verify(settlementRepository).delete(stale);
    }

    @Test
    void zeroAmountOpenSettlementsAreDeleted() {
        when(expenseService.getExpensesForGroup(GROUP_ID, ALICE)).thenReturn(List.of());

        Settlement stale = new Settlement(group, bobUser, aliceUser, BigDecimal.ZERO);
        stale.setSettlementDate(null);
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(stale));

        service.computeSettlement(GROUP_ID, ALICE, new SettlementRequest(List.of()));

        verify(settlementRepository).delete(stale);
    }
}
