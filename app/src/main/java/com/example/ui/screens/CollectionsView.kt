package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.data.MediaItem
import com.example.ui.GalleryViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.GlassBarFill
import com.example.ui.theme.GlassBarBorder
import android.hardware.biometrics.BiometricPrompt
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsView(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val favorites by viewModel.favoriteItems.collectAsStateWithLifecycle()
    val rawItems by viewModel.rawMediaItems.collectAsStateWithLifecycle()
    val memories by viewModel.memoryCollections.collectAsStateWithLifecycle()
    val customAlbums by viewModel.customAlbums.collectAsStateWithLifecycle()
    val hiddenItems by viewModel.hiddenItems.collectAsStateWithLifecycle()

    // Sub-view album viewer coordinates
    var selectedSubAlbumName by remember { mutableStateOf<String?>(null) }
    var selectedSubAlbumItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // MoreHoriz dropdown menu state
    var showMoreMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedSubAlbumName != null) {
        selectedSubAlbumName = null
    }

    var showBiometricDialog by remember { mutableStateOf(false) }

    if (showBiometricDialog) {
        val context = LocalContext.current
        LaunchedEffect(showBiometricDialog) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val executor = context.mainExecutor
                val biometricPrompt = BiometricPrompt.Builder(context)
                    .setTitle("Acceso a Ocultos")
                    .setSubtitle("Identifícate con tu huella")
                    .setNegativeButton("Cancelar", executor) { _, _ -> showBiometricDialog = false }
                    .build()
                biometricPrompt.authenticate(
                    android.os.CancellationSignal(), executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            showBiometricDialog = false
                            selectedSubAlbumName = "Ocultos"
                            selectedSubAlbumItems = hiddenItems
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            showBiometricDialog = false
                        }
                        override fun onAuthenticationFailed() {}
                    }
                )
            } else {
                showBiometricDialog = false
                selectedSubAlbumName = "Ocultos"
                selectedSubAlbumItems = hiddenItems
            }
        }
    }

    AnimatedContent(
        targetState = selectedSubAlbumName,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f),
                    initialOffsetX = { it / 3 }
                ) + fadeIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f))) togetherWith
                (slideOutHorizontally(
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f),
                    targetOffsetX = { -it / 3 }
                ) + fadeOut(animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f)))
            } else {
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f),
                    initialOffsetX = { -it / 3 }
                ) + fadeIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f))) togetherWith
                (slideOutHorizontally(
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f),
                    targetOffsetX = { it / 3 }
                ) + fadeOut(animationSpec = spring(dampingRatio = 0.5f, stiffness = 350f)))
            }
        },
        label = "subAlbumTransition"
    ) { subAlbum ->
        if (subAlbum != null) {
        // Sub Grid album inspector screen!
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedSubAlbumName = null },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassBarFill)
                        .border(1.dp, GlassBarBorder, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = selectedSubAlbumName ?: "Álbum",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${selectedSubAlbumItems.size} elementos",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            if (selectedSubAlbumItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Este álbum está vacío.", color = Color.Gray, fontSize = 15.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(selectedSubAlbumItems, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { viewModel.setDetailItem(item, selectedSubAlbumItems) }
                        ) {
                            val context = LocalContext.current
                            val imageRequest = remember(item.uri) {
                                ImageRequest.Builder(context)
                                    .data(item.uri)
                                    .size(256)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Main curated Collections scroll
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Header bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 12.dp)
                        .animateItem(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Colecciones",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${rawItems.size} elementos en total",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }

                    // Top Options
                    Box {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GlassBarFill)
                                .border(1.dp, GlassBarBorder, CircleShape)
                                .clickable { showMoreMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "Más opciones", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier
                                .background(Color(0xCC1C1C1E))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ajustes", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White) },
                                onClick = { showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Acerca de", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                                onClick = { showMoreMenu = false }
                            )
                        }
                    }
                }
            }

            // Memories section ("Recuerdos" Horizontal List with Play buttons)
            item {
                Column(modifier = Modifier.padding(vertical = 16.dp).animateItem()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recuerdos",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }

                    if (memories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay recuerdos registrados", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(memories) { group ->
                                Box(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable {
                                            selectedSubAlbumName = "Recuerdos: ${group.location}"
                                            selectedSubAlbumItems = group.items
                                        }
                                ) {
                                    val context = LocalContext.current
                                    val imageRequest = remember(group.coverUri) {
                                        ImageRequest.Builder(context)
                                            .data(group.coverUri)
                                            .size(512)
                                            .crossfade(true)
                                            .build()
                                    }
                                    // Memory cover photo
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Ambient dark vignette overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                )
                                            )
                                    )

                                    // Play icon overlay bottom-rightcorner
                                    Box(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                                            .align(Alignment.BottomEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Visualizar",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Location & timeline labels on bottom-left, matching screenshots exactly!
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = group.location,
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${group.description} ${group.year}",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // FIXED SECTION ("Fijadas" horizontal scroll: Favorites, Captured, Map coordinates)
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp).animateItem()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Fijadas",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card A: Favorites ("Favoritos")
                        item {
                            GlassCard(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clickable {
                                        selectedSubAlbumName = "Favoritos"
                                        selectedSubAlbumItems = favorites
                                    },
                                cornerRadius = 20.dp
                            ) {
                                // Cover if exists
                                if (favorites.isNotEmpty()) {
                                    val context = LocalContext.current
                                    val firstFavoriteUri = favorites.first().uri
                                    val imageRequest = remember(firstFavoriteUri) {
                                        ImageRequest.Builder(context)
                                            .data(firstFavoriteUri)
                                            .size(256)
                                            .crossfade(true)
                                            .build()
                                    }
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.25f))
                                    )
                                }

                                // Heart icon on top right
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                )

                                Text(
                                    "Favoritos",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .align(Alignment.BottomStart)
                                )
                            }
                        }

                        // Card B: Guardados recientemente
                        item {
                            val recentCount = rawItems.filter { !it.isHidden }.size.coerceAtMost(99)
                            GlassCard(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clickable {
                                        selectedSubAlbumName = "Guardados recientemente"
                                        selectedSubAlbumItems = rawItems.filter { !it.isHidden }.take(15)
                                    },
                                cornerRadius = 20.dp
                            ) {
                                if (rawItems.isNotEmpty()) {
                                    val context = LocalContext.current
                                    val firstItemUri = rawItems.first().uri
                                    val imageRequest = remember(firstItemUri) {
                                        ImageRequest.Builder(context)
                                            .data(firstItemUri)
                                            .size(256)
                                            .crossfade(true)
                                            .build()
                                    }
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.25f))
                                    )
                                }

                                // Photo count badge with icon
                                Box(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.25f), CircleShape)
                                        .align(Alignment.TopEnd),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "$recentCount",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                Text(
                                    "Guardados recientemente",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .align(Alignment.BottomStart)
                                )
                            }
                        }

                        // Card C: Mapa
                        item {
                            val mapContext = LocalContext.current
                            GlassCard(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clickable {
                                        val itemsWithLocation = rawItems.filter { !it.isHidden && it.location != null }
                                        if (itemsWithLocation.isNotEmpty()) {
                                            val uriBuilder = StringBuilder("https://www.google.com/maps/dir/?api=1")
                                            itemsWithLocation.take(25).forEachIndexed { index, item ->
                                                val coords = item.location ?: return@forEachIndexed
                                                if (coords.contains(",")) {
                                                    val parts = coords.split(",")
                                                    if (parts.size == 2) {
                                                        val lat = parts[0].trim()
                                                        val lon = parts[1].trim()
                                                        uriBuilder.append("&waypoint$index=$lat,$lon")
                                                    }
                                                }
                                            }
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                data = android.net.Uri.parse(uriBuilder.toString())
                                                setPackage("com.google.android.apps.maps")
                                            }
                                            try {
                                                mapContext.startActivity(intent)
                                            } catch (e: Exception) {
                                                val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    data = android.net.Uri.parse(uriBuilder.toString())
                                                }
                                                mapContext.startActivity(webIntent)
                                            }
                                        }
                                    },
                                cornerRadius = 20.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF30D158),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .align(Alignment.Center)
                                )

                                Text(
                                    "Mapa",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .align(Alignment.BottomStart)
                                )
                            }
                        }

                        // Card D: Secure Hidden Album
                        item {
                            GlassCard(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clickable {
                                        showBiometricDialog = true
                                    },
                                cornerRadius = 20.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .align(Alignment.Center)
                                )

                                Text(
                                    "Ocultas",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .align(Alignment.BottomStart)
                                )
                            }
                        }
                    }
                }
            }

            // USER CUSTOM ALBUMS LIST ("Álbumes")
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).animateItem()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Álbumes creados",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (customAlbums.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No has creado ningún álbum aún.\n(Usa 'Añadir a Álbum' en la foto para crear uno)",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (customAlbums.isNotEmpty()) {
                items(customAlbums.entries.toList(), key = { it.key }) { (albumName, albumItems) ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSubAlbumName = albumName
                                    selectedSubAlbumItems = albumItems
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Folder thumbnail cover
                            val context = LocalContext.current
                            val albumCoverUri = albumItems.firstOrNull()?.uri
                            val imageRequest = remember(albumCoverUri) {
                                ImageRequest.Builder(context)
                                    .data(albumCoverUri)
                                    .size(128)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1C1C1E))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(albumName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${albumItems.size} elementos", color = Color.Gray, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
    }
}
