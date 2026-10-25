package com.smarttransparentmess.erp.data.finance

import com.smarttransparentmess.erp.domain.finance.MemberFinance
import com.smarttransparentmess.erp.domain.finance.PeriodFinance
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

interface FinanceRepository {
    suspend fun loadMyFinance(): MemberFinance
}

class SupabaseFinanceRepository(
    private val client: SupabaseClient,
) : FinanceRepository {
    override suspend fun loadMyFinance(): MemberFinance {
        check(client.auth.currentUserOrNull() != null) { "An authenticated session is required." }

        // Both views are security_invoker views restricted to auth.uid(). The app never
        // supplies a member ID for its own financial view.
        val periods = client.from("my_financial_summary")
            .select()
            .decodeList<FinanceSummaryDto>()
            .map(FinanceSummaryDto::toDomain)
            .sortedWith(
                compareByDescending<PeriodFinance> { it.periodStart }
                    .thenByDescending { it.periodEnd },
            )

        val payments = client.from("my_payment_receipts")
            .select()
            .decodeList<PaymentReceiptDto>()
            .map(PaymentReceiptDto::toDomain)
            .sortedByDescending { it.paidAt }
            .take(20)

        return MemberFinance(
            currentPeriod = periods.firstOrNull(),
            recentPayments = payments,
        )
    }
}
