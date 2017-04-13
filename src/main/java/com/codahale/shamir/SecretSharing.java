package com.codahale.shamir;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Two static methods which use Shamir's Secret Sharing over {@code GF(256)} to
 * securely split secrets into {@code N} shares, of which {@code K} can be
 * combined to recover the original secret.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Shamir%27s_Secret_Sharing>Shamir's Secret Sharing</a>
 */
public final class SecretSharing {
    private SecretSharing() {
        throw new AssertionError("No SecretSharing instances for you!");
    }

    /**
     * Splits the given secret into {@code n} shares, of which any {@code k} or
     * more can be combined to recover the original secret.
     *
     * @param n      the number of shares to produce (must be {@code >1})
     * @param k      the threshold of combinable shares (must be {@code <= n})
     * @param secret the secret to split
     * @return a set of {@code n} {@link Share} instances
     */
    public static Set<Share> split(@Nonnegative int n, @Nonnegative int k, @Nonnull byte[] secret) {
        if (k <= 1) {
            throw new IllegalArgumentException("K must be > 1");
        }

        if (n < k) {
            throw new IllegalArgumentException("N must be >= K");
        }

        // generate shares
        final byte[][] shares = new byte[n][secret.length];
        for (int i = 0; i < secret.length; i++) {
            // for each byte, generate a random polynomial, p
            final byte[] p = GF256.generate(k - 1, secret[i]);
            for (byte x = 1; x <= n; x++) {
                // each share's byte is p(shareId)
                shares[x - 1][i] = GF256.eval(p, x);
            }
        }

        // accumulate shares in a set
        final Set<Share> result = new HashSet<>(n);
        for (int i = 0; i < shares.length; i++) {
            result.add(new Share(i + 1, shares[i]));
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Combines the given shares into the original secret.
     * <p>
     * <b>N.B.:</b> There is no way to determine whether or not the returned
     * value is actually the original secret. If the shares are incorrect, or
     * are under the threshold value used to split the secret, a random value
     * will be returned.
     *
     * @param shares a set of {@link Share} instances
     * @return the original secret
     * @throws IllegalArgumentException if {@code shares} is empty or contains
     *                                  values of varying lengths
     */
    public static byte[] combine(@Nonnull Set<Share> shares) {
        final int[] lens = shares.stream().mapToInt(s -> s.getValue().length).distinct().toArray();
        if (lens.length == 0) {
            throw new IllegalArgumentException("No shares provided");
        } else if (lens.length != 1) {
            throw new IllegalArgumentException("Varying lengths of share values");
        }

        final byte[] secret = new byte[lens[0]];
        for (int i = 0; i < secret.length; i++) {
            final byte[][] points = new byte[shares.size()][secret.length];
            int p = 0;
            for (Share share : shares) {
                points[p][GF256.X] = (byte) share.id;
                points[p][GF256.Y] = share.value[i];
                p++;
            }
            secret[i] = GF256.interpolate(points);
        }
        return secret;
    }
}
