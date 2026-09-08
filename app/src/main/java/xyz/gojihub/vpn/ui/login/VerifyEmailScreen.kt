package xyz.gojihub.vpn.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.gojihub.vpn.auth.AuthRepository
import xyz.gojihub.vpn.auth.AuthResult
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.ui.theme.GodjiColors
import javax.inject.Inject

data class VerifyUiState(val code: String = "", val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(VerifyUiState())
    val state: StateFlow<VerifyUiState> = _state

    fun onCodeChange(v: String) { _state.value = _state.value.copy(code = v.filter { it.isDigit() }.take(6), error = null) }

    fun verify(email: String, onSuccess: () -> Unit) {
        val code = _state.value.code
        if (code.length != 6) {
            _state.value = _state.value.copy(error = Loc.s.errorEnter6Digits)
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = authRepository.verifyOtp(email, code)) {
                is AuthResult.Success -> onSuccess()
                is AuthResult.Error -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }
}

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerified: () -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(GodjiColors.Background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(Loc.s.verifyTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(Loc.s.verifySentTo(email), color = GodjiColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            label = { Text(Loc.s.verifyCodeLabel) },
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = GodjiColors.Danger, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.verify(email, onSuccess = onVerified) },
            enabled = !state.loading,
            colors = ButtonDefaults.buttonColors(containerColor = GodjiColors.TealBright),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (state.loading) Loc.s.verifyChecking else Loc.s.verifyConfirm, color = GodjiColors.Background, fontWeight = FontWeight.Bold)
        }
    }
}
