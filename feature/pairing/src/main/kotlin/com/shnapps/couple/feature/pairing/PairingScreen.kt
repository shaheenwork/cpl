package com.shnapps.couple.feature.pairing

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.WaitingForPartner
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.DisplayFontFamily
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * Pairing two accounts (BUILD_PROMPT.md section 8).
 *
 * Deliberately **not** a `SecureScreen`. The code on this screen exists to be shared, and
 * a screenshot is a perfectly good way to share it. It is short-lived, single-use, and
 * useless without the creator's confirmation, so there is nothing here to protect.
 */
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.step) {
        if (state.step == PairingStep.Paired) onPaired()
    }

    PairingContent(
        state = state,
        onInvite = viewModel::startInvite,
        onHaveCode = viewModel::startEnteringCode,
        onCodeChange = viewModel::onCodeChange,
        onSubmitCode = viewModel::submitCode,
        onApprove = viewModel::approve,
        onDecline = viewModel::decline,
        onCancel = viewModel::cancel,
        onBack = viewModel::backToChoose,
        onShare = { code ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                // Neutral wording: this lands in a chat history someone else might read.
                putExtra(Intent.EXTRA_TEXT, "Our code: $code\n${inviteLink(code)}")
            }
            context.startActivity(Intent.createChooser(send, null))
        },
        modifier = modifier,
    )
}

/** The invite link. A custom scheme until an App Links domain exists (DECISIONS.md D-016). */
fun inviteLink(code: String): String = "afterhours://pair?code=$code"

@Composable
internal fun PairingContent(
    state: PairingUiState,
    onInvite: () -> Unit,
    onHaveCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        when (val step = state.step) {
            PairingStep.Choose -> ChooseStep(state.isBusy, onInvite, onHaveCode)
            is PairingStep.Invite -> InviteStep(step, state.isBusy, onShare, onInvite, onCancel)
            is PairingStep.ConfirmPartner -> ConfirmStep(step.verification, state.isBusy, onApprove, onDecline)
            PairingStep.EnterCode -> EnterCodeStep(state, onCodeChange, onSubmitCode, onBack)
            is PairingStep.Waiting -> WaitingStep(step.verification, state.isBusy, onCancel)
            PairingStep.Declined -> DeclinedStep(state.isBusy, onCancel)
            PairingStep.Paired -> Unit
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = state.errorMessage },
            )
        }
    }
}

