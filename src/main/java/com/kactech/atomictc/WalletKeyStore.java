package com.kactech.atomictc;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;

public class WalletKeyStore implements KeyStore {
	Wallet w;

	public WalletKeyStore(Wallet w) {
		super();
		this.w = w;
	}

	@Override
	public ECKey[] getKeys(byte[]... pubKeyHashes) {
		ECKey[] keys = new ECKey[pubKeyHashes.length];
		for (int i = 0; i < pubKeyHashes.length; i++)
			keys[i] = w.findKeyFromPubHash(pubKeyHashes[i]);
		return keys;
	}

}