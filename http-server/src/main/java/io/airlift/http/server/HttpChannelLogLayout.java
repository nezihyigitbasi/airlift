package io.airlift.http.server;

import ch.qos.logback.core.LayoutBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class HttpChannelLogLayout
        extends LayoutBase<HttpChannelEvent>
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    @Override
    public String doLayout(HttpChannelEvent event)
    {
        Optional<List<Long>> contentWithTimestamp = event.getContentWithTimestamp();

        StringBuilder builder = new StringBuilder();
        builder = builder.append(ISO_FORMATTER.format(event.getTimestamp()))
                .append('\t')
                .append(event.getStreamId())
                .append('\t')
                .append(event.getMethod())
                .append('\t')
                .append(event.getUri())
                .append('\t')
                .append(event.getBeginToDispatchTime())
                .append('\t')
                .append(event.getFirstToLastContentTimeInMillis())
                .append('\t')
                .append(event.getProcessingTime())
                .append('\t');

        if (contentWithTimestamp.isPresent()) {
            StringBuilder contentBuffer = new StringBuilder();
            List<Long> contents = contentWithTimestamp.get();
            long firstTimestamp = -1;
            for (int i = 0; i < contents.size(); i++) {
                long content = contents.get(i);
                if (i % 2 == 0) {
                    //size
                    contentBuffer.append(String.valueOf(content));
                }
                else {
                    // timestamp
                    if (firstTimestamp == -1) {
                        // first timestamp
                        contentBuffer.append(String.valueOf(content));
                        firstTimestamp = content;
                    }
                    else {
                        contentBuffer.append(String.valueOf(NANOSECONDS.toMillis(content - firstTimestamp)));
                    }
                }
                if (i != contents.size() - 1) {
                    contentBuffer.append(",");
                }
            }
            builder.append(contentBuffer.toString());
        }
        else {
            builder.append("[]");
        }

        builder.append('\n');
        return builder.toString();
    }

    public static void main(String[] args)
    {
        long now = System.nanoTime();
        List<Long> list = new ArrayList<>();
        list.add(-999L);
        list.add(now);
        list.add(-999L);
        list.add(now + MILLISECONDS.toNanos(1));
        list.add(-999L);
        list.add(now + MILLISECONDS.toNanos(2));
        list.add(-999L);
        list.add(now + MILLISECONDS.toNanos(3));

        HttpChannelLogLayout l = new HttpChannelLogLayout();
        System.out.println(l.doLayout(new HttpChannelEvent(Instant.now(), 1, "uri", 1, 2, 3, "method",
                Optional.of(list))));
    }
}
