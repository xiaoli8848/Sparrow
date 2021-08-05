package org.to2mbn.jmccc.mcdownloader.download;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.to2mbn.jmccc.mcdownloader.download.concurrent.CompletedFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Future;

public class CachedDownloader implements DownloaderService {

    public static final String DEFAULT_CACHE_NAME = CachedDownloader.class.getCanonicalName();
    private final DownloaderService upstream;
    private final Cache<URI, byte[]> cache;
    private final CacheManager cacheManager;
    public CachedDownloader(DownloaderService upstream, CacheManagerBuilder<CacheManager> cacheBuilder) {
        this(upstream, cacheBuilder, DEFAULT_CACHE_NAME);
    }

    public CachedDownloader(DownloaderService upstream, CacheManagerBuilder<CacheManager> cacheBuilder, String cacheName) {
        Objects.requireNonNull(upstream);
        Objects.requireNonNull(cacheBuilder);
        Objects.requireNonNull(cacheName);
        this.upstream = upstream;

        cacheManager = cacheBuilder.build(true);
        cache = cacheManager.getCache(cacheName, URI.class, byte[].class);
        if (cache == null) {
            throw new IllegalArgumentException(String.format("No such cache [%s]", cacheName));
        }
    }

    @Override
    public <T> Future<T> download(DownloadTask<T> task, DownloadCallback<T> callback) {
        return downloadIfNecessary(task, callback, -1);
    }

    @Override
    public <T> Future<T> download(DownloadTask<T> task, DownloadCallback<T> callback, int tries) {
        return downloadIfNecessary(task, callback, tries);
    }

    @Override
    public void shutdown() {
        try {
            upstream.shutdown();
        } finally {
            cacheManager.close();
        }
    }

    @Override
    public boolean isShutdown() {
        return upstream.isShutdown();
    }

    private <T> Future<T> downloadIfNecessary(DownloadTask<T> task, DownloadCallback<T> callback, int tries) {
        if (task.isCacheable()) {
            byte[] cached = cache.get(task.getURI());
            if (cached == null) {
                return submitToUpstream(new CachingDownloadTask<>(task), callback, tries);
            } else {
                T result;
                try {
                    result = processCache(task, cached);
                } catch (Throwable e) {
                    cache.remove(task.getURI());
                    return submitToUpstream(new CachingDownloadTask<>(task), callback, tries);
                }
                if (callback != null) {
                    callback.done(result);
                }
                return new CompletedFuture<T>(result);
            }
        } else {
            return submitToUpstream(task, callback, tries);
        }
    }

    private <T> Future<T> submitToUpstream(DownloadTask<T> task, DownloadCallback<T> callback, int tries) {
        if (tries == -1) {
            return upstream.download(task, callback);
        } else {
            return upstream.download(task, callback, tries);
        }
    }

    private <T> T processCache(DownloadTask<T> task, byte[] cached) throws Exception {
        DownloadSession<T> session = task.createSession(cached.length);
        try {
            session.receiveData(ByteBuffer.wrap(cached));
        } catch (Throwable e) {
            session.failed();
            throw e;
        }
        return session.completed();
    }

    private class CachingDownloadTask<T> extends DownloadTask<T> {

        private final DownloadTask<T> proxiedTask;

        public CachingDownloadTask(DownloadTask<T> proxiedTask) {
            super(proxiedTask.getURI());
            this.proxiedTask = proxiedTask;
        }

        @Override
        public DownloadSession<T> createSession() throws IOException {
            return new CachingDownloadSession(proxiedTask.createSession(), 8192);
        }

        @Override
        public DownloadSession<T> createSession(long length) throws IOException {
            return new CachingDownloadSession(proxiedTask.createSession(length), length);
        }

        private class CachingDownloadSession implements DownloadSession<T> {

            private final DownloadSession<T> proxiedSession;

            // use SoftReference to prevent OOM
            private SoftReference<ByteArrayOutputStream> bufRef;

            public CachingDownloadSession(DownloadSession<T> proxiedSession, long length) {
                this.proxiedSession = proxiedSession;
                if (length < Integer.MAX_VALUE) {
                    try {
                        bufRef = new SoftReference<>(new ByteArrayOutputStream((int) length));
                    } catch (OutOfMemoryError e) {
                        dropCache();
                    }
                }
            }

            @Override
            public void receiveData(ByteBuffer data) throws IOException {
                byte[] copiedData = new byte[data.remaining()];
                data.get(copiedData);

                proxiedSession.receiveData(ByteBuffer.wrap(copiedData));

                if (bufRef != null) {
                    try {
                        ByteArrayOutputStream buf = bufRef.get();
                        if (buf != null) {
                            buf.write(copiedData);
                        }
                    } catch (OutOfMemoryError e) {
                        dropCache();
                    }
                }
            }

            @Override
            public T completed() throws Exception {
                T result;
                try {
                    result = proxiedSession.completed();
                } catch (Throwable e) {
                    dropCache();
                    throw e;
                }
                saveCache();
                return result;
            }

            @Override
            public void failed() throws Exception {
                dropCache();
                proxiedSession.failed();
            }

            private void dropCache() {
                if (bufRef != null) {
                    bufRef.clear();
                    bufRef = null;
                }
            }

            private void saveCache() {
                if (bufRef != null) {
                    try {
                        ByteArrayOutputStream buf = bufRef.get();
                        if (buf != null) {
                            byte[] data = buf.toByteArray();
                            cache.put(proxiedTask.getURI(), data);
                        }
                    } catch (OutOfMemoryError e) {
                        dropCache();
                    }
                }
            }

        }

    }

}
