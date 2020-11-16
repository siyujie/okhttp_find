package com.singleman.okio;

import java.io.IOException;
public abstract class ForwardingSource implements Source {
    private final Source delegate;

    public ForwardingSource(Source delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate == null");
        this.delegate = delegate;
    }

    /** {@link Source} to which this instance is delegating. */
    public final Source delegate() {
        return delegate;
    }

    @Override public long read(Buffer sink, long byteCount) throws IOException {
        return delegate.read(sink, byteCount);
    }

    @Override public Timeout timeout() {
        return delegate.timeout();
    }

    @Override public void close() throws IOException {
        delegate.close();
    }

    @Override public String toString() {
        return getClass().getSimpleName() + "(" + delegate.toString() + ")";
    }
}
