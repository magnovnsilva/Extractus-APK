package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppFilter
import com.example.data.model.SortOrder
import com.example.ui.components.AppDetailBottomSheet
import com.example.ui.components.AppListItem
import com.example.ui.components.BatchActionBar
import com.example.ui.components.ExtractedApkItem
import com.example.ui.components.ExtractionProgressDialog
import com.example.ui.components.WifiShareDialog
import com.example.ui.viewmodel.ApkExtractorViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ApkExtractorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val extractedApks by viewModel.extractedApks.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val filteredExtractedApks by viewModel.filteredExtractedApks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractionProgress by viewModel.extractionProgress.collectAsStateWithLifecycle()
    val extractingAppName by viewModel.extractingAppName.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()
    val selectedAppForDetail by viewModel.selectedAppForDetail.collectAsStateWithLifecycle()
    val wifiServerUrl by viewModel.wifiServerUrl.collectAsStateWithLifecycle()
    val wifiServerQrBitmap by viewModel.wifiServerQrBitmap.collectAsStateWithLifecycle()
    val wifiSharedFiles by viewModel.wifiSharedFiles.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Extractus Apk",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadData() },
                        modifier = Modifier.testTag("btn_refresh")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Atualizar lista")
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("btn_sort")
                        ) {
                            Icon(imageVector = Icons.Default.Sort, contentDescription = "Ordenar lista")
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Nome (A-Z)") },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.NAME_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Nome (Z-A)") },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.NAME_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tamanho (Maior primeiro)") },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.SIZE_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tamanho (Menor primeiro)") },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.SIZE_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Data de atualização (Recente)") },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.DATE_DESC)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BatchActionBar(
                selectedCount = selectedApps.size,
                onSelectAll = { viewModel.selectAll() },
                onClearSelection = { viewModel.clearSelection() },
                onBatchExtract = { viewModel.batchExtract(context) },
                onBatchShare = { viewModel.batchShare(context) },
                onBatchWifi = { viewModel.batchWifiShare() }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Pesquisar app ou pacote...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpar pesquisa")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_input")
            )

            // Filter Chips Bar
            val userAppsCount = remember(installedApps) { installedApps.count { !it.isSystemApp } }
            val systemAppsCount = remember(installedApps) { installedApps.count { it.isSystemApp } }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == AppFilter.ALL,
                    onClick = { viewModel.setFilter(AppFilter.ALL) },
                    label = { Text("Todos (${installedApps.size})", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("tab_all")
                )

                FilterChip(
                    selected = selectedFilter == AppFilter.USER,
                    onClick = { viewModel.setFilter(AppFilter.USER) },
                    label = { Text("Usuário ($userAppsCount)", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("tab_user")
                )

                FilterChip(
                    selected = selectedFilter == AppFilter.SYSTEM,
                    onClick = { viewModel.setFilter(AppFilter.SYSTEM) },
                    label = { Text("Sistema ($systemAppsCount)", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("tab_system")
                )

                FilterChip(
                    selected = selectedFilter == AppFilter.EXTRACTED,
                    onClick = { viewModel.setFilter(AppFilter.EXTRACTED) },
                    label = { Text("Extraídos (${extractedApks.size})", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("tab_extracted")
                )
            }

            // Main List Content
            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Carregando aplicativos do celular...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedFilter == AppFilter.EXTRACTED) {
                if (filteredExtractedApks.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderZip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nenhum APK extraído ainda",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Toque no botão de download em qualquer aplicativo para extrair o arquivo APK para o celular.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredExtractedApks, key = { it.id }) { apk ->
                            ExtractedApkItem(
                                apk = apk,
                                onInstallClick = { viewModel.installApk(File(apk.filePath), context) },
                                onShareClick = { viewModel.shareApkFile(File(apk.filePath), context) },
                                onWifiShareClick = { viewModel.startWifiShare(listOf(apk)) },
                                onDeleteClick = { viewModel.deleteExtractedApk(apk) }
                            )
                        }
                    }
                }
            } else {
                if (filteredApps.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nenhum aplicativo encontrado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tente alterar os termos de pesquisa ou o filtro selecionado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppListItem(
                                app = app,
                                isSelected = selectedApps.contains(app.packageName),
                                isBatchMode = selectedApps.isNotEmpty(),
                                onSelectToggle = { viewModel.toggleAppSelection(app.packageName) },
                                onCardClick = { viewModel.showAppDetail(app) },
                                onExtractClick = { viewModel.extractSingleApp(app, context) },
                                onShareClick = { viewModel.extractSingleApp(app, context, autoShare = true) },
                                onWifiShareClick = { viewModel.extractSingleApp(app, context, autoWifi = true) }
                            )
                        }
                    }
                }
            }
        }
    }

    // App Detail Bottom Sheet
    selectedAppForDetail?.let { app ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppDetailBottomSheet(
            app = app,
            sheetState = sheetState,
            onDismiss = { viewModel.dismissAppDetail() },
            onExtract = {
                viewModel.extractSingleApp(app, context)
                viewModel.dismissAppDetail()
            },
            onShare = {
                viewModel.extractSingleApp(app, context, autoShare = true)
                viewModel.dismissAppDetail()
            },
            onWifiShare = {
                viewModel.extractSingleApp(app, context, autoWifi = true)
                viewModel.dismissAppDetail()
            },
            onOpenApp = {
                viewModel.openApp(app, context)
                viewModel.dismissAppDetail()
            },
            onOpenSettings = {
                viewModel.openAppSettings(app, context)
                viewModel.dismissAppDetail()
            }
        )
    }

    // Wi-Fi Local Server Sharing Dialog
    wifiServerUrl?.let { url ->
        WifiShareDialog(
            url = url,
            qrBitmap = wifiServerQrBitmap,
            files = wifiSharedFiles,
            onDismiss = { viewModel.stopWifiShare() }
        )
    }

    // Ongoing Extraction Progress Modal
    if (isExtracting) {
        ExtractionProgressDialog(
            appName = extractingAppName,
            progress = extractionProgress
        )
    }
}
