package com.brexa.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brexa.app.R
import com.brexa.app.data.ai.BrexamanChatMessage
import com.brexa.app.data.ai.MessageSender
import com.brexa.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrexamanChatSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    messages: List<BrexamanChatMessage>,
    isThinking: Boolean,
    onSendMessage: (String) -> Unit,
    onSendQuickPrompt: (String) -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = BorderSolid
            ) {}
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(BackgroundBase, CircleShape)
                            .border(1.5.dp, AccentLime, CircleShape)
                            .padding(2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.brexaman_peeking),
                            contentDescription = "Brexaman Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "BREXAMAN",
                                style = BrexaTypography.titleMedium.copy(
                                    color = AccentLime,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentPurple.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple)
                            ) {
                                Text(
                                    text = "AI TERMINAL",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = BrexaTypography.labelSmall.copy(
                                        color = AccentPurple,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        Text(
                            text = if (isThinking) "⚡ Analizando tus finanzas..." else "🟢 Conectado al historial Room",
                            style = BrexaTypography.labelSmall.copy(
                                color = if (isThinking) AccentLime else IncomeGreen,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSolid)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickPromptChip("⚡ ¿Cómo voy este mes?") { onSendQuickPrompt("MONTH_STATUS") }
                QuickPromptChip("📊 Gastos top") { onSendQuickPrompt("TOP_EXPENSES") }
                QuickPromptChip("💡 Consejo del día") { onSendQuickPrompt("DAILY_TIP") }
                QuickPromptChip("🚨 Pagos pendientes") { onSendQuickPrompt("PENDING_DEBTS") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        BrexamanBubble(
                            content = "¡Hola! Soy Brexaman. Conozco tus cuentas, tus gastos de este mes y tus deudas por pagar. Toca una sugerencia rápida o pregúntame lo que quieras."
                        )
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    if (msg.sender == MessageSender.USER) {
                        UserBubble(content = msg.content)
                    } else {
                        BrexamanBubble(content = msg.content)
                    }
                }

                if (isThinking) {
                    item {
                        ThinkingBubble()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundBase, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderSolid, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Pregúntale algo a Brexaman...",
                            style = BrexaTypography.bodyMedium.copy(color = TextMuted, fontSize = 13.sp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !isThinking) {
                                val text = inputText
                                inputText = ""
                                onSendMessage(text)
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isThinking) {
                            val text = inputText
                            inputText = ""
                            onSendMessage(text)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isThinking
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (inputText.isNotBlank() && !isThinking) AccentLime else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = SurfaceCardElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSolid)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = BrexaTypography.labelSmall.copy(
                color = AccentLime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun BrexamanBubble(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(BackgroundBase, CircleShape)
                .border(1.dp, AccentPurple, CircleShape)
                .padding(2.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.brexaman_peeking),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(BackgroundBase, RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .border(1.dp, BorderSolid, RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = content,
                style = BrexaTypography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            )
        }
    }
}

@Composable
private fun UserBubble(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(SurfaceCardElevated, RoundedCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .border(1.5.dp, AccentLime, RoundedCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = content,
                style = BrexaTypography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_dots")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dots_alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(36.dp))
        Box(
            modifier = Modifier
                .background(BackgroundBase, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSolid, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Brexaman está pensando...",
                style = BrexaTypography.labelSmall.copy(
                    color = AccentLime.copy(alpha = alpha),
                    fontSize = 11.sp
                )
            )
        }
    }
}
