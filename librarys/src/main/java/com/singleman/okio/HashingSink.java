package com.singleman.okio;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.singleman.okio.Util.checkOffsetAndCount;

public final class HashingSink extends ForwardingSink {
    private final MessageDigest messageDigest;

    /** Returns a sink that uses the obsolete MD5 hash algorithm. */
    public static HashingSink md5(Sink sink) {
        return new HashingSink(sink, "MD5");
    }

    /** Returns a sink that uses the obsolete SHA-1 hash algorithm. */
    public static HashingSink sha1(Sink sink) {
        return new HashingSink(sink, "SHA-1");
    }

    /** Returns a sink that uses the SHA-256 hash algorithm. */
    public static HashingSink sha256(Sink sink) {
        return new HashingSink(sink, "SHA-256");
    }

    private HashingSink(Sink sink, String algorithm) {
        super(sink);
        try {
            this.messageDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError();
        }
    }

    @Override public void write(Buffer source, long byteCount) throws IOException {
        checkOffsetAndCount(source.size, 0, byteCount);

        // Hash byteCount bytes from the prefix of source.
        long hashedCount = 0;
        for (Segment s = source.head; hashedCount < byteCount; s = s.next) {
            int toHash = (int) Math.min(byteCount - hashedCount, s.limit - s.pos);
            messageDigest.update(s.data, s.pos, toHash);
            hashedCount += toHash;
        }

        // Write those bytes to the sink.
        super.write(source, byteCount);
    }

    /**
     * Returns the hash of the bytes accepted thus far and resets the internal state of this sink.
     *
     * <p><strong>Warning:</strong> This method is not idempotent. Each time this method is called its
     * internal state is cleared. This starts a new hash with zero bytes accepted.
     */
    public ByteString hash() {
        byte[] result = messageDigest.digest();
        return ByteString.of(result);
    }
}
