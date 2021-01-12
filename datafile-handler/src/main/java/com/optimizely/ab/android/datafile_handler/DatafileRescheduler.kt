/****************************************************************************
 * Copyright 2016-2017, Optimizely, Inc. and contributors                   *
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
package com.optimizely.ab.android.datafile_handler

import android.content.*
import android.os.Build
import com.optimizely.ab.android.shared.Cache
import com.optimizely.ab.android.shared.ServiceScheduler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Broadcast Receiver that handles app upgrade and phone restart broadcasts in order
 * to reschedule [DatafileService]
 * In order to use this class you must include the declaration in your AndroidManifest.xml.
 * <pre>
 * `<receiver
 * android:name="DatafileRescheduler"
 * android:enabled="true"
 * android:exported="false">
 * <intent-filter>
 * <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
 * <action android:name="android.intent.action.BOOT_COMPLETED" />
 * </intent-filter>
 * </receiver>
` *
</pre> *
 *
 * as well as set the download interval for datafile download in the Optimizely builder.
 */
class DatafileRescheduler : BroadcastReceiver() {
    @JvmField
    var logger = LoggerFactory.getLogger(DatafileRescheduler::class.java)
    override fun onReceive(context: Context, intent: Intent) {
        var intent: Intent? = intent
        if (context != null && intent != null && (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)) {
            logger.info("Received intent with action {}", intent.action)
            val backgroundWatchersCache = BackgroundWatchersCache(
                    Cache(context, LoggerFactory.getLogger(Cache::class.java)),
                    LoggerFactory.getLogger(BackgroundWatchersCache::class.java))
            val dispatcher = Dispatcher(context, backgroundWatchersCache, LoggerFactory.getLogger(Dispatcher::class.java))
            intent = Intent(context, DatafileService::class.java)
            dispatcher.dispatch(intent)
        } else {
            logger.warn("Received invalid broadcast to data file rescheduler")
        }
    }

    /**
     * Handles building sending Intents to [DatafileService]
     *
     * This abstraction mostly makes unit testing easier
     */
    internal class Dispatcher(private val context: Context, private val backgroundWatchersCache: BackgroundWatchersCache, private val logger: Logger) {
        fun dispatch(intent: Intent) {
            val datafileConfigs = backgroundWatchersCache.watchingDatafileConfigs
            for (datafileConfig in datafileConfigs!!) {
                // for scheduled jobs Android O and above, we use the JobScheduler and persistent periodic jobs
                // so, we don't need to do anything.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    intent.putExtra(DatafileService.Companion.EXTRA_DATAFILE_CONFIG, datafileConfig!!.toJSONString())
                    ServiceScheduler.startService(context, DatafileService.Companion.JOB_ID, intent)
                    logger.info("Rescheduled data file watching for project {}", datafileConfig)
                }
            }
        }
    }
}