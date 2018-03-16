/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.server;

import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import io.airlift.log.Logger;
import io.airlift.units.DataSize;
import org.eclipse.jetty.http2.server.HttpChannelOverHTTP2;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.component.LifeCycle;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.airlift.units.DataSize.Unit.MEGABYTE;

class DelimitedChannelListenerLog
        implements LifeCycle
{
    private static final Logger log = Logger.get(DelimitedChannelListenerLog.class);
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final FileSize BUFFER_SIZE_IN_BYTES = new FileSize(new DataSize(1, MEGABYTE).toBytes());

    // Tab-separated
    // Time, ip, method, url, user, agent, response code, request length, response length, response time
    private final AsyncAppenderBase<HttpChannelEvent> asyncAppender;

    public DelimitedChannelListenerLog(String filename, int maxHistory, int queueSize, long maxFileSizeInBytes)
    {
        ContextBase context = new ContextBase();
        HttpChannelLogLayout httpLogLayout = new HttpChannelLogLayout();

        recoverTempFiles(filename);

        RollingFileAppender<HttpChannelEvent> fileAppender = new RollingFileAppender<>();
        SizeAndTimeBasedFNATP<HttpChannelEvent> triggeringPolicy = new SizeAndTimeBasedFNATP<>();
        TimeBasedRollingPolicy<HttpChannelEvent> rollingPolicy = new TimeBasedRollingPolicy<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(filename + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(new FileSize(maxFileSizeInBytes));

        fileAppender.setContext(context);
        fileAppender.setFile(filename);
        fileAppender.setAppend(true);
        fileAppender.setBufferSize(BUFFER_SIZE_IN_BYTES);
        fileAppender.setLayout(httpLogLayout);
        fileAppender.setRollingPolicy(rollingPolicy);

        asyncAppender = new AsyncAppenderBase<>();
        asyncAppender.setContext(context);
        asyncAppender.setQueueSize(queueSize);
        asyncAppender.addAppender(fileAppender);

        rollingPolicy.start();
        triggeringPolicy.start();
        fileAppender.start();
        asyncAppender.start();
    }

    public void log(Request request, long beginToDispatchTime, long firstToLastContentTimeInMillis, long processingTime, Optional<List<Long>> contentWithTimestamp)
    {
        HttpChannelOverHTTP2 http2Channel = (HttpChannelOverHTTP2) request.getHttpChannel();
        int streamId = http2Channel.getHttpTransport().getStream().getId();
        asyncAppender.doAppend(new HttpChannelEvent(Instant.ofEpochMilli(request.getTimeStamp()), streamId, request.getRequestURI(), beginToDispatchTime, firstToLastContentTimeInMillis, processingTime, request.getMethod(), contentWithTimestamp));
    }

    @Override
    public void start()
            throws Exception
    {
    }

    @Override
    public void stop()
            throws Exception
    {
        asyncAppender.stop();
    }

    @Override
    public boolean isRunning()
    {
        return true;
    }

    @Override
    public boolean isStarted()
    {
        return true;
    }

    @Override
    public boolean isStarting()
    {
        return false;
    }

    @Override
    public boolean isStopping()
    {
        return false;
    }

    @Override
    public boolean isStopped()
    {
        return false;
    }

    @Override
    public boolean isFailed()
    {
        return false;
    }

    @Override
    public void addLifeCycleListener(Listener listener)
    {
    }

    @Override
    public void removeLifeCycleListener(Listener listener)
    {
    }

    private static void recoverTempFiles(String logPath)
    {
        // logback has a tendency to leave around temp files if it is interrupted
        // these .tmp files are log files that are about to be compressed.
        // This method recovers them so that they aren't orphaned

        File logPathFile = new File(logPath).getParentFile();
        File[] tempFiles = logPathFile.listFiles((dir, name) -> {
            return name.endsWith(TEMP_FILE_EXTENSION);
        });

        if (tempFiles != null) {
            for (File tempFile : tempFiles) {
                String newName = tempFile.getName().substring(0, tempFile.getName().length() - TEMP_FILE_EXTENSION.length());
                File newFile = new File(tempFile.getParent(), newName + LOG_FILE_EXTENSION);
                if (tempFile.renameTo(newFile)) {
                    log.info("Recovered temp file: %s", tempFile);
                }
                else {
                    log.warn("Could not rename temp file [%s] to [%s]", tempFile, newFile);
                }
            }
        }
    }
}
