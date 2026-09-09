package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 「我的-赞赏作者」页 (用户 2026-09-09 指令) — 展示微信 / 支付宝赞赏码。
 * 两张码均为静态图片资源(drawable-nodpi, 不随密度缩放), 直接显示即可扫码。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.donate_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.donate_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            item {
                DonateQrCard(
                    label = stringResource(R.string.donate_wechat),
                    resId = R.drawable.donate_wechat
                )
            }
            item {
                DonateQrCard(
                    label = stringResource(R.string.donate_alipay),
                    resId = R.drawable.donate_alipay
                )
            }
            item {
                Text(
                    text = stringResource(R.string.donate_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DonateQrCard(label: String, resId: Int) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 白底圆角容器: 二维码周围留白, 深色主题下也能正常扫码
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(12.dp)
        ) {
            Image(
                painter = painterResource(resId),
                contentDescription = label,
                modifier = Modifier.width(220.dp).height(220.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
