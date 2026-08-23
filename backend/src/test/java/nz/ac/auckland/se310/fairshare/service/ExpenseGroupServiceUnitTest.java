package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.SettlementLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseGroupServiceUnitTest {

    @Test
    void calculateSettlements_returnsEmptyForNullOrEmpty() {
        assertThat(ExpenseGroupService.calculateSettlements(null)).isEmpty();
        assertThat(ExpenseGroupService.calculateSettlements(new HashMap<>())).isEmpty();
    }

    @Test
    void calculateSettlements_simpleDebtorCreditorProducesOneLine() {
        Map<Long, BigDecimal> totals = new HashMap<>();
        totals.put(1L, new BigDecimal("10.00")); // debtor
        totals.put(2L, new BigDecimal("-10.00")); // creditor

        List<SettlementLine> lines = ExpenseGroupService.calculateSettlements(totals);

        assertThat(lines).hasSize(1);
        SettlementLine line = lines.get(0);
        assertThat(line.fromUserId()).isEqualTo(1L);
        assertThat(line.toUserId()).isEqualTo(2L);
        assertThat(line.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void calculateSettlements_smallAmountsRoundingProducesExpectedTransactions() {
        Map<Long, BigDecimal> totals = new HashMap<>();
        // debtor owes a very small amount which will round to 0.01 when creating a payment
        totals.put(1L, new BigDecimal("0.009"));
        totals.put(2L, new BigDecimal("-0.009"));

        List<SettlementLine> lines = ExpenseGroupService.calculateSettlements(totals);

        // rounding should produce a single 0.01 transaction
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).amount()).isEqualByComparingTo("0.01");
    }
}
