package com.singleman.okio;

import java.io.IOException;

public final class Pipe {
    final long maxBufferSize;
    final Buffer buffer = new Buffer();
    boolean sinkClosed;
    boolean sourceClosed;
    private final Sink sink = new PipeSink();
    private final Source source = new PipeSource();

    public Pipe(long maxBufferSize) {
        if (maxBufferSize < 1L) {
            throw new IllegalArgumentException("maxBufferSize < 1: " + maxBufferSize);
        }
        this.maxBufferSize = maxBufferSize;
    }

    public Source source() {
        return source;
    }

    public Sink sink() {
        return sink;
    }

    final class PipeSink implements Sink {
        final Timeout timeout = new Timeout();

        @Override public void write(Buffer source, long byteCount) throws IOException {
            synchronized (buffer) {
                if (sinkClosed) throw new IllegalStateException("closed");

                while (byteCount > 0) {
                    if (sourceClosed) throw new IOException("source is closed");

                    long bufferSpaceAvailable = maxBufferSize - buffer.size();
                    if (bufferSpaceAvailable == 0) {
                        timeout.waitUntilNotified(buffer); // Wait until the source drains the buffer.
                        continue;
                    }

                    long bytesToWrite = Math.min(bufferSpaceAvailable, byteCount);
                    buffer.write(source, bytesToWrite);
                    byteCount -= bytesToWrite;
                    buffer.notifyAll(); // Notify the source that it can resume reading.
                }
            }
        }

        @Override public void flush() throws IOException {
            synchronized (buffer) {
                if (sinkClosed) throw new IllegalStateException("closed");

                while (buffer.size() > 0) {
                    if (sourceClosed) throw new IOException("source is closed");
                    timeout.waitUntilNotified(buffer);
                }
            }
        }

        @Override public void close() throws IOException {
            synchronized (buffer) {
                if (sinkClosed) return;
                try {
                    flush();
                } finally {
                    sinkClosed = true;
                    buffer.notifyAll(); // Notify the source that no more bytes are coming.
                }
            }
        }

        @Override public Timeout timeout() {
            return timeout;
        }
    }

    final class PipeSource implements Source {
        final Timeout timeout = new Timeout();

        @Override public long read(Buffer sink, long byteCount) throws IOException {
            synchronized (buffer) {
                if (sourceClosed) throw new IllegalStateException("closed");

                while (buffer.size() == 0) {
                    if (sinkClosed) return -1L;
                    timeout.waitUntilNotified(buffer); // Wait until the sink fills the buffer.
                }

                long result = buffer.read(sink, byteCount);
                buffer.notifyAll(); // Notify the sink that it can resume writing.
                return result;
            }
        }

        @Override public void close() throws IOException {
            synchronized (buffer) {
                sourceClosed = true;
                buffer.notifyAll(); // Notify the sink that no more bytes are desired.
            }
        }

        @Override public Timeout timeout() {
            return timeout;
        }
    }
}