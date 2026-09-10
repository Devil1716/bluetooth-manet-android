package com.devil1716.bluetoothmanet;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PeerIdentityStoreTest {
    @Test
    public void acceptsFirstKeyAndRejectsSubstitution() throws Exception {
        File file = Files.createTempFile("mesh-peers", ".txt").toFile();
        PeerIdentityStore store = new PeerIdentityStore(file);
        assertEquals(PeerIdentityStore.PutResult.ACCEPTED, store.putVerified("B4Q1", "aabbccdd"));
        assertEquals(PeerIdentityStore.PutResult.UNCHANGED, store.putVerified("b4q1", "AABBCCDD"));
        assertEquals(PeerIdentityStore.PutResult.REJECTED_MISMATCH, store.putVerified("B4Q1", "ffffffff"));
        assertEquals("aabbccdd", store.get("B4Q1"));

        PeerIdentityStore reloaded = new PeerIdentityStore(file);
        assertEquals("aabbccdd", reloaded.get("B4Q1"));
        assertTrue(reloaded.fingerprint("B4Q1").contains(" "));
    }
}
