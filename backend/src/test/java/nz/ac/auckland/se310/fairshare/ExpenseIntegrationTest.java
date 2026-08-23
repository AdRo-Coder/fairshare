package nz.ac.auckland.se310.fairshare;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import nz.ac.auckland.se310.fairshare.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestCurrentUserConfig.class)
class ExpenseIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String CAROL_EMAIL = "carol@test.com";
    private static final String AMOUNT_FIELD = "amount";
    private static final String GROCERIES = "Groceries";
    private static final String GROCERIES_AMOUNT = "42.50";
    private static final String TAXI_AMOUNT = "10.00";

    @Autowired
    ExpenseGroupService groupService;
    @Autowired
    ExpenseService expenseService;
    @Autowired
    ExpenseGroupRepository groupRepository;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    ExpenseShareRepository expenseShareRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    Validator validator;

    private Long aliceId;
    private Long bobId;
    private Long carolId;
    private Long groupId;
    private List<Long> memberIds;

    @BeforeEach
    void setUp() {
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        groupRepository.deleteAll();

        aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
        carolId = userRepository.findByEmail(CAROL_EMAIL)
                .orElseGet(() -> userRepository.save(new User(
                        "carol", "x", CAROL_EMAIL, User.Country.NEW_ZEALAND, User.Currency.NZD)))
                .getId();
        memberIds = List.of(aliceId, bobId, carolId);

        groupId = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId).id();
        groupService.addMember(groupId, "bob@test.com", aliceId);
    }

    @Test
    void ac1_createsExpenseWithGivenDetails() {
        var request = new CreateExpenseRequest(
                new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, LocalDate.of(2026, Month.AUGUST, 1));

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        assertThat(created.id()).isNotNull();
        assertThat(created.groupId()).isEqualTo(groupId);
        assertThat(created.amount()).isEqualByComparingTo(GROCERIES_AMOUNT);
        assertThat(created.description()).isEqualTo(GROCERIES);
        assertThat(created.paidByUserId()).isEqualTo(bobId);
        assertThat(created.paidByUsername()).isEqualTo("bob");
        assertThat(created.expenseDate()).isEqualTo(LocalDate.of(2026, Month.AUGUST, 1));
        assertThat(created.participantUserIds()).containsExactlyInAnyOrder(aliceId, bobId);
        assertThat(expenseRepository.findById(created.id())).isPresent();
    }

    @Test
    void ac2AndAc3_rejectsMissingFieldsAndNonPositiveAmounts() {
        assertThat(violations(new CreateExpenseRequest(null, "  ", null, memberIds, null)))
                .containsOnlyKeys(AMOUNT_FIELD, "description", "paidByUserId");

        assertThat(violations(new CreateExpenseRequest(BigDecimal.ZERO, "Taxi", aliceId, memberIds, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amount must be a positive number");

        assertThat(violations(new CreateExpenseRequest(new BigDecimal("-5.00"), "Taxi", aliceId, memberIds, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amount must be a positive number");
    }

    @Test
    void ac3_rejectsAmountsSmallerThanOneCent() {
        assertThat(violations(new CreateExpenseRequest(new BigDecimal("0.004"), "Taxi", aliceId, memberIds, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .containsExactly("Amount must be at least 0.01");
    }

    @Test
    void ac5_payerMustBeAGroupMember() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", carolId, memberIds, null);

        assertThatThrownBy(() -> expenseService.createExpense(groupId, request, aliceId))
                .isInstanceOf(InvalidPayerException.class);
    }

    @Test
    void ac6_expenseDateDefaultsToTodayWhenOmitted() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, null);

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        assertThat(created.expenseDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void ac6_rejectsAFutureDateAndAcceptsAPastOne() {
        assertThat(violations(new CreateExpenseRequest(
                new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, LocalDate.now().plusDays(1))))
                .extractingByKey("expenseDate", list(String.class))
                .containsExactly("Expense date cannot be in the future");

        assertThat(violations(new CreateExpenseRequest(
                new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, LocalDate.now().minusDays(30))))
                .isEmpty();
    }

    @Test
    void ac7_listsGroupExpensesForEveryMemberNewestFirst() {
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, LocalDate.now().minusDays(3)), aliceId);
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("20.00"), "Pizza", bobId, memberIds, LocalDate.now().minusDays(1)), aliceId);

        List<ExpenseResponse> expenses = expenseService.getExpensesForGroup(groupId, bobId);

        assertThat(expenses)
                .extracting(ExpenseResponse::description)
                .containsExactly("Pizza", "Taxi");
        assertThat(expenses.getFirst().paidByUsername()).isEqualTo("bob");
        assertThat(expenses.getFirst().amount()).isEqualByComparingTo("20.00");
        assertThat(expenses.getFirst().expenseDate()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    void ac8_nonMemberCannotCreateOrViewExpenses() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, null);

        assertThatThrownBy(() -> expenseService.createExpense(groupId, request, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
        assertThatThrownBy(() -> expenseService.getExpensesForGroup(groupId, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
    }

    @Test
    void ac6_atLeastOneMemberMustBeIncludedInTheSplit() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, List.of(), null);

        assertThat(violations(request))
                .extractingByKey("participantUserIds", list(String.class))
                .containsExactly("At least one participant is required");
    }

    @Test
    void ac7_editingAnExpensePersistsTheNewExpenseDate() {
        LocalDate originalDate = LocalDate.of(2026, Month.AUGUST, 1);
        LocalDate updatedDate = LocalDate.of(2026, Month.AUGUST, 15);
        ExpenseResponse created = expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, originalDate), aliceId);

        expenseService.updateExpense(groupId, new CreateExpenseRequest(
                new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, updatedDate), aliceId, created.id());

        assertThat(expenseService.getExpense(groupId, created.id(), aliceId).expenseDate())
                .isEqualTo(updatedDate);
    }

    /**
     * A field can break more than one constraint at a time, so every message is kept.
     */
    private Map<String, List<String>> violations(CreateExpenseRequest request) {
        return validator.validate(request).stream()
                .collect(Collectors.groupingBy(
                        violation -> violation.getPropertyPath().toString(),
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
    }
}
