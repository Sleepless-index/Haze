package sleepless_index.haze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The vault's own lock: a PIN, a pattern or a password. Only a salted hash is stored. */
object VaultLockStore {
    const val PIN = "pin"
    const val PATTERN = "pattern"
    const val PASSWORD = "password"

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 60_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Stored as "type|salt|hash". */
    const val PIN_LENGTH = 4

    fun make(type: String, secret: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val base = type + "|" + salt.joinToString("") { "%02x".format(it) } + "|" + hash(secret, salt)
        return if (type == PIN) base + "|" + secret.length else base
    }

    fun typeOf(stored: String): String? = stored.split("|").takeIf { it.size >= 3 }?.get(0)

    /** Digits in a saved PIN, or null for older PINs that were saved without one. */
    fun pinLength(stored: String): Int? = stored.split("|").getOrNull(3)?.toIntOrNull()

    fun verify(stored: String, secret: String): Boolean {
        val parts = stored.split("|")
        if (parts.size < 3) return false
        val salt = ByteArray(parts[1].length / 2) { parts[1].substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return MessageDigest.isEqual(hash(secret, salt).toByteArray(), parts[2].toByteArray())
    }
}

private fun typeName(type: String) = when (type) {
    VaultLockStore.PIN -> "PIN"
    VaultLockStore.PATTERN -> "pattern"
    else -> "password"
}

/** Collects a secret of the given type. [resetKey] clears the input (after a wrong try). */
@Composable
fun SecretInput(type: String, resetKey: Int, pinLength: Int?, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    when (type) {
        VaultLockStore.PIN -> PinPad(resetKey, pinLength, onSubmit, onTooShort)
        VaultLockStore.PATTERN -> PatternPad(resetKey, onSubmit, onTooShort)
        else -> PasswordEntry(resetKey, onSubmit, onTooShort)
    }
}

@Composable
private fun PinPad(resetKey: Int, length: Int?, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var pin by remember(resetKey) { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(length ?: maxOf(4, pin.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) cs.primary else cs.surfaceVariant)
                )
            }
        }
        val last = if (length == null) "OK" else ""
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", last))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Box(Modifier.size(72.dp))
                    } else {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (key == "OK") cs.primary else cs.surfaceVariant.copy(alpha = 0.7f))
                                .bounceClick {
                                    when (key) {
                                        "⌫" -> pin = pin.dropLast(1)
                                        "OK" -> if (pin.length >= 4) onSubmit(pin) else onTooShort("Use at least 4 digits")
                                        else -> {
                                            val max = length ?: 12
                                            if (pin.length < max) {
                                                pin += key
                                                if (length != null && pin.length == length) onSubmit(pin)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                key,
                                fontSize = if (key[0].isDigit()) 28.sp else 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (key == "OK") cs.onPrimary else cs.onBackground,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternPad(resetKey: Int, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.primary
    val dot = cs.onSurfaceVariant
    val path = remember(resetKey) { mutableStateListOf<Int>() }
    var finger by remember(resetKey) { mutableStateOf<Offset?>(null) }
    var sizePx by remember { mutableStateOf(1f) }

    fun center(i: Int): Offset {
        val cell = sizePx / 3f
        return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
    }

    fun hit(p: Offset) {
        for (i in 0 until 9) {
            if (i !in path && (center(i) - p).getDistance() < sizePx / 7f) path.add(i)
        }
    }

    Canvas(
        Modifier
            .size(300.dp)
            .pointerInput(resetKey) {
                detectDragGestures(
                    onDragStart = { o -> sizePx = size.width.toFloat(); path.clear(); finger = o; hit(o) },
                    onDrag = { change, _ -> change.consume(); finger = change.position; hit(change.position) },
                    onDragEnd = {
                        finger = null
                        if (path.size >= 4) {
                            onSubmit(path.joinToString("-"))
                        } else {
                            path.clear()
                            onTooShort("Connect at least 4 dots")
                        }
                    },
                    onDragCancel = { finger = null; path.clear() },
                )
            }
    ) {
        sizePx = size.width
        for (k in 1 until path.size) {
            drawLine(line, center(path[k - 1]), center(path[k]), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        val f = finger
        if (f != null && path.isNotEmpty()) {
            drawLine(line, center(path.last()), f, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        for (i in 0 until 9) {
            val on = i in path
            drawCircle(if (on) line else dot, radius = if (on) 10.dp.toPx() else 6.dp.toPx(), center = center(i))
            if (on) drawCircle(line.copy(alpha = 0.25f), radius = 26.dp.toPx(), center = center(i), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun PasswordEntry(resetKey: Int, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var text by remember(resetKey) { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    fun go() { if (text.length >= 4) onSubmit(text) else onTooShort("Use at least 4 characters") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else FirstLetterMask,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            trailingIcon = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).bounceClick(onClick = { show = !show }),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        if (show) sleepless_index.haze.ui.theme.GIcons.EyeOff else sleepless_index.haze.ui.theme.GIcons.Eye,
                        contentDescription = if (show) "Hide password" else "Show password",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { go() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Continue",
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(cs.primary)
                .bounceClick(onClick = { go() })
                .padding(horizontal = 40.dp, vertical = 14.dp),
        )
    }
}

/** Icon, title, message, input and footer stacked and centred. */
@Composable
private fun LockLayout(
    title: String,
    message: String?,
    messageIsError: Boolean,
    footer: String,
    onFooter: () -> Unit,
    lowered: Boolean,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = if (lowered) 16.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (lowered) Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(cs.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(sleepless_index.haze.ui.theme.GIcons.Lock, null, tint = cs.primary, modifier = Modifier.size(24.dp))
        }
        Text(
            title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            message ?: " ", fontSize = 14.sp, textAlign = TextAlign.Center,
            color = if (message != null && messageIsError) cs.error else cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        content()
        Text(
            footer,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .bounceClick(onClick = onFooter)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** Pick a lock type, enter it, confirm it. */
@Composable
fun LockSetup(title: String, onDone: (type: String, secret: String) -> Unit, onCancel: () -> Unit) {
    var type by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(0) }
    val t = type

    LockLayout(
        title = if (t == null) title else if (first == null) "Enter a new ${typeName(t)}" else "Confirm your ${typeName(t)}",
        message = if (t == null) "Choose how to lock the Vault" else msg,
        messageIsError = t != null,
        footer = "Cancel",
        onFooter = onCancel,
        lowered = t == VaultLockStore.PIN || t == VaultLockStore.PATTERN,
    ) {
        if (t == null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                listOf(VaultLockStore.PIN to "PIN", VaultLockStore.PATTERN to "Pattern", VaultLockStore.PASSWORD to "Password").forEach { (k, label) ->
                    sleepless_index.haze.editor.Chip(label, false) { type = k }
                }
            }
        } else {
            SecretInput(
                type = t, resetKey = key, pinLength = VaultLockStore.PIN_LENGTH,
                onTooShort = { msg = it; key++ },
                onSubmit = { s ->
                    val f = first
                    if (f == null) {
                        first = s; msg = null; key++
                    } else if (f == s) {
                        onDone(t, s)
                    } else {
                        first = null; msg = "Didn't match, try again"; key++
                    }
                },
            )
        }
    }
}

/** Asks for the saved lock. */
@Composable
fun LockEntry(stored: String, onUnlock: () -> Unit, onBack: () -> Unit) {
    val type = VaultLockStore.typeOf(stored) ?: VaultLockStore.PIN
    var msg by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(0) }
    LockLayout(
        title = "Enter your ${typeName(type)}",
        message = msg,
        messageIsError = true,
        footer = "Back",
        onFooter = onBack,
        lowered = type != VaultLockStore.PASSWORD,
    ) {
        SecretInput(
            type = type, resetKey = key, pinLength = VaultLockStore.pinLength(stored),
            onTooShort = { msg = it; key++ },
            onSubmit = { s ->
                if (VaultLockStore.verify(stored, s)) onUnlock() else { msg = "Wrong ${typeName(type)}"; key++ }
            },
        )
    }
}

/** Hides the password except for the letter just typed. */
private val FirstLetterMask = VisualTransformation { text ->
    val masked = if (text.isEmpty()) "" else "•".repeat(text.length - 1) + text.text.last()
    TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
}
