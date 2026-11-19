package com.ultrasys.ultrachat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ultrasys.ultrachat.net.ServerUser
import com.ultrasys.ultrachat.ui.UserSearchState
import com.ultrasys.ultrachat.ui.components.Glyph
import com.ultrasys.ultrachat.ui.components.GlyphIcon
import com.ultrasys.ultrachat.ui.components.UltraAvatar
import com.ultrasys.ultrachat.ui.components.UltraDivider
import com.ultrasys.ultrachat.ui.theme.CreatorGold
import com.ultrasys.ultrachat.ui.theme.Danger
import com.ultrasys.ultrachat.ui.theme.Success
import com.ultrasys.ultrachat.ui.theme.TelegramBlue
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.TextTertiary
import com.ultrasys.ultrachat.ui.theme.UltraTheme

/**
 * Поиск собеседника по нику. В списке только совпадения с запросом,
 * а не все пользователи, которые когда-либо регистрировались.
 */
@Composable
fun AddContactDialog(
    state: UserSearchState,
    serverOnline: Boolean,
    onQueryChange: (String) -> Unit,
    onAdd: (ServerUser) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = UltraTheme.colors
    val query = state.query.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.panelElevated,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "Добавить собеседника",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "Введите имя или ник — сервер найдёт совпадения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Например, Lofer") },
                    leadingIcon = {
                        GlyphIcon(
                            glyph = Glyph.SEARCH,
                            modifier = Modifier.size(18.dp),
                            tint = TextSecondary,
                            strokeWidth = 1.8.dp,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.line,
                        focusedContainerColor = palette.panel,
                        unfocusedContainerColor = palette.panel,
                        cursorColor = palette.accent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                    ),
                )

                if (!serverOnline) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Нет связи с сервером. Проверьте интернет — поиск работает через сервер.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                    )
                }

                when {
                    state.loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = TelegramBlue,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Ищу…",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                    }

                    state.error != null -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Danger,
                        )
                    }

                    query.length == 1 -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Введите хотя бы 2 символа.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }

                    query.length >= 2 && state.users.isEmpty() && serverOnline -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Никого не нашли. Попробуйте другое имя.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }

                    state.users.isNotEmpty() -> {
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 330.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(items = state.users, key = { it.id }) { user ->
                                ContactResultRow(user = user, onClick = { onAdd(user) })
                                UltraDivider(Modifier.padding(start = 52.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = palette.accent)
            }
        },
    )
}

@Composable
private fun ContactResultRow(user: ServerUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UltraAvatar(
            displayName = user.displayName,
            size = 42.dp,
            isCreator = user.isCreator,
            avatarId = user.avatarId,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (user.isCreator) CreatorGold else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (user.online) "В сети" else "Не в сети",
                style = MaterialTheme.typography.bodySmall,
                color = if (user.online) Success else TextTertiary,
            )
        }
        Spacer(Modifier.width(8.dp))
        GlyphIcon(
            glyph = Glyph.PLUS,
            modifier = Modifier.size(19.dp),
            tint = TelegramBlue,
            strokeWidth = 2.dp,
        )
    }
}
