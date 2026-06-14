package com.example.ui.screens

import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.MediaItem
import com.example.ui.GalleryViewModel
import com.example.ui.components.AnimatedPressIcon
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSlider
import com.example.ui.components.liquidGlassBackdrop
import com.example.ui.theme.GlassBarFill
import com.example.ui.theme.GlassBarBorder
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaDetailView(
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val activeItem by viewModel.activeDetailItem.collectAsStateWithLifecycle()
    val allItems by viewModel.detailList.collectAsStateWithLifecycle()
    
    val currentItem = activeItem ?: return
    val currentIndex = allItems.indexOfFirst { it.id == currentItem.id }
    val detailContext = LocalContext.current

    // Swiping pager initialization and synchronization state
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceAtLeast(0),
        pageCount = { allItems.size }
    )

    // Guard pager against out-of-bounds crash when list shrinks
    LaunchedEffect(allItems.size) {
        if (allItems.isNotEmpty() && pagerState.currentPage >= allItems.size) {
            pagerState.scrollToPage(allItems.size - 1)
        }
    }

    // Flag to break cross-feedback loop between pager swipes and external navigation
    var isPagerDriven by remember { mutableStateOf(false) }
    var isInitialLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(100)
        isInitialLoad = false
    }

    // When pager swipe changes the page, update the active detail item
    LaunchedEffect(pagerState.currentPage) {
        if (!isInitialLoad && pagerState.currentPage in allItems.indices) {
            val itemAtPage = allItems[pagerState.currentPage]
            if (itemAtPage.id != activeItem?.id) {
                isPagerDriven = true
                viewModel.setDetailItem(itemAtPage)
            }
        }
    }

    // When external navigation changes active item, scroll the pager
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < allItems.size && pagerState.currentPage != currentIndex) {
            if (!isPagerDriven) {
                delay(50)
                pagerState.scrollToPage(currentIndex)
            }
        }
        isPagerDriven = false
    }

    // Sliders Panel display state
    var showAdjustSliders by remember { mutableStateOf(false) }

    // Animated enter/exit transition state
    var isExiting by remember { mutableStateOf(false) }

    // Swipe-down-to-close drag state
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isDraggingDown by remember { mutableStateOf(false) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(areControlsVisible, lastInteractionTime) {
        if (areControlsVisible) {
            kotlinx.coroutines.delay(3000)
            if (System.currentTimeMillis() - lastInteractionTime >= 3000) {
                areControlsVisible = false
            }
        }
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            viewModel.clearDetailListOverride()
            kotlinx.coroutines.delay(250)
            onBack()
        }
    }

    // Fade-only exit — grid visible underneath through transparent background
    val detailAlpha by animateFloatAsState(
        targetValue = if (!isExiting) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "detailAlpha"
    )
    val detailScale by animateFloatAsState(
        targetValue = if (!isExiting) 1f else 1.03f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "detailExitScale"
    )

    // Sliders variables representing real image correction params!
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var saturation by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }

    // Dialog variables for metadata customization ("Ajustar datos")
    var showAdjustMetadataDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(currentItem.displayName) }
    var newLocation by remember { mutableStateOf(currentItem.location ?: "") }
    var newDateText by remember { mutableStateOf("") }

    var lastResetId by remember { mutableStateOf(currentItem.id) }

    LaunchedEffect(currentItem) {
        if (currentItem.id != lastResetId) {
            newTitle = currentItem.displayName
            newLocation = currentItem.location ?: ""
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            newDateText = formatter.format(Date(currentItem.dateAdded * 1000))
            lastResetId = currentItem.id
        }
    }

    // Dialog for custom Album Adding
    var showAddAlbumDialog by remember { mutableStateOf(false) }
    var albumNameInput by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val detailSnackbarHostState = remember { SnackbarHostState() }
    val detailSnackbarScope = rememberCoroutineScope()

    // Generate native ColorMatrix for filters dynamically
    val colorMatrixState = remember(brightness, contrast, saturation) {
        val cm = ColorMatrix()
        // Saturation adjustment
        cm.setToSaturation(saturation)
        
        // Custom matrix representation for contrast and brightness
        val array = cm.values
        array[0] = array[0] * contrast
        array[6] = array[6] * contrast
        array[12] = array[12] * contrast
        
        // Brightness offsets
        array[4] = brightness * 255f
        array[9] = brightness * 255f
        array[14] = brightness * 255f
        
        cm
    }

    val colorFilter = remember(brightness, contrast, saturation) {
        if (brightness == 0f && contrast == 1f && saturation == 1f) {
            null
        } else {
            ColorFilter.colorMatrix(colorMatrixState)
        }
    }

    var detailBottomBarRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var detailLocationRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var detailBackButtonRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var detailOptionsButtonRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var videoControlsRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    class VideoController {
        var isPlaying by mutableStateOf(false)
        var position by mutableLongStateOf(0L)
        var duration by mutableLongStateOf(0L)
        var isReady by mutableStateOf(false)
        var togglePlay: () -> Unit = {}
        var seek: (Int) -> Unit = {}
    }
    val videoController = remember { VideoController() }

    fun formatTime(ms: Long): String {
        val totalSec = floor(ms / 1000.0).toLong()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val dragProgress = (dragOffsetY / 800f).coerceIn(0f, 0.5f)
                alpha = (1f - dragProgress) * detailAlpha
                translationY = dragOffsetY
                scaleX = detailScale
                scaleY = detailScale
            }
    ) {
        BackHandler(enabled = true) { isExiting = true }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdrop(
                    mainBarRect = { if (areControlsVisible) detailBottomBarRect else androidx.compose.ui.geometry.Rect.Zero },
                    densityBarRect = { if (areControlsVisible) detailLocationRect else androidx.compose.ui.geometry.Rect.Zero },
                    searchBarRect = { if (areControlsVisible) detailBackButtonRect else androidx.compose.ui.geometry.Rect.Zero },
                    searchFabRect = { if (areControlsVisible) detailOptionsButtonRect else androidx.compose.ui.geometry.Rect.Zero },
                    pageIndicatorRect = { if (areControlsVisible && currentItem.isVideo) videoControlsRect else androidx.compose.ui.geometry.Rect.Zero }
                )
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                userScrollEnabled = true,
                beyondViewportPageCount = 1,
                key = { allItems.getOrNull(it)?.id ?: it }
            ) { pageIndex ->
            val pageItem = allItems.getOrNull(pageIndex) ?: return@HorizontalPager
            val pageOffset = (pageIndex - pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        // Parallax: background image shifts slower than pager swipe
                        translationX = pageOffset * -size.width * 0.08f
                    },
                contentAlignment = Alignment.Center
            ) {
                if (pageItem.isVideo) {
                    val isCurrentPage = pagerState.currentPage == pageIndex
                    var isVideoPlaying by remember(pageItem.id) { mutableStateOf(false) }
                    var videoPosition by remember(pageItem.id) { mutableLongStateOf(0L) }
                    var videoDuration by remember(pageItem.id) { mutableLongStateOf(0L) }
                    var videoReady by remember(pageItem.id) { mutableStateOf(false) }
                    val videoViewRef = remember { mutableStateOf<VideoView?>(null) }

                    DisposableEffect(pageItem.id) {
                        onDispose {
                            videoViewRef.value?.stopPlayback()
                            videoViewRef.value = null
                            videoReady = false
                        }
                    }

                    LaunchedEffect(isCurrentPage) {
                        val vv = videoViewRef.value ?: return@LaunchedEffect
                        if (isCurrentPage) {
                            vv.start()
                            isVideoPlaying = true
                        } else {
                            vv.pause()
                            isVideoPlaying = false
                        }
                    }

                    LaunchedEffect(isVideoPlaying, videoReady) {
                        if (isVideoPlaying && videoReady) {
                            while (true) {
                                val vv = videoViewRef.value
                                if (vv != null && vv.isPlaying) {
                                    videoPosition = vv.currentPosition.toLong()
                                    videoDuration = vv.duration.toLong()
                                }
                                delay(250)
                            }
                        }
                    }

                    LaunchedEffect(pageItem.id, videoDuration, videoPosition, isVideoPlaying, videoReady) {
                        if (isCurrentPage) {
                            videoController.position = videoPosition
                            videoController.duration = videoDuration
                            videoController.isPlaying = isVideoPlaying
                            videoController.isReady = videoReady
                            videoController.togglePlay = {
                                val vv = videoViewRef.value
                                if (vv != null) {
                                    if (vv.isPlaying) { vv.pause() } else { vv.start() }
                                }
                            }
                            videoController.seek = { pos -> videoViewRef.value?.seekTo(pos) }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setVideoURI(android.net.Uri.parse(pageItem.uri))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        videoDuration = mp.duration.toLong()
                                        videoReady = true
                                        if (isCurrentPage) {
                                            start()
                                            isVideoPlaying = true
                                        }
                                    }
                                    setOnCompletionListener { isVideoPlaying = false }
                                    videoViewRef.value = this
                                }
                            },
                            update = { view ->
                                if (isCurrentPage && videoReady) {
                                    view.start()
                                    isVideoPlaying = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pageItem.id) {
                                    detectTapGestures(
                                        onTap = {
                                            areControlsVisible = !areControlsVisible
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                        onDoubleTap = { videoController.togglePlay() }
                                    )
                                }
                        )

                        AnimatedVisibility(
                            visible = !isVideoPlaying && videoReady,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Interactive Zoomable image (Pan & Scale)
                    var scale by remember(pageItem.id) { mutableStateOf(1f) }
                    var offset by remember(pageItem.id) { mutableStateOf(Offset.Zero) }
                    val animatedScale by animateFloatAsState(
                        targetValue = scale,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                        label = "zoomScale"
                    )
                    val animatedOffsetX by animateFloatAsState(
                        targetValue = offset.x,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                        label = "offsetX"
                    )
                    val animatedOffsetY by animateFloatAsState(
                        targetValue = offset.y,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                        label = "offsetY"
                    )

                    // Reset zoom on swipe/page change
                    LaunchedEffect(pageItem.id) {
                        scale = 1f
                        offset = Offset.Zero
                    }

                    // Conditional modifier: only intercept gestures when zoomed in
                    val zoomEnabled = scale > 1f

                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        val detailUri = remember(pageItem.uri) { android.net.Uri.parse(pageItem.uri) }
                        val thumbnailRequest = remember(pageItem.uri) {
                            ImageRequest.Builder(detailContext)
                                .data(detailUri)
                                .size(512)
                                .crossfade(true)
                                .build()
                        }
                        val imageRequest = remember(pageItem.uri) {
                            ImageRequest.Builder(detailContext)
                                .data(detailUri)
                                .crossfade(300)
                                .allowRgb565(false)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pageItem.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                                        scale = newScale
                                        if (scale > 1f) {
                                            offset = Offset(
                                                x = (offset.x + pan.x).coerceIn(
                                                    -(size.width * (scale - 1f) / 2f),
                                                    size.width * (scale - 1f) / 2f
                                                ),
                                                y = (offset.y + pan.y).coerceIn(
                                                    -(size.height * (scale - 1f) / 2f),
                                                    size.height * (scale - 1f) / 2f
                                                )
                                            )
                                        }
                                    }
                                }
                                .pointerInput(pageItem.id) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 3f
                                            }
                                            areControlsVisible = false
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                        onTap = {
                                            areControlsVisible = !areControlsVisible
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                    )
                                }
                        ) {
                            // 1. Instantly visible cached thumbnail layer
                            AsyncImage(
                                model = thumbnailRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                onError = { android.util.Log.e("DetailView", "thumbnail load failed: ${pageItem.uri}") },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                        translationX = animatedOffsetX
                                        translationY = animatedOffsetY
                                        rotationZ = rotation
                                    }
                            )
                            // 2. Full-resolution HDR-compatible layer fading in on top
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = pageItem.displayName,
                                contentScale = ContentScale.Fit,
                                colorFilter = colorFilter,
                                onError = { android.util.Log.e("DetailView", "full image load failed: ${pageItem.uri}") },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                        translationX = animatedOffsetX
                                        translationY = animatedOffsetY
                                        rotationZ = rotation
                                    }
                            )
                        }

                        // Zoom level indicator (visible when zoomed)
                        AnimatedVisibility(
                            visible = scale > 1f,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        ) {
                            GlassCard(
                                modifier = Modifier.size(40.dp),
                                cornerRadius = 20.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${(scale * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }



        // --- 2. FLOATING OVERHEAD HEADER CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val headerTarget = if (areControlsVisible) 1f else 0f
                    alpha = headerTarget
                    translationY = (1f - headerTarget) * (-20f)
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isExiting = true },
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) {
                            detailBackButtonRect = coords.boundsInRoot()
                        }
                    }
                    .clip(CircleShape)
                    .background(GlassBarFill)
                    .border(1.dp, GlassBarBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Atrás",
                    tint = Color.White
                )
            }

            // Location & Date label just like Albacete 2 de junio 15:30
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) {
                            detailLocationRect = coords.boundsInRoot()
                        }
                    }
                    .clip(CircleShape)
                    .background(GlassBarFill)
                    .border(1.dp, GlassBarBorder, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentItem.location ?: "Ubicación",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val formatter = SimpleDateFormat("d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-ES"))
                val dateLabel = try {
                    formatter.format(Date(currentItem.dateAdded * 1000))
                } catch (e: Exception) {
                    "Fecha"
                }
                Text(
                    text = dateLabel,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            // Options menu trigger button (...)
            var showDetailMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showDetailMenu = true },
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                detailOptionsButtonRect = coords.boundsInRoot()
                            }
                        }
                        .clip(CircleShape)
                        .background(GlassBarFill)
                        .border(1.dp, GlassBarBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = Color.White
                    )
                }

                // Glassmorphic Style Custom Menu Popups
                DropdownMenu(
                    expanded = showDetailMenu,
                    onDismissRequest = { showDetailMenu = false },
                    modifier = Modifier
                        .background(Color(0xCC1C1C1E))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Copiar", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White) },
                        onClick = {
                            viewModel.copyItemToClipboard(currentItem)
                            showDetailMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White) },
                        onClick = {
                            viewModel.duplicateItem(currentItem)
                            showDetailMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ocultar", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White) },
                        onClick = {
                            viewModel.hideItem(currentItem)
                            showDetailMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir a Álbum", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showAddAlbumDialog = true
                            showDetailMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ajustar datos", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showAdjustMetadataDialog = true
                            showDetailMenu = false
                        }
                    )
                }
            }
        }

        // --- 3. BOTTOM CAROUSEL & ACTIONS ---

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .graphicsLayer {
                    val controlsTarget = if (areControlsVisible) 1f else 0f
                    alpha = controlsTarget
                    translationY = (1f - controlsTarget) * 30f
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
                .padding(bottom = 12.dp)
        ) {
            // B) Tool bar - Simplified: Share left, Favorite/Info/Edit center, Delete right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Share only
                AnimatedPressIcon(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = if (currentItem.isVideo) "video/*" else "image/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(currentItem.uri))
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        detailContext.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir"))
                    },
                    contentDescription = "Compartir",
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassBarFill)
                        .border(1.dp, GlassBarBorder, CircleShape),
                    icon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                )

                // CENTER: GlassCard with Favorite + Info + Edit
                GlassCard(
                    modifier = Modifier
                        .width(180.dp)
                        .height(44.dp)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                detailBottomBarRect = coords.boundsInRoot()
                            }
                        },
                    cornerRadius = 22.dp,
                    borderAlpha = 0f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite with pulse
                        val favScale by animateFloatAsState(
                            targetValue = if (currentItem.isFavorite) 1.25f else 1f,
                            animationSpec = spring(dampingRatio = 0.3f, stiffness = 600f),
                            label = "favScale"
                        )
                        val favGlow by animateFloatAsState(
                            targetValue = if (currentItem.isFavorite) 1f else 0f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                            label = "favGlow"
                        )
                        Box(modifier = Modifier.size(36.dp)) {
                            if (favGlow > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = favGlow * 0.4f; scaleX = favScale; scaleY = favScale }
                                        .background(Color.Red.copy(alpha = 0.3f), CircleShape)
                                )
                            }
                            AnimatedPressIcon(
                                onClick = { viewModel.toggleFavorite(currentItem) },
                                contentDescription = "Favorito",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                tint = if (currentItem.isFavorite) Color(0xFF0A84FF) else Color.White,
                                icon = {
                                    Icon(
                                        imageVector = if (currentItem.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (currentItem.isFavorite) Color(0xFF0A84FF) else Color.White,
                                        modifier = Modifier.size(18.dp).graphicsLayer(scaleX = favScale, scaleY = favScale)
                                    )
                                }
                            )
                        }

                        // Info
                        AnimatedPressIcon(
                            onClick = { showAdjustMetadataDialog = true },
                            contentDescription = "Info",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            tint = Color.White,
                            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        )

                        // Edit (tune / adjust)
                        AnimatedPressIcon(
                            onClick = { showAdjustSliders = !showAdjustSliders },
                            contentDescription = "Ajustar filtros",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showAdjustSliders) Color(0xFF0A84FF).copy(alpha = 0.2f) else Color.Transparent
                                ),
                            tint = if (showAdjustSliders) Color(0xFF0A84FF) else Color.White,
                            icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = if (showAdjustSliders) Color(0xFF0A84FF) else Color.White, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                // RIGHT: Delete only
                AnimatedPressIcon(
                    onClick = { showDeleteConfirm = true },
                    contentDescription = "Eliminar",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassBarFill)
                        .border(1.dp, GlassBarBorder, CircleShape),
                    tint = Color.White,
                    icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                )
            }
        }

        // --- 4. VIDEO CONTROLS (glass floating bar for video playback) ---
        AnimatedVisibility(
            visible = areControlsVisible && currentItem.isVideo && videoController.isReady,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
                initialOffsetY = { it }
            ) + fadeIn(),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
                targetOffsetY = { it }
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        videoControlsRect = coords.boundsInRoot()
                    }
                }
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Slider(
                        value = if (videoController.duration > 0) videoController.position.toFloat() / videoController.duration.toFloat() else 0f,
                        onValueChange = { fraction ->
                            videoController.seek((fraction * videoController.duration).toInt())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.85f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(videoController.position),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { videoController.togglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (videoController.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (videoController.isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = formatTime(videoController.duration),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- 5. FLOATING GLASS FILTERS ADJUST PANELS (slide up with spring) ---
        AnimatedVisibility(
            visible = showAdjustSliders,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f),
                initialOffsetY = { it }
            ) + fadeIn(),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f),
                targetOffsetY = { it }
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
                .padding(horizontal = 16.dp)
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Filtros de Cristal",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    // Brightness slider
                    GlassSlider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = -0.5f..0.5f,
                        label = "Brillo"
                    )

                    GlassSlider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = 0.5f..2.0f,
                        label = "Contraste"
                    )

                    GlassSlider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..2.0f,
                        label = "Saturación"
                    )

                    GlassSlider(
                        value = rotation,
                        onValueChange = { rotation = it },
                        valueRange = 0f..360f,
                        label = "Girar"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            brightness = 0f
                            contrast = 1f
                            saturation = 1f
                            rotation = 0f
                        }) {
                            Text("Restaurar", color = Color.White.copy(alpha = 0.6f))
                        }
                        TextButton(onClick = { showAdjustSliders = false }) {
                            Text("Guardar", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 6. ADJUST METADATA DIALOG ("Ajustar datos") ---
        if (showAdjustMetadataDialog) {
            AlertDialog(
                onDismissRequest = { showAdjustMetadataDialog = false },
                title = { Text("Ajustar datos de Imagen") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("Título / Nombre de Archivo") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newLocation,
                            onValueChange = { newLocation = it },
                            label = { Text("Ubicación (ej: Albacete, Peñíscola)") },
                            leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newDateText,
                            onValueChange = {
                                newDateText = it
                                dateError = false
                            },
                            label = { Text("Fecha (Formato: DD/MM/AAAA hh:mm)") },
                            leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                            singleLine = true,
                            isError = dateError,
                            supportingText = if (dateError) {{ Text("Fecha inválida. Usa DD/MM/AAAA hh:mm", color = MaterialTheme.colorScheme.error) }} else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable {
                                val parsedDate = try {
                                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    sdf.parse(newDateText)?.time
                                } catch (e: Exception) {
                                    null
                                }
                                if (parsedDate == null && newDateText.isNotBlank()) {
                                    dateError = true
                                    return@clickable
                                }
                                viewModel.changeMetadata(
                                    item = currentItem,
                                    title = newTitle,
                                    location = newLocation,
                                    dateMs = parsedDate
                                )
                                dateError = false
                                showAdjustMetadataDialog = false
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Actualizar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { showAdjustMetadataDialog = false }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                }
            )
        }

        // --- 7. ADD TO CUSTOM ALBUM DIALOG ---
        if (showAddAlbumDialog) {
            AlertDialog(
                onDismissRequest = { showAddAlbumDialog = false },
                title = { Text("Añadir a un Álbum") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Introduce el nombre del álbum en el que deseas añadir este elemento:")
                        OutlinedTextField(
                            value = albumNameInput,
                            onValueChange = { albumNameInput = it },
                            placeholder = { Text("Ej. Vacaciones, Familia") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable {
                                if (albumNameInput.isNotBlank()) {
                                    viewModel.addToAlbum(currentItem, albumNameInput.trim())
                                    showAddAlbumDialog = false
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Añadir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { showAddAlbumDialog = false }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                }
            )
        }

        // --- 8. DELETE CONFIRMATION DIALOG ---
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("¿Ocultar este elemento?") },
                text = { Text("El elemento se ocultará de la galería. Puedes recuperarlo desde la sección Ocultas.") },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.25f))
                            .border(1.dp, Color.Red.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                viewModel.deleteItem(currentItem)
                                showDeleteConfirm = false
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ocultar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { showDeleteConfirm = false }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                }
            )
        }
    }
}
