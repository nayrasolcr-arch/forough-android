package io.github.nayrasolcr.forough.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nayrasolcr.forough.BuildConfig
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.ui.theme.ForoughGold
import io.github.nayrasolcr.forough.ui.theme.ForoughNavy

/**
 * AboutScreen — the desktop AboutDialog as a screen, with the dedicated
 * Forough texts (same content as the desktop About dialog and metainfo).
 */
@Composable
fun AboutScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Logo over the brand gradient halo.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ForoughGold.copy(alpha = 0.28f),
                                ForoughNavy.copy(alpha = 0.0f)
                            )
                        )
                    )
            )
            Image(
                painter = painterResource(R.drawable.forough_logo_circle),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )
        }

        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = ForoughGold
        )
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionCard(title = stringResource(R.string.about)) {
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start
            )
        }
        SectionCard(title = stringResource(R.string.app_name)) {
            Text(
                stringResource(R.string.about_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
