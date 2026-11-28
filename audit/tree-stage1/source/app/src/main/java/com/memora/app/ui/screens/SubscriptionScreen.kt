package com.memora.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.billing.EntitlementMode
import com.memora.app.billing.EntitlementState
import com.memora.app.billing.SubscriptionCatalog
import com.memora.app.billing.SubscriptionPlan
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.theme.MemoraDesign

@Composable
fun SubscriptionScreen(
    language: AppLanguage,
    entitlement: EntitlementState,
    plans: List<SubscriptionPlan>,
    onBack: () -> Unit,
    onSubscribe: (String) -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = L.t(language, S.SUBSCRIPTION),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                color = if (entitlement.canWrite) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (entitlement.canWrite) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = null
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            statusTitle(language, entitlement),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!entitlement.canWrite) {
                            Text(
                                L.t(language, S.READ_ONLY_BODY),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                L.t(language, S.PRIVACY_BILLING_NOTE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entitlement.mode == EntitlementMode.CONFIGURATION_ERROR) {
                Text(
                    L.t(language, S.BILLING_CONFIGURATION_ERROR),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (!entitlement.canWrite) {
                Spacer(Modifier.height(24.dp))
                Text(
                    L.t(language, S.CHOOSE_PLAN),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (plans.isEmpty()) {
                    Text(
                        L.t(language, S.BILLING_UNAVAILABLE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        plans.forEach { plan ->
                            Button(
                                onClick = { onSubscribe(plan.basePlanId) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(MemoraDesign.radiusSmall)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        "${planLabel(language, plan.basePlanId)} · ${plan.formattedPrice}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (plan.hasOneMonthFreeTrial) {
                                        Text(
                                            L.t(language, S.FREE_TRIAL),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        L.t(
                                            language,
                                            if (plan.hasOneMonthFreeTrial) S.TRIAL_RENEW_TERMS
                                            else S.AUTO_RENEW_TERMS
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            ) {
                Text(L.t(language, S.RESTORE_PURCHASES))
            }
            TextButton(
                onClick = onManage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(L.t(language, S.MANAGE_SUBSCRIPTION))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun statusTitle(language: AppLanguage, entitlement: EntitlementState): String = when (entitlement.mode) {
    EntitlementMode.ACTIVE, EntitlementMode.DEBUG -> L.t(language, S.SUBSCRIPTION_ACTIVE)
    EntitlementMode.OFFLINE_GRACE -> L.t(language, S.OFFLINE_ACCESS)
    EntitlementMode.PENDING -> L.t(language, S.SUBSCRIPTION_PENDING)
    else -> L.t(language, S.READ_ONLY_TITLE)
}

private fun planLabel(language: AppLanguage, basePlanId: String): String = when (basePlanId) {
    SubscriptionCatalog.MONTHLY -> "1 ${L.t(language, S.MONTH)}"
    SubscriptionCatalog.QUARTERLY -> "3 ${L.t(language, S.MONTH)}"
    SubscriptionCatalog.SEMIANNUAL -> "6 ${L.t(language, S.MONTH)}"
    SubscriptionCatalog.ANNUAL -> "1 ${L.t(language, S.YEAR)}"
    else -> basePlanId
}