@Composable
private fun Heading(eyebrow: String, headline: String, body: String? = null) {
    Text(text = eyebrow, style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
    Text(text = headline, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
    if (body != null) {
        Text(text = body, style = MaterialTheme.typography.bodyLarge, color = AfterhoursTheme.colors.textMuted)
    }
}

@Composable
private fun ChooseStep(busy: Boolean, onInvite: () -> Unit, onHaveCode: () -> Unit) {
    Heading(
        eyebrow = "JUST YOU TWO",
        headline = "Pair with your partner.",
        body = "One of you makes a code, the other enters it. Then you'll both check the same " +
            "three symbols, so you know it's really each other.",
    )
    Spacer(Modifier.padding(top = AfterhoursTheme.spacing.md))
    GlowButton(
        text = if (busy) "One moment…" else "Invite my partner",
        onClick = onInvite,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "I have a code",
        onClick = onHaveCode,
        style = GlowButtonStyle.Secondary,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun InviteStep(
    step: PairingStep.Invite,
    busy: Boolean,
    onShare: (String) -> Unit,
    onNewCode: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val minutesLeft = rememberMinutesLeft(step.expiresAtEpochMillis)
    val expired = minutesLeft != null && minutesLeft <= 0

    Heading(eyebrow = "YOUR CODE", headline = "Give them this.")
    CinematicCard(glowing = !expired) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                // Grouped 3+3 so it can be read aloud without losing the place.
                text = "${step.code.take(3)} ${step.code.drop(3)}",
                fontFamily = DisplayFontFamily,
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 4.sp),
                color = if (expired) AfterhoursTheme.colors.textFaint else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = "Your code: ${step.code.toCharArray().joinToString(" ")}"
                },
            )
            if (!expired) {
                QrCode(content = inviteLink(step.code), description = "QR code for your invite")
            }
            Text(
                text = when {
                    expired -> "This code expired."
                    minutesLeft == null -> "Valid for 15 minutes."
                    else -> "Expires in $minutesLeft min."
                },
                style = MaterialTheme.typography.bodySmall,
                color = AfterhoursTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (expired) {
        GlowButton(text = "Make a new code", onClick = onNewCode, enabled = !busy, modifier = Modifier.fillMaxWidth())
    } else {
        GlowButton(text = "Share code", onClick = { onShare(step.code) }, modifier = Modifier.fillMaxWidth())
    }
    GlowButton(
        text = "Cancel",
        onClick = onCancel,
        style = GlowButtonStyle.Quiet,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun rememberMinutesLeft(expiresAt: Long?): Long? {
    if (expiresAt == null) return null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    // Rounded up, so a code with 30 seconds left still reads "1 min" rather than "0".
    return ceil((expiresAt - now).coerceAtLeast(0) / MINUTE_MS.toDouble()).toLong()
}

@Composable
private fun VerificationSymbols(symbols: List<String>) {
    Text(
        text = symbols.joinToString("   "),
        style = TextStyle(fontSize = 44.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Verification symbols: ${symbols.joinToString(", ")}" },
    )
}

@Composable
private fun ConfirmStep(symbols: List<String>, busy: Boolean, onApprove: () -> Unit, onDecline: () -> Unit) {
    Heading(
        eyebrow = "SOMEONE ENTERED YOUR CODE",
        headline = "Is it really them?",
        body = "Ask your partner what's on their screen. It should be exactly this:",
    )
    CinematicCard(glowing = true) { VerificationSymbols(symbols) }
    Text(
        text = "If they don't see these — or haven't entered your code — it isn't them. Say no.",
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
    GlowButton(
        text = if (busy) "Pairing…" else "Yes — pair us",
        onClick = onApprove,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "No, that's not them",
        onClick = onDecline,
        style = GlowButtonStyle.Quiet,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EnterCodeStep(
    state: PairingUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Heading(eyebrow = "JOIN", headline = "Enter their code.", body = "Six digits, from your partner's screen.")
    OutlinedTextField(
        value = state.codeInput,
        onValueChange = onCodeChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Six-digit code" },
        label = { Text("Code") },
        singleLine = true,
        enabled = !state.isBusy,
        textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 6.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
    GlowButton(
        text = if (state.isBusy) "Checking…" else "Continue",
        onClick = onSubmit,
        enabled = state.canSubmitCode,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "Back",
        onClick = onBack,
        style = GlowButtonStyle.Quiet,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WaitingStep(symbols: List<String>, busy: Boolean, onCancel: () -> Unit) {
    Heading(
        eyebrow = "ALMOST THERE",
        headline = "Show them this.",
        body = "Your partner confirms on their phone that they see the same three symbols.",
    )
    CinematicCard(glowing = true) { VerificationSymbols(symbols) }
    WaitingForPartner(message = "Waiting for your partner to confirm")
    GlowButton(
        text = "Cancel",
        onClick = onCancel,
        style = GlowButtonStyle.Quiet,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeclinedStep(busy: Boolean, onStartOver: () -> Unit) {
    Heading(
        eyebrow = "NOT PAIRED",
        headline = "That didn't go through.",
        body = "Your partner didn't confirm, or made a new code. Ask them for the latest one.",
    )
    GlowButton(text = "Start again", onClick = onStartOver, enabled = !busy, modifier = Modifier.fillMaxWidth())
}

private const val TICK_MS = 15_000L
private const val MINUTE_MS = 60_000L
