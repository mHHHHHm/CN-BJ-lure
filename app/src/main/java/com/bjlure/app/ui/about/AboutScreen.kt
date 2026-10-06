package com.bjlure.app.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjlure.app.R
import com.bjlure.app.ui.FishingUiState
import com.bjlure.app.ui.FishingViewModel
import com.bjlure.app.ui.theme.DeepBlue
import com.bjlure.app.ui.theme.NoRed
import com.bjlure.app.ui.theme.NoRedBg
import com.bjlure.app.ui.theme.WarnOrange
import com.bjlure.app.ui.theme.WarnOrangeBg

/**
 * 应用版本号 —— 从 PackageManager 读，不硬编码。
 *
 * 之前把版本号写死在 gradle 里忘了改，连着交付了好几版都叫 1.1。
 * 从包信息读就不会有这个问题：改了 build.gradle.kts 这里自动跟着变。
 */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
}

@Composable
fun AboutScreen(state: FishingUiState, viewModel: FishingViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 这只鱼就是本 App 的吉祥物，也是图标上那条
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.fat_fish),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(0.62f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("大肥鱼历险记", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "只收免费、合法的路亚水域",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(7.dp))
                    // 署名 + 版本：就一行淡字，别做底色块 —— 深蓝药丸在整片浅色卡里太扎眼了
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "by MMH & 大肥鱼",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "v${appVersion()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 这个 App 最想说清楚的一件事
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("三层水域，别搞混", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "这是这个 App 最想讲清楚的一件事，也是很多钓鱼 App 会标错的地方。",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    LayerRow(
                        tag = "禁止垂钓",
                        color = NoRed,
                        bg = NoRedBg,
                        body = "饮用水水源一级保护区、增殖放流水域、公园非钓鱼区。" +
                            "这三类是真不能下杆，App 会把落在这里的点标红并排到列表最后。",
                    )
                    Spacer(Modifier.height(9.dp))
                    LayerRow(
                        tag = "禁渔区 / 禁渔期",
                        color = WarnOrange,
                        bg = WarnOrangeBg,
                        body = "北运河、潮白河、永定河等 179 条河流全年禁渔；密云水库、怀柔水库、海子水库等 " +
                            "40 处水库湖泊每年 4 月 1 日 0 时至 9 月 24 日 24 时禁渔。" +
                            "但通告禁止的是「除钓具（不包括河湖延绳钓）之外的所有作业方式」，" +
                            "一人一杆一饵一钩可以正常钓 —— 所以 App 不会把这类水域标成红色。",
                    )
                    Spacer(Modifier.height(9.dp))
                    LayerRow(
                        tag = "其他水域",
                        color = MaterialTheme.colorScheme.primary,
                        bg = MaterialTheme.colorScheme.primaryContainer,
                        body = "正常垂钓，留意现场标识与临时管制。",
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "法律依据：北京市农业农村局《关于调整禁渔区、禁渔期的通告》" +
                            "京政农发〔2019〕63 号（现行有效）；《关于划定禁止垂钓的增殖放流水域的通告》" +
                            "京农发〔2008〕180 号；北京市水务局市级河湖禁游区 / 禁钓区清单。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // App 内置的禁钓区清单
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("内置禁钓区 ${viewModel.allZones().size} 处", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    viewModel.allZones().forEach { z ->
                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row {
                                Text(z.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (z.type.blocksAngling) "禁止垂钓" else "仅限作业方式",
                                    fontSize = 11.5.sp,
                                    color = if (z.type.blocksAngling) NoRed else WarnOrange,
                                )
                            }
                            Text(
                                z.ruleDescription,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 数据质量：把不确定性摊开给用户看，而不是假装数据很准
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("数据质量", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    state.quality.forEach { (k, v) ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(k, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("$v", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "「坐标为大致位置」「位置待核实」的点在地图上会区别显示，位置待核实的点不上图。" +
                            "宁可标出来不确定，也不给一个看着很准、去了找不着的坐标。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("数据来源", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SourceLine("北京市水务局《市水务局公布 22 条段适宜垂钓区域》（2019-08-12）及其《推荐适宜垂钓区域一览表》附件")
                    SourceLine("北京市农业农村局《关于调整禁渔区、禁渔期的通告》京政农发〔2019〕63 号 附件 1、附件 2")
                    SourceLine("《关于划定禁止垂钓的增殖放流水域的通告》京农发〔2008〕180 号")
                    SourceLine("北京市水务局公布的市级河湖禁游区 / 禁钓区清单")
                    SourceLine("水域边界与点位坐标：OpenStreetMap（ODbL），经 Overpass API 提取")
                    SourceLine("民间钓点线索：《北京路亚野钓钓点分享》（2024）、路亚之家论坛北京版（2017）")

                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WarnOrangeBg, RoundedCornerShape(8.dp))
                            .padding(11.dp)
                    ) {
                        Text(
                            text = "必须知道的局限：官方适宜垂钓区名单发布于 2019 年，河段与设施可能已变化；" +
                                "地图上的禁钓区多边形是水体形状示意，不是法定边界；" +
                                "民间钓点时效性差，鱼情随放水、清淤、施工剧烈变化，不保证有鱼。" +
                                "本 App 只做信息汇总，不构成法律意见。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = WarnOrange,
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("隐私", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "定位只用于计算钓点距离、做距离筛选，全部在本机完成，不上传、不联网。" +
                            "App 不收集任何个人信息，没有账号体系，没有埋点。",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerRow(tag: String, color: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, body: String) {
    Column {
        Box(
            modifier = Modifier
                .background(bg, RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 2.dp)
        ) {
            Text(tag, fontSize = 11.5.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(5.dp))
        Text(body, fontSize = 12.5.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun SourceLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("·", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.fillMaxWidth(0.02f))
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
