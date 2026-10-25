package com.smarttransparentmess.erp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

val SmartMessTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(lineHeight = Typography().bodyLarge.lineHeight),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
