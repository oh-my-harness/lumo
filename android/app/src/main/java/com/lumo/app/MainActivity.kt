package com.lumo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.lumo.app.data.LumoRepository
import com.lumo.app.ui.chat.ChatDetailScreen
import com.lumo.app.ui.chat.ChatListScreen
import com.lumo.app.ui.notes.NoteEditorScreen
import com.lumo.app.ui.notes.NotesListScreen
import com.lumo.app.ui.profile.ProfileScreen
import com.lumo.app.ui.quiz.QuizScreen
import com.lumo.app.ui.theme.LumoTheme
import com.lumo.app.ui.today.TodayScreen
import com.lumo.app.ui.onboarding.OnboardingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TabItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumoTheme {
                LumoApp()
            }
        }
    }
}

@Composable
fun LumoApp() {
    val repo = LumoRepository.get()
    var showOnboarding by remember { mutableStateOf(false) }
    var configChecked by remember { mutableStateOf(false) }

    // Check if provider config exists on first launch
    LaunchedEffect(Unit) {
        try {
            val config = withContext(Dispatchers.IO) { repo.getProviderConfig() }
            if (config == null) showOnboarding = true
        } catch (e: Exception) {}
        configChecked = true
    }

    if (showOnboarding) {
        OnboardingDialog(
            onDismiss = { showOnboarding = false },
            onConfigSaved = { showOnboarding = false },
        )
    }
    val tabs = listOf(
        TabItem("today", "今日", Icons.Filled.Today),
        TabItem("chat", "对话", Icons.Filled.Chat),
        TabItem("quiz", "题库", Icons.Filled.Quiz),
        TabItem("notes", "笔记", Icons.Filled.Note),
        TabItem("profile", "我的", Icons.Filled.Person),
    )
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDestination = currentRoute?.destination?.route

    val showBottomBar = currentDestination in listOf("today", "chat", "quiz", "notes", "profile")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = currentDestination == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(innerPadding).statusBarsPadding()
        ) {
            composable("today") { TodayScreen() }
            composable("chat") { ChatListScreen(navController) }
            composable("chat/{sessionId}") { backStackEntry ->
                ChatDetailScreen(
                    sessionId = backStackEntry.arguments?.getString("sessionId") ?: "",
                    navController = navController,
                )
            }
            composable("quiz") { QuizScreen() }
            composable("notes") { NotesListScreen(navController) }
            composable("notes/editor") { NoteEditorScreen(navController) }
            composable("notes/editor/{noteId}") { backStackEntry ->
                NoteEditorScreen(
                    navController = navController,
                    noteId = backStackEntry.arguments?.getString("noteId"),
                )
            }
            composable("profile") { ProfileScreen() }
        }
    }
}
