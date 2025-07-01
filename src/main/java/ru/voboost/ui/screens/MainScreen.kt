package ru.voboost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.voboost.ui.model.TabItem
import ru.voboost.ui.theme.TabBackground
import ru.voboost.ui.theme.TabSelected
import ru.voboost.ui.theme.TabUnselected
import ru.voboost.ui.theme.VoboostTheme
import ru.voboost.ui.viewmodel.ConfigViewModel
import ru.voboost.ui.viewmodel.ConfigUiState

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    configViewModel: ConfigViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(TabItem.SETTINGS) }
    val uiState by configViewModel.uiState.collectAsState()

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
    ) {
        // Left Navigation Rail
        NavigationRail(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(200.dp),
            containerColor = TabBackground,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            TabItem.values().forEach { tab ->
                NavigationRailItem(
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (selectedTab == tab) TabSelected else TabUnselected
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            color = if (selectedTab == tab) TabSelected else TabUnselected,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    colors =
                        NavigationRailItemDefaults.colors(
                            selectedIconColor = TabSelected,
                            unselectedIconColor = TabUnselected,
                            selectedTextColor = TabSelected,
                            unselectedTextColor = TabUnselected,
                            indicatorColor = Color.Transparent
                        ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Main Content Area
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            when (selectedTab) {
                TabItem.LAUNCHER -> LauncherContent()
                TabItem.APPLICATIONS -> ApplicationsContent()
                TabItem.INTERFACE -> InterfaceContent()
                TabItem.VEHICLE -> VehicleContent()
                TabItem.SETTINGS -> SettingsContent(
                    uiState = uiState,
                    onRetryConfig = { configViewModel.reloadConfig() }
                )
            }
        }
    }
}

@Composable
fun LauncherContent() {
    ContentPlaceholder(
        title = "Launcher",
        description = "Application launcher interface will be here"
    )
}

@Composable
fun ApplicationsContent() {
    ContentPlaceholder(
        title = "Applications",
        description = "List of installed applications will be here"
    )
}

@Composable
fun InterfaceContent() {
    ContentPlaceholder(
        title = "Interface",
        description = "Interface settings will be here:\n• Appearance\n• Dark/Light theme\n• Interface language"
    )
}

@Composable
fun VehicleContent() {
    ContentPlaceholder(
        title = "Vehicle",
        description = "Vehicle settings will be here:\n• Fuel mode\n• Drive mode\n• Voyah parameters"
    )
}

@Composable
fun SettingsContent(
    uiState: ConfigUiState = ConfigUiState(isLoading = true),
    onRetryConfig: () -> Unit = {}
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        // Configuration Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Configuration Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    uiState.isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp))
                            Text(
                                text = "Loading configuration...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    uiState.error != null -> {
                        Column {
                            Text(
                                text = "✗ Configuration error: ${uiState.error}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRetryConfig
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    uiState.config != null -> {
                        Text(
                            text = "✓ Configuration loaded successfully",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Text(
                            text = "No configuration loaded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Version Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Voboost 1.0.0.2025012501",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Voyah vehicle configuration management system",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Language Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Language",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageButton("Russian", isSelected = true)
                    LanguageButton("English", isSelected = false)
                }
            }
        }

        // Theme Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeButton("Auto", isSelected = false)
                    ThemeButton("Dark", isSelected = true)
                    ThemeButton("Light", isSelected = false)
                }
            }
        }
    }
}

@Composable
fun ContentPlaceholder(
    title: String,
    description: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LanguageButton(
    text: String,
    isSelected: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) TabSelected else MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ThemeButton(
    text: String,
    isSelected: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) TabSelected else MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, widthDp = 1200, heightDp = 800)
@Composable
fun MainScreenPreview() {
    VoboostTheme {
        MainScreen()
    }
}

