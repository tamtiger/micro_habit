package vn.nhip2phut.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import vn.nhip2phut.ui.theme.Nhip2PhutThemeTokens

const val FOUNDATION_SCREEN_TEST_TAG = "foundation-screen"

@Composable
fun FoundationScreen(modifier: Modifier = Modifier) {
    val spacing = Nhip2PhutThemeTokens.spacing()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(FOUNDATION_SCREEN_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(spacing.screenPadding),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.contentGap),
            ) {
                Text(
                    text = stringResource(id = R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(id = R.string.home_status),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

