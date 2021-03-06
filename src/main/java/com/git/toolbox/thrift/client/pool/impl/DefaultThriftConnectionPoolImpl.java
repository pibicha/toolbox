/**
 * 
 */
package com.git.toolbox.thrift.client.pool.impl;

import com.git.toolbox.thrift.client.pool.ThriftConnectionPoolProvider;
import com.git.toolbox.thrift.client.pool.ThriftServerInfo;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;

import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * <p>
 * DefaultThriftConnectionPoolImpl class.
 * </p>
 */
public final class DefaultThriftConnectionPoolImpl implements ThriftConnectionPoolProvider {

    private static final Logger logger = getLogger(DefaultThriftConnectionPoolImpl.class);
    private static final int MIN_CONN = 1;
    private static final int MAX_CONN = 1000;
    private static final int TIMEOUT = (int) MINUTES.toMillis(5);

    private final GenericKeyedObjectPool<ThriftServerInfo, TTransport> connections;

    /**
     * <p>
     * Constructor for DefaultThriftConnectionPoolImpl.
     * </p>
     *
     * @param config a
     *        {@link GenericKeyedObjectPoolConfig}
     *        object.
     * @param transportProvider a {@link Function}
     *        object.
     */
    public DefaultThriftConnectionPoolImpl(GenericKeyedObjectPoolConfig config,
            Function<ThriftServerInfo, TTransport> transportProvider) {
        connections = new GenericKeyedObjectPool<>(new ThriftConnectionFactory(transportProvider),
                config);
    }

    /**
     * <p>
     * Constructor for DefaultThriftConnectionPoolImpl.
     * </p>
     *
     * @param config a
     *        {@link GenericKeyedObjectPoolConfig}
     *        object.
     */
    public DefaultThriftConnectionPoolImpl(GenericKeyedObjectPoolConfig config) {
        this(config, info -> {
            TSocket tsocket = new TSocket(info.getHost(), info.getPort());
            tsocket.setTimeout(TIMEOUT);
            return new TFramedTransport(tsocket);
        });
    }

    /**
     * <p>
     * getInstance.
     * </p>
     *
     */
    public static DefaultThriftConnectionPoolImpl getInstance() {
        return LazyHolder.INSTANCE;
    }

    /** {@inheritDoc} */
    @Override
    public TTransport getConnection(ThriftServerInfo thriftServerInfo) {
        try {
            return connections.borrowObject(thriftServerInfo);
        } catch (Exception e) {
            logger.error("fail to get connection for {}", thriftServerInfo, e);
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void returnConnection(ThriftServerInfo thriftServerInfo, TTransport transport) {
        connections.returnObject(thriftServerInfo, transport);
    }

    /** {@inheritDoc} */
    @Override
    public void returnBrokenConnection(ThriftServerInfo thriftServerInfo, TTransport transport) {
        try {
            connections.invalidateObject(thriftServerInfo, transport);
        } catch (Exception e) {
            logger.error("fail to invalid object:{},{}", thriftServerInfo, transport, e);
        }
    }

    private static class LazyHolder {

        private static final DefaultThriftConnectionPoolImpl INSTANCE;
        static {
            GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
            config.setMaxTotal(MAX_CONN);
            config.setMaxTotalPerKey(MAX_CONN);
            config.setMaxIdlePerKey(MAX_CONN);
            config.setMinIdlePerKey(MIN_CONN);
            config.setTestOnBorrow(true);
            config.setMinEvictableIdleTimeMillis(MINUTES.toMillis(1));
            config.setSoftMinEvictableIdleTimeMillis(MINUTES.toMillis(1));
            config.setJmxEnabled(false);
            INSTANCE = new DefaultThriftConnectionPoolImpl(config);
        }
    }

    public static final class ThriftConnectionFactory implements
                                                     KeyedPooledObjectFactory<ThriftServerInfo, TTransport> {

        private final Function<ThriftServerInfo, TTransport> transportProvider;

        public ThriftConnectionFactory(Function<ThriftServerInfo, TTransport> transportProvider) {
            this.transportProvider = transportProvider;
        }

        /* (non-Javadoc)
         * @see org.apache.commons.pool2.PooledObjectFactory#makeObject()
         */
        @Override
        public PooledObject<TTransport> makeObject(ThriftServerInfo info) throws Exception {
            TTransport transport = transportProvider.apply(info);
            transport.open();
            DefaultPooledObject<TTransport> result = new DefaultPooledObject<>(transport);
            logger.trace("make new thrift connection:{}", info);
            return result;
        }

        /* (non-Javadoc)
         * @see org.apache.commons.pool2.PooledObjectFactory#destroyObject(org.apache.commons.pool2.PooledObject)
         */
        @Override
        public void destroyObject(ThriftServerInfo info, PooledObject<TTransport> p)
                throws Exception {
            TTransport transport = p.getObject();
            if (transport != null && transport.isOpen()) {
                transport.close();
                logger.trace("close thrift connection:{}", info);
            }
        }

        /* (non-Javadoc)
         * @see org.apache.commons.pool2.PooledObjectFactory#validateObject(org.apache.commons.pool2.PooledObject)
         */
        @Override
        public boolean validateObject(ThriftServerInfo info, PooledObject<TTransport> p) {
            try {
                return p.getObject().isOpen();
            } catch (Throwable e) {
                logger.error("fail to validate tsocket:{}", info, e);
                return false;
            }
        }

        /* (non-Javadoc)
         * @see org.apache.commons.pool2.PooledObjectFactory#activateObject(org.apache.commons.pool2.PooledObject)
         */
        @Override
        public void activateObject(ThriftServerInfo info, PooledObject<TTransport> p)
                throws Exception {
            // do nothing
        }

        /* (non-Javadoc)
         * @see org.apache.commons.pool2.PooledObjectFactory#passivateObject(org.apache.commons.pool2.PooledObject)
         */
        @Override
        public void passivateObject(ThriftServerInfo info, PooledObject<TTransport> p)
                throws Exception {
            // do nothing
        }

    }

}
