package io.airlift.http.server;

import io.airlift.log.Logger;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.util.component.Container;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class AirliftServerSelectorListener
        implements ManagedSelector.Listener, Container.InheritedListener
{
    private static final Logger LOG = Logger.get(AirliftServerSelectorListener.class);
    private final ConcurrentMap<ManagedSelector, List<Long>> selections = new ConcurrentHashMap<>();

    @Override
    public void onSelectedBegin(ManagedSelector selector)
    {
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
    public void onSelectedEnd(ManagedSelector selector)
    {
        long now = System.nanoTime();
        List<Long> times = selections.remove(selector);
//        LOG.info("[HTTP2-TESTS] Selection took %d ms, number of keys: %d", NANOSECONDS.toMillis(now - times.get(0)), times.size());
        // dump the list if it takes more than 5s
        if (NANOSECONDS.toSeconds(now - times.get(0)) >= 5) {
            LOG.info("[HTTP2-TESTS] Selected key times: %s", times.stream()
                    .map(time -> NANOSECONDS.toMicros(time - times.get(0)))
                    .map(String::valueOf)
                    .skip(1)
                    .collect(Collectors.joining(",")));
        }
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
