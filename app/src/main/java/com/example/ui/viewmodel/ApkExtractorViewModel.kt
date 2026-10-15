package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppFilter
import com.example.data.model.AppItem
import com.example.data.model.ExtractedApk
import com.example.data.model.SortOrder
import com.example.data.repository.AppManagerRepository
import com.example.server.LocalHttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ApkExtractorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppManagerRepository(application)
    private val localHttpServer = LocalHttpServer(application)

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _extractedApks = MutableStateFlow<List<ExtractedApk>>(emptyList())
    val extractedApks: StateFlow<List<ExtractedApk>> = _extractedApks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractionProgress = MutableStateFlow(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()

    private val _extractingAppName = MutableStateFlow("")
    val extractingAppName: StateFlow<String> = _extractingAppName.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppFilter.ALL)
    val selectedFilter: StateFlow<AppFilter> = _selectedFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NAME_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedApps: StateFlow<Set<String>> = _selectedApps.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<AppItem?>(null)
    val selectedAppForDetail: StateFlow<AppItem?> = _selectedAppForDetail.asStateFlow()

    // Wi-Fi sharing state
    private val _wifiServerUrl = MutableStateFlow<String?>(null)
    val wifiServerUrl: StateFlow<String?> = _wifiServerUrl.asStateFlow()

    private val _wifiServerQrBitmap = MutableStateFlow<Bitmap?>(null)
    val wifiServerQrBitmap: StateFlow<Bitmap?> = _wifiServerQrBitmap.asStateFlow()

    private val _wifiSharedFiles = MutableStateFlow<List<ExtractedApk>>(emptyList())
    val wifiSharedFiles: StateFlow<List<ExtractedApk>> = _wifiSharedFiles.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _installedApps.value = repository.getInstalledApps()
            _extractedApks.value = repository.loadExtractedApks()
            _isLoading.value = false
        }
    }

    fun refreshExtracted() {
        viewModelScope.launch {
            _extractedApks.value = repository.loadExtractedApks()
        }
    }

    val filteredApps: StateFlow<List<AppItem>> = combine(
        _installedApps,
        _searchQuery,
        _selectedFilter,
        _sortOrder
    ) { apps, query, filter, sort ->
        val filtered = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
                AppFilter.EXTRACTED -> true
            }

            val matchesQuery = query.isBlank() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }

        when (sort) {
            SortOrder.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.appName.lowercase() }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.apkSize }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.apkSize }
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.lastUpdateTime }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.firstInstallTime }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredExtractedApks: StateFlow<List<ExtractedApk>> = combine(
        _extractedApks,
        _searchQuery
    ) { apks, query ->
        if (query.isBlank()) {
            apks
        } else {
            apks.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true) ||
                        it.fileName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: AppFilter) {
        _selectedFilter.value = filter
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleAppSelection(packageName: String) {
        val current = _selectedApps.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedApps.value = current
    }

    fun selectAll() {
        val currentVisible = filteredApps.value.map { it.packageName }.toSet()
        _selectedApps.value = currentVisible
    }

    fun clearSelection() {
        _selectedApps.value = emptySet()
    }

    fun showAppDetail(app: AppItem) {
        _selectedAppForDetail.value = app
    }

    fun dismissAppDetail() {
        _selectedAppForDetail.value = null
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun extractSingleApp(
        app: AppItem,
        context: Context,
        autoShare: Boolean = false,
        autoWifi: Boolean = false
    ) {
        viewModelScope.launch {
            _isExtracting.value = true
            _extractingAppName.value = app.appName
            _extractionProgress.value = 0f

            val result = repository.extractApk(app) { progress ->
                _extractionProgress.value = progress
            }

            _isExtracting.value = false

            result.onSuccess { extracted ->
                refreshExtracted()
                _userMessage.value = "${app.appName} extraído com sucesso!"

                if (autoShare) {
                    shareApkFile(File(extracted.filePath), context)
                } else if (autoWifi) {
                    startWifiShare(listOf(extracted))
                }
            }.onFailure { e ->
                _userMessage.value = "Erro ao extrair: ${e.message}"
            }
        }
    }

    fun batchExtract(context: Context) {
        val selectedPackages = _selectedApps.value
        if (selectedPackages.isEmpty()) return

        val appsToExtract = _installedApps.value.filter { it.packageName in selectedPackages }

        viewModelScope.launch {
            _isExtracting.value = true
            var successCount = 0

            for ((index, app) in appsToExtract.withIndex()) {
                _extractingAppName.value = "(${index + 1}/${appsToExtract.size}) ${app.appName}"
                _extractionProgress.value = (index.toFloat() / appsToExtract.size.toFloat())

                val result = repository.extractApk(app)
                if (result.isSuccess) {
                    successCount++
                }
            }

            _isExtracting.value = false
            refreshExtracted()
            clearSelection()
            _userMessage.value = "$successCount de ${appsToExtract.size} APKs extraídos com sucesso!"
        }
    }

    fun batchShare(context: Context) {
        val selectedPackages = _selectedApps.value
        if (selectedPackages.isEmpty()) return

        val appsToShare = _installedApps.value.filter { it.packageName in selectedPackages }

        viewModelScope.launch {
            _isExtracting.value = true
            val extractedFiles = mutableListOf<File>()

            for ((index, app) in appsToShare.withIndex()) {
                _extractingAppName.value = "Preparando (${index + 1}/${appsToShare.size}) ${app.appName}..."
                _extractionProgress.value = (index.toFloat() / appsToShare.size.toFloat())

                val result = repository.extractApk(app)
                result.onSuccess {
                    extractedFiles.add(File(it.filePath))
                }
            }

            _isExtracting.value = false
            refreshExtracted()
            clearSelection()

            if (extractedFiles.isNotEmpty()) {
                shareMultipleApkFiles(extractedFiles, context)
            } else {
                _userMessage.value = "Nenhum APK pôde ser preparado para compartilhamento"
            }
        }
    }

    fun batchWifiShare() {
        val selectedPackages = _selectedApps.value
        if (selectedPackages.isEmpty()) return

        val appsToShare = _installedApps.value.filter { it.packageName in selectedPackages }

        viewModelScope.launch {
            _isExtracting.value = true
            val extractedList = mutableListOf<ExtractedApk>()

            for ((index, app) in appsToShare.withIndex()) {
                _extractingAppName.value = "Preparando (${index + 1}/${appsToShare.size}) ${app.appName}..."
                val result = repository.extractApk(app)
                result.onSuccess {
                    extractedList.add(it)
                }
            }

            _isExtracting.value = false
            refreshExtracted()
            clearSelection()

            if (extractedList.isNotEmpty()) {
                startWifiShare(extractedList)
            }
        }
    }

    fun shareApkFile(file: File, context: Context) {
        try {
            val shareIntent = repository.createShareIntent(listOf(file))
            val chooser = Intent.createChooser(shareIntent, "Compartilhar APK via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMultipleApkFiles(files: List<File>, context: Context) {
        try {
            val shareIntent = repository.createShareIntent(files)
            val chooser = Intent.createChooser(shareIntent, "Compartilhar ${files.size} APKs via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun installApk(file: File, context: Context) {
        try {
            val intent = repository.createInstallIntent(file)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir o instalador: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteExtractedApk(apk: ExtractedApk) {
        viewModelScope.launch {
            val deleted = repository.deleteExtractedApk(apk)
            if (deleted) {
                refreshExtracted()
                _userMessage.value = "${apk.fileName} excluído"
            }
        }
    }

    fun startWifiShare(files: List<ExtractedApk>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            try {
                val url = localHttpServer.startServer(files)
                _wifiServerUrl.value = url
                _wifiSharedFiles.value = files
                _wifiServerQrBitmap.value = LocalHttpServer.generateQrCodeBitmap(url, 512)
            } catch (e: Exception) {
                _userMessage.value = "Erro ao iniciar servidor Wi-Fi: ${e.message}"
            }
        }
    }

    fun stopWifiShare() {
        localHttpServer.stopServer()
        _wifiServerUrl.value = null
        _wifiServerQrBitmap.value = null
        _wifiSharedFiles.value = emptyList()
    }

    fun openApp(app: AppItem, context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "Este aplicativo não possui tela inicial", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppSettings(app: AppItem, context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir configurações: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        localHttpServer.stopServer()
    }
}
