/*
 * Copyright (C) 2026 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.libremobileos.qs.footer.domain.interactor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.SubscriptionManager
import android.text.BidiFormatter
import android.text.format.Formatter
import com.android.settingslib.net.DataUsageController
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import com.libremobileos.providers.LMOSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Provides the daily data usage shown in the QS footer. */
@SysUISingleton
class QSFooterDataUsageInteractor
@Inject
constructor(
    @Application private val context: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    systemSettingsRepository: SystemSettingsRepository,
) {

    /** Whether the user enabled the data usage in the QS footer. */
    val isEnabled: Flow<Boolean> =
        systemSettingsRepository.boolSetting(LMOSettings.System.QS_FOOTER_DATA_USAGE, false)

    private val dataController by lazy { DataUsageController(context) }
    private val connectivityManager by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }
    private val wifiManager by lazy { context.getSystemService(WifiManager::class.java) }
    private val subscriptionManager by lazy {
        context.getSystemService(SubscriptionManager::class.java)
    }

    /**
     * Returns the formatted data usage of today for the active network, or `null` if it can't be
     * retrieved.
     */
    suspend fun getDataUsage(): String? =
        withContext(backgroundDispatcher) {
            val info: DataUsageController.DataUsageInfo?
            val suffix: String
            if (isWifiConnected()) {
                info = dataController.wifiDailyDataUsageInfo
                suffix = getWifiSsid()
            } else {
                dataController.setSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId())
                info = dataController.dailyDataUsageInfo
                suffix = getSlotCarrierName()
            }
            if (info == null) return@withContext null
            "${formatDataUsage(info.usageLevel)} ${context.getString(R.string.usage_data)} " +
                "($suffix)"
        }

    private fun formatDataUsage(byteValue: Long): CharSequence {
        val res =
            Formatter.formatBytes(context.resources, byteValue, Formatter.FLAG_IEC_UNITS)
        return BidiFormatter.getInstance()
            .unicodeWrap(
                context.getString(
                    com.android.internal.R.string.fileSizeSuffix,
                    res.value,
                    res.units,
                )
            )
    }

    private fun isWifiConnected(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager?.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getSlotCarrierName(): String {
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val subInfo =
            subscriptionManager?.getActiveSubscriptionInfoList(true)?.firstOrNull {
                it.subscriptionId == subId
            }
        return subInfo?.displayName?.toString()
            ?: context.getString(R.string.usage_data_default_suffix)
    }

    private fun getWifiSsid(): String {
        val wifiInfo = wifiManager?.connectionInfo
        val ssid = wifiInfo?.ssid
        return if (wifiInfo == null || wifiInfo.hiddenSSID || ssid == null ||
                ssid == WifiManager.UNKNOWN_SSID) {
            context.getString(R.string.usage_wifi_default_suffix)
        } else {
            ssid.replace("\"", "")
        }
    }
}
