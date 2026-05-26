package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.ui.theme.*
import com.example.ui.viewmodel.DigiBookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    viewModel: DigiBookViewModel,
    modifier: Modifier = Modifier
) {
    val serverUrl by viewModel.serverUrlInput.collectAsState()
    val sessionToken by viewModel.sessionTokenInput.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    
    // Cloudflare Zero Trust States
    val cfEmail by viewModel.cfEmailInput.collectAsState()
    val cfCode by viewModel.cfCodeInput.collectAsState()
    val isCfOtpSent by viewModel.isCfOtpSent.collectAsState()
    val isCfConnecting by viewModel.isCfConnecting.collectAsState()
    val cfError by viewModel.cfError.collectAsState()
    val cfSuccessMessage by viewModel.cfSuccessMessage.collectAsState()
    
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepDarkBackground, Color(0xFF26242A), DeepDarkBackground)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
        ) {
            // Elegant glowing audio/book deck logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                colors = listOf(GoldPrimary.copy(alpha = 0.25f), Color.Transparent)
                            ),
                            radius = size.width * 0.8f
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SurfaceDarkCard)
                        .border(1.5.dp, GoldPrimary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "DigiBook Logo",
                        tint = GoldPrimary,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DIGIBOOK",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp
                ),
                color = GoldPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Self-Hosted Custom Audiobook Streamer",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Cloudflare Zero Trust Card (Optional)
            var cfExpanded by remember { mutableStateOf(isCfOtpSent || cfEmail.isNotEmpty()) }
            
            Surface(
                color = SurfaceDarkCard,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (cfSuccessMessage != null) TextGreenSync.copy(alpha = 0.5f) else SurfaceLighterCard),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cfExpanded = !cfExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Security",
                                tint = if (cfSuccessMessage != null) TextGreenSync else GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Cloudflare Zero Trust Access",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextCreamWhite
                                )
                                Text(
                                    text = if (cfSuccessMessage != null) "Authenticated: $cfEmail" else "Bypass Cloudflare Zero Trust protection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (cfSuccessMessage != null) TextGreenSync else TextMutedGray
                                )
                            }
                        }
                        
                        Text(
                            text = if (cfExpanded) "Hide" else "Show",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = GoldPrimary
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = cfExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text(
                                text = "If your server is protected by Cloudflare Zero Trust, request and confirm an OTP below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMutedGray,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Cloudflare Email
                            OutlinedTextField(
                                value = cfEmail,
                                onValueChange = { viewModel.cfEmailInput.value = it },
                                label = { Text("Email address", color = TextMutedGray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = SurfaceLighterCard,
                                    focusedTextColor = TextCreamWhite,
                                    unfocusedTextColor = TextCreamWhite,
                                    focusedContainerColor = DeepDarkBackground,
                                    unfocusedContainerColor = DeepDarkBackground
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth().testTag("cf_email_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Cloudflare OTP Code
                            AnimatedVisibility(
                                visible = isCfOtpSent,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    OutlinedTextField(
                                        value = cfCode,
                                        onValueChange = { viewModel.cfCodeInput.value = it },
                                        label = { Text("6-Digit PIN Code", color = TextMutedGray) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = SurfaceLighterCard,
                                            focusedTextColor = TextCreamWhite,
                                            unfocusedTextColor = TextCreamWhite,
                                            focusedContainerColor = DeepDarkBackground,
                                            unfocusedContainerColor = DeepDarkBackground
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("cf_code_input"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // CF error or messages
                            cfError?.let { err ->
                                Text(
                                    text = err,
                                    color = TextRedPending,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            cfSuccessMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    color = TextGreenSync,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Send / Resend button
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.sendCfOtp()
                                    },
                                    enabled = !isCfConnecting && cfEmail.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCfOtpSent) SurfaceLighterCard else GoldPrimary,
                                        contentColor = if (isCfOtpSent) TextCreamWhite else DeepDarkBackground,
                                        disabledContainerColor = SurfaceLighterCard.copy(alpha = 0.5f),
                                        disabledContentColor = TextMutedGray
                                    ),
                                    modifier = Modifier.weight(1.2f).height(44.dp).testTag("cf_send_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isCfConnecting && !isCfOtpSent) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DeepDarkBackground, strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            text = if (isCfOtpSent) "Resend Code" else "Send Code",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                // Verify button
                                if (isCfOtpSent) {
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.verifyCfOtp()
                                        },
                                        enabled = !isCfConnecting && cfCode.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldPrimary,
                                            contentColor = DeepDarkBackground,
                                            disabledContainerColor = SurfaceLighterCard.copy(alpha = 0.5f),
                                            disabledContentColor = TextMutedGray
                                        ),
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("cf_verify_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isCfConnecting) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DeepDarkBackground, strokeWidth = 2.dp)
                                        } else {
                                            Text(
                                                text = "Verify PIN",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Connection Box
            Surface(
                color = SurfaceDarkCard,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, SurfaceLighterCard),
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connect to Server",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextCreamWhite,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Server URL Input
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { viewModel.serverUrlInput.value = it },
                        label = { Text("Server URL", color = TextMutedGray) },
                        placeholder = { Text("https://your-server.com", color = TextMutedGray.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = SurfaceLighterCard,
                            focusedTextColor = TextCreamWhite,
                            unfocusedTextColor = TextCreamWhite,
                            focusedContainerColor = DeepDarkBackground,
                            unfocusedContainerColor = DeepDarkBackground
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // API Key / Token input
                    OutlinedTextField(
                        value = sessionToken,
                        onValueChange = { viewModel.sessionTokenInput.value = it },
                        label = { Text("Session Token / API Key", color = TextMutedGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = SurfaceLighterCard,
                            focusedTextColor = TextCreamWhite,
                            unfocusedTextColor = TextCreamWhite,
                            focusedContainerColor = DeepDarkBackground,
                            unfocusedContainerColor = DeepDarkBackground
                        ),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = BronzeSecondary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_token_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated connection error state
                    AnimatedVisibility(
                        visible = connectionError != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        connectionError?.let { err ->
                            Text(
                                text = err,
                                color = TextRedPending,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Start
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.validateAndConnect()
                        },
                        enabled = !isConnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = DeepDarkBackground,
                            disabledContainerColor = SurfaceLighterCard,
                            disabledContentColor = TextMutedGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("connect_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = DeepDarkBackground,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "SECURE SYNC",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Connected clients sync play positions in real time.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGray.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
