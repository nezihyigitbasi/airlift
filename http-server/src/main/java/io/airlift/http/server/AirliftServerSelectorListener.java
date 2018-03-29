package io.airlift.http.server;

import io.airlift.log.Logger;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.statistic.OnOffStatistic;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class AirliftServerSelectorListener
        implements ManagedSelector.Listener, Container.InheritedListener
{
    private static final Logger LOG = Logger.get(AirliftServerSelectorListener.class);

    private final ConcurrentMap<ManagedSelector, List<Long>> selections = new ConcurrentHashMap<>();
    private final ConcurrentMap<SelectionKey, OnOffStatistic> writeblocked = new ConcurrentHashMap<>();
    private long lastSelectedEnd = -1;
    private long lastSelecting = -1;

    @Override
    public void onSelecting(ManagedSelector selector)
    {
        long now = System.nanoTime();
        if (lastSelectedEnd != -1) {
            long sinceSelected = NANOSECONDS.toMillis(now - lastSelectedEnd);
            if (sinceSelected > 100) {
                LOG.info("[HTTP2-TESTS] onSelecting: sinceSelected %d > 100ms", sinceSelected);
            }
        }
        lastSelectedEnd = -1;
        lastSelecting = now;
    }

    @Override
    public void onSelectedBegin(ManagedSelector selector)
    {
        long now = System.nanoTime();

        if (lastSelecting != -1) {
            long selecting = NANOSECONDS.toMillis(now - lastSelecting);
            if (selecting > 1000) {
                LOG.info("[HTTP2-TESTS] onSelectedBegin: selecting %d > 1000ms", selecting);
            }
        }

        lastSelecting = -1;

        ArrayList<Long> times = new ArrayList<>();
        times.add(System.nanoTime());
        selections.put(selector, times);
    }

    @Override
    public void onSelectedKey(ManagedSelector selector, SelectionKey key)
    {
        selections.get(selector).add(System.nanoTime());
    }

    @Override
    public void onUpdatedKey(ManagedSelector selector, SelectionKey selectionKey)
    {
        OnOffStatistic blocked = writeblocked.get(selectionKey);
        boolean isWriteInterested = (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;

        if (isWriteInterested) {
            if (blocked == null) {
                blocked = new OnOffStatistic(true);
                writeblocked.put(selectionKey, blocked);
            }
            else {
                blocked.record(true);
            }
        }
        else if (blocked != null && blocked.record(false)) {
            if (blocked.getLastOn(MILLISECONDS) > 1000) {
                LOG.info("[HTTP2-TESTS] WRITE BLOCKED > 1000ms: %s %s", blocked, selectionKey.attachment());
            }
        }
    }

    @Override
    public void onSelectedEnd(ManagedSelector selector)
    {
        List<Long> times = selections.remove(selector);
//        LOG.info("[HTTP2-TESTS] Selection took %d ms, number of keys: %d", NANOSECONDS.toMillis(now - times.get(0)), times.size());
        // dump the list if it takes more than 1s
        if (times.size() > 1) {
            long total = NANOSECONDS.toMillis(times.get(times.size() - 1) - times.get(0));
            if (total >= 1000) {
                LOG.info("[HTTP2-TESTS] onSelectedEnd > 1000ms: %s", times.stream()
                        .map(time -> NANOSECONDS.toMillis(time - times.get(0)))
                        .map(String::valueOf)
                        .skip(1)
                        .collect(Collectors.joining(",")));
            }
        }
        lastSelectedEnd = System.nanoTime();
    }

    @Override
    public void onClosed(ManagedSelector selector, SelectionKey selectionKey)
    {
        writeblocked.remove(selectionKey);
    }

    @Override
    public void beanAdded(Container parent, Object child)
    {
    }

    @Override
    public void beanRemoved(Container parent, Object child)
    {
    }
}
