package com.example

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MediaItem
import com.example.ui.GalleryViewModel
import com.example.ui.GalleryViewModelFactory
import com.example.ui.components.GlassCard
import com.example.ui.components.liquidGlassBackdrop
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.ui.screens.CollectionsView
import com.example.ui.screens.LibraryGridView
import com.example.ui.screens.MediaDetailView
import com.example.ui.theme.GlassBarFill
import com.example.ui.theme.GlassBarBorder
import com.example.ui.theme.GlassSelectorFill
import com.example.ui.theme.MyApplicationTheme

private val NavBarHeight = 56.dp
private val NavBarCornerRadius = 28.dp
private val NavIconSize = 20.dp
private val NavLabelSize = 11.sp
private val BottomBarPadding = 28.dp
private val CollapsedFabSize = 48.dp

class MainActivity : ComponentActivity() {
    
    private val viewModel: GalleryViewModel by viewModels {
        GalleryViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MyApplicationTheme {
                MainGalleryScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainGalleryScreen(viewModel: GalleryViewModel) {
    val activeDetailItem by viewModel.activeDetailItem.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coordinated bottom bars scroll states
    var isLibraryScrolling by remember { mutableStateOf(false) }
    var densityBarVisible by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showMainBar by remember { mutableStateOf(true) }
    var isGridAtTop by remember { mutableStateOf(true) }
    var selectBtnRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    var mainBarRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var densityBarRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var searchBarRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var searchFabRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var collapsedFabRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var isDetailVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val showDensityBar = (currentTab == "Fototeca") && !isSearchActive

    // Immediately zero shader rects when bars hide — prevents distortion trail
    LaunchedEffect(showMainBar) {
        if (!showMainBar) mainBarRect = androidx.compose.ui.geometry.Rect.Zero
    }
    LaunchedEffect(densityBarVisible, showDensityBar) {
        if (!densityBarVisible || !showDensityBar) {
            densityBarRect = androidx.compose.ui.geometry.Rect.Zero
        }
    }
    LaunchedEffect(densityBarVisible, showMainBar) {
        if (!densityBarVisible || showMainBar) {
            collapsedFabRect = androidx.compose.ui.geometry.Rect.Zero
        }
    }

    LaunchedEffect(showDensityBar) {
        if (!showDensityBar) {
            densityBarRect = androidx.compose.ui.geometry.Rect.Zero
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            mainBarRect = androidx.compose.ui.geometry.Rect.Zero
            searchFabRect = androidx.compose.ui.geometry.Rect.Zero
        } else {
            searchBarRect = androidx.compose.ui.geometry.Rect.Zero
        }
    }

    LaunchedEffect(activeDetailItem) {
        if (activeDetailItem != null) {
            isDetailVisible = true
        }
    }

    LaunchedEffect(isLibraryScrolling) {
        if (isLibraryScrolling) {
            showMainBar = false
            densityBarVisible = true
        } else {
            if (isGridAtTop) {
                showMainBar = true
                densityBarVisible = false
            } else {
                kotlinx.coroutines.delay(1800)
                showMainBar = true
                densityBarVisible = false
            }
        }
    }

    // --- A. MEDIASTORE READ PERMISSIONS RESOLVER ---
    val permissionsToRequest = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = (results[Manifest.permission.READ_MEDIA_IMAGES] == true)
                && (results[Manifest.permission.READ_MEDIA_VIDEO] == true)
        if (allGranted) {
            Toast.makeText(context, "Fotos del dispositivo cargadas con éxito", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    val detailAlpha by animateFloatAsState(
        targetValue = if (isDetailVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "detailAlpha"
    )
    val detailScale by animateFloatAsState(
        targetValue = if (isDetailVisible) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
        label = "detailScale"
    )
    // Keep detail composable alive during exit animation
    val showDetailComposable = isDetailVisible || detailAlpha > 0.01f

    // --- C. DUAL SCREEN NAVIGATION (DETAIL VS MAIN FRAMING) ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        BackHandler(enabled = activeDetailItem != null) {
            viewModel.setDetailItem(null)
        }
        BackHandler(enabled = isSearchActive) {
            viewModel.setSearchQuery("")
            isSearchActive = false
        }

        // Grid stays fully visible; detail fades transparently on top
        Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidGlassBackdrop(
                            mainBarRect = { mainBarRect },
                            densityBarRect = { densityBarRect },
                            searchBarRect = { searchBarRect },
                            searchFabRect = { searchFabRect },
                            selectBtnRect = { selectBtnRect },
                            collapsedFabRect = { collapsedFabRect }
                        )
                ) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val dir = if (targetState == "Fototeca") -1 else 1
                            slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                                initialOffsetX = { fullWidth -> dir * fullWidth / 4 }
                            ) + fadeIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)) togetherWith
                            slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                                targetOffsetX = { fullWidth -> -dir * fullWidth / 4 }
                            ) + fadeOut(animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f))
                        },
                        label = "tabTransition"
                    ) { tab ->
                        when (tab) {
                            "Fototeca" -> LibraryGridView(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize(),
                                onScrollStateChanged = { isLibraryScrolling = it },
                                onIsAtTop = { isGridAtTop = it },
                                onSelectBtnRectChanged = { selectBtnRect = it },
                                onSearchFabRectChanged = { searchFabRect = it },
                                onSearchClick = {
                                    viewModel.selectTab("Fototeca")
                                    isSearchActive = true
                                },
                                isSearchActive = isSearchActive
                            )
                            "Colecciones" -> {
                                LaunchedEffect(Unit) {
                                    isLibraryScrolling = false
                                    densityBarVisible = false
                                }
                                CollectionsView(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }

                // --- D. FLOATING GLASS NAVIGATION BAR ---
                val showDensityBar = (currentTab == "Fototeca") && !isSearchActive
                val gridMode by viewModel.gridMode.collectAsStateWithLifecycle()

                // SCROLL MODE: density bar + collapsed Fototeca button (same bottom slot as nav)
                AnimatedVisibility(
                    visible = showDensityBar && densityBarVisible && !showMainBar,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                        initialOffsetY = { it }
                    ) + fadeIn(animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f)),
                    exit = slideOutVertically(
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
                        targetOffsetY = { it }
                    ) + fadeOut(animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = BottomBarPadding)
                        .width(260.dp)
                ) {
                    val densityModes = listOf("Años", "Meses", "Todo")
                    val selectedDensityIndex = densityModes.indexOf(gridMode).coerceAtLeast(0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .height(NavBarHeight)
                                .onGloballyPositioned { coords ->
                                    if (densityBarVisible && !showMainBar && coords.isAttached) {
                                        densityBarRect = coords.boundsInRoot()
                                    }
                                }
                                .clip(RoundedCornerShape(NavBarCornerRadius))
                                .background(GlassBarFill)
                                .border(1.dp, GlassBarBorder, RoundedCornerShape(NavBarCornerRadius))
                        ) {
                            val segmentWidth = maxWidth / densityModes.size
                            val densityIndicatorOffset by animateDpAsState(
                                targetValue = segmentWidth * selectedDensityIndex,
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                                label = "densityIndicator"
                            )
                            Box(
                                modifier = Modifier
                                    .offset(x = densityIndicatorOffset)
                                    .width(segmentWidth)
                                    .fillMaxHeight()
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(NavBarCornerRadius - 6.dp))
                                    .background(GlassSelectorFill)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                densityModes.forEach { mode ->
                                    val selected = gridMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { viewModel.setGridMode(mode) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(CollapsedFabSize)
                                .onGloballyPositioned { coords ->
                                    if (densityBarVisible && !showMainBar && coords.isAttached) {
                                        collapsedFabRect = coords.boundsInRoot()
                                    }
                                }
                                .clip(CircleShape)
                                .background(GlassBarFill)
                                .border(1.dp, GlassBarBorder, CircleShape)
                                .clickable {
                                    showMainBar = true
                                    densityBarVisible = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Mostrar barra de navegación",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(NavIconSize)
                            )
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            densityBarRect = androidx.compose.ui.geometry.Rect.Zero
                            collapsedFabRect = androidx.compose.ui.geometry.Rect.Zero
                        }
                    }
                }

                // MAIN TAB NAVIGATION BAR
                AnimatedVisibility(
                    visible = !isSearchActive && showMainBar,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f),
                        initialOffsetY = { it / 2 }
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f),
                        targetOffsetY = { it / 2 }
                    ) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = BottomBarPadding)
                        .width(180.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NavBarHeight)
                            .onGloballyPositioned { coords ->
                                if (showMainBar && coords.isAttached) {
                                    mainBarRect = coords.boundsInRoot()
                                }
                            }
                            .clip(RoundedCornerShape(NavBarCornerRadius))
                            .background(GlassBarFill)
                            .border(1.dp, GlassBarBorder, RoundedCornerShape(NavBarCornerRadius))
                    ) {
                        val tabWidth = maxWidth / 2
                        val isFototeca = currentTab == "Fototeca"
                        val indicatorOffset by animateDpAsState(
                            targetValue = if (isFototeca) 0.dp else tabWidth,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                            label = "tabIndicator"
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .fillMaxHeight()
                                .padding(6.dp)
                                .clip(RoundedCornerShape(NavBarCornerRadius - 6.dp))
                                .background(GlassSelectorFill)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { viewModel.selectTab("Fototeca") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = "Fototeca",
                                        tint = if (isFototeca) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(NavIconSize)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Fototeca",
                                        color = if (isFototeca) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.75f),
                                        fontSize = NavLabelSize,
                                        fontWeight = if (isFototeca) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { viewModel.selectTab("Colecciones") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = "Colecciones",
                                            tint = if (currentTab == "Colecciones") Color(0xFF0A84FF) else Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(NavIconSize)
                                        )
                                        val hiddenCount by viewModel.hiddenItems.collectAsStateWithLifecycle()
                                        if (hiddenCount.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(Color(0xFFFF453A), CircleShape)
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-2).dp)
                                                    .border(1.5.dp, GlassBarFill, CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Colecciones",
                                        color = if (currentTab == "Colecciones") Color(0xFF0A84FF) else Color.White.copy(alpha = 0.75f),
                                        fontSize = NavLabelSize,
                                        fontWeight = if (currentTab == "Colecciones") FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose { mainBarRect = androidx.compose.ui.geometry.Rect.Zero }
                    }
                }

                // --- TOP SEARCH GLASS BAR ---
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        initialOffsetY = { -it }
                    ) + fadeIn(
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
                    ),
                    exit = slideOutVertically(
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f),
                        targetOffsetY = { -it }
                    ) + fadeOut(
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 12.dp)
                        .align(Alignment.TopCenter)
                ) {
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .onGloballyPositioned { coords ->
                                if (isSearchActive && coords.isAttached) {
                                    searchBarRect = coords.boundsInRoot()
                                } else {
                                    searchBarRect = androidx.compose.ui.geometry.Rect.Zero
                                }
                            },
                        cornerRadius = 28.dp
                    ) {
                        DisposableEffect(Unit) {
                            onDispose {
                                searchBarRect = androidx.compose.ui.geometry.Rect.Zero
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = {
                                    Text(
                                        "Buscar fotos, ubicaciones...",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 15.sp
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    viewModel.setSearchQuery("")
                                    isSearchActive = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar búsqueda",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                }
                }

        // Detail overlay — grid stays visible underneath, premium scale+fade transition
        if (showDetailComposable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = detailAlpha
                        scaleX = detailScale
                        scaleY = detailScale
                    }
            ) {
                MediaDetailView(
                    viewModel = viewModel,
                    onBack = {
                        isDetailVisible = false
                        scope.launch {
                            delay(350)
                            viewModel.setDetailItem(null)
                        }
                    }
                )
            }
        }
    }
}
