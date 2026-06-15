package com.efecanseymen.b1.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.efecanseymen.b1.viewmodel.HomeViewModel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

@Composable
fun MainScreen(viewModel: HomeViewModel, onLogOutClick: () -> Unit){
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf(
        Triple("Ana Sayfa", Icons.Filled.Home, 0),
        Triple("Yoklama", Icons.Filled.AssignmentTurnedIn, 1),
        Triple("Hangi Derslik", Icons.Filled.Nfc, 2)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, color = MaterialTheme.colorScheme.onSurface) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(tween(300)) { width -> width } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(tween(300)) { width -> -width } + fadeOut(tween(300))
                } else {
                    slideInHorizontally(tween(300)) { width -> -width } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(tween(300)) { width -> width } + fadeOut(tween(300))
                }
            },
            label = "nav_transition",
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onLogOutClick = onLogOutClick,
                    modifier = Modifier
                )
                1 -> ScanScreen(
                    viewModel = viewModel,
                    modifier = Modifier
                )
                2 -> ClassScreen(
                    viewModel = viewModel,
                    modifier = Modifier
                )
            }
        }
    }
}
