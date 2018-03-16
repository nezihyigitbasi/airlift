package io.airlift.http.server;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class HttpChannelEvent
{
    private final Instant timestamp;
    private final String uri;
    private final long processingTime;
    private final String method;
    private final int streamId;
    private final long beginToDispatchTime;
    private final long firstToLastContentTimeInMillis;
    private final Optional<List<Long>> contentWithTimestamp;

    public HttpChannelEvent(
            Instant timestamp,
            int streamId,
            String uri,
            long beginToDispatchTime,
            long firstToLastContentTimeInMillis,
            long processingTime,
            String method,
            Optional<List<Long>> contentWithTimestamp)
    {
        this.timestamp = requireNonNull(timestamp, "timestamp is null");
        this.uri = requireNonNull(uri, "uri is null");
        this.processingTime = processingTime;
        this.beginToDispatchTime = beginToDispatchTime;
        this.method = requireNonNull(method, "method is null");
        this.streamId = streamId;
        this.contentWithTimestamp = requireNonNull(contentWithTimestamp, "contentWithTimestamp is null");
        this.firstToLastContentTimeInMillis = firstToLastContentTimeInMillis;
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public String getUri()
    {
        return uri;
    }

    public long getProcessingTime()
    {
        return processingTime;
    }

    public String getMethod()
    {
        return method;
    }

    public int getStreamId()
    {
        return streamId;
    }

    public long getBeginToDispatchTime()
    {
        return beginToDispatchTime;
    }

    public Optional<List<Long>> getContentWithTimestamp()
    {
        return contentWithTimestamp;
    }

    public long getFirstToLastContentTimeInMillis()
    {
        return firstToLastContentTimeInMillis;
    }
}
