package com.kactech.atomictc;

import com.google.bitcoin.core.ECKey;

public interface KeyStore {
	public ECKey[] getKeys(byte[]... pubKeyHashes);
}