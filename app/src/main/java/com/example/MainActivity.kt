package com.example

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.crypto.AndroidKeystoreDeviceKeyManager
import com.example.crypto.QrImageDecoder
import com.example.data.VaultRepository
import com.example.data.cloud.FirestoreCloudVaultRepository
import com.example.data.cloud.GoogleAuthHelper
import com.example.data.local.VaultDatabase
import com.example.data.local.VaultPreferencesStore
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricAvailability
import com.example.ui.AuthenticatorViewModel
import com.example.ui.screens.AccountEditorScreen
import com.example.ui.screens.CloudBackupScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.theme.ZayanAuthenticatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val DASHBOARD = "dashboard"
    const val QR_SCANNER = "qr_scanner"
    const val MANUAL_ENTRY = "manual_entry"
    const val EDIT_ACCOUNT = "edit_account/{accountId}"
    const val SECURITY_SETTINGS = "security_settings"
    const val CLOUD_BACKUP = "cloud_backup"

    fun editAccount(accountId: String) = "edit_account/$accountId"
}

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: AuthenticatorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Protect verification codes & secrets against screenshots/screen-recording by default
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val appContext = applicationContext
        val repository = VaultRepository(
            accountDao = VaultDatabase.getInstance(appContext).encryptedAccountDao(),
            preferencesStore = VaultPreferencesStore(appContext),
            deviceKeyManager = AndroidKeystoreDeviceKeyManager(),
            cloudDataSource = FirestoreCloudVaultRepository(appContext)
        )

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthenticatorViewModel(repository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[AuthenticatorViewModel::class.java]

        setContent {
            ZayanAuthenticatorApp(
                activity = this,
                viewModel = viewModel,
                onApplyScreenshotProtection = { enabled ->
                    if (enabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            )
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (::viewModel.isInitialized) {
            viewModel.recordUserActivity()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) {
            viewModel.onAppBackgrounded()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::viewModel.isInitialized) {
            viewModel.onAppForegrounded()
        }
    }
}

@Composable
fun ZayanAuthenticatorApp(
    activity: FragmentActivity,
    viewModel: AuthenticatorViewModel,
    onApplyScreenshotProtection: (Boolean) -> Unit
) {
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val filteredAccounts by viewModel.filteredAccounts.collectAsStateWithLifecycle()
    val currentTimeMillis by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isReorderMode by viewModel.isReorderMode.collectAsStateWithLifecycle()
    val cloudStatus by viewModel.cloudStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val biometricAvailability = remember(activity) { BiometricAuthHelper.checkAvailability(activity) }

    // Apply FLAG_SECURE dynamically whenever screenshot protection preference changes
    LaunchedEffect(securitySettings.screenshotProtectionEnabled) {
        onApplyScreenshotProtection(securitySettings.screenshotProtectionEnabled)
    }

    // Attempt silent Google Sign-In on startup (never blocks offline usage)
    LaunchedEffect(Unit) {
        val credentialManager = CredentialManager.create(activity)
        GoogleAuthHelper.attemptAutoSignIn(
            context = activity,
            credentialManager = credentialManager,
            scope = scope
        )
    }

    // Collect toast/snackbar notifications
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Zero-permission Android Photo Picker for importing QR code images
    val qrPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decodedText = withContext(Dispatchers.IO) {
                    try {
                        activity.contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            bitmap?.let { QrImageDecoder.decodeFromBitmap(it) }
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (decodedText.isNullOrBlank()) {
                    viewModel.notifyMessage("No readable QR code found in the selected image.")
                } else {
                    viewModel.importFromQrPayload(
                        rawPayload = decodedText,
                        onSuccess = {},
                        onError = { err -> viewModel.notifyMessage(err) }
                    )
                }
            }
        }
    }

    fun launchPhotoPickerForQr() {
        qrPhotoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    ZayanAuthenticatorTheme(themeMode = securitySettings.themeMode) {
        if (lockState.isLocked && securitySettings.appLockEnabled) {
            val canUseBiometric = securitySettings.biometricEnabled &&
                biometricAvailability == BiometricAvailability.AVAILABLE
            LockScreen(
                lockState = lockState,
                biometricAvailableAndEnabled = canUseBiometric,
                onUnlockWithPin = { pin -> viewModel.verifyPinAndUnlock(pin) },
                onTriggerBiometric = {
                    if (canUseBiometric) {
                        BiometricAuthHelper.authenticate(
                            activity = activity,
                            onSuccess = { viewModel.unlockWithBiometricSuccess() },
                            onError = { msg -> viewModel.notifyMessage(msg) }
                        )
                    }
                }
            )
        } else {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        accounts = filteredAccounts,
                        totalAccountCount = allAccounts.size,
                        currentTimeMillis = currentTimeMillis,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        isReorderMode = isReorderMode,
                        cloudStatus = cloudStatus,
                        securitySettings = securitySettings,
                        snackbarHostState = snackbarHostState,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onSelectCategory = viewModel::setSelectedCategory,
                        onToggleReorderMode = viewModel::toggleReorderMode,
                        onMoveAccount = viewModel::moveAccount,
                        onCopyCode = { account, _ ->
                            viewModel.notifyMessage("Copied ${account.displayTitle} code")
                        },
                        onOpenQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                        onImportQrFromPhoto = { launchPhotoPickerForQr() },
                        onOpenManualEntry = { navController.navigate(Routes.MANUAL_ENTRY) },
                        onEditAccount = { account ->
                            navController.navigate(Routes.editAccount(account.id))
                        },
                        onDeleteAccount = viewModel::deleteAccount,
                        onOpenSecuritySettings = { navController.navigate(Routes.SECURITY_SETTINGS) },
                        onOpenCloudBackup = { navController.navigate(Routes.CLOUD_BACKUP) },
                        onLockNow = viewModel::lockNow
                    )
                }

                composable(Routes.QR_SCANNER) {
                    QrScannerScreen(
                        onQrCodeScanned = { rawQr ->
                            viewModel.importFromQrPayload(
                                rawPayload = rawQr,
                                onSuccess = { navController.popBackStack() },
                                onError = { msg ->
                                    viewModel.notifyMessage(msg)
                                    navController.popBackStack()
                                }
                            )
                        },
                        onImportQrFromPhoto = {
                            navController.popBackStack()
                            launchPhotoPickerForQr()
                        },
                        onManualEntry = {
                            navController.popBackStack()
                            navController.navigate(Routes.MANUAL_ENTRY)
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.MANUAL_ENTRY) {
                    AccountEditorScreen(
                        existingAccount = null,
                        currentTimeMillis = currentTimeMillis,
                        onSaveNew = { issuer, accountName, secret, algo, digits, period, category ->
                            viewModel.addManualAccount(
                                issuer = issuer,
                                accountName = accountName,
                                secretRaw = secret,
                                algorithm = algo,
                                digits = digits,
                                periodSeconds = period,
                                category = category,
                                onComplete = { navController.popBackStack() }
                            )
                        },
                        onSaveEdit = {},
                        onDeleteExisting = {},
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.EDIT_ACCOUNT,
                    arguments = listOf(navArgument("accountId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val accountId = backStackEntry.arguments?.getString("accountId")
                    val account = allAccounts.firstOrNull { it.id == accountId }
                    AccountEditorScreen(
                        existingAccount = account,
                        currentTimeMillis = currentTimeMillis,
                        onSaveNew = { _, _, _, _, _, _, _ -> },
                        onSaveEdit = { updated ->
                            viewModel.updateExistingAccount(updated) {
                                navController.popBackStack()
                            }
                        },
                        onDeleteExisting = { toDelete ->
                            viewModel.deleteAccount(toDelete)
                            navController.popBackStack()
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SECURITY_SETTINGS) {
                    SecuritySettingsScreen(
                        settings = securitySettings,
                        cloudStatus = cloudStatus,
                        biometricAvailability = biometricAvailability,
                        onSetPin = viewModel::setAppLockPin,
                        onDisableAppLock = viewModel::disableAppLock,
                        onToggleBiometric = viewModel::setBiometricEnabled,
                        onSelectAutoLockTimeout = viewModel::setAutoLockTimeout,
                        onToggleScreenshotProtection = viewModel::setScreenshotProtectionEnabled,
                        onSelectThemeMode = viewModel::setThemeMode,
                        onOpenCloudBackup = { navController.navigate(Routes.CLOUD_BACKUP) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.CLOUD_BACKUP) {
                    CloudBackupScreen(
                        cloudStatus = cloudStatus,
                        settings = securitySettings,
                        localAccountCount = allAccounts.size,
                        snackbarHostState = snackbarHostState,
                        onStopCloudListenerBeforeSignOut = viewModel::stopRemoteVaultObserver,
                        onAuthMessage = viewModel::notifyMessage,
                        onToggleAutoBackup = viewModel::setAutoCloudBackupEnabled,
                        onSetupRecoveryPassword = viewModel::setupRecoveryPasswordAndBackup,
                        onChangeRecoveryPassword = viewModel::changeRecoveryPassword,
                        onManualBackup = viewModel::triggerManualBackup,
                        onRestoreFromCloud = viewModel::restoreFromCloud,
                        onDeleteCloudBackup = viewModel::deleteCloudBackup,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
