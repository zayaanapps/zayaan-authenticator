package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.OtpAuthUriParser
import com.example.crypto.TotpGenerator
import com.example.data.BackupOperationResult
import com.example.data.RestoreOperationResult
import com.example.data.VaultRepository
import com.example.data.cloud.authStateFlow
import com.example.data.local.StoredVaultMetadata
import com.example.data.model.AccountCategory
import com.example.data.model.AppThemeMode
import com.example.data.model.AuthenticatorAccount
import com.example.data.model.AutoLockTimeout
import com.example.data.model.CloudConflictInfo
import com.example.data.model.CloudSyncState
import com.example.data.model.EncryptedCloudVault
import com.example.data.model.HashAlgorithm
import com.example.data.model.SecuritySettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CloudStatusUi(
    val signedInUser: FirebaseUser? = null,
    val syncState: CloudSyncState = CloudSyncState.OFFLINE_LOCAL_ONLY,
    val statusDetail: String = "Offline mode — codes generate locally",
    val remoteVault: EncryptedCloudVault? = null,
    val localMetadata: StoredVaultMetadata? = null,
    val conflict: CloudConflictInfo? = null,
    val isBusy: Boolean = false
)

data class LockUiState(
    val isLocked: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val errorMessage: String? = null
)

class AuthenticatorViewModel(
    private val repository: VaultRepository,
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<AccountCategory?>(null)
    val selectedCategory: StateFlow<AccountCategory?> = _selectedCategory.asStateFlow()

    private val _isReorderMode = MutableStateFlow(false)
    val isReorderMode: StateFlow<Boolean> = _isReorderMode.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _lockState = MutableStateFlow(LockUiState())
    val lockState: StateFlow<LockUiState> = _lockState.asStateFlow()

    private val _cloudStatus = MutableStateFlow(CloudStatusUi())
    val cloudStatus: StateFlow<CloudStatusUi> = _cloudStatus.asStateFlow()

    val securitySettings: StateFlow<SecuritySettings> = repository.securitySettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SecuritySettings()
        )

    val allAccounts: StateFlow<List<AuthenticatorAccount>> = repository.observeActiveAccounts()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val filteredAccounts: StateFlow<List<AuthenticatorAccount>> = combine(
        allAccounts,
        _searchQuery,
        _selectedCategory
    ) { accounts, query, category ->
        val q = query.trim().lowercase()
        accounts.filter { acc ->
            val matchesCategory = category == null || acc.category == category
            val matchesQuery = q.isEmpty() ||
                acc.issuer.lowercase().contains(q) ||
                acc.accountName.lowercase().contains(q) ||
                acc.category.displayName.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    private var remoteVaultObserverJob: Job? = null
    private var lastActivityEpochMs: Long = System.currentTimeMillis()
    private var backgroundedAtEpochMs: Long = 0L
    private var hasEvaluatedInitialLock = false

    init {
        startClockTicker()
        observeInitialLockSetting()
        observeVaultMetadata()
        observeFirebaseAuthState()
        startInactivityWatcher()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                _currentTimeMillis.value = System.currentTimeMillis()
                delay(250L)
            }
        }
    }

    private fun observeInitialLockSetting() {
        viewModelScope.launch {
            repository.securitySettingsFlow.collect { settings ->
                if (!hasEvaluatedInitialLock) {
                    hasEvaluatedInitialLock = true
                    if (settings.appLockEnabled) {
                        _lockState.update { it.copy(isLocked = true) }
                    }
                } else if (!settings.appLockEnabled && _lockState.value.isLocked) {
                    _lockState.update { it.copy(isLocked = false, errorMessage = null) }
                }
            }
        }
    }

    private fun observeVaultMetadata() {
        viewModelScope.launch {
            repository.vaultMetadataFlow.collect { meta ->
                _cloudStatus.update { current ->
                    val nextState = computeSyncState(
                        user = current.signedInUser,
                        meta = meta,
                        remote = current.remoteVault,
                        conflict = current.conflict,
                        existingState = current.syncState
                    )
                    current.copy(
                        localMetadata = meta,
                        syncState = nextState,
                        statusDetail = buildStatusDetail(current.signedInUser, meta, current.remoteVault, current.conflict, nextState)
                    )
                }
            }
        }
    }

    private fun observeFirebaseAuthState() {
        viewModelScope.launch {
            auth.authStateFlow().collect { user ->
                stopRemoteVaultObserver()
                if (user == null || user.uid.isBlank()) {
                    _cloudStatus.update {
                        it.copy(
                            signedInUser = null,
                            remoteVault = null,
                            conflict = null,
                            syncState = CloudSyncState.OFFLINE_LOCAL_ONLY,
                            statusDetail = "Offline mode — sign in with Google for encrypted cloud backup"
                        )
                    }
                } else {
                    _cloudStatus.update { current ->
                        current.copy(
                            signedInUser = user,
                            syncState = CloudSyncState.SYNCING,
                            statusDetail = "Checking encrypted cloud vault..."
                        )
                    }
                    startRemoteVaultObserver(user.uid)
                }
            }
        }
    }

    private fun startRemoteVaultObserver(userId: String) {
        stopRemoteVaultObserver()
        if (userId.isBlank()) return

        remoteVaultObserverJob = viewModelScope.launch {
            repository.observeRemoteCloudVault(userId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Cloud vault observer paused: ${root.message}")
                    _cloudStatus.update {
                        it.copy(
                            syncState = CloudSyncState.ERROR,
                            statusDetail = "Offline or cloud temporarily unreachable — local codes still work"
                        )
                    }
                }
                .collect { remoteVault ->
                    handleIncomingRemoteVault(userId, remoteVault)
                }
        }
    }

    fun stopRemoteVaultObserver() {
        remoteVaultObserverJob?.cancel()
        remoteVaultObserverJob = null
    }

    private suspend fun handleIncomingRemoteVault(
        userId: String,
        remoteVault: EncryptedCloudVault?
    ) {
        val localMeta = _cloudStatus.value.localMetadata
        if (remoteVault == null) {
            _cloudStatus.update { current ->
                val state = if (localMeta?.cloudWrappedKey != null) {
                    CloudSyncState.PENDING_BACKUP
                } else {
                    CloudSyncState.SIGNED_IN_SETUP_REQUIRED
                }
                current.copy(
                    remoteVault = null,
                    conflict = null,
                    syncState = state,
                    statusDetail = buildStatusDetail(current.signedInUser, localMeta, null, null, state)
                )
            }
            return
        }

        // Check whether this remote vault belongs to our same vaultUuid
        val localUuid = localMeta?.vaultUuid
        if (!localUuid.isNullOrBlank() && remoteVault.vaultUuid == localUuid) {
            if (remoteVault.vaultVersion > (localMeta.localVaultVersion)) {
                val synced = repository.syncIncomingRemoteVaultIfCompatible(userId, remoteVault)
                if (synced) {
                    _snackbarEvents.tryEmit("Synced updated vault from ${remoteVault.deviceName}")
                }
            }
            _cloudStatus.update { current ->
                current.copy(
                    remoteVault = remoteVault,
                    conflict = null,
                    syncState = CloudSyncState.SYNCED,
                    statusDetail = "Encrypted & synced (v${remoteVault.vaultVersion} • ${remoteVault.accountCount} accounts)"
                )
            }
        } else {
            // Different vault lineage (e.g. new device or reinstall!) — NEVER overwrite silently!
            val conflict = CloudConflictInfo(
                remoteVault = remoteVault,
                reason = "Encrypted cloud backup found (${remoteVault.accountCount} accounts from ${remoteVault.deviceName}). Enter your recovery password to restore it to this device.",
                isNewDeviceOrDifferentVault = true
            )
            _cloudStatus.update { current ->
                current.copy(
                    remoteVault = remoteVault,
                    conflict = conflict,
                    syncState = CloudSyncState.CONFLICT_DETECTED,
                    statusDetail = "Existing cloud backup detected — recovery required"
                )
            }
        }
    }

    private fun startInactivityWatcher() {
        viewModelScope.launch {
            while (isActive) {
                delay(5000L)
                val settings = securitySettings.value
                if (settings.appLockEnabled &&
                    !_lockState.value.isLocked &&
                    settings.autoLockTimeout != AutoLockTimeout.NEVER &&
                    settings.autoLockTimeout != AutoLockTimeout.IMMEDIATE
                ) {
                    val idleMs = System.currentTimeMillis() - lastActivityEpochMs
                    if (idleMs >= settings.autoLockTimeout.durationMs) {
                        _lockState.update { it.copy(isLocked = true, errorMessage = null) }
                    }
                }
            }
        }
    }

    fun recordUserActivity() {
        lastActivityEpochMs = System.currentTimeMillis()
    }

    fun onAppBackgrounded() {
        backgroundedAtEpochMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        val settings = securitySettings.value
        if (!settings.appLockEnabled) return
        val timeout = settings.autoLockTimeout
        if (timeout == AutoLockTimeout.NEVER) return
        val elapsed = if (backgroundedAtEpochMs > 0L) {
            System.currentTimeMillis() - backgroundedAtEpochMs
        } else {
            0L
        }
        if (timeout == AutoLockTimeout.IMMEDIATE || elapsed >= timeout.durationMs) {
            _lockState.update { it.copy(isLocked = true, errorMessage = null) }
        }
        lastActivityEpochMs = System.currentTimeMillis()
    }

    fun lockNow() {
        if (securitySettings.value.appLockEnabled) {
            _lockState.update { it.copy(isLocked = true, errorMessage = null) }
        }
    }

    fun unlockWithBiometricSuccess() {
        lastActivityEpochMs = System.currentTimeMillis()
        _lockState.value = LockUiState(isLocked = false)
    }

    fun verifyPinAndUnlock(candidate: String) {
        val now = System.currentTimeMillis()
        val state = _lockState.value
        if (now < state.lockoutUntilEpochMs) {
            val waitSec = ((state.lockoutUntilEpochMs - now) / 1000L).coerceAtLeast(1L)
            _lockState.update { it.copy(errorMessage = "Too many attempts. Try again in ${waitSec}s.") }
            return
        }
        viewModelScope.launch {
            val chars = candidate.toCharArray()
            val valid = try {
                repository.verifyAppLockPin(chars)
            } finally {
                chars.fill('\u0000')
            }
            if (valid) {
                lastActivityEpochMs = System.currentTimeMillis()
                _lockState.value = LockUiState(isLocked = false)
            } else {
                val attempts = state.failedAttempts + 1
                val lockoutUntil = if (attempts >= 5) System.currentTimeMillis() + 30_000L else 0L
                _lockState.value = LockUiState(
                    isLocked = true,
                    failedAttempts = attempts,
                    lockoutUntilEpochMs = lockoutUntil,
                    errorMessage = if (lockoutUntil > 0L) {
                        "5 failed attempts. Locked for 30 seconds."
                    } else {
                        "Incorrect passcode (${5 - attempts} attempts remaining)"
                    }
                )
            }
        }
    }

    // --- Dashboard Search, Filter & Account CRUD ---

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: AccountCategory?) {
        _selectedCategory.value = category
    }

    fun toggleReorderMode() {
        _isReorderMode.update { !it }
    }

    fun addManualAccount(
        issuer: String,
        accountName: String,
        secretRaw: String,
        algorithm: HashAlgorithm,
        digits: Int,
        periodSeconds: Int,
        category: AccountCategory,
        onComplete: () -> Unit
    ) {
        val normalizedSecret = TotpGenerator.normalizeBase32(secretRaw)
        if (!TotpGenerator.isValidBase32(normalizedSecret)) {
            _snackbarEvents.tryEmit("Invalid Base32 secret key")
            return
        }
        viewModelScope.launch {
            val account = AuthenticatorAccount(
                issuer = issuer.trim().ifBlank { "Account" },
                accountName = accountName.trim(),
                secretBase32 = normalizedSecret,
                algorithm = algorithm,
                digits = digits,
                periodSeconds = periodSeconds,
                category = category
            )
            repository.addAccounts(listOf(account))
            _snackbarEvents.tryEmit("Added ${account.displayTitle}")
            triggerAutoBackupIfEnabled()
            onComplete()
        }
    }

    fun importFromQrPayload(rawPayload: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        when (val parsed = OtpAuthUriParser.parse(rawPayload)) {
            is OtpAuthUriParser.ParseResult.Error -> onError(parsed.message)
            is OtpAuthUriParser.ParseResult.Success -> {
                viewModelScope.launch {
                    val count = repository.addAccounts(parsed.accounts)
                    val msg = if (count == 1) {
                        "Added ${parsed.accounts.first().displayTitle}"
                    } else {
                        "Imported $count authenticator accounts"
                    }
                    _snackbarEvents.tryEmit(msg)
                    triggerAutoBackupIfEnabled()
                    onSuccess(count)
                }
            }
        }
    }

    fun updateExistingAccount(account: AuthenticatorAccount, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateAccount(account)
            _snackbarEvents.tryEmit("Updated ${account.displayTitle}")
            triggerAutoBackupIfEnabled()
            onComplete()
        }
    }

    fun deleteAccount(account: AuthenticatorAccount) {
        viewModelScope.launch {
            repository.deleteAccount(account)
            _snackbarEvents.tryEmit("Removed ${account.displayTitle}")
            triggerAutoBackupIfEnabled()
        }
    }

    fun moveAccount(accountId: String, moveUp: Boolean) {
        val list = allAccounts.value.toMutableList()
        val index = list.indexOfFirst { it.id == accountId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in list.indices) return
        val moved = list.removeAt(index)
        list.add(targetIndex, moved)
        viewModelScope.launch {
            repository.reorderAccounts(list)
            triggerAutoBackupIfEnabled()
        }
    }

    // --- Security Settings Actions ---

    fun setAppLockPin(pinOrPassword: String) {
        viewModelScope.launch {
            val chars = pinOrPassword.toCharArray()
            try {
                repository.setAppLockPin(chars)
            } finally {
                chars.fill('\u0000')
            }
            _snackbarEvents.tryEmit("App lock passcode saved")
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            repository.disableAppLock()
            _snackbarEvents.tryEmit("App lock disabled")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricEnabled(enabled)
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            repository.setAutoLockTimeout(timeout)
        }
    }

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setScreenshotProtectionEnabled(enabled)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setAutoCloudBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoCloudBackupEnabled(enabled)
        }
    }

    // --- Zero-Knowledge Cloud Backup & Recovery Actions ---

    fun setupRecoveryPasswordAndBackup(
        recoveryPassword: String,
        forceReplaceExisting: Boolean = false,
        onResult: (BackupOperationResult) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            val res = BackupOperationResult.Failure("Sign in with Google first to enable encrypted cloud backup.")
            _snackbarEvents.tryEmit(res.message)
            onResult(res)
            return
        }
        viewModelScope.launch {
            _cloudStatus.update { it.copy(isBusy = true, syncState = CloudSyncState.SYNCING, statusDetail = "Deriving Argon2id key & encrypting vault...") }
            val chars = recoveryPassword.toCharArray()
            val result = try {
                repository.setupRecoveryPasswordAndBackup(
                    userId = uid,
                    recoveryPassword = chars,
                    forceReplaceExisting = forceReplaceExisting
                )
            } finally {
                chars.fill('\u0000')
            }
            applyBackupResult(result)
            onResult(result)
        }
    }

    fun changeRecoveryPassword(
        newRecoveryPassword: String,
        onResult: (BackupOperationResult) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            val res = BackupOperationResult.Failure("Sign in with Google to update your recovery password.")
            _snackbarEvents.tryEmit(res.message)
            onResult(res)
            return
        }
        viewModelScope.launch {
            _cloudStatus.update { it.copy(isBusy = true, syncState = CloudSyncState.SYNCING, statusDetail = "Re-wrapping vault key with Argon2id...") }
            val chars = newRecoveryPassword.toCharArray()
            val result = try {
                repository.changeRecoveryPassword(uid, chars)
            } finally {
                chars.fill('\u0000')
            }
            applyBackupResult(result)
            if (result is BackupOperationResult.Success) {
                _snackbarEvents.tryEmit("Recovery password updated & cloud vault re-wrapped")
            }
            onResult(result)
        }
    }

    fun triggerManualBackup(forceOverwrite: Boolean = false) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _snackbarEvents.tryEmit("Sign in with Google to back up your encrypted vault")
            return
        }
        viewModelScope.launch {
            _cloudStatus.update { it.copy(isBusy = true, syncState = CloudSyncState.SYNCING, statusDetail = "Encrypting & uploading vault...") }
            val result = repository.backupNow(uid, forceOverwrite = forceOverwrite)
            applyBackupResult(result)
        }
    }

    fun restoreFromCloud(
        recoveryPassword: String,
        mergeWithLocal: Boolean,
        onResult: (RestoreOperationResult) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            val res = RestoreOperationResult.Failure("Sign in with Google to restore your cloud backup.")
            _snackbarEvents.tryEmit(res.message)
            onResult(res)
            return
        }
        viewModelScope.launch {
            _cloudStatus.update { it.copy(isBusy = true, syncState = CloudSyncState.SYNCING, statusDetail = "Unlocking key with Argon2id & decrypting vault...") }
            val chars = recoveryPassword.toCharArray()
            val result = try {
                repository.restoreFromCloudWithRecoveryPassword(
                    userId = uid,
                    recoveryPassword = chars,
                    mergeWithLocal = mergeWithLocal
                )
            } finally {
                chars.fill('\u0000')
            }
            when (result) {
                is RestoreOperationResult.Success -> {
                    _cloudStatus.update {
                        it.copy(
                            isBusy = false,
                            conflict = null,
                            syncState = CloudSyncState.SYNCED,
                            statusDetail = "Restored ${result.restoredCount} accounts (v${result.version})"
                        )
                    }
                    _snackbarEvents.tryEmit("Restored ${result.restoredCount} encrypted accounts")
                }
                is RestoreOperationResult.WrongPassword -> {
                    _cloudStatus.update { it.copy(isBusy = false) }
                    _snackbarEvents.tryEmit(result.message)
                }
                is RestoreOperationResult.NoRemoteBackup -> {
                    _cloudStatus.update { it.copy(isBusy = false) }
                    _snackbarEvents.tryEmit(result.message)
                }
                is RestoreOperationResult.Failure -> {
                    _cloudStatus.update { it.copy(isBusy = false, syncState = CloudSyncState.ERROR, statusDetail = result.message) }
                    _snackbarEvents.tryEmit(result.message)
                }
            }
            onResult(result)
        }
    }

    fun deleteCloudBackup() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _cloudStatus.update { it.copy(isBusy = true) }
            val res = repository.deleteCloudBackup(uid)
            res.fold(
                onSuccess = {
                    _cloudStatus.update {
                        it.copy(
                            isBusy = false,
                            remoteVault = null,
                            conflict = null,
                            syncState = CloudSyncState.SIGNED_IN_SETUP_REQUIRED,
                            statusDetail = "Cloud backup deleted — local accounts unaffected"
                        )
                    }
                    _snackbarEvents.tryEmit("Cloud backup deleted")
                },
                onFailure = { err ->
                    _cloudStatus.update { it.copy(isBusy = false) }
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to delete cloud backup")
                }
            )
        }
    }

    fun notifyMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private fun triggerAutoBackupIfEnabled() {
        val uid = auth.currentUser?.uid ?: return
        val settings = securitySettings.value
        if (!settings.autoCloudBackupEnabled || !settings.recoveryPasswordConfigured) {
            _cloudStatus.update { current ->
                if (current.signedInUser != null && settings.recoveryPasswordConfigured) {
                    current.copy(syncState = CloudSyncState.PENDING_BACKUP, statusDetail = "Local changes not yet backed up")
                } else current
            }
            return
        }
        viewModelScope.launch {
            _cloudStatus.update { it.copy(syncState = CloudSyncState.SYNCING, statusDetail = "Auto-backing up encrypted vault...") }
            val result = repository.backupNow(uid, forceOverwrite = false)
            applyBackupResult(result, silentSuccess = true)
        }
    }

    private fun applyBackupResult(result: BackupOperationResult, silentSuccess: Boolean = false) {
        when (result) {
            is BackupOperationResult.Success -> {
                _cloudStatus.update {
                    it.copy(
                        isBusy = false,
                        conflict = null,
                        syncState = CloudSyncState.SYNCED,
                        statusDetail = "Encrypted & synced (v${result.version} • ${result.accountCount} accounts)"
                    )
                }
                if (!silentSuccess) {
                    _snackbarEvents.tryEmit("Encrypted cloud backup complete (v${result.version})")
                }
            }
            is BackupOperationResult.ConflictDetected -> {
                _cloudStatus.update {
                    it.copy(
                        isBusy = false,
                        conflict = result.conflict,
                        remoteVault = result.conflict.remoteVault,
                        syncState = CloudSyncState.CONFLICT_DETECTED,
                        statusDetail = "Backup conflict — action required"
                    )
                }
                _snackbarEvents.tryEmit("Existing cloud backup detected — review in Cloud Backup")
            }
            is BackupOperationResult.NeedsRecoveryPasswordSetup -> {
                _cloudStatus.update {
                    it.copy(
                        isBusy = false,
                        syncState = CloudSyncState.SIGNED_IN_SETUP_REQUIRED,
                        statusDetail = "Set a recovery password to enable encrypted backup"
                    )
                }
                if (!silentSuccess) {
                    _snackbarEvents.tryEmit(result.message)
                }
            }
            is BackupOperationResult.Failure -> {
                _cloudStatus.update {
                    it.copy(
                        isBusy = false,
                        syncState = CloudSyncState.PENDING_BACKUP,
                        statusDetail = "Offline / pending sync — local vault saved securely"
                    )
                }
                if (!silentSuccess) {
                    _snackbarEvents.tryEmit(result.message)
                }
            }
        }
    }

    private fun computeSyncState(
        user: FirebaseUser?,
        meta: StoredVaultMetadata?,
        remote: EncryptedCloudVault?,
        conflict: CloudConflictInfo?,
        existingState: CloudSyncState
    ): CloudSyncState {
        if (user == null) return CloudSyncState.OFFLINE_LOCAL_ONLY
        if (conflict != null) return CloudSyncState.CONFLICT_DETECTED
        if (existingState == CloudSyncState.SYNCING) return CloudSyncState.SYNCING
        if (meta?.cloudWrappedKey == null) return CloudSyncState.SIGNED_IN_SETUP_REQUIRED
        if (remote != null && meta.localVaultVersion >= remote.vaultVersion) return CloudSyncState.SYNCED
        return CloudSyncState.PENDING_BACKUP
    }

    private fun buildStatusDetail(
        user: FirebaseUser?,
        meta: StoredVaultMetadata?,
        remote: EncryptedCloudVault?,
        conflict: CloudConflictInfo?,
        state: CloudSyncState
    ): String {
        return when (state) {
            CloudSyncState.OFFLINE_LOCAL_ONLY -> "Offline mode — codes generate locally"
            CloudSyncState.SIGNED_IN_SETUP_REQUIRED -> "Signed in as ${user?.email ?: "Google user"} — set recovery password"
            CloudSyncState.SYNCING -> "Synchronizing encrypted vault..."
            CloudSyncState.SYNCED -> "Encrypted & synced (v${remote?.vaultVersion ?: meta?.localVaultVersion ?: 1})"
            CloudSyncState.PENDING_BACKUP -> "Local changes ready for encrypted backup"
            CloudSyncState.CONFLICT_DETECTED -> conflict?.reason ?: "Existing cloud backup detected"
            CloudSyncState.ERROR -> "Cloud unavailable — offline codes remain active"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRemoteVaultObserver()
    }

    private companion object {
        const val TAG = "AuthenticatorVM"
    }
}
