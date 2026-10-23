package com.mysecret.intimatetracker.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.db.SettingEntity
import com.mysecret.intimatetracker.data.db.SettingsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val settings: SettingsDao,
) : ViewModel() {

    var enabled by mutableStateOf(false)
        private set

    var unlocked by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            enabled = settings.get(KEY_ENABLED) == "true"
        }
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        unlocked = !value
        viewModelScope.launch { settings.put(SettingEntity(KEY_ENABLED, value.toString())) }
    }

    fun markUnlocked() {
        unlocked = true
    }

    companion object {
        const val KEY_ENABLED = "lock.enabled"
    }
}

@Composable
fun AppLockGate(
    content: @Composable () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    when {
        !viewModel.enabled -> content()
        viewModel.unlocked -> content()
        else -> LockScreen(onUnlock = viewModel::markUnlocked)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    fun prompt() {
        val activity = context as? FragmentActivity ?: return
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> message = "设备不支持生物识别"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> message = "请先在系统设置中录入指纹或锁屏密码"
            else -> message = "无法使用生物识别"
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    message = errString.toString()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.app_name))
                .setSubtitle("验证后查看记录")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "已锁定",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { prompt() }) {
            Text("解锁")
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    }
}
