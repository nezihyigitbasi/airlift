package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.thread.Scheduler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * For IP address literals this SocketAddressResolver implementation does not dispatch the address resolution
 * to the executor. Under high load this helps offloading the executor, which may be shared by multiple different
 * HTTP clients.
 */
public class JettyAsyncSocketAddressResolver
        extends SocketAddressResolver.Async
{
    public JettyAsyncSocketAddressResolver(Executor executor, Scheduler scheduler, long timeout)
    {
        super(executor, scheduler, timeout);
    }

    @Override
    public void resolve(String host, int port, Promise<List<InetSocketAddress>> promise)
    {
        //TODO Update after https://github.com/google/guava/issues/3001
        try {
            InetAddress address = InetAddresses.forString(host);
            promise.succeeded(ImmutableList.of(new InetSocketAddress(address, port)));
            return;
        }
        catch (IllegalArgumentException ignored) {
            // not an IP address literal
        }
        super.resolve(host, port, promise);
    }
}
