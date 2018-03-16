package io.airlift.http.server;

import org.eclipse.jetty.server.HttpChannel.Listener;
import org.eclipse.jetty.server.Request;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class AirliftChannelListener
        implements Listener
{
    private final DelimitedChannelListenerLog logger;

    public AirliftChannelListener(DelimitedChannelListenerLog logger)
    {
        this.logger = requireNonNull(logger, "logger is null");
    }

    private final String requestBeginAttr = getClass().getName() + ".begin";
    private final String requestBegintToBeginDispacthAttr = getClass().getName() + ".begin_to_dispatch";
    private final String responseContentListAttr = getClass().getName() + ".response_content_list";
    private final String responseContentListPreviousTimestampAttr = getClass().getName() + ".response_content_list_previous_timestamp";

    @Override
    public void onRequestBegin(Request request)
    {
        request.setAttribute(requestBeginAttr, System.nanoTime());
    }

    @Override
    public void onBeforeDispatch(Request request)
    {
        long now = System.nanoTime();
        long requestBeginTime = (Long) request.getAttribute(requestBeginAttr);

        // time [ns] from onRequestBegin() to onBeforeDispatch()
        request.setAttribute(requestBegintToBeginDispacthAttr, now - requestBeginTime);
    }

    @Override
    public void onResponseBegin(Request request)
    {
        request.setAttribute(responseContentListAttr, new ArrayList<Long>());
    }

    @Override
    public void onResponseContent(Request request, ByteBuffer content)
    {
        List<Long> contentList = (List<Long>) request.getAttribute(responseContentListAttr);
        contentList.add((long) content.remaining());
        contentList.add(System.nanoTime());
    }

    @Override
    public void onComplete(Request request)
    {
        long now = System.nanoTime();
        long requestBeginTime = (Long) request.getAttribute(requestBeginAttr);
        long elapsedMillis = NANOSECONDS.toMillis(now - requestBeginTime);

        List<Long> contentList = (List<Long>) request.getAttribute(responseContentListAttr);
        Optional<List<Long>> contentWithTimestamp = Optional.of(contentList);
        long firstTimestamp = contentList.get(1);
        long lastTimestamp = contentList.get(contentList.size() - 1);
        long firstToLastContentTimeInMillis = NANOSECONDS.toMillis(lastTimestamp - firstTimestamp);
//        if (firstToLastContentTimeInMillis >= 5_000) {
//            contentWithTimestamp = Optional.of(contentList);
//        }

        // dump the time [ms] from onRequestBegin() to onBeforeDispatch()
        long beginToDispatchMillis = NANOSECONDS.toMillis((Long) request.getAttribute(requestBegintToBeginDispacthAttr));
        logger.log(request, beginToDispatchMillis, firstToLastContentTimeInMillis, elapsedMillis, contentWithTimestamp);
    }
}
