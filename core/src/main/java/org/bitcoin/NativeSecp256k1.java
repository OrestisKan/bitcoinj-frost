/*
 * Copyright 2013 Google Inc.
 * Copyright 2014-2016 the libsecp256k1 contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoin;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.math.BigInteger;

import com.google.common.base.Preconditions;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.bitcoin.NativeSecp256k1Util.*;

/**
 * <p>This class holds native methods to handle ECDSA verification.</p>
 *
 * <p>You can find an example library that can be used for this at https://github.com/bitcoin/secp256k1</p>
 *
 * <p>To build secp256k1 for use with bitcoinj, run
 * `./configure --enable-jni --enable-experimental --enable-module-schnorr --enable-module-ecdh`
 * and `make` then copy `.libs/libsecp256k1.so` to your system library path
 * or point the JVM to the folder containing it with -Djava.library.path
 * </p>
 */
public class NativeSecp256k1 {

    private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private static final Lock r = rwl.readLock();
    private static final Lock w = rwl.writeLock();
    private static ThreadLocal<ByteBuffer> nativeECDSABuffer = new ThreadLocal<>();

    /**
     * Verifies the given secp256k1 signature in native code. Calling when enabled == false is undefined (probably
     * library not loaded)
     *
     * @param data      The data which was signed, must be exactly 32 bytes
     * @param signature The signature
     * @param pub       The public key which did the signing
     * @return true if correct signature
     * @throws AssertFailException never thrown?
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] pub) throws AssertFailException {
        Preconditions.checkArgument(data.length == 32 && signature.length <= 520 && pub.length <= 520);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < 520) {
            byteBuff = ByteBuffer.allocateDirect(520);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(data);
        byteBuff.put(signature);
        byteBuff.put(pub);

        r.lock();
        try {
            return secp256k1_ecdsa_verify(byteBuff, Secp256k1Context.getContext(), signature.length, pub.length) == 1;
        } finally {
            r.unlock();
        }
    }

    /**
     * libsecp256k1 Create an ECDSA signature.
     *
     * @param data Message hash, 32 bytes
     * @param sec  Secret key, 32 bytes
     * @return sig byte array of signature
     * @throws AssertFailException on bad signature length
     */
    public static byte[] sign(byte[] data, byte[] sec) throws AssertFailException {
        Preconditions.checkArgument(data.length == 32 && sec.length <= 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < 32 + 32) {
            byteBuff = ByteBuffer.allocateDirect(32 + 32);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(data);
        byteBuff.put(sec);

        byte[][] retByteArray;

        r.lock();
        try {
            retByteArray = secp256k1_ecdsa_sign(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] sigArr = retByteArray[0];
        int sigLen = new BigInteger(new byte[]{retByteArray[1][0]}).intValue();
        int retVal = new BigInteger(new byte[]{retByteArray[1][1]}).intValue();

        assertEquals(sigArr.length, sigLen, "Got bad signature length.");

        return retVal == 0 ? new byte[0] : sigArr;
    }

    /**
     * libsecp256k1 Seckey Verify - returns 1 if valid, 0 if invalid
     *
     * @param seckey ECDSA Secret key, 32 bytes
     * @return true if valid, false if invalid
     */
    public static boolean secKeyVerify(byte[] seckey) {
        Preconditions.checkArgument(seckey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < seckey.length) {
            byteBuff = ByteBuffer.allocateDirect(seckey.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(seckey);

        r.lock();
        try {
            return secp256k1_ec_seckey_verify(byteBuff, Secp256k1Context.getContext()) == 1;
        } finally {
            r.unlock();
        }
    }

    /**
     * libsecp256k1 Compute Pubkey - computes public key from secret key
     *
     * @param seckey ECDSA Secret key, 32 bytes
     * @return pubkey ECDSA Public key, 33 or 65 bytes
     * @throws AssertFailException if bad pubkey length
     */
    // TODO add a 'compressed' arg
    public static byte[] computePubkey(byte[] seckey) throws AssertFailException {
        Preconditions.checkArgument(seckey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < seckey.length) {
            byteBuff = ByteBuffer.allocateDirect(seckey.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(seckey);

        byte[][] retByteArray;

        r.lock();
        try {
            retByteArray = secp256k1_ec_pubkey_create(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] pubArr = retByteArray[0];
        int pubLen = new BigInteger(new byte[]{retByteArray[1][0]}).intValue();
        int retVal = new BigInteger(new byte[]{retByteArray[1][1]}).intValue();

        assertEquals(pubArr.length, pubLen, "Got bad pubkey length.");

        return retVal == 0 ? new byte[0] : pubArr;
    }

    /**
     * libsecp256k1 Cleanup - This destroys the secp256k1 context object This should be called at the end of the program
     * for proper cleanup of the context.
     */
    public static synchronized void cleanup() {
        w.lock();
        try {
            secp256k1_destroy_context(Secp256k1Context.getContext());
        } finally {
            w.unlock();
        }
    }

    /**
     * Clone context
     *
     * @return context reference
     */
    public static long cloneContext() {
        r.lock();
        try {
            return secp256k1_ctx_clone(Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }
    }

    /**
     * libsecp256k1 PrivKey Tweak-Mul - Tweak privkey by multiplying to it
     *
     * @param tweak   some bytes to tweak with
     * @param privkey 32-byte seckey
     * @return The tweaked private key
     * @throws AssertFailException assertion failure
     */
    public static byte[] privKeyTweakMul(byte[] privkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(privkey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < privkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(privkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(privkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_privkey_tweak_mul(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] privArr = retByteArray[0];

        int privLen = (byte) new BigInteger(new byte[]{retByteArray[1][0]}).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[]{retByteArray[1][1]}).intValue();

        assertEquals(privArr.length, privLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return privArr;
    }

    /**
     * libsecp256k1 PrivKey Tweak-Add - Tweak privkey by adding to it
     *
     * @param tweak   some bytes to tweak with
     * @param privkey 32-byte seckey
     * @return The tweaked private key
     * @throws AssertFailException assertion failure
     */
    public static byte[] privKeyTweakAdd(byte[] privkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(privkey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < privkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(privkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(privkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_privkey_tweak_add(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] privArr = retByteArray[0];

        int privLen = (byte) new BigInteger(new byte[]{retByteArray[1][0]}).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[]{retByteArray[1][1]}).intValue();

        assertEquals(privArr.length, privLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return privArr;
    }

    /**
     * libsecp256k1 PubKey Tweak-Add - Tweak pubkey by adding to it
     *
     * @param tweak  some bytes to tweak with
     * @param pubkey 32-byte seckey
     * @return The tweaked private key
     * @throws AssertFailException assertion failure
     */
    public static byte[] pubKeyTweakAdd(byte[] pubkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(pubkey.length == 33 || pubkey.length == 65);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < pubkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(pubkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(pubkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_pubkey_tweak_add(byteBuff, Secp256k1Context.getContext(), pubkey.length);
        } finally {
            r.unlock();
        }

        byte[] pubArr = retByteArray[0];

        int pubLen = (byte) new BigInteger(new byte[]{retByteArray[1][0]}).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[]{retByteArray[1][1]}).intValue();

        assertEquals(pubArr.length, pubLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return pubArr;
    }

    /**
     * libsecp256k1 PubKey Tweak-Mul - Tweak pubkey by multiplying to it
     *
     * @param tweak  some bytes to tweak with
     * @param pubkey 32-byte seckey
     * @return The tweaked private key
     * @throws AssertFailException assertion failure
     */
    public static byte[] pubKeyTweakMul(byte[] pubkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(pubkey.length == 33 || pubkey.length == 65);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < pubkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(pubkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(pubkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_pubkey_tweak_mul(byteBuff, Secp256k1Context.getContext(), pubkey.length);
        } finally {
            r.unlock();
        }

        byte[] pubArr = retByteArray[0];

        int pubLen = (byte) new BigInteger(new byte[]{retByteArray[1][0]}).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[]{retByteArray[1][1]}).intValue();

        assertEquals(pubArr.length, pubLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return pubArr;
    }

    /**
     * libsecp256k1 create ECDH secret - constant time ECDH calculation
     *
     * @param seckey byte array of secret key used in exponentiation
     * @param pubkey byte array of public key used in exponentiation
     * @return the secret
     * @throws AssertFailException assertion failure
     */
    public static byte[] createECDHSecret(byte[] seckey, byte[] pubkey) throws AssertFailException {
        Preconditions.checkArgument(seckey.length <= 32 && pubkey.length <= 65);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < 32 + pubkey.length) {
            byteBuff = ByteBuffer.allocateDirect(32 + pubkey.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(seckey);
        byteBuff.put(pubkey);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_ecdh(byteBuff, Secp256k1Context.getContext(), pubkey.length);
        } finally {
            r.unlock();
        }

        byte[] resArr = retByteArray[0];
        int retVal = new BigInteger(new byte[]{retByteArray[1][0]}).intValue();

        assertEquals(resArr.length, 32, "Got bad result length.");
        assertEquals(retVal, 1, "Failed return value check.");

        return resArr;
    }

    /**
     * libsecp256k1 randomize - updates the context randomization
     *
     * @param seed 32-byte random seed
     * @return true if successful, false otherwise
     * @throws AssertFailException never thrown?
     */
    public static synchronized boolean randomize(byte[] seed) throws AssertFailException {
        Preconditions.checkArgument(seed.length == 32 || seed == null);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < seed.length) {
            byteBuff = ByteBuffer.allocateDirect(seed.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(seed);

        w.lock();
        try {
            return secp256k1_context_randomize(byteBuff, Secp256k1Context.getContext()) == 1;
        } finally {
            w.unlock();
        }
    }

    /**
     * @param data data to sign
     * @param sec  secret key
     * @return Signature or byte[0]
     * @throws AssertFailException assertion failure
     */
    public static byte[] schnorrSign(byte[] data, byte[] sec) throws AssertFailException {
        Preconditions.checkArgument(data.length == 32 && sec.length <= 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null) {
            byteBuff = ByteBuffer.allocateDirect(32 + 32);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        ((Buffer) byteBuff).rewind();
        byteBuff.put(data);
        byteBuff.put(sec);

        byte[][] retByteArray;

        r.lock();
        try {
            retByteArray = secp256k1_schnorr_sign(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] sigArr = retByteArray[0];
        int retVal = new BigInteger(new byte[]{retByteArray[1][0]}).intValue();

        assertEquals(sigArr.length, 64, "Got bad signature length.");

        return retVal == 0 ? new byte[0] : sigArr;
    }

    public static byte[][] generateKeyPair() {
        return generate_key_pair(Secp256k1Context.getContext());
    }

    public static void generateKey(FrostSecret secret, FrostSigner signer) {
        generate_key(secret, signer, Secp256k1Context.getContext());
    }

    public static byte[] getAggregatedPublicKey(FrostSigner[] signers) {
        byte[][] publicKeys = new byte[signers.length][];
        for(int i =0; i<signers.length; i++)
            publicKeys[i] = signers[i].pubkey;
        return get_combined_public_keys(publicKeys, publicKeys.length, Secp256k1Context.getContext());
    }

    public static void keyGenRound2Send(FrostSecret secret, FrostSigner[] signers, FrostSession session, FrostCache cache, int index) {
        send_vss_sign(secret, signers, session, cache, index, Secp256k1Context.getContext());
    }

    public static void keyGenRound2Receive(FrostSigner signer, FrostSession session, FrostCache cache) {
        receive_vss_sign(signer, session, cache, Secp256k1Context.getContext());
    }

    public static byte[] aggregate_vss_signatures(FrostSigner[] signers, FrostSession session) {
        return aggregate_vss_sign(signers, session, Secp256k1Context.getContext());
    }

    public static void keyGenRound1Send(FrostSigner[] signers, FrostSecret secret, FrostSigner signer) {
        byte[][] publicKeys = new byte[signers.length][];
        for(int i =0; i<signers.length; i++)
            publicKeys[i] = signers[i].pubkey;
        send_shares(publicKeys, secret, signer, Secp256k1Context.getContext());

    }

    public static boolean verifyVSS(byte[] signature, FrostSigner aggr_signer, byte[] aggr_key) {
        return verify_vss_sign(signature, aggr_signer, aggr_key, Secp256k1Context.getContext());

    }

    private static byte[] aggregateCommitments(FrostSigner[] signers, FrostSession session, int aggregatorIndex) {
        byte[] aggregate_vss_signatures = NativeSecp256k1.aggregate_vss_signatures(signers, session);
        byte[] aggregated_public_key = NativeSecp256k1.getAggregatedPublicKey(signers);
        if (!NativeSecp256k1.verifyVSS(aggregate_vss_signatures, signers[aggregatorIndex], aggregated_public_key)){
            System.out.println("Commitment Verification Failed!");
            return null;
        }
        return aggregate_vss_signatures;
    }
//  public static byte signFrost()

    public static void signRound1Send(FrostSigner[] signers, FrostSecret secret, byte[] msg, FrostSession session, FrostCache cache, int aggregatorIndex) {
        byte[] sig = aggregateCommitments(signers, session, aggregatorIndex);
        sign_message_first(secret, signers[aggregatorIndex], msg, sig, session, cache, Secp256k1Context.getContext());
    }

    public static void signRound1Receive(int[] participants, FrostSecret secret, FrostSigner[] signers, byte[] msg, FrostSession session, FrostCache cache, int index) {
        sign_message_second(participants, secret, signers, msg, session, cache, index, Secp256k1Context.getContext());
    }

    public static byte[] aggregateFrostSignature(FrostSigner[] signers, FrostSession session, int aggregatorIndex) {
        byte[] sig = aggregateCommitments(signers, session, aggregatorIndex);
        return sign_message_third(sig, signers, session, Secp256k1Context.getContext());
    }

    public static boolean frostVerify(byte[] sig, byte[] msg, byte[] key) {
        return verify_frost(sig, msg, key, Secp256k1Context.getContext());
    }


    public static void keyGenRound1Receive(FrostSigner[] signers, FrostSecret secret, int index) {
        byte[][] shares = new byte[signers.length][];
        for(int i = 0; i < signers.length; i++) {
            shares[i] = signers[i].partial_sig;
        }
        receive_commitments(shares, secret, signers, index, Secp256k1Context.getContext());
    }

    private static native long secp256k1_ctx_clone(long context);

    private static native int secp256k1_context_randomize(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_privkey_tweak_add(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_privkey_tweak_mul(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_pubkey_tweak_add(ByteBuffer byteBuff, long context, int pubLen);

    private static native byte[][] secp256k1_pubkey_tweak_mul(ByteBuffer byteBuff, long context, int pubLen);

    private static native void secp256k1_destroy_context(long context);

    private static native int secp256k1_ecdsa_verify(ByteBuffer byteBuff, long context, int sigLen, int pubLen);

    private static native byte[][] secp256k1_ecdsa_sign(ByteBuffer byteBuff, long context);

    private static native int secp256k1_ec_seckey_verify(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_ec_pubkey_create(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_ec_pubkey_parse(ByteBuffer byteBuff, long context, int inputLen);

    private static native byte[][] secp256k1_schnorr_sign(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_ecdh(ByteBuffer byteBuff, long context, int inputLen);

    private static native byte[][] generate_key_pair(long context);

    private static native void generate_key(FrostSecret secret, FrostSigner signer, long context);

    private static native byte[] get_combined_public_keys(byte[][] publicKeys, int totalNumberOfPublicKeys, long context);

    private static native byte[][] send_shares(byte[][] publicKeys, FrostSecret secret, FrostSigner signer, long context);

    private static native void receive_commitments(byte[][] shares, FrostSecret secret, FrostSigner[] signerS, int index, long context);

    private static native void send_vss_sign(FrostSecret secret, FrostSigner[] signerS, FrostSession session, FrostCache cache, int index, long context);

    private static native void receive_vss_sign(FrostSigner signer, FrostSession session, FrostCache cache, long context);

    private static native byte[] aggregate_vss_sign(FrostSigner[] signerS, FrostSession session, long context);

    private static native boolean verify_vss_sign(byte[] signature, FrostSigner aggr_signer, byte[] aggr_key, long context);

    private static native byte[] sign_message_first(FrostSecret secret, FrostSigner signer, byte[] msg, byte[] sig, FrostSession session, FrostCache cache, long context);

    private static native void sign_message_second(int[] participants, FrostSecret secret, FrostSigner[] signers, byte[] msg, FrostSession session, FrostCache cache, int index, long context);

    private static native byte[] sign_message_third(byte[] sig, FrostSigner[] signers, FrostSession session, long context);

    private static native boolean verify_frost(byte[] sig, byte[] msg, byte[] key, long ctx_l);
}
