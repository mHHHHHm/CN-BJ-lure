package com.bjlure.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bjlure.app.R
import com.bjlure.app.ui.about.AboutScreen
import com.bjlure.app.ui.detail.SpotDetailScreen
import com.bjlure.app.ui.list.SpotListScreen
import com.bjlure.app.ui.map.MapScreen
import com.bjlure.app.ui.tool.FishToolScreen
import kotlinx.coroutines.delay

object Routes {
    const val SPOTS = "spots"
    const val TOOL = "tool"
    const val MAP = "map"
    const val ABOUT = "about"
    const val SPOT_DETAIL = "spot/{spotId}"

    fun spotDetail(id: String) = "spot/$id"
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    viewModel: FishingViewModel,
    onRequestLocation: () -> Unit,
) {
    // 开机动画：先亮 2 秒「摸鱼」，再进主界面
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS)
        showSplash = false
    }
    if (showSplash) {
        SplashScreen()
        return
    }

    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(
        Tab(Routes.SPOTS, "钓点", { Icon(Icons.Filled.Search, contentDescription = null) }),
        Tab(Routes.TOOL, "假饵", { Icon(Icons.Filled.LocationOn, contentDescription = null) }),
        Tab(Routes.MAP, "地图", { Icon(Icons.Filled.Map, contentDescription = null) }),
        Tab(Routes.ABOUT, "说明", { Icon(Icons.Filled.Info, contentDescription = null) }),
    )

    val isDetail = currentRoute?.startsWith("spot/") == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isDetail) "钓点详情" else stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            if (!isDetail) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.SPOTS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPOTS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.SPOTS) {
                SpotListScreen(
                    state = state,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onOpenSpot = { navController.navigate(Routes.spotDetail(it)) },
                    onRequestLocation = onRequestLocation,
                )
            }
            composable(Routes.TOOL) {
                FishToolScreen(viewModel = viewModel)
            }
            composable(Routes.MAP) {
                MapScreen(viewModel = viewModel, onOpenSpot = { navController.navigate(Routes.spotDetail(it)) })
            }
            composable(Routes.ABOUT) {
                AboutScreen(state = state, viewModel = viewModel)
            }
            composable(
                route = Routes.SPOT_DETAIL,
                arguments = listOf(navArgument("spotId") { type = NavType.StringType }),
            ) { entry ->
                val spotId = entry.arguments?.getString("spotId").orEmpty()
                SpotDetailScreen(
                    spotId = spotId,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** 开机动画时长：两秒 */
private const val SPLASH_MILLIS = 2000L

/**
 * 开机动画。
 *
 * 用的是社区表情包 501「摸鱼」—— 大肥鱼抱着鲸鱼抱枕睡得正香，旁边一个
 * 「摸鱼」气泡。配淡蓝渐变底，正好和 App 主题一路。
 *
 * 另外在 themes.xml 里把 windowBackground 也设成了同色，
 * 这样从点图标到 Compose 画出第一帧之间不会闪白屏。
 */
@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE4F0FC), Color(0xFFCFE2F7)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.splash_fish),
                contentDescription = null,
                // 撑到屏幕宽度的 92%，但配 Fit 缩放 —— 宁可留白也不裁边，
                // 「摸鱼」那张的气泡和尾巴都在画布边缘，裁一点就残缺了
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 460.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "大肥鱼历险记",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0D3B66),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "只收免费、合法的路亚水域",
                fontSize = 13.sp,
                color = Color(0xFF5B6F86),
            )
        }

        // 署名压在底部：字号和颜色都往回收，别跟中间的「摸鱼」抢视线
        Text(
            text = "by MMH & 大肥鱼",
            fontSize = 10.sp,
            color = Color(0xFFAFC2D6),
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp),
        )
    }
}
