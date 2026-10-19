package com.aura.chat.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aura.chat.container
import com.aura.chat.ui.screens.AboutScreen
import com.aura.chat.ui.screens.AppearanceScreen
import com.aura.chat.ui.screens.CallScreen
import com.aura.chat.ui.screens.ChatScreen
import com.aura.chat.ui.screens.ConnectorsScreen
import com.aura.chat.ui.screens.HomeScreen
import com.aura.chat.ui.screens.MemoryScreen
import com.aura.chat.ui.screens.ModelPickerScreen
import com.aura.chat.ui.screens.OnboardingScreen
import com.aura.chat.ui.screens.PersonasScreen
import com.aura.chat.ui.screens.PromptsScreen
import com.aura.chat.ui.screens.ProviderEditScreen
import com.aura.chat.ui.screens.ProvidersScreen
import com.aura.chat.ui.screens.SearchScreen
import com.aura.chat.ui.screens.SettingsScreen
import com.aura.chat.ui.screens.SkillsScreen
import com.aura.chat.ui.screens.CompareScreen
import com.aura.chat.ui.screens.InsightsScreen
import com.aura.chat.ui.screens.SplashScreen
import com.aura.chat.ui.screens.StudioScreen
import com.aura.chat.ui.screens.TranslateScreen
import com.aura.chat.ui.screens.VoiceSettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()

    NavHost(
        navController = nav,
        startDestination = "splash",
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 7 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 7 } },
    ) {
        composable("splash") {
            SplashScreen {
                scope.launch {
                    val done = container.settings.flow.first().onboarded
                    nav.navigate(if (done) "home" else "onboarding") { popUpTo("splash") { inclusive = true } }
                }
            }
        }
        composable("onboarding") {
            OnboardingScreen { name ->
                scope.launch {
                    container.settings.setUserName(name.trim())
                    container.settings.setOnboarded(true)
                    container.repo.seedPrompts()
                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                }
            }
        }
        composable("home") {
            HomeScreen(
                onOpenChat = { nav.navigate("chat/$it") },
                onPersonas = { nav.navigate("personas") },
                onSearch = { nav.navigate("search") },
                onSettings = { nav.navigate("settings") },
                onProviders = { nav.navigate("providers") },
                onTool = { nav.navigate(it) },
                onCall = { nav.navigate("call") },
                onMemory = { nav.navigate("memory") },
                onSkills = { nav.navigate("skills") },
                onConnectors = { nav.navigate("connectors") },
            )
        }
        composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ChatScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenModels = { nav.navigate("models") },
                onOpenProviders = { nav.navigate("providers") },
                onCall = { nav.navigate("call") },
            )
        }
        composable("personas") {
            PersonasScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("personas") { inclusive = true } } },
            )
        }
        composable("search") {
            SearchScreen(onBack = { nav.popBackStack() }, onOpenChat = { nav.navigate("chat/$it") })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onProviders = { nav.navigate("providers") },
                onModels = { nav.navigate("models") },
                onAppearance = { nav.navigate("appearance") },
                onPrompts = { nav.navigate("prompts") },
                onAbout = { nav.navigate("about") },
                onVoice = { nav.navigate("voice-settings") },
                onMemory = { nav.navigate("memory") },
                onSkills = { nav.navigate("skills") },
                onConnectors = { nav.navigate("connectors") },
            )
        }
        composable("providers") {
            ProvidersScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate("provider/$it") })
        }
        composable("provider/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ProviderEditScreen(idArg = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable("models") {
            ModelPickerScreen(
                onBack = { nav.popBackStack() },
                onProviders = { nav.navigate("providers") },
            )
        }
        composable("appearance") { AppearanceScreen(onBack = { nav.popBackStack() }) }
        composable("prompts") {
            PromptsScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("prompts") { inclusive = true } } },
            )
        }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
        composable("call") { CallScreen(onBack = { nav.popBackStack() }) }
        composable("memory") { MemoryScreen(onBack = { nav.popBackStack() }) }
        composable("skills") { SkillsScreen(onBack = { nav.popBackStack() }) }
        composable("connectors") { ConnectorsScreen(onBack = { nav.popBackStack() }) }
        composable("voice-settings") { VoiceSettingsScreen(onBack = { nav.popBackStack() }) }
        composable("compare") { CompareScreen(onBack = { nav.popBackStack() }, onProviders = { nav.navigate("providers") }) }
        composable("insights") { InsightsScreen(onBack = { nav.popBackStack() }) }
        composable("translate") { TranslateScreen(onBack = { nav.popBackStack() }) }
        composable("studio") { StudioScreen(onBack = { nav.popBackStack() }) }
    }
}
