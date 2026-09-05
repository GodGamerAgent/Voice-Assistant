package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.storage.AppPreferencesRepository
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PeopleScreen
import com.example.ui.screens.ProvidersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TonePresetsScreen
import com.example.ui.theme.MyApplicationTheme

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    PEOPLE("People", Icons.Filled.People, Icons.Outlined.People),
    PROVIDERS("Providers", Icons.Filled.Tune, Icons.Outlined.Tune),
    TONES("Tones", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = AppPreferencesRepository.getInstance(this)

        setContent {
            val themeMode by repository.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                val onboardingCompleted by repository.onboardingCompleted.collectAsState()
                var showOnboardingManual by remember { mutableStateOf(false) }

                if (!onboardingCompleted || showOnboardingManual) {
                    OnboardingScreen(
                        repository = repository,
                        onFinish = { showOnboardingManual = false }
                    )
                } else {
                    MainAppLayout(
                        repository = repository,
                        onOpenOnboarding = { showOnboardingManual = true }
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppLayout(
    repository: AppPreferencesRepository,
    onOpenOnboarding: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                MainTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    repository = repository,
                    onOpenOnboarding = onOpenOnboarding,
                    onNavigateToProviders = { selectedTab = MainTab.PROVIDERS },
                    onNavigateToPeople = { selectedTab = MainTab.PEOPLE }
                )
                MainTab.PEOPLE -> PeopleScreen(repository = repository)
                MainTab.PROVIDERS -> ProvidersScreen(repository = repository)
                MainTab.TONES -> TonePresetsScreen(repository = repository)
                MainTab.SETTINGS -> SettingsScreen(
                    repository = repository,
                    onOpenOnboarding = onOpenOnboarding
                )
            }
        }
    }
}
