package com.lingion.sleepy.ui.screen.imports

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.XmuAutoLoginClient
import com.lingion.sleepy.data.jw.XmuJw
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.launch

/**
 * 厦门大学课表导入(账号密码自动登录单通道)。
 *
 * v1.0.52-xmu4: 移除手动登录(WebView)入口 — 厦大统一身份认证对在校生登录不出现
 * 图形验证码, 账号密码自动登录可独立完成。流程: 输入学号/密码 → 自动登录抓取 →
 * 解析 → 自动建表落库(开学日期按当前学期推断, 节次时间用教务字典 + 厦大作息兜底),
 * 完成后直接回到课表页。
 */
class JwImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = remember(systemDark) {
                AppPrefs.isDarkMode(this@JwImportActivity, systemDark)
            }
            val themeKey by AppPrefs.themeKeyFlow(this@JwImportActivity)
                .collectAsState(initial = AppPrefs.getThemeKey(this@JwImportActivity))
            SleepyThemeProvider(darkTheme = dark, themeKey = themeKey) {
                val jwViewModel: JwImportViewModel = viewModel()
                val scope = rememberCoroutineScope()

                // 学号预填(仅记忆学号, 不存密码)
                var account by remember {
                    mutableStateOf(AppPrefs.getJwAccount(this@JwImportActivity))
                }
                var busy by remember { mutableStateOf(false) }
                var errorMsg by remember { mutableStateOf<String?>(null) }
                var statusMsg by remember { mutableStateOf<String?>(null) }
                var importFinished by remember { mutableStateOf(false) }

                fun rememberAccount() {
                    if (account.isNotBlank()) AppPrefs.setJwAccount(this@JwImportActivity, account)
                }

                /** 节次字典缺失时的兜底(厦大作息表 1..11 节; 超 11 节用默认课表的节次)。 */
                fun xmuFallbackRow(node: Int): Pair<String, String> {
                    XmuJw.FALLBACK_PERIOD[node]?.let { return it }
                    val def = TimeTableUtils.parseTimeSlotRows(TimeTableUtils.DEFAULT_TIME_JSON)
                        .firstOrNull { it.node == node }
                    return if (def != null && def.start.isNotBlank() && def.end.isNotBlank()) {
                        def.start to def.end
                    } else {
                        "21:45" to "22:30"
                    }
                }

                fun runAutoLogin(acct: String, password: String) {
                    if (busy) return
                    if (acct.isBlank() || password.isBlank()) {
                        errorMsg = getString(R.string.xmu_account_required)
                        return
                    }
                    busy = true
                    rememberAccount()
                    statusMsg = getString(R.string.xmu_auto_logging_in)
                    scope.launch {
                        try {
                            val client = XmuAutoLoginClient(acct, password)
                            val result = client.fetchSchedule()
                            // 解析 → 自动建表落库(开学日期自动推断, 节次时间自动铺满)
                            statusMsg = getString(R.string.import_parsing)
                            val courses = jwViewModel.parseHtml(result.courseJson, JwProtocol.TYPE_XMU)
                            Log.d("JwImport", "xmu parseHtml returned ${courses.size} courses semester=${result.semesterCode}")
                            if (courses.isEmpty()) {
                                errorMsg = getString(R.string.jw_err_empty_semester)
                                statusMsg = null
                                busy = false
                                return@launch
                            }
                            val maxNode = courses.maxOf { maxOf(it.startNode, it.endNode) }
                            val periodMap = HashMap<Int, Pair<String, String>>()
                            result.periods.forEach { periodMap[it.first] = it.second to it.third }
                            val rows = (1..maxNode).map { node ->
                                val t = periodMap[node] ?: xmuFallbackRow(node)
                                TimeTableUtils.TimeSlotRow(node = node, start = t.first, end = t.second)
                            }
                            jwViewModel.importAsNewTable(
                                courses = courses,
                                tableName = getString(R.string.xmu_table_default_name),
                                startDate = null,
                                timeJson = TimeTableUtils.buildTimeJsonFromRows(rows),
                                nodesPerDay = maxNode
                            )
                            statusMsg = getString(R.string.jw_import_success, courses.size)
                            importFinished = true
                        } catch (e: XmuAutoLoginClient.LoginException) {
                            Log.w("JwImport", "auto login failed kind=${e.kind}", e)
                            statusMsg = null
                            errorMsg = e.message ?: getString(R.string.xmu_auto_failed)
                            busy = false
                        } catch (e: Exception) {
                            Log.e("JwImport", "auto login unexpected", e)
                            statusMsg = null
                            errorMsg = getString(R.string.jw_parse_failed, e.message ?: "")
                            busy = false
                        }
                    }
                }

                if (importFinished) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    XmuAutoLoginScreen(
                        account = account,
                        onAccountChange = { account = it },
                        onStart = { acct, password -> runAutoLogin(acct, password) },
                        onBack = { finish() }
                    )
                }

                // 错误与状态提示: 中央 errorMsg 卡片 + 底部 statusMsg
                errorMsg?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SleepyTheme.colors.errorContainer
                            )
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                color = SleepyTheme.colors.onErrorContainer
                            )
                        }
                    }
                }
                statusMsg?.let { msg ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Snackbar(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(msg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XmuAutoLoginScreen(
    account: String,
    onAccountChange: (String) -> Unit,
    onStart: (account: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = SleepyTheme.colors
    var password by remember { mutableStateOf("") }

    fun submit() {
        onStart(account.trim(), password)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.xmu_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.xmu_import_auto_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                value = account,
                onValueChange = onAccountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.xmu_account_label)) },
                placeholder = { Text(stringResource(R.string.xmu_account_placeholder), color = colors.onSurfaceVariant) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = SleepyTheme.fieldShape,
                colors = SleepyTheme.fieldColors()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.xmu_auto_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = SleepyTheme.fieldShape,
                colors = SleepyTheme.fieldColors()
            )
            Button(
                onClick = { submit() },
                enabled = account.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SleepyTheme.Buttons.regularHeight),
                shape = SleepyTheme.Buttons.shape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(
                    text = stringResource(R.string.xmu_auto_login_button),
                    color = colors.onPrimary
                )
            }
            Text(
                text = stringResource(R.string.xmu_auto_tip),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}
