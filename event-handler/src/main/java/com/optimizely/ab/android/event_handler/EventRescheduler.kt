/****************************************************************************
 * Copyright 2016, Optimizely, Inc. and contributors                        *
 * *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                            *
 * *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 */
package com.optimizely.ab.android.event_handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import com.optimizely.ab.android.shared.ServiceScheduler
import com.optimizely.ab.android.shared.ServiceScheduler.PendingIntentFactory
import org.slf4j.LoggerFactory

/**
 * Reschedules event flushing after package updates and reboots
 *
 *
 * After the app is updated or the phone is rebooted the event flushing
 * jobs scheduled by [ServiceScheduler] are cancelled.
 *
 *
 * This code is called by the Android Framework.  The Intent Filters are registered
 * AndroidManifest.xml.
 * <pre>
 * `<receiver android:name="com.optimizely.ab.android.event_handler.EventRescheduler" android:enabled="true" android:exported="false">
 * <intent-filter>
 * <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
 * <action android:name="android.intent.action.BOOT_COMPLETED" />
 * <action android:name="android.net.wifi.supplicant.CONNECTION_CHANGE" />
 * </intent-filter>
 * </receiver>
` *
</pre> *
 */
class EventRescheduler : BroadcastReceiver() {
    @JvmField
    var logger = LoggerFactory.getLogger(EventRescheduler::class.java)

    /**
     * Called when intent filter has kicked in.
     * @param context current context
     * @param intent broadcast intent received.  Try and reschedule.
     * @see BroadcastReceiver.onReceive
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (context != null && intent != null) {
            val serviceScheduler = ServiceScheduler(
                    context,
                    PendingIntentFactory(context),
                    LoggerFactory.getLogger(ServiceScheduler::class.java))
            val eventServiceIntent = Intent(context, EventIntentService::class.java)
            reschedule(context, intent, eventServiceIntent, serviceScheduler)
        } else {
            logger.warn("Received invalid broadcast to event rescheduler")
        }
    }

    /**
     * Actually reschedule the service
     * @param context current context
     * @param broadcastIntent broadcast intent (reboot, wifi change, reinstall)
     * @param eventServiceIntent event service intent
     * @param serviceScheduler scheduler for rescheduling.
     */
    fun reschedule(context: Context, broadcastIntent: Intent, eventServiceIntent: Intent, serviceScheduler: ServiceScheduler) {
        if (broadcastIntent.action == Intent.ACTION_BOOT_COMPLETED || broadcastIntent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ServiceScheduler.startService(context, EventIntentService.Companion.JOB_ID, eventServiceIntent)
            logger.info("Rescheduling event flushing if necessary")
        } else if (broadcastIntent.action == WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION && broadcastIntent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
            if (serviceScheduler.isScheduled(eventServiceIntent)) {
                // If we get wifi and the event flushing service is scheduled preemptively
                // flush events before the next interval occurs.  If sending fails even
                // with wifi the service will be rescheduled on the interval.
                // Wifi connection state changes all the time and starting services is expensive
                // so it's important to only do this if we have stored events.
                // In android O and higher, we use a persistent job so we do not need to restart.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    ServiceScheduler.startService(context, EventIntentService.Companion.JOB_ID, eventServiceIntent)
                    logger.info("Preemptively flushing events since wifi became available")
                }
            }
        } else {
            logger.warn("Received unsupported broadcast action to event rescheduler")
        }
    }
}